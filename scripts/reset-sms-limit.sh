#!/bin/sh
# 로컬 서버(H2 파일 DB)에서 휴대폰 인증번호 발송 횟수와 잠금을 0으로 되돌린다.
# 하루 5번 제한(PhoneVerification.DAILY_SEND_LIMIT)에 걸려 테스트 계정으로 로그인할 수 없을 때 쓴다.
# 서버를 끈 뒤에 돌린다 (H2 파일은 한 프로세스만 열 수 있다).
#
#   sh scripts/reset-sms-limit.sh
set -e
cd "$(dirname "$0")/.."
H2_JAR=$(find "$HOME/.gradle/caches/modules-2" -name 'h2-*.jar' | sort | tail -1)
if [ -z "$H2_JAR" ]; then
  echo "H2 jar를 찾지 못했어요. 서버를 한 번 빌드해 주세요." >&2
  exit 1
fi
java -cp "$H2_JAR" org.h2.tools.Shell \
  -url "jdbc:h2:file:./data/ieo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE" -user sa \
  -sql "UPDATE phone_verifications SET send_count = 0, last_sent_at = NULL, locked_until = NULL, failed_attempts = 0; SELECT phone, send_day, send_count FROM phone_verifications"
