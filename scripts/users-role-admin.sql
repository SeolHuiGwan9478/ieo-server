-- users.role 에 운영자(ADMIN)를 허용한다 (2026-11-09, 운영자 서류 심사).
-- Hibernate가 처음 테이블을 만들 때 역할 값 목록(DRIVER, BROKER)으로 CHECK 제약을 걸어 두는데,
-- ddl-auto: update 는 이미 있는 제약을 바꾸지 않는다. 기존 DB(로컬 ieo, 운영)에 한 번 실행한다.
-- 테스트 DB는 create-drop 이라 필요 없다. scripts/seed-documents.py 가 운영자 계정을 만들 때 같이 실행한다.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('DRIVER', 'BROKER', 'ADMIN'));
