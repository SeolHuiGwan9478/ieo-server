-- 배차 요청: 철회했거나 만료된 오더를 다시 요청할 수 있게 (DSP-020)
-- 예전에는 (오더, 차주)마다 요청 한 줄만 둘 수 있었다. 이제 다시 요청할 때마다 줄이 새로 생기고,
-- 대기 중(PENDING) 요청만 (오더, 차주)마다 하나로 막는다. JPA가 부분 인덱스를 만들지 못해 손으로 건다.
-- 한 번만 돌리면 되고, 다시 돌려도 괜찮다.
ALTER TABLE dispatch_requests DROP CONSTRAINT IF EXISTS uk_dispatch_requests_order_driver;
CREATE UNIQUE INDEX IF NOT EXISTS uk_dispatch_requests_pending
    ON dispatch_requests (order_id, driver_id) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_dispatch_requests_order_driver
    ON dispatch_requests (order_id, driver_id);
