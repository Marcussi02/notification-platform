package io.github.marcussi02.notification.domain.notification;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "delivery_attempts")
public class DeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "notification_job_id", nullable = false)
    private UUID notificationJobId;

    @Column(name = "attempt_number")
    private int attemptNumber;

    @Column(nullable = false)
    private String status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "attempted_at")
    private LocalDateTime attemptedAt = LocalDateTime.now();

    public DeliveryAttempt() {}

    public UUID getId() { return id; }
    public UUID getNotificationJobId() { return notificationJobId; }
    public void setNotificationJobId(UUID notificationJobId) { this.notificationJobId = notificationJobId; }
    public int getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getAttemptedAt() { return attemptedAt; }
    public void setAttemptedAt(LocalDateTime attemptedAt) { this.attemptedAt = attemptedAt; }
}
