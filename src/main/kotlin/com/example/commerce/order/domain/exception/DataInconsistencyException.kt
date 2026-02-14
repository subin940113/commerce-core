package com.example.commerce.order.domain.exception

import com.example.commerce.common.error.DomainException
import com.example.commerce.common.error.ErrorCode

class DataInconsistencyException(message: String) :
    DomainException(ErrorCode.DATA_INCONSISTENCY, message)
