-- 배차 요청에 "추천에서 눌러 보냈나" 칸 (2026-11-11, REC-003). 추천 목록과 대답 기록(recommendations)은 더 쓰지 않는다
-- 운영(ddl-auto: validate)은 새 서버를 올리기 전에 한 번 돌린다
BEGIN;
ALTER TABLE dispatch_requests ADD COLUMN IF NOT EXISTS is_recommendation boolean NOT NULL DEFAULT false;
DROP TABLE IF EXISTS recommendations;
COMMIT;
