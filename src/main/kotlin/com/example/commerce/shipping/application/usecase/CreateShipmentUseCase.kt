package com.example.commerce.shipping.application.usecase

import com.example.commerce.shipping.application.CreateShipmentCommand
import com.example.commerce.shipping.domain.Shipping
import com.example.commerce.shipping.domain.ShippingStatus
import com.example.commerce.shipping.persistence.ShippingRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 주문당 배송 1건 생성. Outbox는 at-least-once를 가정하므로 이벤트 재처리 시에도 배송은 orderId 기준으로 중복 생성되면 안 된다.
 * order_id UNIQUE + 있으면 반환/없으면 insert, UNIQUE 위반 시 재조회로 멱등을 맞춘다.
 */
@Service
class CreateShipmentUseCase(
    private val shippingRepository: ShippingRepository,
) {

    @Transactional
    fun execute(command: CreateShipmentCommand): Shipping {
        shippingRepository.findByOrderId(command.orderId)?.let { return it }
        return try {
            val shipping = Shipping(orderId = command.orderId, status = ShippingStatus.CREATED)
            shippingRepository.saveAndFlush(shipping)
        } catch (e: DataIntegrityViolationException) {
            // 동시 삽입 시 UNIQUE 위반 → 이미 생성된 건 반환.
            shippingRepository.findByOrderId(command.orderId) ?: throw e
        }
    }
}
