package com.hosopy.actioncable

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.actor
import okhttp3.*
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.Executors
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.CoroutineContext

typealias OkHttpClientFactory = () -> OkHttpClient

class Connection internal constructor(private val uri: URI, private val options: Options) {
    /**
     * Options for connection.
     *
     * @property sslSocketFactory SSLSocketFactory
     * @property trustManager X509TrustManager
     * @property hostnameVerifier HostnameVerifier
     * @property cookieJar CookieJar
     * @property query Query parameters to send on handshake.
     * @property headers HTTP Headers to send on handshake.
     * @property reconnection Whether to reconnect automatically. If reconnection is true, the client attempts to reconnect to the server when underlying connection is stale.
     * @property reconnectionMaxAttempts The maximum number of attempts to reconnect.
     * @property reconnectionDelay First delay seconds of reconnection.
     * @property reconnectionDelayMax Max delay seconds of reconnection.
     * @property okHttpClientFactory To use your own OkHttpClient, set this option.
     */
    data class Options(
        var sslSocketFactory: SSLSocketFactory? = null,
        var trustManager: X509TrustManager? = null,
        var hostnameVerifier: HostnameVerifier? = null,
        var cookieJar: CookieJar? = null,
        var query: Map<String, String>? = null,
        var headers: Map<String, String>? = null,
        var reconnection: Boolean = false,
        var reconnectionMaxAttempts: Int = 30,
        var reconnectionDelay: Int = 3,
        var reconnectionDelayMax: Int = 30,
        var okHttpClientFactory: OkHttpClientFactory? = null
    )

    private enum class State {
        CONNECTING,
        OPEN,
        CLOSING,
        CLOSED
    }

    internal var onOpen: () -> Unit = {}
    internal var onMessage: (jsonString: String) -> Unit = {}
    internal var onClose: () -> Unit = {}
    internal var onFailure: (t: Throwable) -> Unit = {}

    private var state = State.CONNECTING

    private var webSocket: WebSocket? = null

    @ObsoleteCoroutinesApi
    private val operationQueue = SerializedOperationQueue()

    private var isReopening = false

    @ObsoleteCoroutinesApi
    internal fun open() {
        operationQueue.push {
            if (isOpen()) {
                fireOnFailure(IllegalStateException("Must close existing connection before opening"))
            } else {
                doOpen()
            }
        }
    }

    @ObsoleteCoroutinesApi
    internal fun close() {
        operationQueue.push {
            webSocket?.let { webSocket ->
                if (!isState(State.CLOSING, State.CLOSED)) {
                    try {
                        webSocket.close(1000, "connection closed manually")
                        state = State.CLOSING
                    } catch (e: IOException) {
                        fireOnFailure(e)
                    } catch (e: IllegalStateException) {
                        fireOnFailure(e)
                    }
                }
            }
        }
    }

    @ObsoleteCoroutinesApi
    internal fun reopen() {
        if (isState(State.CLOSED)) {
            open()
        } else {
            isReopening = true
            close()
        }
    }

    @ObsoleteCoroutinesApi
    internal fun send(data: String): Boolean {
        if (!isOpen()) return false

        operationQueue.push {
            doSend(data)
        }

        return true
    }

    private fun isState(vararg states: State) = states.contains(state)

    private fun isOpen() = webSocket?.let { isState(State.OPEN) } ?: false

    @ObsoleteCoroutinesApi
    private fun doOpen() {
        state = State.CONNECTING

        val client = options.okHttpClientFactory?.invoke() ?: OkHttpClient()

        client.newBuilder().apply {
            options.sslSocketFactory?.let { sslSocketFactory(it, options.trustManager ?: return) }
            options.hostnameVerifier?.let { hostnameVerifier(it) }
            options.cookieJar?.let { cookieJar(it) }
        }

        val urlBuilder = StringBuilder(uri.toString())

        options.query?.let { urlBuilder.append("?${it.toQueryString()}") }

        val requestBuilder = Request.Builder().url(urlBuilder.toString())

        options.headers?.forEach { (key, value) -> requestBuilder.addHeader(key, value) }

        val request = requestBuilder.build()

        client.newWebSocket(request, webSocketListener)
        client.dispatcher.executorService.shutdown()
    }

    private fun doSend(data: String) {
        webSocket?.let { webSocket ->
            try {
                webSocket.send(data)
            } catch (e: IOException) {
                fireOnFailure(e)
            }
        }
    }

    private fun fireOnFailure(error: Exception) {
        onFailure.invoke(error)
    }

    @ObsoleteCoroutinesApi
    private val webSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            state = State.OPEN
            this@Connection.webSocket = webSocket
            operationQueue.push {
                onOpen.invoke()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            operationQueue.push {
                state = State.CLOSED
                onFailure.invoke(t)
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            operationQueue.push {
                onMessage.invoke(text)
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            state = State.CLOSED
            operationQueue.push {
                state = State.CLOSED

                onClose.invoke()

                if (isReopening) {
                    isReopening = false
                    open()
                }
            }
        }
    }
}

@ObsoleteCoroutinesApi
private class SerializedOperationQueue(capacity: Int = 0) : CoroutineScope {
    val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Unconfined + job

    private val singleThreadContext = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private val actor = actor<suspend () -> Unit>(singleThreadContext, capacity) {
        for (operation in channel) {
            operation.invoke()
        }
    }

    fun push(operation: suspend () -> Unit) = launch {
        actor.send(operation)
    }
}

private fun Map<String, String>.toQueryString(): String {
    return this.keys.mapNotNull { key ->
        this[key]?.let {
            "$key=${URLEncoder.encode(this[key], "UTF-8")}"
        }
    }.joinToString("&")
}
