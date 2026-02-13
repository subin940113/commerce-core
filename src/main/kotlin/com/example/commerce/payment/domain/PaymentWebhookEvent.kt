package com.example.commerce.payment.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "payment_webhook_events")
class PaymentWebhookEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 50)
    val provider: String,

    @Column(name = "provider_event_id", nullable = false, length = 100)
    val providerEventId: String,

    @Column(name = "provider_payment_id", length = 100)
    val providerPaymentId: String? = null,

    @Column(name = "payment_id")
    val paymentId: Long? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val payload: Map<String, Any>? = null,

    @Column(name = "received_at", nullable = false)
    val receivedAt: Instant = Instant.now(),
)
