-- 기사님 30일 플랜, 플랜 데모 결제 기록, 운송료 세금계산서 데모 발행 (2026-11-12, PLAN-007·008, BILL-007, LEDG-040~045)
-- 운영(ddl-auto: validate)은 새 서버를 올리기 전에 한 번 돌린다. 로컬은 update가 만들어 준다
BEGIN;

CREATE TABLE IF NOT EXISTS driver_subscriptions (
    driver_id            BIGINT PRIMARY KEY REFERENCES drivers (user_id),
    plan                 VARCHAR(20) NOT NULL,
    period_start         TIMESTAMP WITH TIME ZONE,
    period_end           TIMESTAMP WITH TIME ZONE,
    trial                BOOLEAN     NOT NULL DEFAULT FALSE,
    trial_used           BOOLEAN     NOT NULL DEFAULT FALSE,
    cancel_at_period_end BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS plan_payments (
    id           BIGSERIAL PRIMARY KEY,
    owner_type   VARCHAR(10) NOT NULL,
    owner_id     BIGINT      NOT NULL,
    plan         VARCHAR(20) NOT NULL,
    amount       INTEGER     NOT NULL,
    method       VARCHAR(10) NOT NULL,
    period_start TIMESTAMP WITH TIME ZONE NOT NULL,
    period_end   TIMESTAMP WITH TIME ZONE NOT NULL,
    paid_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS plan_payments_owner ON plan_payments (owner_type, owner_id);

CREATE TABLE IF NOT EXISTS tax_invoices (
    id                       BIGSERIAL PRIMARY KEY,
    driver_id                BIGINT       NOT NULL REFERENCES drivers (user_id),
    ledger_entry_id          BIGINT       NOT NULL UNIQUE REFERENCES ledger_entries (id),
    broker_id                BIGINT       NOT NULL REFERENCES brokers (id),
    write_date               DATE         NOT NULL,
    supplier_business_number VARCHAR(10)  NOT NULL,
    supplier_name            VARCHAR(100) NOT NULL,
    supplier_representative  VARCHAR(50)  NOT NULL,
    buyer_business_number    VARCHAR(10)  NOT NULL,
    buyer_name               VARCHAR(100) NOT NULL,
    buyer_representative     VARCHAR(50),
    item                     VARCHAR(100) NOT NULL,
    fare_amount              INTEGER      NOT NULL,
    wait_fee_amount          INTEGER      NOT NULL,
    supply_amount            INTEGER      NOT NULL,
    vat                      INTEGER      NOT NULL,
    provider                 VARCHAR(10)  NOT NULL,
    approval_number          VARCHAR(30)  NOT NULL,
    issued_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS tax_invoices_driver_issued ON tax_invoices (driver_id, issued_at);

COMMIT;
