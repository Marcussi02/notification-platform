package io.github.marcussi02.notification.provider;

import io.github.marcussi02.notification.domain.notification.NotificationJob;
import io.github.marcussi02.notification.domain.notification.Recipient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class PushProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(PushProvider.class);
    private static final Random RANDOM = new Random();
    private static final double FAILURE_RATE = 0.25;

    @Override
    public ProviderResult send(NotificationJob job, Recipient recipient, String messageTemplate) {
        simulateLatency();

        if (RANDOM.nextDouble() < FAILURE_RATE) {
            log.warn("[PUSH] Delivery failed | jobId={} | recipientId={}",
                    job.getId(), recipient.getRecipientId());
            return ProviderResult.fail("Device token expired or invalid");
        }

        log.info("[PUSH] Delivered | jobId={} | campaignId={} | tenantId={} | recipientId={}",
                job.getId(), job.getCampaignId(), job.getTenantId(), recipient.getRecipientId());

        return ProviderResult.ok();
    }

    private void simulateLatency() {
        try {
            Thread.sleep(50 + RANDOM.nextInt(150));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
