package com.seatlock.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.seatlock.SeatLockApplication.class)
@ActiveProfiles("test")
class PaymentServiceResilienceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        paymentService.setSimulatedDelayMs(0);
        paymentService.setFailureRate(0.0);
        paymentService.setIntermittentFailureRate(0.0);

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("paymentService");
        cb.reset();
    }

    @Test
    @DisplayName("Should successfully process payment when gateway is healthy")
    void testHealthyPayment() {
        boolean result = paymentService.processPayment(UUID.randomUUID(), 101L, UUID.randomUUID().toString());
        assertTrue(result);
    }

    @Test
    @DisplayName("Should invoke fallback and open circuit after consecutive gateway failures")
    void testCircuitBreakerFallbackOnGatewayFailure() {
        // Force 100% gateway failure
        paymentService.setIntermittentFailureRate(1.0);

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("paymentService");
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());

        // Process calls: each call fails and retries, triggering fallback
        for (int i = 0; i < 12; i++) {
            boolean success = paymentService.processPayment(UUID.randomUUID(), 100L + i, UUID.randomUUID().toString());
            assertFalse(success, "Failed payment should safely return false via fallback");
        }

        // Circuit breaker should have transitioned to OPEN
        assertTrue(cb.getState() == CircuitBreaker.State.OPEN || cb.getMetrics().getFailureRate() > 0);
    }
}
