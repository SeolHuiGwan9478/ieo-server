-- 주선사 늦은 취소 보상 쿠폰 (2026-11-13, DSP-034). 기사님이 [사용하기]를 누르면 프로 이용일이 더해진다
-- plans-and-tax-invoices.sql 다음에 돌린다. 운영(ddl-auto: validate)은 새 서버를 올리기 전에, 로컬은 update가 만들어 준다
BEGIN;

-- 쿠폰으로 받은 프로 이용일로만 쓰는 중이면 true. 끝나면 결제 없이 베이직으로
ALTER TABLE driver_subscriptions ADD COLUMN IF NOT EXISTS gift BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS plan_coupons (
    id         BIGSERIAL PRIMARY KEY,
    driver_id  BIGINT       NOT NULL REFERENCES drivers (user_id),
    trip_id    BIGINT       NOT NULL UNIQUE,
    days       INTEGER      NOT NULL,
    reason     VARCHAR(100) NOT NULL,
    issued_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS plan_coupons_driver_issued ON plan_coupons (driver_id, issued_at);

COMMIT;
