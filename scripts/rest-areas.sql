-- 고속도로 휴게소 표 (2026-11-11, TRIP-032). 도로공사 휴게소 API + 전국휴게소정보표준데이터 좌표
-- 운영(ddl-auto: validate)은 새 서버를 올리기 전에 한 번 돌린다. 데이터는 서버가 뜰 때(표가 비었으면)와 매주 월요일 새벽 4시에 채운다
BEGIN;
CREATE TABLE IF NOT EXISTS rest_areas (
    id               bigserial PRIMARY KEY,
    code             varchar(20)  NOT NULL UNIQUE,
    name             varchar(100) NOT NULL,
    route_name       varchar(50),
    direction        varchar(30),
    road_direction   varchar(10),
    facilities       varchar(200) NOT NULL,
    truck_rest_area  boolean      NOT NULL,
    phone            varchar(30),
    address          varchar(200),
    location         geography(Point,4326),
    location_manual  boolean      NOT NULL DEFAULT false,
    content_hash     varchar(64)  NOT NULL,
    last_seen_at     timestamp(6) with time zone NOT NULL,
    missing_since    timestamp(6) with time zone,
    created_at       timestamp(6) with time zone NOT NULL,
    updated_at       timestamp(6) with time zone NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_rest_areas_location ON rest_areas USING gist (location);
COMMIT;
