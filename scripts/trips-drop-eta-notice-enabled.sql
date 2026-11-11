-- 운행마다 도착 예정 문자를 끄는 칸을 없앤다 (2026-11-11, TRIP-024 뺌). 문자는 주선사 공유 동의 하나로 켜고 끈다
-- 운영(ddl-auto: validate)은 새 서버를 올리기 전에 한 번 돌린다. 안 돌리면 NOT NULL 칸 때문에 운행 등록이 실패한다
BEGIN;
ALTER TABLE trips DROP COLUMN IF EXISTS eta_notice_enabled;
COMMIT;
