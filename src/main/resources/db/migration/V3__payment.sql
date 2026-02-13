-- payments
create table payments (
    id bigserial primary key,
    order_id bigint not null references orders(id),
    amount bigint not null,
    status varchar(30) not null,
    provider varchar(50) not null,
    provider_payment_id varchar(100),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_payments_order_id on payments(order_id);

-- payment_webhook_events (멱등성)
create table payment_webhook_events (
    id bigserial primary key,
    provider varchar(50) not null,
    provider_event_id varchar(100) not null,
    provider_payment_id varchar(100),
    payment_id bigint references payments(id),
    payload jsonb,
    received_at timestamptz not null default now()
);

create unique index uk_webhook_provider_event on payment_webhook_events(provider, provider_event_id);
