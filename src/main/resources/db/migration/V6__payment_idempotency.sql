-- 결제 승인 API Idempotency-Key 저장
create table payment_idempotency_records (
    id bigserial primary key,
    payment_id bigint not null references payments(id),
    idempotency_key varchar(200) not null,
    request_hash varchar(64) not null,
    response_payload jsonb not null,
    created_at timestamptz not null default now()
);

create unique index uk_payment_idempotency on payment_idempotency_records(payment_id, idempotency_key);
create index idx_payment_idempotency_payment_created on payment_idempotency_records(payment_id, created_at);
