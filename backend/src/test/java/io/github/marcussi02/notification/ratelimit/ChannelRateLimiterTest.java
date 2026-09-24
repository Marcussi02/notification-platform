package io.github.marcussi02.notification.ratelimit;

import io.github.marcussi02.notification.domain.campaign.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelRateLimiterTest {

    private ChannelRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new ChannelRateLimiter();
        ReflectionTestUtils.setField(rateLimiter, "requestsPerMinute", 3);
    }

    @Test
    void allowsRequestsUnderLimit() {
        assertThat(rateLimiter.tryAcquire(Channel.EMAIL)).isTrue();
        assertThat(rateLimiter.tryAcquire(Channel.EMAIL)).isTrue();
        assertThat(rateLimiter.tryAcquire(Channel.EMAIL)).isTrue();
    }

    @Test
    void blocksRequestsOverLimit() {
        rateLimiter.tryAcquire(Channel.SMS);
        rateLimiter.tryAcquire(Channel.SMS);
        rateLimiter.tryAcquire(Channel.SMS);

        boolean result = rateLimiter.tryAcquire(Channel.SMS);
        assertThat(result).isFalse();
    }

    @Test
    void channelsAreIndependent() {
        // Exhaust EMAIL limit
        for (int i = 0; i < 3; i++) rateLimiter.tryAcquire(Channel.EMAIL);

        // SMS should still be available
        assertThat(rateLimiter.tryAcquire(Channel.SMS)).isTrue();
        assertThat(rateLimiter.tryAcquire(Channel.PUSH)).isTrue();
    }

    @Test
    void resetsCountersAfterReset() {
        // Exhaust limit
        for (int i = 0; i < 3; i++) rateLimiter.tryAcquire(Channel.EMAIL);
        assertThat(rateLimiter.tryAcquire(Channel.EMAIL)).isFalse();

        // Simulate minute reset
        rateLimiter.resetCounters();

        // Should be allowed again
        assertThat(rateLimiter.tryAcquire(Channel.EMAIL)).isTrue();
    }
}
