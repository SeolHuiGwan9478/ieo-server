# ieo-server

Ieo(화물차 기사를 위한 차량용 운행 비서)의 백엔드. Java 21, Spring Boot 4.

차량 앱과 기획 문서는 [ieo-app](https://github.com/SeolHuiGwan9478/ieo-app) 저장소에 있다.

## 실행

```bash
./gradlew bootRun
```

- 기본 프로필은 `local`이고 H2 파일 DB(`data/`)를 씀
- 프로필별 설정: `application-local.yml`(H2), `application-dev.yml`·`application-prod.yml`(PostgreSQL, `DB_URL`·`DB_USERNAME`·`DB_PASSWORD` 환경변수 필요)
- 다른 프로필로 실행: `./gradlew bootRun --args='--spring.profiles.active=dev'`
- 패키지 구조: `global`(공통 설정·엔티티·예외) / `domain/{도메인}/{controller,service,entity,repository,dto}`
- 헬스체크: http://localhost:8080/actuator/health
- H2 콘솔: http://localhost:8080/h2-console

## 테스트

```bash
./gradlew test
```
