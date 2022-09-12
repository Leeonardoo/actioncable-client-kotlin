package com.hosopy.actioncable

import com.beust.klaxon.JsonObject
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

private const val TIMEOUT = 10000L

class SubscriptionTest {
    @Test
    fun identifier() {
        val consumer = Consumer(URI("ws://example.com:28080"))
        val channel = Channel("CommentsChannel")
        val subscription = Subscription(consumer, channel)

        assertEquals("{\"channel\":\"CommentsChannel\"}", subscription.identifier)
    }

    @Test(timeout = TIMEOUT)
    fun onConnected() = runBlocking {
        val events = Channel<String>()

        val mockWebServer = MockWebServer()
        val mockResponse = MockResponse().withWebSocketUpgrade(object : DefaultWebSocketListener() {
            private var currentWebSocket: WebSocket? = null

            override fun onOpen(webSocket: WebSocket, response: Response) {
                currentWebSocket = webSocket
                // send welcome message
                launch(Unconfined) {
                    currentWebSocket?.send("{\"type\":\"welcome\"}")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("subscribe")) {
                    // accept subscribe command
                    launch(Unconfined) {
                        currentWebSocket?.send(
                            "{\"identifier\":\"{\\\"channel\\\":\\\"CommentsChannel\\\"}\",\"type\":\"confirm_subscription\"}"
                        )
                    }
                }
            }
        })
        mockWebServer.enqueue(mockResponse)
        mockWebServer.start()

        val channel = Channel("CommentsChannel")
        val consumer = Consumer(URI(mockWebServer.url("/").toUri().toString()))
        val subscription = consumer.subscriptions.create(channel)

        subscription.onConnected = {
            launch(Unconfined) {
                events.send("onConnected")
            }
        }

        consumer.connect()

        assertEquals("onConnected", events.receive())

        mockWebServer.shutdown()
    }

    @Test(timeout = TIMEOUT)
    fun onRejected() = runBlocking {
        val events = Channel<String>()

        val mockWebServer = MockWebServer()
        val mockResponse = MockResponse().withWebSocketUpgrade(object : DefaultWebSocketListener() {
            private var currentWebSocket: WebSocket? = null

            override fun onOpen(webSocket: WebSocket, response: Response) {
                currentWebSocket = webSocket
                // send welcome message
                launch(Unconfined) {
                    currentWebSocket?.send("{\"type\":\"welcome\"}")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {

                if (text.contains("subscribe")) {
                    // reject subscribe command
                    launch(Unconfined) {
                        currentWebSocket?.send(
                            "{\"identifier\":\"{\\\"channel\\\":\\\"CommentsChannel\\\"}\",\"type\":\"reject_subscription\"}"
                        )
                    }
                }
            }
        })
        mockWebServer.enqueue(mockResponse)
        mockWebServer.start()

        val channel = Channel("CommentsChannel")
        val consumer = Consumer(URI(mockWebServer.url("/").toUri().toString()))
        val subscription = consumer.subscriptions.create(channel)

        subscription.onRejected = {
            launch(Unconfined) {
                events.send("onRejected")
            }
        }

        consumer.connect()

        assertEquals("onRejected", events.receive())

        mockWebServer.shutdown()
    }

    @Test(timeout = TIMEOUT)
    fun onReceived() = runBlocking {
        val events = Channel<String>()

        val mockWebServer = MockWebServer()
        val mockResponse = MockResponse().withWebSocketUpgrade(object : DefaultWebSocketListener() {
            private var currentWebSocket: WebSocket? = null

            override fun onOpen(webSocket: WebSocket, response: Response) {
                currentWebSocket = webSocket
                // send welcome message
                launch(Unconfined) {
                    currentWebSocket?.send("{\"type\":\"welcome\"}")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("subscribe")) {
                    // accept subscribe command
                    launch(Unconfined) {
                        currentWebSocket?.send(
                            "{\"identifier\":\"{\\\"channel\\\":\\\"CommentsChannel\\\"}\",\"type\":\"confirm_subscription\"}"
                        )
                    }
                } else if (text.contains("hello")) {
                    launch(Unconfined) {
                        currentWebSocket?.send(
                            "{\"identifier\":\"{\\\"channel\\\":\\\"CommentsChannel\\\"}\",\"message\":{\"foo\":\"bar\"}}"
                        )
                    }
                }
            }
        })
        mockWebServer.enqueue(mockResponse)
        mockWebServer.start()

        val channel = Channel("CommentsChannel")
        val consumer = Consumer(URI(mockWebServer.url("/").toUri().toString()))
        val subscription = consumer.subscriptions.create(channel)

        subscription.onConnected = {
            subscription.perform("hello")
        }

        subscription.onReceived = { data ->
            launch(Unconfined) {
                events.send("onReceived:${(data as JsonObject).toJsonString()}")
            }
        }

        subscription.onDisconnected = {

        }

        consumer.connect()

        assertEquals("onReceived:{\"foo\":\"bar\"}", events.receive())

        mockWebServer.shutdown()
    }

    @Test(timeout = TIMEOUT)
    fun onFailed() = runBlocking {
        val events = Channel<String>()

        val mockWebServer = MockWebServer()
        val mockResponse = MockResponse()
        mockResponse.setResponseCode(500)
        mockResponse.status = "HTTP/1.1 500 Internal Server Error"
        mockWebServer.enqueue(mockResponse)
        mockWebServer.start()

        val channel = Channel("CommentsChannel")
        val consumer = Consumer(URI(mockWebServer.url("/").toUri().toString()))
        val subscription = consumer.subscriptions.create(channel)

        subscription.onFailed = {
            launch(Unconfined) {
                events.send("onFailed")
            }
        }

        consumer.connect()

        assertEquals("onFailed", events.receive())

        mockWebServer.shutdown()
    }

    @Test(timeout = TIMEOUT)
    fun performWithoutParams() = runBlocking {
        val events = Channel<String>()

        val mockWebServer = MockWebServer()
        val mockResponse = MockResponse().withWebSocketUpgrade(object : DefaultWebSocketListener() {
            private var currentWebSocket: WebSocket? = null

            override fun onOpen(webSocket: WebSocket, response: Response) {
                currentWebSocket = webSocket
                // send welcome message
                launch(Unconfined) {
                    currentWebSocket?.send("{\"type\":\"welcome\"}")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("subscribe")) {
                    // accept subscribe command
                    launch(Unconfined) {
                        currentWebSocket?.send(
                            "{\"identifier\":\"{\\\"channel\\\":\\\"CommentsChannel\\\"}\",\"type\":\"confirm_subscription\"}"
                        )
                    }
                } else if (text.contains("hello")) {
                    launch(Unconfined) {
                        events.send("onMessage:$text")
                    }
                }
            }
        })
        mockWebServer.enqueue(mockResponse)
        mockWebServer.start()

        val channel = Channel("CommentsChannel")
        val consumer = Consumer(URI(mockWebServer.url("/").toUri().toString()))
        val subscription = consumer.subscriptions.create(channel)

        subscription.onConnected = {
            subscription.perform("hello")
        }

        consumer.connect()

        assertEquals(
            "onMessage:{\"command\":\"message\",\"identifier\":\"{\\\"channel\\\":\\\"CommentsChannel\\\"}\",\"data\":\"{\\\"action\\\":\\\"hello\\\"}\"}",
            events.receive()
        )

        mockWebServer.shutdown()
    }

    @Test(timeout = TIMEOUT)
    fun performWithParams() = runBlocking {
        val events = Channel<String>()

        val mockWebServer = MockWebServer()
        val mockResponse = MockResponse().withWebSocketUpgrade(object : DefaultWebSocketListener() {
            private var currentWebSocket: WebSocket? = null

            override fun onOpen(webSocket: WebSocket, response: Response) {
                currentWebSocket = webSocket
                // send welcome message
                launch(Unconfined) {
                    currentWebSocket?.send("{\"type\":\"welcome\"}")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("subscribe")) {
                    // accept subscribe command
                    launch(Unconfined) {
                        currentWebSocket?.send(
                            "{\"identifier\":\"{\\\"channel\\\":\\\"CommentsChannel\\\"}\",\"type\":\"confirm_subscription\"}"
                        )
                    }
                } else if (text.contains("hello")) {
                    launch(Unconfined) {
                        events.send("onMessage:$text")
                    }
                }
            }
        })
        mockWebServer.enqueue(mockResponse)
        mockWebServer.start()

        val channel = Channel("CommentsChannel")
        val consumer = Consumer(URI(mockWebServer.url("/").toUri().toString()))
        val subscription = consumer.subscriptions.create(channel)

        subscription.onConnected = {
            subscription.perform("hello", mapOf("foo" to "bar"))
        }

        consumer.connect()

        assertEquals(
            "onMessage:{\"command\":\"message\",\"identifier\":\"{\\\"channel\\\":\\\"CommentsChannel\\\"}\",\"data\":\"{\\\"foo\\\":\\\"bar\\\",\\\"action\\\":\\\"hello\\\"}\"}",
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
