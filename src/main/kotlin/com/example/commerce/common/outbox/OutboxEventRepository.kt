package com.example.commerce.common.outbox

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OutboxEventRepository : JpaRepository<OutboxEvent, Long> {

    fun findTop50ByStatusOrderByCreatedAtAsc(status: OutboxStatus): List<OutboxEvent>

    /** 여러 인스턴스에서 퍼블리셔가 동시에 돌아도 같은 행을 같이 잡지 않도록 FOR UPDATE SKIP LOCKED로 조회한다. */
    @Query(
        value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun findBatchForUpdate(@Param("limit") limit: Int): List<OutboxEvent>
}
