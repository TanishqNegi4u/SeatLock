package com.seatlock.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock payment service with configurable failure rate.
 * In production, this would call Stripe/PayPal/etc.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    @Value("${seatlock.payment.failure-rate:0.20}")
    private double failureRate;

    @Value("${seatlock.payment.simulated-delay-ms:500}")
    private long simulatedDelayMs;

    @Value("${seatlock.chaos.payment-delay-ms:0}")
    private long chaosDelayMs;

    /**
     * Simulate a payment. Returns true if payment succeeds.
     * Failure rate is configurable via seatlock.payment.failure-rate.
     */
    public boolean processPayment(UUID userId, Long seatId, String idempotencyKey) {
        log.info("[PAYMENT] Processing payment for user={} seat={} key={}",
                userId.toString().substring(0, 8), seatId, idempotencyKey.substring(0, 8));

        // Normal processing delay
        if (simulatedDelayMs > 0) {
            sleep(simulatedDelayMs);
        }

        // Chaos delay — for pod-kill testing (Step 10)
        // Set seatlock.chaos.payment-delay-ms=10000 to hold a txn open for 10s
        if (chaosDelayMs > 0) {
            log.warn("[CHAOS] Injecting {}ms payment delay for chaos testing", chaosDelayMs);
            sleep(chaosDelayMs);
        }

        // Random failure based on configured rate
        boolean success = ThreadLocalRandom.current().nextDouble() >= failureRate;

        if (success) {
            log.info("[PAYMENT] Payment SUCCEEDED for user={} seat={}",
                    userId.toString().substring(0, 8), seatId);
        } else {
            log.warn("[PAYMENT] Payment FAILED for user={} seat={} (simulated failure, rate={})",
                    userId.toString().substring(0, 8), seatId, failureRate);
        }

        return success;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Payment processing interrupted", e);
        }
    }
}
