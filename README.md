<div align="center">

<img src="docs/assets/ieo_icon_orange.svg" alt="Ieo icon" width="96">

<br><br>

<img src="docs/assets/ieo_wordmark_orange.svg" alt="ieo." width="180">

### Backend for Ieo (이에오)

API server for the in-car assistant for truck drivers:<br>
accounts, loads and dispatch, trips, waiting records and the ledger.

<br>

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)
![PostGIS](https://img.shields.io/badge/PostGIS-3.7-FF6C37)

[In-car app & docs (ieo-app)](https://github.com/SeolHuiGwan9478/ieo-app)

</div>

<br>

## Overview

Ieo has two kinds of users: **drivers**, who use the in-car app, and **brokers**, who post loads and dispatch them. This server holds both and is what the car app talks to.

Current state: the account model is in place (users, driver and broker profiles, consents) with the `/api/users/me` endpoints. Authentication endpoints are defined but not implemented yet. Trips, dispatch and the ledger come next.

## Getting started

**Requirements:** JDK 21, PostgreSQL with PostGIS (macOS: `brew install postgresql@18 postgis`).

Create the local database once:

```bash
createdb ieo
psql -d ieo -c 'CREATE EXTENSION IF NOT EXISTS postgis'
```

```bash
./gradlew bootRun
```

The default `local` profile connects to `jdbc:postgresql://localhost:5432/ieo` as your OS user with no password. Override with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

| | URL |
|---|---|
| Health check | http://localhost:8080/actuator/health |

### Profiles

| Profile | Database | Notes |
|---|---|---|
| `local` (default) | Local PostgreSQL + PostGIS (`ieo`) | For development |
| `test` | Local PostgreSQL + PostGIS (`ieo_test`) | Used by `./gradlew test`. Tables are recreated on every run |
| `dev` | PostgreSQL | Needs `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |
| `prod` | PostgreSQL | Needs `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### Tests

```bash
./gradlew test
```

Tests run on a local PostgreSQL database `ieo_test` with PostGIS. Create it once:

```bash
createdb ieo_test
psql -d ieo_test -c 'CREATE EXTENSION IF NOT EXISTS postgis'
```

If your `ieo` database was created before sites moved to PostGIS `geography`, run this once before starting the new server:

```bash
psql -d ieo -f scripts/sites-geography.sql
```

Override the test connection with `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`. Never point it at the `ieo` or a deployed database: tests drop and recreate tables.

## API

| Method | Path | Description | Status |
|---|---|---|---|
| `GET` | `/api/users/me` | My account | Done |
| `PATCH` | `/api/users/me` | Update my name | Done |
| `POST` | `/api/auth/code` | Send a verification code by SMS | Returns 501 |
| `POST` | `/api/auth/verify` | Verify the code and issue tokens | Returns 501 |
| `POST` | `/api/auth/refresh` | Refresh the access token | Returns 501 |

The user ID will come from a JWT interceptor that sets the `userId` request attribute; that interceptor is not built yet.

## Project structure

```
src/main/java/com/ieo/server
├── global/                 Shared code
│   ├── config/             JPA auditing and other config
│   ├── entity/             BaseTimeEntity (created/updated timestamps)
│   └── error/              ErrorCode, BusinessException, global handler
└── domain/
    ├── user/               Accounts: controller, service, entity, repository, dto, enums
    ├── driver/             Driver profile (license number)
    └── broker/             Broker company and its members
```

Each domain follows `controller / service / entity / repository / dto`, with enums in an `enums` package.

### Data model

| Table | Holds |
|---|---|
| `users` | Shared account: phone, name, role (driver or broker), status |
| `drivers` | Driver profile, 1:1 with `users` |
| `brokers` | Broker company, including its settlement days |
| `broker_members` | Users who belong to a broker, with their position |
| `user_consents` | Consent history (terms, privacy, location, sharing with brokers, voice data, marketing) |

Requirements for every domain are in the [ieo-app docs](https://github.com/SeolHuiGwan9478/ieo-app/tree/main/docs/requirements) (Korean).

## Tech stack

Java 21 · Spring Boot 4.1 · Spring Data JPA (Hibernate 7) · PostgreSQL + PostGIS · Gradle

## 주선사 콘솔 시연 데이터 (로컬 전용)

PostgreSQL 설정과 `local` 서버 실행 후 다음 명령으로 별도 시연 회사를 준비한다.

```powershell
python scripts/seed-console.py
```

- 로그인 번호: `01098765432`, 회사: `[시연] 한빛물류`. 인증번호 받기를 누르면 로컬 로그인 화면 아래에 표시된다.
- 오더 42건: 과거 완료 24건, 취소 3건, 진행 4건, 예약 3건, 모집 6건, 마감 2건.
- 단골 차주 6명, 직원 3명, 초대 대기, 저장 주소 6곳, 대기 증빙, 최근 서명 인수증과 월별 실적을 포함한다.
- 회사 플랜은 시연 회사에 한해 `PRO_PLUS`로 설정해 리포트도 둘러볼 수 있다. 실제 결제는 없다.
- 기존 회사·오더·계정은 삭제하지 않는다. 고정 시연 번호·회사명·오더 태그를 검사하며, 충돌하면 중단한다.
- API로 데이터를 만들고, 과거 실적을 보여 주기 위한 시각과 장부 보정은 해당 시연 회사의 생성 ID 범위에서만 수행한다.
- `.env`의 DB 주소가 `jdbc:postgresql://localhost:5432/ieo`인 경우에만 실행한다. `psql` 경로가 다르면 `--psql`로 지정한다.
- 재실행은 기존 태그 데이터를 재사용한다. 휴대폰 인증 재발급은 1분 간격·하루 5회 제한을 따른다.
- 배차 요청은 10분 후 만료되고, 진행 중 위치는 저장한 시각의 샘플이다. 실시간으로 운행하는 차량이 아니다.
