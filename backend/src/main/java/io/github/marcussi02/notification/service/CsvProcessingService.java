package io.github.marcussi02.notification.service;

import io.github.marcussi02.notification.domain.notification.NotificationJob;
import io.github.marcussi02.notification.domain.notification.NotificationStatus;
import io.github.marcussi02.notification.domain.notification.Recipient;
import io.github.marcussi02.notification.repository.NotificationJobRepository;
import io.github.marcussi02.notification.repository.RecipientRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class CsvProcessingService {

    private static final Logger log = LoggerFactory.getLogger(CsvProcessingService.class);

    private final RecipientRepository recipientRepository;
    private final NotificationJobRepository notificationJobRepository;

    public CsvProcessingService(RecipientRepository recipientRepository,
                                NotificationJobRepository notificationJobRepository) {
        this.recipientRepository     = recipientRepository;
        this.notificationJobRepository = notificationJobRepository;
    }

    @Transactional
    public int processRecipients(InputStream csvStream, UUID campaignId, UUID tenantId) throws IOException {
        int count = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvStream, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT
                     .builder()
                     .setHeader("recipientId", "email", "phone")
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                String recipientId = record.get("recipientId");
                String email       = record.get("email");
                String phone       = record.get("phone");

                if (recipientId == null || recipientId.isBlank()) {
                    log.warn("Skipping CSV row {} - missing recipientId", parser.getCurrentLineNumber());
                    continue;
                }

                Recipient recipient = new Recipient();
                recipient.setCampaignId(campaignId);
                recipient.setTenantId(tenantId);
                recipient.setRecipientId(recipientId);
                recipient.setEmail(email);
                recipient.setPhone(phone);

                Recipient saved = recipientRepository.save(recipient);

                String idempotencyKey = campaignId + ":" + saved.getId();

                NotificationJob job = new NotificationJob();
                job.setCampaignId(campaignId);
                job.setTenantId(tenantId);
                job.setRecipientId(saved.getId());
                job.setIdempotencyKey(idempotencyKey);
                job.setStatus(NotificationStatus.PENDING);

                try {
                    notificationJobRepository.save(job);
                    count++;
                } catch (Exception e) {
                    log.debug("Skipping duplicate job for idempotency key: {}", idempotencyKey);
                }
            }
        }

        log.info("CSV processing complete | campaignId={} | recipientsLoaded={}", campaignId, count);
        return count;
    }
}
