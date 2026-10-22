<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/ieo_icon_white.svg">
  <img src="docs/assets/ieo_icon_black.svg" alt="Ieo icon" width="96">
</picture>

<br><br>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/ieo_wordmark_white.svg">
  <img src="docs/assets/ieo_wordmark_black.svg" alt="ieo." width="180">
</picture>

### Backend for Ieo (이에오)

API server for the in-car assistant for truck drivers:<br>
accounts, loads and dispatch, trips, waiting records and the ledger.

<br>

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)
![H2](https://img.shields.io/badge/H2-local-1E1E1E)

[In-car app & docs (ieo-app)](https://github.com/SeolHuiGwan9478/ieo-app)

</div>

<br>

## Overview

Ieo has two kinds of users: **drivers**, who use the in-car app, and **brokers**, who post loads and dispatch them. This server holds both and is what the car app talks to.

Current state: the account model is in place (users, driver and broker profiles, consents) with the `/api/users/me` endpoints. Authentication endpoints are defined but not implemented yet. Trips, dispatch and the ledger come next.

## Getting started

**Requirements:** JDK 21.

```bash
./gradlew bootRun
```

The default `local` profile uses an H2 file database under `data/`, so data survives restarts.

| | URL |
|---|---|
| Health check | http://localhost:8080/actuator/health |
| H2 console | http://localhost:8080/h2-console |

### Profiles

| Profile | Database | Notes |
|---|---|---|
| `local` (default) | H2 file DB | For development |
| `dev` | PostgreSQL | Needs `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |
| `prod` | PostgreSQL | Needs `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### Tests

```bash
./gradlew test
```

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

Java 21 · Spring Boot 4.1 · Spring Data JPA (Hibernate 7) · H2 · PostgreSQL · Gradle
