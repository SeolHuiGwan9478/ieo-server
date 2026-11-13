-- 배차 추천 기록과 설정 바꾼 때 (2026-11-13, REC-001). TMAP 호출을 줄이려고 같은 상하차지에서는 이전 추천을 다시 쓴다
-- plan-coupons.sql 다음에 돌린다. 운영(ddl-auto: validate)은 새 서버를 올리기 전에, 로컬은 update가 만들어 준다
BEGIN;

-- 이보다 먼저 받은 추천은 다시 쓰지 않는다
ALTER TABLE recommendation_preferences ADD COLUMN IF NOT EXISTS changed_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE IF NOT EXISTS recommendation_shown (
    id           BIGSERIAL PRIMARY KEY,
    driver_id    BIGINT       NOT NULL REFERENCES drivers (user_id),
    trip_id      BIGINT,
    stop_type    VARCHAR(20),
    origin_lat   DOUBLE PRECISION NOT NULL,
    origin_lng   DOUBLE PRECISION NOT NULL,
    order_id     BIGINT REFERENCES orders (id),
    net_profit   INTEGER,
    fuel_cost    INTEGER,
    toll_fare    INTEGER,
    empty_meters BIGINT,
    shown_at     TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS recommendation_shown_trip ON recommendation_shown (driver_id, trip_id, stop_type);
CREATE INDEX IF NOT EXISTS recommendation_shown_driver_at ON recommendation_shown (driver_id, shown_at);

COMMIT;
