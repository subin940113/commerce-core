# commerce-core

Spring Boot + Kotlin 기반 커머스 서버이며, 주문 → 결제 → 배송 흐름에서 정합성, 멱등성, 상태 전이 제어, 이벤트 발행을 코드로 구현·검증한다.

---

## 목차

- [비즈니스 흐름](#비즈니스-흐름)
- [API 개요](#api-개요)
- [설계 요약](#설계-요약)
- [프로젝트 구조](#프로젝트-구조)
- [에러 처리](#에러-처리)
- [테스트](#테스트)
- [로컬 실행](#로컬-실행)
- [Tech Stack](#tech-stack)

---

## 비즈니스 흐름

1. **주문 생성** — 재고 예약(`reserved` 증가), 결제 전까지 `available` 유지
2. **결제 시도** — 주문 상태를 `PAYMENT_PENDING`으로 변경
3. **결제 승인** — 두 경로 지원
   - **승인 API**: `POST /payments/{id}/authorize` + `Idempotency-Key` (클라이언트 재시도 대비)
   - **웹훅**: PG → 서버 콜백, `providerEventId` 기준 멱등
4. **승인 처리** — 주문 상태를 `PAID`로 변경, 재고 확정 차감, Outbox에 `PAYMENT_AUTHORIZED` 기록(같은 트랜잭션)
5. **Outbox 발행** — `PENDING` 이벤트를 RabbitMQ로 발행, 다중 인스턴스 시 `FOR UPDATE SKIP LOCKED`로 분배
6. **배송 생성** — Consumer가 큐에서 수신 후 `CreateShipmentUseCase` 호출, `order_id` UNIQUE로 멱등

자세한 단계·보장 성질은 [docs/flow.md](docs/flow.md) 참고

---

## API 개요

| 구분 | Method | 경로 | 비고 |
|------|--------|------|------|
| 주문 생성 | POST | `/api/v1/orders` | body: userId, items |
| 결제 시도 | POST | `/api/v1/payments` | body: orderId |
| 결제 승인 | POST | `/api/v1/payments/{paymentId}/authorize` | Header: `Idempotency-Key` 필수, body: result(`AUTHORIZED`/`FAILED`), providerPaymentId(선택) |
| 결제 웹훅(모의) | POST | `/api/v1/payments/webhooks/mock` | body: provider, providerEventId, paymentId, result |

응답은 공통 포맷(`success`, `data`/`error`, `traceId`, `timestamp`)을 사용한다.

---

## 설계 요약

### 동시성·정합성

| 구분 | 내용 |
|------|------|
| 가용량 | `available - reserved` 기준으로 주문·승인 시 검증 |
| 주문 시 | 행 락(`FOR UPDATE`) + productId 오름차순 락으로 데드락 완화 |
| 승인·실패 시 | 동일 방식으로 재고 확정 또는 예약 해제 일괄 처리 |

### 상태 전이 제어

| 대상 | 전이 | 조건 |
|------|------|------|
| 주문 | `CREATED` → `PAYMENT_PENDING` → `PAID` | 결제 시도는 `CREATED`일 때만 허용, 그 외 전이 차단 |
| 결제 | 승인 API·웹훅 처리 | 허용 상태에서만 처리 후 주문·재고·Outbox 일괄 반영 |

### 멱등성

| 대상 | 키 | 동작 |
|------|-----|------|
| 승인 API | `(paymentId, Idempotency-Key)` + 요청 해시 | 동일 키·동일 요청 → 기존 응답 반환, 동일 키·다른 요청 → `409` |
| 웹훅 | `(provider, providerEventId)` 유니크 | 이미 처리된 이벤트 → `200` + 현재 상태 반환 |
| 배송 | `order_id` UNIQUE | at-least-once 가정, "있으면 반환, 없으면 insert"로 주문당 1건만 생성 |

### 이벤트 발행 (Outbox + RabbitMQ)

| 구분 | 내용 |
|------|------|
| 트랜잭션 | 결제 승인과 Outbox 적재를 한 트랜잭션에서 수행 |
| OutboxPublisher | `PENDING` 이벤트 배치 조회 → RabbitMQ 발행, 실패 시 재시도·N회 초과 시 `FAILED`로 유지(운영 확인용) |
| Shipping Consumer | `shipping.payment-authorized` 큐 구독, 반복 실패 메시지는 DLQ로 분리 |

---

## 프로젝트 구조

도메인별 패키지 구성. 각 도메인은 `api → application → domain ← persistence` 구조를 따른다.

```
com.example.commerce/
├── order/                    # 주문
│   ├── api/                  # OrderController, Request/Response DTO
│   ├── application/          # CreateOrderUseCase, Command/Result
│   ├── domain/               # Order, OrderStatus, 예외
│   └── persistence/          # OrderRepository
│
├── payment/                  # 결제
│   ├── api/                  # PaymentController, Authorize/Webhook DTO
│   ├── application/         # CreatePayment, AuthorizePayment, ProcessWebhook, PaymentOutcomeApplier
│   ├── domain/               # Payment, PaymentStatus, WebhookResultType
│   └── persistence/         # PaymentRepository, Idempotency, WebhookEvent
│
├── shipping/                 # 배송
│   ├── application/          # CreateShipmentUseCase, Consumer
│   ├── domain/               # Shipping, ShippingStatus
│   └── persistence/          # ShippingRepository
│
├── catalog/                  # 상품·재고 (order/payment에서 참조)
│   └── InventoryRepository, ProductRepository, Inventory, Product
│
└── common/
    ├── api/                  # ApiResponse, TraceIdFilter
    ├── error/                 # ErrorCode, DomainException, GlobalExceptionHandler
    ├── outbox/                # OutboxEvent, OutboxPublisher, OutboxEventRepository
    └── messaging/             # RabbitMQConstants, RabbitTopologyConfig, Jackson 컨버터
```

---

## 에러 처리

| 구분 | 방식 |
|------|------|
| 도메인 예외 | `DomainException` + `ErrorCode` 기반으로 HTTP 상태·코드·메시지 일관성 유지 |
| 전역 핸들러 | `DomainException` → errorCode 기반 응답, `MethodArgumentNotValidException` → `400`, 기타 → `500` |
| 트랜잭션 | UseCase 계층에만 경계, Controller는 변환·호출만 |

---

## 테스트

| 영역 | 검증 내용 |
|------|------|
| Order | 재고 부족 → `409`, 검증 실패 → `400`, 동일 상품 합산·락 순서, 동시성 초과 예약 방지(Testcontainers 통합) |
| Payment | 결제 시도 → `PAYMENT_PENDING`/`CREATED`, 웹훅 `AUTHORIZED`/`FAILED` 시 재고 반영, 멱등(동일 키 → 동일 응답, 다른 payload → `409`) |
| Outbox·Shipping | 승인 → Outbox → RabbitMQ → Consumer → 배송 1건, 중복·동시 발행 시에도 1건 유지 |
| Shipping 단위 | CreateShipmentUseCase 멱등·동시 2스레드 시 1건만 생성, Consumer 수신 → 배송 1건 생성 |

---

## 로컬 실행

1. `docker compose up -d` — Postgres(5432), RabbitMQ(5672, Management 15672) 기동
2. `./gradlew bootRun` — 애플리케이션 실행
3. RabbitMQ Management: http://localhost:15672 (guest/guest)

---

## Tech Stack

| 영역 | 기술 |
|------|------|
| 언어·프레임워크 | Kotlin, Spring Boot 3 |
| DB | PostgreSQL, JPA(Hibernate), Flyway |
| 메시징 | RabbitMQ(Spring AMQP) |
| 테스트 | JUnit 5, Testcontainers(Postgres, RabbitMQ), Awaitility |
