package com.seatlock.service;

import com.seatlock.exception.PaymentGatewayException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock payment service with configurable failure rate and Resilience4j circuit breaking / retries.
 * In production, this would call Stripe/PayPal/etc.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    @Value("${seatlock.payment.failure-rate:0.20}")
    private double failureRate;

    @Value("${seatlock.payment.intermittent-failure-rate:0.0}")
    private double intermittentFailureRate;

    @Value("${seatlock.payment.simulated-delay-ms:500}")
    private long simulatedDelayMs;

    @Value("${seatlock.chaos.payment-delay-ms:0}")
    private long chaosDelayMs;

    /**
     * Simulate a payment with CircuitBreaker and Retry protection.
     * Intermittent gateway exceptions trigger retries and trip the circuit breaker if persistent.
     */
    @CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
    @Retry(name = "paymentService", fallbackMethod = "paymentFallback")
    public boolean processPayment(UUID userId, Long seatId, String idempotencyKey) {
        log.info("[PAYMENT] Processing payment for user={} seat={} key={}",
                userId.toString().substring(0, 8), seatId, idempotencyKey.substring(0, 8));

        // Simulated processing delay
        if (simulatedDelayMs > 0) {
            sleep(simulatedDelayMs);
        }

        // Chaos delay for pod kill testing
        if (chaosDelayMs > 0) {
            log.warn("[CHAOS] Injecting {}ms payment delay for chaos testing", chaosDelayMs);
            sleep(chaosDelayMs);
        }

        // Intermittent network gateway failure simulation
        if (intermittentFailureRate > 0 && ThreadLocalRandom.current().nextDouble() < intermittentFailureRate) {
            log.error("[PAYMENT] Simulated gateway connectivity error (rate={})", intermittentFailureRate);
            throw new PaymentGatewayException("Payment gateway unreachable or timed out");
        }

        // Standard payment success / failure logic
        boolean success = ThreadLocalRandom.current().nextDouble() >= failureRate;

        if (success) {
            log.info("[PAYMENT] Payment SUCCEEDED for user={} seat={}",
                    userId.toString().substring(0, 8), seatId);
        } else {
            log.warn("[PAYMENT] Payment FAILED for user={} seat={} (declined by bank, rate={})",
                    userId.toString().substring(0, 8), seatId, failureRate);
        }

        return success;
    }

    /**
     * Fallback method executed when CircuitBreaker is OPEN or retries are exhausted.
     */
    public boolean paymentFallback(UUID userId, Long seatId, String idempotencyKey, Throwable t) {
        log.warn("[PAYMENT-FALLBACK] Circuit breaker / retry fallback triggered for user={} seat={}: {}",
                userId != null ? userId.toString().substring(0, 8) : "null",
                seatId,
                t.getMessage());
        return false;
    }

    public void setIntermittentFailureRate(double rate) {
        this.intermittentFailureRate = rate;
    }

    public void setFailureRate(double rate) {
        this.failureRate = rate;
    }

    public void setSimulatedDelayMs(long ms) {
        this.simulatedDelayMs = ms;
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
