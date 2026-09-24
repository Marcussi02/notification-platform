package io.github.marcussi02.notification.provider;

import io.github.marcussi02.notification.domain.notification.NotificationJob;
import io.github.marcussi02.notification.domain.notification.Recipient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class SmsProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(SmsProvider.class);
    private static final Random RANDOM = new Random();
    private static final double FAILURE_RATE = 0.15;

    @Override
    public ProviderResult send(NotificationJob job, Recipient recipient, String messageTemplate) {
        simulateLatency();

        if (RANDOM.nextDouble() < FAILURE_RATE) {
            log.warn("[SMS] Delivery failed | jobId={} | phone={}",
                    job.getId(), maskPhone(recipient.getPhone()));
            return ProviderResult.fail("SMS gateway timeout");
        }

        log.info("[SMS] Delivered | jobId={} | campaignId={} | tenantId={} | to={}",
                job.getId(), job.getCampaignId(), job.getTenantId(), maskPhone(recipient.getPhone()));

        return ProviderResult.ok();
    }

    private void simulateLatency() {
        try {
            Thread.sleep(50 + RANDOM.nextInt(150));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return "***" + phone.substring(phone.length() - 4);
    }
}
