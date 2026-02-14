package com.example.commerce.order.domain.exception

import com.example.commerce.common.error.DomainException
import com.example.commerce.common.error.ErrorCode

class ProductNotFoundException(productId: Long) :
    DomainException(ErrorCode.PRODUCT_NOT_FOUND, "Product not found: productId=$productId")
