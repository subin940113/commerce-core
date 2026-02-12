package com.example.commerce.catalog

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "products")
class Product(
    @Id
    val id: Long = 0,

    @Column(nullable = false, length = 200)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: ProductStatus,

    @Column(nullable = false)
    var price: Long,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

enum class ProductStatus {
    ACTIVE,
    INACTIVE,
}
