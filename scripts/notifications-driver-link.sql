-- 단골 연결 요청 앱 알림 (2026-11-13, BRK-002). 알림이 배차 요청 대신 단골 요청을 가리킬 수 있게 한다
-- recommendation-shown.sql 다음에 돌린다. 운영(ddl-auto: validate)은 새 서버를 올리기 전에, 로컬은 update가 만들어 준다
BEGIN;

ALTER TABLE notifications ALTER COLUMN dispatch_request_id DROP NOT NULL;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS driver_link_id BIGINT REFERENCES broker_driver_links (id);

COMMIT;
