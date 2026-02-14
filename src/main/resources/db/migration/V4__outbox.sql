-- NOTE:
-- 학습 환경에서는 outbox_events를 재생성하지만,
-- 실제 운영 환경에서는 ALTER TABLE 방식으로 점진적 마이그레이션을 수행해야 합니다.
--
-- V1에서 생성된 outbox_events를 새 스키마로 마이그레이션 (ALTER 기반)

-- dedup_key 제거 (유니크 인덱스 함께 제거)
drop index if exists uk_outbox_dedup_key;
alter table outbox_events drop column if exists dedup_key;

-- aggregate_id를 bigint로 변경
alter table outbox_events alter column aggregate_id type bigint using aggregate_id::bigint;

-- retry_count 컬럼 추가
do $$
begin
  if not exists (
    select 1 from information_schema.columns
    where table_schema = current_schema() and table_name = 'outbox_events' and column_name = 'retry_count'
  ) then
    alter table outbox_events add column retry_count int not null default 0;
  end if;
end $$;

-- status 기본값 (기존 행 호환)
alter table outbox_events alter column status set default 'PENDING';

-- 인덱스 정리: 기존 idx_outbox_status_created_at 유지, 추가 인덱스 생성
create index if not exists idx_outbox_status_created on outbox_events(status, created_at);
create index if not exists idx_outbox_aggregate on outbox_events(aggregate_type, aggregate_id);
create index if not exists idx_outbox_event_type on outbox_events(event_type);
