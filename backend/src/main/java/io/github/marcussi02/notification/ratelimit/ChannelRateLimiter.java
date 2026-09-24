package io.github.marcussi02.notification.ratelimit;

import io.github.marcussi02.notification.domain.campaign.Channel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Token bucket rate limiter — allows up to requestsPerMinute calls per channel per minute.
 * The counter resets every 60 seconds via a scheduled task.
 */
@Component
public class ChannelRateLimiter {

    @Value("${app.rate-limit.requests-per-minute:100}")
    private int requestsPerMinute;

    private final ConcurrentHashMap<Channel, AtomicInteger> counters = new ConcurrentHashMap<>();

    public ChannelRateLimiter() {
        for (Channel channel : Channel.values()) {
            counters.put(channel, new AtomicInteger(0));
        }
    }

    /**
     * Try to acquire a slot for the given channel.
     * Returns true if the request is allowed, false if rate-limited.
     */
    public boolean tryAcquire(Channel channel) {
        AtomicInteger counter = counters.get(channel);
        int current = counter.incrementAndGet();
        return current <= requestsPerMinute;
    }

    // Resets all counters every minute
    @Scheduled(fixedRate = 60_000)
    public void resetCounters() {
        counters.values().forEach(counter -> counter.set(0));
    }

    public int getUsage(Channel channel) {
        return counters.getOrDefault(channel, new AtomicInteger(0)).get();
    }
}
