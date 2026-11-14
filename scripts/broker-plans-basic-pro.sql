-- 주선사 플랜을 베이직·프로 둘로, 프로는 30일 단위로 (2026-11-14, 12장). 프로 플러스를 쓰던 회사는 프로로 옮긴다. dispatch-canceled.sql 다음에 돌린다
-- 담당자 수 한도는 없앴다(두 플랜 모두 무제한). 지난 결제 기록(plan_payments.plan)은 글자라 그대로 둔다
-- 운영(ddl-auto: validate)은 새 서버를 올리기 전에. 로컬도 update가 검사 조건은 고치지 않으니 이것도 돌린다
BEGIN;

UPDATE broker_subscriptions SET plan = 'PRO' WHERE plan = 'PRO_PLUS';
ALTER TABLE broker_subscriptions DROP CONSTRAINT IF EXISTS broker_subscriptions_plan_check;
ALTER TABLE broker_subscriptions ADD CONSTRAINT broker_subscriptions_plan_check CHECK (plan IN ('BASIC', 'PRO'));

COMMIT;

-- 주선사 프로도 30일 단위 (2026-11-14, PLAN-008, BILL-004). 이미 프로인 회사는 오늘부터 30일로 시작한다
BEGIN;

ALTER TABLE broker_subscriptions ADD COLUMN IF NOT EXISTS period_start TIMESTAMP WITH TIME ZONE;
ALTER TABLE broker_subscriptions ADD COLUMN IF NOT EXISTS period_end TIMESTAMP WITH TIME ZONE;
ALTER TABLE broker_subscriptions ADD COLUMN IF NOT EXISTS cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE broker_subscriptions SET period_start = now(), period_end = now() + INTERVAL '30 days'
    WHERE plan = 'PRO' AND period_end IS NULL;

COMMIT;
