-- products
create table products (
    id bigserial primary key,
    name varchar(200) not null,
    status varchar(30) not null,
    price bigint not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_products_status on products(status);

-- inventory
create table inventory (
    product_id bigint primary key references products(id),
    available_qty int not null,
    reserved_qty int not null,
    version bigint not null default 0,
    updated_at timestamptz not null default now(),
    constraint chk_inventory_available_qty check (available_qty >= 0),
    constraint chk_inventory_reserved_qty check (reserved_qty >= 0)
);

-- orders
create table orders (
    id bigserial primary key,
    user_id bigint not null,
    status varchar(30) not null,
    total_amount bigint not null,
    shipping_fee bigint not null default 0,
    discount_amount bigint not null default 0,
    payable_amount bigint not null,
    currency varchar(10) not null default 'KRW',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_orders_user_id_created_at on orders(user_id, created_at desc);

-- order_items
create table order_items (
    id bigserial primary key,
    order_id bigint not null references orders(id),
    product_id bigint not null,
    product_name_snapshot varchar(200) not null,
    unit_price_snapshot bigint not null,
    qty int not null,
    line_amount bigint not null
);

create index idx_order_items_order_id on order_items(order_id);
create index idx_order_items_product_id on order_items(product_id);
