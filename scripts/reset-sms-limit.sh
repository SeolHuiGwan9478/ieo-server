#!/bin/sh
# 로컬 서버(PostgreSQL ieo DB)에서 휴대폰 인증번호 발송 횟수와 잠금을 0으로 되돌린다.
# 하루 5번 제한(PhoneVerification.DAILY_SEND_LIMIT)에 걸려 테스트 계정으로 로그인할 수 없을 때 쓴다.
# 서버가 켜져 있어도 된다. 다른 DB를 쓰면 DB_NAME으로 바꾼다.
#
#   sh scripts/reset-sms-limit.sh
set -e
psql -d "${DB_NAME:-ieo}" -c "UPDATE phone_verifications SET send_count = 0, last_sent_at = NULL, locked_until = NULL, failed_attempts = 0" \
  -c "SELECT phone, send_day, send_count FROM phone_verifications"
