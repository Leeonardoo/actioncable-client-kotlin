package com.hosopy.actioncable

import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val TIMEOUT = 10000L

class ConsumerTest {
    @Test
    fun createWithValidUri() {
        val consumer = Consumer(URI("ws://example.com:28080"))
        assertNotNull(consumer)
    }

    @Test
    fun createWithValidUriAndOptions() {
        val consumer = Consumer(URI("ws://example.com:28080"), Consumer.Options())
        assertNotNull(consumer)
    }

    @Test
    fun subscriptions() {
        val consumer = Consumer(URI("ws://example.com:28080"))
        assertNotNull(consumer.subscriptions)
    }

    @Test(timeout = TIMEOUT)
    fun connect() = runBlocking {
        val events = Channel<String>()

        val mockWebServer = MockWebServer()
        val mockResponse = MockResponse().withWebSocketUpgrade(object : DefaultWebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                launch(Unconfined) {
                    events.send("onOpen")
                }
            }
        })
        mockWebServer.enqueue(mockResponse)
        mockWebServer.start()

        val consumer = ActionCable.createConsumer(URI(mockWebServer.url("/").toUri().toString()))
        consumer.connect()

        assertEquals("onOpen", events.receive())

        mockWebServer.shutdown()
    }

    @Test(timeout = TIMEOUT)
    fun disconnect() = runBlocking {
        val events = Channel<String>()

        val mockWebServer = MockWebServer()
        val mockResponse = MockResponse().withWebSocketUpgrade(object : DefaultWebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                launch(Unconfined) {
                    events.send("onOpen")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            }
        })
        mockWebServer.enqueue(mockResponse)
        mockWebServer.start()

        val consumer = ActionCable.createConsumer(URI(mockWebServer.url("/").toUri().toString()))
        consumer.connect()

        assertEquals("onOpen", events.receive())

        consumer.disconnect()

        assertEquals("onClose", events.receive())

        mockWebServer.shutdown()
    }

    @Test(timeout = TIMEOUT)
    fun send() = runBlocking {
        val events = Channel<String>()

        val mockWebServer = MockWebServer()
        val mockResponse = MockResponse().withWebSocketUpgrade(object : DefaultWebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                launch(Unconfined) {
                    events.send("onOpen")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                launch(Unconfined) {
                    events.send("onMessage:$text")
                }
            }
        })
        mockWebServer.enqueue(mockResponse)
        mockWebServer.start()

        val consumer = ActionCable.createConsumer(URI(mockWebServer.url("/").toUri().toString()))
        consumer.connect()

        assertEquals("onOpen", events.receive())

        consumer.send(Command.subscribe("identifier"))

        assertEquals(
            "onMessage:{\"command\":\"subscribe\",\"identifier\":\"identifier\"}",
            events.receive()
        )

        mockWebServer.shutdown()
    }


    private open class DefaultWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        }
    }
}
