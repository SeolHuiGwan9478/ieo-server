-- 휴대폰 푸시(FCM) 기기 토큰 (2026-11-13, 09 알림). 휴대폰 앱이 로그인하면 토큰을 올린다
-- notifications-driver-link.sql 다음에 돌린다. 운영(ddl-auto: validate)은 새 서버를 올리기 전에, 로컬은 update가 만들어 준다
-- 서버 환경변수 FIREBASE_CREDENTIALS(서비스 계정 키)가 비어 있으면 토큰만 쌓이고 푸시는 보내지 않는다
BEGIN;

CREATE TABLE IF NOT EXISTS push_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(512) NOT NULL UNIQUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_push_tokens_user ON push_tokens (user_id);

COMMIT;
