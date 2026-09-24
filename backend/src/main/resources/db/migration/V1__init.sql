-- Tenants table
CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    monthly_campaign_limit INT NOT NULL DEFAULT 100,
    monthly_message_limit INT NOT NULL DEFAULT 1000000,
    campaigns_used INT NOT NULL DEFAULT 0,
    messages_used INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Campaigns table
CREATE TABLE campaigns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(255) NOT NULL,
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('EMAIL', 'SMS', 'PUSH')),
    message_template TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'RUNNING', 'COMPLETED', 'FAILED')),
    scheduled_time TIMESTAMP,
    total_recipients INT NOT NULL DEFAULT 0,
    sent_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_campaigns_tenant_id ON campaigns(tenant_id);
CREATE INDEX idx_campaigns_status ON campaigns(status);
CREATE INDEX idx_campaigns_created_at ON campaigns(created_at DESC);

-- Recipients table (one row per recipient per campaign)
CREATE TABLE recipients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES campaigns(id),
    tenant_id UUID NOT NULL,
    recipient_id VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(50),
    timezone VARCHAR(50) DEFAULT 'UTC',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recipients_campaign_id ON recipients(campaign_id);
CREATE INDEX idx_recipients_tenant_id ON recipients(tenant_id);

-- Global suppression list (opt-outs per channel)
CREATE TABLE suppression_list (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    recipient_id VARCHAR(255) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, recipient_id, channel)
);

CREATE INDEX idx_suppression_tenant_recipient ON suppression_list(tenant_id, recipient_id);

-- Notification jobs (one per recipient per campaign)
CREATE TABLE notification_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES campaigns(id),
    tenant_id UUID NOT NULL,
    recipient_id UUID NOT NULL REFERENCES recipients(id),
    -- Idempotency key ensures no duplicate sends
    idempotency_key VARCHAR(512) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'SKIPPED', 'DELAYED')),
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (idempotency_key)
);

CREATE INDEX idx_notification_jobs_campaign_id ON notification_jobs(campaign_id);
CREATE INDEX idx_notification_jobs_tenant_id ON notification_jobs(tenant_id);
CREATE INDEX idx_notification_jobs_status ON notification_jobs(status);
-- Composite index for the processor query (status + next_retry_at)
CREATE INDEX idx_notification_jobs_processable ON notification_jobs(status, next_retry_at)
    WHERE status IN ('PENDING', 'FAILED', 'DELAYED');

-- Delivery attempts (one per send attempt for auditing)
CREATE TABLE delivery_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_job_id UUID NOT NULL REFERENCES notification_jobs(id),
    attempt_number INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    attempted_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_delivery_attempts_job_id ON delivery_attempts(notification_job_id);
CREATE INDEX idx_delivery_attempts_attempted_at ON delivery_attempts(attempted_at DESC);

-- Seed data: default tenants
INSERT INTO tenants (id, name, monthly_campaign_limit, monthly_message_limit)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'Demo Tenant', 100, 1000000),
    ('00000000-0000-0000-0000-000000000002', 'Acme Corp', 50, 500000),
    ('00000000-0000-0000-0000-000000000003', 'Tech Startup', 200, 2000000);
