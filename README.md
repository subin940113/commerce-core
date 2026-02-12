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
- 상태는 `CREATED`로 시작

재고는 `available_qty` / `reserved_qty` 두 값으로 관리하며,  
가용 수량은 `available_qty - reserved_qty`로 계산합니다.

주문 생성 시:

1. `SELECT ... FOR UPDATE`로 재고 행을 잠급니다.
2. 가용 수량을 검증합니다.
3. `reserved_qty`를 증가시킵니다.

동시에 여러 요청이 들어와도 초과 예약이 발생하지 않도록 통합 테스트로 검증하고 있습니다.

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

- 결제 승인 API 및 Idempotency-Key 처리
- 결제 웹훅 중복 방지
- Outbox 패턴 기반 이벤트 발행
- 배송 생성 및 상태 반영

기능을 추가하면서도 정합성과 상태 전이가 깨지지 않도록 구조를 유지하는 것을 목표로 합니다.

---

## 테스트

- 재고 부족 시 409 반환
- 잘못된 요청 400 처리
- 존재하지 않는 상품 404 처리
- Testcontainers 기반 동시성 통합 테스트로 초과 예약 방지 검증

---

## Tech Stack

- Kotlin
- Spring Boot 3
- JPA (Hibernate)
- PostgreSQL
- Flyway
- Testcontainers