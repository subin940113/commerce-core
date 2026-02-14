package com.example.commerce.order.domain.exception

import com.example.commerce.common.error.DomainException
import com.example.commerce.common.error.ErrorCode

class InventoryNotFoundException(productId: Long) :
    DomainException(ErrorCode.INVENTORY_NOT_FOUND, "Inventory not found: productId=$productId")
