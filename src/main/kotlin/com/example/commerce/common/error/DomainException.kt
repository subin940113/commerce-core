package com.example.commerce.common.error

/**
 * 도메인 예외의 공통 부모. [ErrorCode]를 보유해 HTTP 상태·클라이언트 코드·메시지 정책을 일원화.
 * 예외 타입별로 핸들러를 늘리지 않고, "도메인 예외 → errorCode 기반 응답" 하나로 처리하기 위함.
 */
open class DomainException(
    open val errorCode: ErrorCode,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
