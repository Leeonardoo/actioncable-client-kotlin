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

private const val TIMEOUT = 10000L

class SubscriptionsTest {

    @Test(timeout = TIMEOUT)
    fun create() = runBlocking {
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
                        currentWebSocket?.send("{\"identifier\":\"{\\\"channel\\\":\\\"CommentsChannel\\\"}\",\"type\":\"confirm_subscription\"}")
                    }
                }
            }
        })
        mockWebServer.enqueue(mockResponse)
        mockWebServer.start()

        val channel = Channel("CommentsChannel")
        val consumer = Consumer(URI(mockWebServer.url("/").toUri().toString()))

        consumer.connect()

        val subscription = consumer.subscriptions.create(channel)
        subscription.onConnected = {
            launch(Unconfined) {
                events.send("onConnected")
            }
        }

        assertEquals("onConnected", events.receive())

        mockWebServer.shutdown()
    }

    @Test(timeout = TIMEOUT)
    fun remove() = runBlocking {
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
                if (text.contains("unsubscribe")) {
                    launch(Unconfined) {
                        events.send(text)
                    }
                } else if (text.contains("subscribe")) {
                    // accept subscribe command
                    launch(Unconfined) {
                        currentWebSocket?.send("{\"identifier\":\"{\\\"channel\\\":\\\"CommentsChannel\\\"}\",\"type\":\"confirm_subscription\"}")
                    }
                }
            }
        })
        mockWebServer.enqueue(mockResponse)
        mockWebServer.start()

        val consumer = Consumer(URI(mockWebServer.url("/").toUri().toString()))
        val subscription1 = consumer.subscriptions.create(Channel("CommentsChannel"))
        val subscription2 = consumer.subscriptions.create(Channel("NotificationChannel"))

        subscription1.onConnected = {
            launch(Unconfined) {
                events.send("onConnected")
            }
        }

        subscription2.onConnected = {
            launch(Unconfined) {
                events.send("onConnected")
            }
        }

        consumer.connect()

        assertEquals("onConnected", events.receive())
        assertEquals("onConnected", events.receive())

        consumer.subscriptions.remove(subscription1)

        assertEquals(false, consumer.subscriptions.contains(subscription1))
        assertEquals(true, consumer.subscriptions.contains(subscription2))
        assertEquals(
            "{\"command\":\"unsubscribe\",\"identifier\":\"{\\\"channel\\\":\\\"CommentsChannel\\\"}\"}",
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
