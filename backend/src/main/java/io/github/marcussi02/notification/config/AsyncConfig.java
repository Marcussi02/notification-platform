package io.github.marcussi02.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Value("${app.worker.pool-size:10}")
    private int poolSize;

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize * 2);
        // Queue capacity acts as back-pressure: if queue fills, reject new tasks
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notif-worker-");
        executor.setRejectedExecutionHandler((r, e) -> {
            // Log rejection (back-pressure activated) instead of silently dropping
            throw new RuntimeException("Notification worker queue is full - back-pressure activated");
        });
        executor.initialize();
        return executor;
    }
}
