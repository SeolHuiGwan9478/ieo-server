-- 사업자등록증 국세청 진위확인 (2026-11-09, 운영자 서류 심사). 반려 사유와 이력 종류에 값이 늘어 기존 DB의 CHECK 제약을 고친다.
-- Hibernate가 enum 컬럼에 건 CHECK 제약은 ddl-auto: update 가 바꾸지 않는다 (users-role-admin.sql 과 같은 이유).
-- 새 컬럼(driver_documents.business_*)은 ddl-auto: update 가 알아서 더한다. scripts/seed-documents.py 가 이 파일도 적용한다.
ALTER TABLE driver_documents DROP CONSTRAINT IF EXISTS driver_documents_reject_reason_check;
ALTER TABLE driver_documents ADD CONSTRAINT driver_documents_reject_reason_check
    CHECK (reject_reason IN ('UNREADABLE', 'CROPPED', 'WRONG_DOCUMENT', 'NAME_MISMATCH', 'BUSINESS_MISMATCH', 'BUSINESS_CLOSED',
                             'VEHICLE_MISMATCH', 'EXPIRED', 'CONTRACT_REQUIRED', 'OTHER'));
ALTER TABLE driver_document_events DROP CONSTRAINT IF EXISTS driver_document_events_type_check;
ALTER TABLE driver_document_events ADD CONSTRAINT driver_document_events_type_check
    CHECK (type IN ('SUBMITTED', 'BUSINESS_CHECKED', 'VIEWED', 'APPROVED', 'REJECTED'));
