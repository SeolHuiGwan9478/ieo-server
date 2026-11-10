-- 테스트 DB를 만든 뒤 Hibernate가 돌린다. JPA로 못 거는 부분 인덱스 (scripts/dispatch-requests-pending-unique.sql과 같다)
CREATE UNIQUE INDEX uk_dispatch_requests_pending ON dispatch_requests (order_id, driver_id) WHERE status = 'PENDING';
