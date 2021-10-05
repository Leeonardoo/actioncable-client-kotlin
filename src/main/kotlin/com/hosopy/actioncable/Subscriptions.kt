package com.hosopy.actioncable

/**
 * Collection class for creating (and internally managing) channel subscriptions.
 *
 * ```
 * // Create a subscription instance
 * val subscription = consumer.subscriptions.create(appearanceChannel)
 *
 * // Remove a subscription instance
 * consumer.subscriptions.remove(subscription)
 * ```
 */
class Subscriptions internal constructor(private val consumer: Consumer) {

    private val subscriptions = hashMapOf<String, Subscription>()

    /**
     * Create [Subscription] instance.
     *
     * @param channel Channel to connect
     * @return Subscription instance
     */
    fun create(channel: Channel): Subscription = Subscription(consumer, channel).also {
        subscriptions[channel.identifier] = it
    }

    /**
     * Remove subscription from collection.
     *
     * @param subscription instance to remove
     */
    fun remove(subscription: Subscription) {
        if (subscriptions.remove(subscription.identifier) != null) {
            consumer.send(Command.unsubscribe(subscription.identifier))
        }
    }

    fun remove(channel: Channel) {
        if (subscriptions.remove(channel.identifier) != null) {
            consumer.send(Command.unsubscribe(channel.identifier))
        }
    }

    fun contains(subscription: Subscription) = subscriptions.containsKey(subscription.identifier)

    fun contains(channel: Channel) = subscriptions.containsKey(channel.identifier)

    internal fun reload() {
        subscriptions.forEach { sendSubscribeCommand(it.value) }
    }

    internal fun notifyConnected(identifier: String) {
        subscriptions.filter { it.key == identifier }.forEach { it.value.notifyConnected() }
    }

    internal fun notifyDisconnected() {
        subscriptions.forEach { it.value.notifyDisconnected() }
    }

    internal fun notifyReceived(identifier: String, data: Any?) {
        subscriptions.filter { it.key == identifier }.forEach { it.value.notifyReceived(data) }
    }

    internal fun notifyFailed(error: Throwable) {
        subscriptions.forEach { it.value.notifyFailed(error) }
    }

    internal fun reject(identifier: String) {
        val removal = subscriptions.filter { it.value.identifier == identifier }
        removal.forEach {
            subscriptions.remove(it.key)
        }
        removal.forEach { it.value.notifyRejected() }
    }

    private fun sendSubscribeCommand(subscription: Subscription) {
        consumer.send(Command.subscribe(subscription.identifier))
    }
}
