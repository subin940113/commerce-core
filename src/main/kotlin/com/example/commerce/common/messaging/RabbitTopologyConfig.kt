package com.example.commerce.common.messaging

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitTopologyConfig {

    @Bean
    fun messageConverter(): MessageConverter = Jackson2JsonMessageConverter()

    @Bean
    fun eventsExchange(): TopicExchange =
        TopicExchange(RabbitMQConstants.EXCHANGE_EVENTS, true, false)

    @Bean
    fun eventsDlxExchange(): TopicExchange =
        TopicExchange(RabbitMQConstants.EXCHANGE_DLX, true, false)

    @Bean
    fun shippingPaymentAuthorizedQueue(): Queue =
        QueueBuilder.durable(RabbitMQConstants.QUEUE_SHIPPING_PAYMENT_AUTHORIZED)
            .withArgument("x-dead-letter-exchange", RabbitMQConstants.EXCHANGE_DLX)
            .withArgument("x-dead-letter-routing-key", RabbitMQConstants.ROUTING_KEY_SHIPPING_PAYMENT_AUTHORIZED_DLQ)
            .build()

    @Bean
    fun shippingPaymentAuthorizedDlqQueue(): Queue =
        Queue(RabbitMQConstants.QUEUE_SHIPPING_PAYMENT_AUTHORIZED_DLQ, true, false, false)

    @Bean
    fun bindingShippingPaymentAuthorized(
        shippingPaymentAuthorizedQueue: Queue,
        eventsExchange: TopicExchange,
    ): Binding =
        BindingBuilder
            .bind(shippingPaymentAuthorizedQueue)
            .to(eventsExchange)
            .with(RabbitMQConstants.ROUTING_KEY_PAYMENT_AUTHORIZED_V1)

    @Bean
    fun bindingShippingPaymentAuthorizedDlq(
        shippingPaymentAuthorizedDlqQueue: Queue,
        eventsDlxExchange: TopicExchange,
    ): Binding =
        BindingBuilder
            .bind(shippingPaymentAuthorizedDlqQueue)
            .to(eventsDlxExchange)
            .with(RabbitMQConstants.ROUTING_KEY_SHIPPING_PAYMENT_AUTHORIZED_DLQ)
}
