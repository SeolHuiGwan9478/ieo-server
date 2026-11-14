-- 지정 오더 알림과 단골 해제 마감 (2026-11-13, BRK-011). push-tokens.sql 다음에 돌린다
-- 운영(ddl-auto: validate)은 새 서버를 올리기 전에. 로컬은 update가 칸은 만들지만 검사 조건은 고치지 않으니 이것도 돌린다
BEGIN;

-- 지정 오더 알림이 가리키는 오더
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS order_id BIGINT REFERENCES orders (id);
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('DISPATCH_CONFIRMED', 'DISPATCH_DECLINED', 'DISPATCH_EXPIRED', 'DRIVER_LINK_REQUESTED', 'ORDER_TARGETED'));

-- 단골 해제로 마감한 지정 오더
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_close_reason_check;
ALTER TABLE orders ADD CONSTRAINT orders_close_reason_check
    CHECK (close_reason IN ('BROKER', 'EXPIRED', 'CANCELED', 'UNLINKED'));

COMMIT;
