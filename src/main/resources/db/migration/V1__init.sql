create table outbox_events (
                               id bigserial primary key,
                               aggregate_type varchar(50) not null,
                               aggregate_id varchar(100) not null,
                               event_type varchar(100) not null,
                               payload jsonb not null,
                               status varchar(20) not null,
                               dedup_key varchar(150),
                               created_at timestamptz not null default now(),
                               published_at timestamptz
);

create index idx_outbox_status_created_at on outbox_events(status, created_at);
create unique index uk_outbox_dedup_key on outbox_events(dedup_key) where dedup_key is not null;