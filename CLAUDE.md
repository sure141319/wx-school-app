# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Campus Trade (校园闲置集市) — a second-hand trading platform for Anhui University of Technology students. Monorepo with three components:

- **`v1/`** — Spring Boot 3.3.5 / Java 17 backend (REST API, JWT auth, MySQL/Redis/MinIO)
- **`wxui_v1/`** — WeChat Mini Program frontend (native WXML/WXSS/JS, no npm)
- **`checkui/`** — Vanilla HTML/CSS/JS admin console for image moderation

## Build & Run Commands

### Backend (v1/)
```bash
cd v1
mvn clean package              # Build JAR
mvn spring-boot:run            # Run dev server (port 8080)
mvn test                       # Run all tests (uses H2 in-memory DB, no external services needed)
mvn test -Dtest=ClassName      # Run a single test class
java -jar target/backend-0.0.1-SNAPSHOT.jar  # Run packaged JAR
```

### Frontend (wxui_v1/)
Open `wxui_v1/` in WeChat Developer Tools. No build step — native mini program.

### Admin UI (checkui/)
```bash
cd checkui
python -m http.server 5173     # Serve static files on port 5173
```

## Architecture

### Backend Layered Structure

Each domain module under `v1/src/main/java/com/campustrade/platform/` follows:
```
module/
├── controller/    # @RestController — REST endpoints
├── service/       # @Service — business logic
├── mapper/        # @Mapper — MyBatis interfaces
├── dataobject/    # DB entity classes (DO suffix)
├── dto/request/   # Incoming DTOs (Java records)
├── dto/response/  # Outgoing DTOs (Java records)
├── assembler/     # DO ↔ DTO conversion (manual, not MapStruct)
└── enums/         # Domain enumerations
```

**Domain modules:** auth, user, goods, category, message, upload, audit, security, config, common.

### Key Backend Patterns

- **Unified response:** All endpoints return `ApiResponse<T>` (record with `success`, `message`, `data`)
- **Pagination:** `PageResponse<T>` wraps list results with `items`, `total`, `page`, `size`
- **Exception handling:** `GlobalExceptionHandler` catches all exceptions → HTTP status codes
- **DTOs are Java records** (immutable)
- **Assemblers** are manual DO↔DTO converters (no MapStruct)
- **Caching:** Spring Cache + Redis (falls back to ConcurrentMapCacheManager when Redis disabled)
- **Auth:** Stateless JWT via `JwtAuthenticationFilter` before Spring Security's filter chain
- **Config properties:** Custom `AppProperties` class under `config/` — prefix `app.*` in application.yml
- **MyBatis XML mappers** in `v1/src/main/resources/mapper/`
- **Flyway migrations** in `v1/src/main/resources/db/migration/` (single baseline: `V1__baseline.sql`)
- **API base path:** `/api/v1/*` — see `v1/API_DOCUMENTATION.md` for full endpoint docs

### Database

- **Production:** MySQL | **Tests:** H2 (MySQL compatibility mode)
- **6 tables:** `users`, `category_do`, `goods_do`, `goods_image_do`, `conversation_do`, `messages`

### Frontend (wxui_v1/)

- 7 pages, each with `.js/.json/.wxml/.wxss` quad-file pattern
- **State:** `wx.setStorageSync` for cross-page data (token, user info); each page has its own `data` object
- **API client:** `utils/request.js` wraps `wx.request` with auto Bearer token injection and 401 handling
- **Env config:** `config/env.js` — toggles dev/prod API base URL

### Test Setup

- JUnit 5 + Spring Boot Test + Mockito
- Tests use H2 in-memory DB and mock external services (Redis disabled, MinIO mocked)
- Config: `v1/src/test/resources/application.yml`
- Patterns: `@SpringBootTest` with inner `@Configuration`, `@MockBean`, `@TestPropertySource`