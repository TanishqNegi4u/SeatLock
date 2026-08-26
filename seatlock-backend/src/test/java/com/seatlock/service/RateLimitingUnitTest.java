package com.seatlock.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitingUnitTest {

    private RateLimitingService rateLimitingService;

    @BeforeEach
    void setUp() {
        rateLimitingService = new RateLimitingService();
        ReflectionTestUtils.setField(rateLimitingService, "capacity", 10L);
        ReflectionTestUtils.setField(rateLimitingService, "refillTokens", 10L);
        ReflectionTestUtils.setField(rateLimitingService, "refillDurationSeconds", 10L);
    }

    @Test
    @DisplayName("Should allow up to 10 requests and reject the 11th within the rate limit window")
    void testRateLimitConsumptionAndRejection() {
        UUID userId = UUID.randomUUID();

        // First 10 requests must succeed
        for (int i = 1; i <= 10; i++) {
            boolean allowed = rateLimitingService.tryConsume(userId);
            assertTrue(allowed, "Request #" + i + " should be allowed");
        }

        // 11th request must be rejected (exhausted bucket)
        boolean allowedAfterLimit = rateLimitingService.tryConsume(userId);
        assertFalse(allowedAfterLimit, "11th request within window should be rejected (429)");
    }

    @Test
    @DisplayName("Rate limits should be isolated per user ID")
    void testPerUserRateLimitIsolation() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        // Exhaust User A's bucket
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimitingService.tryConsume(userA));
        }
        assertFalse(rateLimitingService.tryConsume(userA));

        // User B's bucket must still have full capacity
        assertTrue(rateLimitingService.tryConsume(userB));
    }
}
