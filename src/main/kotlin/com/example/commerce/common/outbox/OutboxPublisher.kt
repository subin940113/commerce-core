package com.example.commerce.common.outbox

import com.example.commerce.common.messaging.RabbitMQConstants
import com.example.commerce.common.messaging.toRoutingKey
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * PENDING 이벤트를 한 번에 N개씩 가져와 RabbitMQ로 발행한다.
 * retry_count 증가 후 MAX_RETRY 초과 시 FAILED로 두어, 운영에서 재처리·관측할 수 있게 한다.
 */
@Component
class OutboxPublisher(
    private val outboxEventRepository: OutboxEventRepository,
    private val rabbitTemplate: RabbitTemplate,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val BATCH_SIZE = 50
        const val MAX_RETRY = 5
    }

    @Transactional
    fun publishPending() {
        val events = outboxEventRepository.findBatchForUpdate(BATCH_SIZE)
        if (events.isEmpty()) return
        log.info("OutboxPublisher starting batch: size={} (max_batch_size={})", events.size, BATCH_SIZE)
        for (event in events) {
            try {
                log.debug("Processing event_id={} event_type={} aggregate_id={}", event.id, event.eventType, event.aggregateId)
                val routingKey = event.eventType.toRoutingKey()
                if (routingKey == null) {
                    log.warn("No routing key for event_type={}, skipping event_id={}", event.eventType, event.id)
                    continue
                }
                rabbitTemplate.convertAndSend(
                    RabbitMQConstants.EXCHANGE_EVENTS,
                    routingKey,
                    event.payload,
                ) { msg ->
                    msg.messageProperties.setHeader("outboxEventId", event.id)
                    msg.messageProperties.setHeader("eventType", event.eventType)
                    msg.messageProperties.setHeader("aggregateId", event.aggregateId)
                    msg
                }
                event.status = OutboxStatus.PUBLISHED
                event.publishedAt = Instant.now()
                outboxEventRepository.saveAndFlush(event)
                log.info("Published event_id={} event_type={} aggregate_id={}", event.id, event.eventType, event.aggregateId)
            } catch (e: Exception) {
                event.retryCount += 1
                val markedFailed = event.retryCount >= MAX_RETRY
                event.status = if (markedFailed) OutboxStatus.FAILED else OutboxStatus.PENDING
                outboxEventRepository.saveAndFlush(event)
                if (markedFailed) {
                    log.error(
                        "Outbox event permanently failed after {} retries: event_id={} event_type={} aggregate_id={} error={}",
                        MAX_RETRY, event.id, event.eventType, event.aggregateId, e.message, e,
                    )
                } else {
                    log.warn(
                        "Failed to publish event_id={} event_type={} aggregate_id={} retry_count={} (max={}): {}",
                        event.id, event.eventType, event.aggregateId, event.retryCount, MAX_RETRY, e.message,
                    )
                }
            }
        }
    }

    @Scheduled(fixedDelay = 2000)
    fun scheduledPublish() {
        publishPending()
    }
}
