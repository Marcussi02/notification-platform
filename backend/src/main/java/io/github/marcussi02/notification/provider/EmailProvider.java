package io.github.marcussi02.notification.provider;

import io.github.marcussi02.notification.domain.notification.NotificationJob;
import io.github.marcussi02.notification.domain.notification.Recipient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class EmailProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(EmailProvider.class);
    private static final Random RANDOM = new Random();
    private static final double FAILURE_RATE = 0.20;

    @Override
    public ProviderResult send(NotificationJob job, Recipient recipient, String messageTemplate) {
        simulateLatency();

        if (RANDOM.nextDouble() < FAILURE_RATE) {
            log.warn("[EMAIL] Delivery failed | jobId={} | recipient={}***",
                    job.getId(), maskEmail(recipient.getEmail()));
            return ProviderResult.fail("Provider returned 500 - internal server error");
        }

        log.info("[EMAIL] Delivered | jobId={} | campaignId={} | tenantId={} | to={}",
                job.getId(), job.getCampaignId(), job.getTenantId(), maskEmail(recipient.getEmail()));

        return ProviderResult.ok();
    }

    private void simulateLatency() {
        try {
            Thread.sleep(50 + RANDOM.nextInt(150));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String maskEmail(String email) {
        if (email == null) return "***";
        int at = email.indexOf('@');
        if (at <= 1) return "***@" + email.substring(at + 1);
        return email.charAt(0) + "***" + email.substring(at);
    }
}
