# Engineering Notes

## 1. Architecture Decisions & Tradeoffs

### Backend: Spring Boot 3.2 + Java 21

**Why Spring Boot?**
Java was a deliberate choice for this service. Spring Boot 3.2 with Java 21 gives us virtual thread support (via `-Dvirtual-threads=true`) which dramatically increases throughput for I/O-bound workloads like notification delivery without the complexity of reactive programming.

**Why PostgreSQL instead of a dedicated message broker?**
For this project's scope, a `notification_jobs` table acts as a reliable queue. This trades raw throughput for operational simplicity — no separate Kafka/RabbitMQ cluster to manage. The tradeoff is that polling-based queues don't scale as well as true message brokers above ~50k msg/min.

**Why an in-process worker pool instead of Kafka?**
Simpler to run locally and reason about. In production, you'd replace the `@Scheduled` poller with Kafka consumers. The `NotificationProcessor` is designed so the processing logic is independent of how jobs arrive — swapping the delivery mechanism requires only changing the "pump" layer.

### Idempotency Design
Every `NotificationJob` has a unique `idempotency_key = campaignId:recipientId`. A `UNIQUE` constraint on this column in PostgreSQL is the final line of defense against duplicate sends — even if the application logic has a bug, the DB will reject the duplicate INSERT.

The `tryClaimJob()` query uses an atomic `UPDATE ... WHERE status IN ('PENDING', 'FAILED', 'DELAYED')`. Only the worker that updates exactly 1 row proceeds — all others receive 0 and bail out. This eliminates race conditions without distributed locks.

### Rate Limiting: Token Bucket via AtomicInteger
A `ConcurrentHashMap<Channel, AtomicInteger>` counts requests per channel, reset every 60 seconds by a `@Scheduled` task. This is an in-process token bucket. Simple and correct for a single-node deployment.

**Tradeoff:** Doesn't work across multiple backend instances. In production, use Redis `INCR` + `EXPIRE` for distributed rate limiting.

### Rule Engine: Chain of Responsibility
Rules implement a `NotificationRule` interface and are evaluated in order by `RuleEngine`. Each rule returns `ALLOW`, `SKIP`, `DELAY`, `REJECT`, or `DISCARD`. Adding a new compliance rule requires only creating a new `@Component` and registering it in the engine — no controller or service changes.

### DDD Lite
The domain model is split into two bounded contexts:
- `domain/campaign/` — Campaign lifecycle (CRUD, status transitions)
- `domain/notification/` — Delivery mechanics (jobs, recipients, attempts)

Domain logic lives in the entities themselves (`campaign.incrementSent()`, `job.markFailed()`), not just in service methods. Value objects like `Channel` and `CampaignStatus` are Java enums with domain meaning.

## 2. How the System Scales

### Vertical Scaling (current approach)
- Thread pool (`notificationExecutor`) sized to `2 × CPU cores`
- Java 21 virtual threads can handle thousands of concurrent I/O waits
- PostgreSQL connection pool (HikariCP) is the true bottleneck at single-node scale

### Horizontal Scaling (production approach)
To scale beyond one instance:

1. **Replace the DB-queue with Kafka**
   - `CampaignService.createCampaign()` publishes a `CampaignCreated` event
   - Separate worker pods consume from a `notification-jobs` topic partitioned by `tenantId`
   - Each partition is processed by one consumer — no locking needed

2. **Distributed rate limiting with Redis**
   - Replace `ChannelRateLimiter` with `INCR` + `EXPIRE` in Redis
   - All worker pods share the same counters

3. **Database partitioning**
   - Partition `notification_jobs` and `delivery_attempts` by `created_at` (monthly ranges)
   - Index on `(tenant_id, status)` for tenant-scoped queries

4. **Read replicas**
   - Direct `GET /campaigns` to a read replica
   - Only writes go to the primary

### Throughput Estimation
- Single node: ~1,000–2,000 notifications/min (limited by provider latency of 50–200ms × 10 workers)
- With 10 worker pods + Kafka: ~10,000–20,000 notifications/min
- For 10M/day target: ~7,000/min needed — achievable with 5–10 worker pods

## 3. Failure Scenarios Considered

| Failure | Behaviour |
|---------|-----------|
| Provider returns 5xx | Job marked FAILED, exponential backoff retry (30s → 60s → 120s) |
| Worker pod crashes mid-job | Job stays in PROCESSING state. A recovery job (not implemented) resets PROCESSING → PENDING after a timeout |
| CSV upload interrupted | Recipients already committed to DB are processed; partial uploads create partial campaigns |
| Rate limit exceeded | Job returned to PENDING, picked up on next processor tick (5s later) |
| DB connection lost | Spring's transaction rolls back; job remains in previous state |
| Queue full (back-pressure) | `RejectedExecutionHandler` logs warning; job stays PENDING and is retried next tick |
| Duplicate campaign creation | Idempotency key constraint at DB level prevents duplicate jobs |

## 4. Known Limitations

1. **In-process queue is not durable** — if the JVM crashes between picking up a job and marking it PROCESSING, that job could be "stuck" in PROCESSING forever. Production fix: a periodic job resets `PROCESSING` jobs older than 5 minutes back to `PENDING`.

2. **Rate limiter resets are approximate** — the `@Scheduled(fixedRate = 60_000)` reset isn't perfectly aligned with wall-clock minutes. A sliding window implementation (e.g., Redis sorted sets) would be more accurate.

3. **No authentication** — the API is open. In production, add JWT-based auth with tenant claims, and validate that the `tenantId` in the request matches the token's claim.

4. **Campaign counts have a race condition** — `campaign.incrementSent()` is called after the job update. Under high concurrency, these two writes might not be atomic. Production fix: use a single `UPDATE campaigns SET sent_count = sent_count + 1 WHERE id = ?` instead of read-modify-write.

5. **No dead letter queue** — jobs that permanently fail (retry count exceeded) just stay FAILED. Production systems send these to a dead letter topic for manual review.

6. **Timezone handling is simplified** — the DND window rule trusts the `timezone` field in the CSV. In production, validate against the IANA timezone database.

## 5. What Would Change in Production

| Area | Current | Production |
|------|---------|------------|
| Queue | DB polling | Apache Kafka |
| Rate limiting | In-memory | Redis INCR |
| Auth | None | JWT + RBAC |
| Observability | Structured logs | OpenTelemetry → Jaeger + Prometheus + Grafana |
| CSV storage | Streamed in memory | S3 → async processing |
| DB migrations | Flyway (current) | Flyway with blue/green deploy strategy |
| Secrets | Env vars | AWS Secrets Manager / Vault |
| Circuit breaking | Resilience4j per-channel (current) | Istio service mesh + Resilience4j |
| Deployment | Docker Compose | Kubernetes with HPA |
| Testing | Unit + Testcontainers integration tests | Contract tests + chaos engineering |
| Notifications | Simulated (log to DB) | Real SMTP / Twilio / FCM |

## 6. AI Assistance

This project was built with assistance from AI. AI was used for:

- **Boilerplate generation**: JPA entity classes, Spring repository interfaces, DTO builders
- **Test scaffolding**: Unit test structure and assertion patterns
- **SQL query optimization**: Index suggestions for the `notification_jobs` table
- **Documentation**: This ENGINEERING_NOTES.md file

All architectural decisions, business rule implementations, idempotency design, rate limiting strategy, and tradeoff analysis were authored by the developer. AI assistance was used to accelerate implementation of well-understood patterns, not to design the system.
