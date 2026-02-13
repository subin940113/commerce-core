# commerce-core

Spring Boot + Kotlin으로 구현한 커머스 백엔드 설계 프로젝트입니다.

주문 → 결제 → 배송 흐름을 단계적으로 구현하면서  
동시성 제어, 결제 멱등성, 이벤트 기반 처리 같은 실제 운영 환경에서 필요한 설계를 다루는 것을 목표로 합니다.

---

## 왜 만들었는가

커머스 시스템은 기능 자체보다도 다음과 같은 문제가 더 중요하다고 생각했습니다.

- 동시에 주문이 들어올 때 재고가 정확히 차감되는가
- 결제 API가 중복 호출되면 어떻게 되는가
- 외부 웹훅이 여러 번 오면 어떻게 처리할 것인가
- 트랜잭션과 이벤트 발행을 어떻게 연결할 것인가

이 프로젝트는 이런 문제를 코드 수준에서 직접 구현하고 테스트해보는 데 초점을 두고 있습니다.

---

## 현재 구현 범위

### 주문 (Order)

- 주문 생성
- 금액 스냅샷 저장
- 재고 예약 처리
- 상태: `CREATED` → 결제 시도 시 `PAYMENT_PENDING` → 웹훅 결과에 따라 `PAID` / `PAYMENT_FAILED`

재고는 `available_qty` / `reserved_qty` 두 값으로 관리하며,  
가용 수량은 `available_qty - reserved_qty`로 계산합니다.

주문 생성 시:

1. `SELECT ... FOR UPDATE`로 재고 행을 잠급니다.
2. 가용 수량을 검증합니다.
3. `reserved_qty`를 증가시킵니다.

동시에 여러 요청이 들어와도 초과 예약이 발생하지 않도록 통합 테스트로 검증하고 있습니다.

### 결제 (Payment) — 웹훅 기반

결제 승인은 클라이언트 호출이 아니라 **PG 측 서버-서버 웹훅**으로 처리하는 방식으로 설계했습니다.

- **결제 시도 생성** `POST /api/v1/payments`: 주문 ID로 결제 레코드 생성, 주문 상태를 `PAYMENT_PENDING`으로 변경
- **모의 웹훅** `POST /api/v1/payments/webhooks/mock`: PG가 호출한다고 가정한 웹훅 수신
  - **멱등 처리**: `provider` + `providerEventId`로 이미 처리된 이벤트면 200 OK로 이전과 동일한 결과만 반환 (중복 반영 없음)
  - **승인(AUTHORIZED)**: 주문 `PAID`, 결제 `AUTHORIZED`, 재고 확정 차감 (`available_qty`·`reserved_qty` 감소)
  - **실패(FAILED)**: 주문 `PAYMENT_FAILED`, 결제 `FAILED`, 예약만 해제 (`reserved_qty` 감소)
- 재고 확정/해제 시에도 `FOR UPDATE`로 행을 잠근 뒤 처리합니다.

---

## 설계 방향

### 레이어 구조

도메인 단위로 패키지를 나누고, 내부는 다음과 같이 구성했습니다.

```
api
application
domain
persistence
```

- api: Controller 및 요청/응답 처리
- application: 유스케이스 및 트랜잭션 경계
- domain: 엔티티와 상태 규칙
- persistence: Repository

도메인 모델은 JPA 기반으로 구성한 단순한 DDD 스타일을 사용했습니다.

---

## 앞으로 확장할 부분

- 실제 PG 연동 및 Idempotency-Key(클라이언트 재시도) 처리
- Outbox 패턴 기반 이벤트 발행
- 배송 생성 및 상태 반영

기능을 추가하면서도 정합성과 상태 전이가 깨지지 않도록 구조를 유지하는 것을 목표로 합니다.

---

## 테스트

- 주문: 재고 부족 409, 검증 실패 400, 상품 없음 404, 동일 상품 수량 합산·빈 items·잘못된 productId 검증
- Testcontainers 기반 동시성 통합 테스트로 초과 예약 방지 검증
- 결제: 주문 → 결제 생성 시 `PAYMENT_PENDING`/`CREATED` 검증
- 웹훅: AUTHORIZED 시 `PAID`·재고 확정 차감, FAILED 시 예약 해제 검증
- 웹훅 멱등성: 동일 `providerEventId` 2회 호출 시 두 번째는 200, 상태·재고 중복 반영 없음 검증

---

## Tech Stack

- Kotlin
- Spring Boot 3
- JPA (Hibernate)
- PostgreSQL
- Flyway
- Testcontainers