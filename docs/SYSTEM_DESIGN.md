**System Design**

Multi-Tenant Notification & Campaign Platform

System Design

**1. Overview**

This platform allows companies (tenants) to send large-scale
notifications — Email, SMS, and Push — to lists of recipients uploaded
as CSV files. The API accepts campaigns immediately (HTTP 202) and
processes them asynchronously in the background.

- **High throughput:** Millions of notifications per day across many
  tenants.

- **Multi-tenant isolation:** Every query is scoped to a tenant_id — no
  cross-tenant data access.

- **Reliability:** Exponential backoff retries, circuit breakers, and
  idempotent job claiming.

- **Compliance:** Opt-out enforcement, DND quiet hours, PII masking in
  logs.

**2. Architecture Diagram**

<img src="docs/media/49b6d4174292fa055532265a4667b6a288d60ac6.png"
title="Architecture" style="width:6.45833in;height:3.90625in"
alt="System architecture diagram" />

*Figure 1 — Production Architecture*

**3. Campaign Flow**

Operators call **POST /api/campaigns** with a CSV, message template, and
channel. The API returns 202 immediately. In the background, each CSV
row becomes a notification job processed through this pipeline:

|                     |                                                                              |
|---------------------|------------------------------------------------------------------------------|
| **Stage**           | **What Happens**                                                             |
| **Rule Engine**     | Checks suppression list, DND window (10 PM–8 AM), and deduplication          |
| **Rate Limiter**    | 100 notifications/min per channel (Token Bucket via Resilience4j)            |
| **Provider Call**   | Sends to Email / SMS / Push — 50–200 ms latency, ~20% failure rate simulated |
| **Retry / Backoff** | 30 s → 60 s → 120 s → permanently FAILED after 4th attempt                   |
| **Audit Record**    | DeliveryAttempt saved; campaign counters updated                             |

Jobs are claimed atomically (UPDATE … WHERE status = 'PENDING') so two
workers can never send the same notification twice.

**4. Reliability**

**Failure Scenarios**

|                               |                                                                                                    |
|-------------------------------|----------------------------------------------------------------------------------------------------|
| **Scenario**                  | **How the System Handles It**                                                                      |
| **Provider returns an error** | Exponential backoff — retry at 30 s, 60 s, 120 s then permanently FAILED                           |
| **Provider down for hours**   | Circuit breaker opens after 5 failures; batch cap + rate limiter absorb the burst when it recovers |
| **Worker crashes mid-job**    | Stuck PROCESSING rows reset to PENDING after 5-minute timeout                                      |
| **Retry storm**               | Batch cap of 20 jobs/tick + queue limit of 500 + rate limiter protect the system                   |

**Business Rules (Rule Engine)**

|                        |           |                                            |
|------------------------|-----------|--------------------------------------------|
| **Rule**               | **Level** | **Action**                                 |
| **Global Suppression** | Recipient | SKIP — recipient opted out                 |
| **DND Window**         | Channel   | DELAY until 8 AM local time                |
| **Credit Check**       | Tenant    | REJECT — monthly limit exceeded            |
| **Deduplication**      | Campaign  | DISCARD — same message sent in last 5 mins |

**5. Scaling**

|                                    |                                                                           |
|------------------------------------|---------------------------------------------------------------------------|
| **Approach**                       | **Impact**                                                                |
| **Horizontal pod scaling (× N)**   | ~4.3M/day per pod — 10 pods → ~43M/day                                    |
| **Kafka (replace DB polling)**     | True parallel consumption, no scheduler lag                               |
| **Redis rate limiter**             | Shared counters across all pods                                           |
| **Read replicas (PostgreSQL)**     | Offload dashboard/reporting queries from write primary                    |
| **CSV streaming (Apache Commons)** | 2M-row file never loads into memory — ~3 min ingestion at 10K inserts/sec |

**6. Data Strategy & Observability**

**Database Tables**

|                       |                                             |
|-----------------------|---------------------------------------------|
| **Table**             | **Purpose**                                 |
| **tenants**           | Monthly campaign/message limits per company |
| **campaigns**         | Status and delivery counters                |
| **notification_jobs** | The work queue — drives all processing      |
| **delivery_attempts** | Full audit trail of every send attempt      |
| **suppression_list**  | Opt-out records checked before every send   |

**Key Metrics to Monitor**

|                            |                                         |
|----------------------------|-----------------------------------------|
| **Metric**                 | **Alert Threshold**                     |
| **Queue depth**            | \> 10,000 jobs (workers falling behind) |
| **Circuit breaker state**  | OPEN (provider down)                    |
| **Failure rate**           | \> 25% (provider or data issue)         |
| **p99 processing latency** | \> 5 seconds (workers overloaded)       |

**7. Security**

- **JWT authentication:** tenant_id from token validated on every
  request — no cross-tenant access possible.

- **Secrets management:** AWS Secrets Manager / HashiCorp Vault for DB
  passwords and provider API keys.

- **PII masking in logs:** Emails logged as a\*\*\*@domain.com, phones
  as \*\*\*1234.

- **Encryption at rest:** AES-256 / PostgreSQL pgcrypto for recipient
  data.

- **Audit trail:** Every delivery attempt timestamped for SOC2/GDPR
  compliance.

*End of System Design Document*
