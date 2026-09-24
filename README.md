# Notification Platform

[![CI](https://github.com/Marcussi02/notification-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/Marcussi02/notification-platform/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)

A multi-tenant **notification and campaign service**. Upload a CSV of recipients, and it delivers Email, SMS or Push notifications asynchronously, with rate limiting, retries and delivery rules. Built with **Java 21, Spring Boot 3.2 and PostgreSQL**.

## Highlights

- **Async API:** `POST /api/campaigns` returns **202 Accepted** at once, and a background worker pool does the sending.
- **Database-backed job queue:** jobs are claimed atomically (`UPDATE … WHERE status IN (…)`), so two workers can never send the same notification.
- **Idempotency:** a unique `campaignId:recipientId` key, enforced by a database constraint, is the last line of defence against duplicate sends.
- **Resilience:** per-channel rate limiting (100 requests/min), exponential backoff retries (2ⁿ × 15 s), and a "retry failed" endpoint.
- **Rule engine:** global suppression (unsubscribes), a do-not-disturb window that respects each recipient's timezone, and de-duplication. These run *before* any provider call.
- **Realistic providers:** simulated Email, SMS and Push gateways with 50–200 ms latency and about a 20% failure rate, to exercise the failure paths.
- **Tested end to end:** unit tests, an H2-backed requirements test that prints measured results, and a Testcontainers PostgreSQL integration test.

📐 Design: [System design](docs/SYSTEM_DESIGN.md) · [Engineering notes and trade-offs](ENGINEERING_NOTES.md)

---

## Quick Start (Docker)

```bash
docker-compose up --build
```

- Backend API: http://localhost:8080
- Health check: http://localhost:8080/actuator/health

---

## Local Development

### Prerequisites
- Java 21+, Maven 3.9+
- PostgreSQL 16 (or use Docker for the DB only)

### Start database only

```bash
docker-compose up postgres -d
```

### Run backend

```bash
cd backend
mvn spring-boot:run
# API on http://localhost:8080
```

### Run tests

```bash
cd backend
mvn test
```

---

## Requirements test

A single test class exercises every core behaviour of the platform and prints a live report of measured values (latency, failure rate, and so on).

**No Docker required.** The test uses an H2 in-memory database so it runs anywhere Java and Maven are installed.

### Run it

```bash
cd backend
mvn test -Dtest=RequirementsIT
```


### What you'll see

The test prints a labelled section for each requirement as it runs:

```
=================================================================
REQ-1 | Provider Latency  (spec: 50-200 ms per call)
=================================================================
  call  1:  69 ms  [SENT]
  call  2: 193 ms  [SENT]
  ...
  min=69 ms  avg=121 ms  max=193 ms
  PASS - all calls within 50-200 ms window

=================================================================
REQ-2 | Provider Failure Rate  (spec: 15-25%)
=================================================================
  Calls: 50  |  Sent: 42  |  Failed: 8
  Failure rate: 16.0%  (FAILURE_RATE=0.20 in EmailProvider)
  PASS - failure rate within expected statistical range

=================================================================
REQ-3 | Rate Limiting  (spec: 100 req/min per channel)
=================================================================
  Allowed: 100  |  Rejected: 5  |  Usage: 105 / 100
  PASS - rate limiter enforces 100 req/min ceiling

=================================================================
REQ-4 | Exponential Backoff  (spec: 2^retry * 15 s)
=================================================================
  Retry 1 -> retryCount=1  nextRetryAt +30 s
  Retry 2 -> retryCount=2  nextRetryAt +60 s
  Retry 3 -> retryCount=3  nextRetryAt=null  (permanently failed)
  PASS - backoff schedule: 30 s, 60 s, then permanently failed

=================================================================
REQ-5a | Rule Engine - Global Suppression
=================================================================
  Rule action: SKIP
  Reason:      Recipient has unsubscribed from EMAIL
  PASS - suppressed recipient blocked before provider call

=================================================================
REQ-5b | Rule Engine - DND Window (22:00-08:00 quiet hours)
=================================================================
  EMAIL channel -> action: ALLOW  (always exempt from DND by design)
  SMS channel, timezone=Pacific/Kiritimati -> action: ALLOW / DELAY
  PASS - EMAIL always exempt; SMS DND rule evaluates per timezone

=================================================================
REQ-6 | Idempotency - Duplicate Key Rejection
=================================================================
  Job 1 saved with idempotency_key='...' -> OK
  Job 2 attempting same key...
  DataIntegrityViolationException thrown
  PASS - duplicate send attempt rejected at the persistence layer

=================================================================
REQ-7 | Atomic Job Claiming  (UPDATE WHERE status='PENDING')
=================================================================
  First claim:  1 row(s) updated -> status now PROCESSING
  Second claim: 0 row(s) updated -> no-op (already claimed)
  PASS - at-most-once delivery guarantee via atomic DB update

=================================================================
REQ-8 | HTTP 202 Accepted - Async Campaign Creation
=================================================================
  POST /api/campaigns -> HTTP 202 ACCEPTED in 141 ms
  PASS - campaign creation is fire-and-forget (non-blocking)

=================================================================
REQ-9 | End-to-End Processing Pipeline
=================================================================
  Processing time: 336 ms (3 x 50-200 ms provider latency)
  Sent: 3 | Failed: 0 | Skipped: 0 | Pending: 0
  Delivery rate: 100.0%
  Campaign status: COMPLETED
  Delivery attempts logged: 3
  PASS - all jobs exited PENDING; campaign counters updated; attempts logged

=================================================================
REQ-10 | Retry Queue Reset
=================================================================
  Jobs reset to PENDING: 2
  PASS - permanently failed jobs successfully re-queued

Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### What each test covers

| Test | Requirement |
|------|-------------|
| REQ-1 | Every provider call takes 50–200 ms (measured) |
| REQ-2 | ~20% of sends fail — within the 15–25% target range |
| REQ-3 | Exactly 100 calls allowed per minute per channel; call 101 is rejected |
| REQ-4 | Retry backoff: 30 s → 60 s → permanently failed (2^n × 15 s formula) |
| REQ-5a | Opted-out recipient is skipped before the provider is ever called |
| REQ-5b | EMAIL is exempt from quiet hours; SMS respects the 10 PM–8 AM DND window |
| REQ-6 | Saving two jobs with the same `idempotency_key` throws a DB constraint error |
| REQ-7 | Two concurrent claim attempts on the same job — only the first one wins |
| REQ-8 | `POST /api/campaigns` returns HTTP 202 immediately (non-blocking) |
| REQ-9 | Full pipeline: claim → rule check → rate limit → provider → DB update |
| REQ-10 | `POST /campaigns/{id}/retry-failures` resets failed jobs back to PENDING |

---

## API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/campaigns` | Create campaign (multipart/form-data) |
| GET | `/api/campaigns` | List campaigns (`?page`, `?size`, `?search`, `?status`, `?tenantId`) |
| GET | `/api/campaigns/{id}` | Get campaign detail with metrics |
| POST | `/api/campaigns/{id}/retry-failures` | Re-enqueue all FAILED jobs |
| GET | `/api/tenants` | List all tenants |

### Create Campaign — multipart/form-data fields

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| tenantId | UUID | yes | |
| name | string | yes | |
| channel | EMAIL \| SMS \| PUSH | yes | |
| messageTemplate | string | yes | |
| scheduleNow | boolean | no | default `true` |
| scheduledTime | ISO datetime | no | required when `scheduleNow=false` |
| csvFile | file | yes | format: `recipientId,email,phone` |

### Sample CSV

```csv
recipientId,email,phone
r001,alice@example.com,+60123456789
r002,bob@example.com,+60198765432
r003,carol@example.com,+60111234567
```

A ready-to-use file is at `sample_recipients.csv`.

---

## Architecture Overview

```
HTTP Client  →  CampaignController  →  CampaignService
                                              ↓
                                    CsvProcessingService (streaming)
                                              ↓
                                       PostgreSQL (Flyway migrations)
                                              ↓
                                    NotificationProcessor (scheduled pool)
                                              ↓
                                    RuleEngine → ProviderGateway (simulated)
```

Key design choices:
- **DDD** — bounded contexts: Campaign Management vs. Notification Delivery
- **Transactional Outbox** — jobs written in the same transaction as campaign creation
- **Rule Engine** — pluggable rules: Global Suppression, DND Window, Deduplication
- **Rate Limiting** — Token Bucket per channel (100 req/min)
- **Circuit Breaker** — Resilience4j per provider
- **Retry** — exponential backoff, max 3 attempts, idempotency via unique constraints

See `ENGINEERING_NOTES.md` and `SYSTEM_DESIGN.md` for full design discussion.

---

## Seeded Tenants

| ID | Name |
|----|------|
| 00000000-0000-0000-0000-000000000001 | Demo Tenant |
| 00000000-0000-0000-0000-000000000002 | Acme Corp |
| 00000000-0000-0000-0000-000000000003 | Tech Startup |

---

## Configuration

Key `application.yml` settings (all overridable via env vars):

| Env Var | Default | Description |
|---------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/notifications` | JDBC URL |
| `DB_USER` | `postgres` | DB username |
| `DB_PASS` | `postgres` | DB password |
| `PORT` | `8080` | Server port |

## License

[MIT](LICENSE) © 2026 Marcus Mah
