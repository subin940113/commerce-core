-- Seed: products and inventory for local/dev
insert into products (id, name, status, price, created_at, updated_at)
values
    (1, '상품 A', 'ACTIVE', 10000, now(), now()),
    (2, '상품 B', 'ACTIVE', 15000, now(), now()),
    (3, '상품 C', 'ACTIVE', 20000, now(), now())
on conflict (id) do nothing;

insert into inventory (product_id, available_qty, reserved_qty, version, updated_at)
values
    (1, 100, 0, 0, now()),
    (2, 50, 0, 0, now()),
    (3, 30, 0, 0, now())
on conflict (product_id) do nothing;
