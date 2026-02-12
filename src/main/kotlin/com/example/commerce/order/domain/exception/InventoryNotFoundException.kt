package com.example.commerce.order.domain.exception

class InventoryNotFoundException(productId: Long) :
    RuntimeException("Inventory not found: productId=$productId")
