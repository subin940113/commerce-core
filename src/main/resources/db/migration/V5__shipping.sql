-- shipments (주문당 1건)
create table shipments (
    id bigserial primary key,
    order_id bigint not null unique references orders(id),
    status varchar(30) not null default 'CREATED',
    tracking_number varchar(100),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_shipments_order_id on shipments(order_id);
create index idx_shipments_status on shipments(status);
