package io.github.marcussi02.notification.provider;

import io.github.marcussi02.notification.domain.campaign.Channel;
import io.github.marcussi02.notification.domain.notification.NotificationJob;
import io.github.marcussi02.notification.domain.notification.Recipient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ProviderGateway {

    private static final Logger log = LoggerFactory.getLogger(ProviderGateway.class);

    private final EmailProvider emailProvider;
    private final SmsProvider   smsProvider;
    private final PushProvider  pushProvider;

    public ProviderGateway(EmailProvider emailProvider, SmsProvider smsProvider, PushProvider pushProvider) {
        this.emailProvider = emailProvider;
        this.smsProvider   = smsProvider;
        this.pushProvider  = pushProvider;
    }

    public ProviderResult send(Channel channel, NotificationJob job, Recipient recipient, String template) {
        return switch (channel) {
            case EMAIL -> sendEmail(job, recipient, template);
            case SMS   -> sendSms(job, recipient, template);
            case PUSH  -> sendPush(job, recipient, template);
        };
    }

    @CircuitBreaker(name = "email-provider", fallbackMethod = "emailFallback")
    public ProviderResult sendEmail(NotificationJob job, Recipient recipient, String template) {
        return emailProvider.send(job, recipient, template);
    }

    @CircuitBreaker(name = "sms-provider", fallbackMethod = "smsFallback")
    public ProviderResult sendSms(NotificationJob job, Recipient recipient, String template) {
        return smsProvider.send(job, recipient, template);
    }

    @CircuitBreaker(name = "push-provider", fallbackMethod = "pushFallback")
    public ProviderResult sendPush(NotificationJob job, Recipient recipient, String template) {
        return pushProvider.send(job, recipient, template);
    }

    ProviderResult emailFallback(NotificationJob job, Recipient recipient, String template, Throwable t) {
        log.error("Email circuit breaker OPEN | jobId={} | cause={}", job.getId(), t.getMessage());
        return ProviderResult.fail("Email provider unavailable (circuit open): " + t.getMessage());
    }

    ProviderResult smsFallback(NotificationJob job, Recipient recipient, String template, Throwable t) {
        log.error("SMS circuit breaker OPEN | jobId={} | cause={}", job.getId(), t.getMessage());
        return ProviderResult.fail("SMS provider unavailable (circuit open): " + t.getMessage());
    }

    ProviderResult pushFallback(NotificationJob job, Recipient recipient, String template, Throwable t) {
        log.error("Push circuit breaker OPEN | jobId={} | cause={}", job.getId(), t.getMessage());
        return ProviderResult.fail("Push provider unavailable (circuit open): " + t.getMessage());
    }
}
