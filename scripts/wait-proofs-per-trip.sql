-- 대기 증빙을 운행당 하나로, 대기 기록에 기사님이 알린 순간 목록(events) 칸 (2026-11-11, WAIT-020, 025)
-- 운영(ddl-auto: validate)은 새 서버를 올리기 전에 한 번 돌린다. 로컬도 한 번 돌린다 (update로는 옛 칸을 지우지 못한다)
BEGIN;

ALTER TABLE wait_records ADD COLUMN IF NOT EXISTS events jsonb;

ALTER TABLE wait_proofs ADD COLUMN IF NOT EXISTS trip_id bigint;
UPDATE wait_proofs p SET trip_id = ts.trip_id
FROM wait_records w JOIN trip_stops ts ON ts.id = w.trip_stop_id
WHERE w.id = p.wait_record_id AND p.trip_id IS NULL;

-- 한 운행에 링크가 둘(상차지·하차지)이면 하나만 남긴다: 주선사에게 보낸 것을 먼저, 그다음 먼저 만든 것.
-- 지워진 링크는 더 열리지 않는다
WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY trip_id ORDER BY sent_at NULLS LAST, id) AS rn FROM wait_proofs
)
DELETE FROM wait_proofs WHERE id IN (SELECT id FROM ranked WHERE rn > 1);

ALTER TABLE wait_proofs ALTER COLUMN trip_id SET NOT NULL;
ALTER TABLE wait_proofs ADD CONSTRAINT uk_wait_proofs_trip UNIQUE (trip_id);
ALTER TABLE wait_proofs ADD CONSTRAINT fk_wait_proofs_trip FOREIGN KEY (trip_id) REFERENCES trips(id);
ALTER TABLE wait_proofs DROP COLUMN wait_record_id;

COMMIT;
