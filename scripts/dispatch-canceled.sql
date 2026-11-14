-- 주선사가 확정된 배차를 취소하면 기사님에게 알림 (2026-11-14, DSP-030, 09 알림). targeted-orders.sql 다음에 돌린다
-- 운영(ddl-auto: validate)은 새 서버를 올리기 전에. 로컬도 update가 검사 조건은 고치지 않으니 이것도 돌린다
BEGIN;

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('DISPATCH_CONFIRMED', 'DISPATCH_DECLINED', 'DISPATCH_EXPIRED', 'DISPATCH_CANCELED',
                    'DRIVER_LINK_REQUESTED', 'ORDER_TARGETED'));

COMMIT;
