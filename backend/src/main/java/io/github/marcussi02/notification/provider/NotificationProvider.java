package io.github.marcussi02.notification.provider;

import io.github.marcussi02.notification.domain.notification.NotificationJob;
import io.github.marcussi02.notification.domain.notification.Recipient;

public interface NotificationProvider {
    ProviderResult send(NotificationJob job, Recipient recipient, String messageTemplate);
}
