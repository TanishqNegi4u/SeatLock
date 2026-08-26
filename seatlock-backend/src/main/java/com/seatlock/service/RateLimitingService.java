package com.seatlock.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token bucket rate limiting using Bucket4j.
 * Adheres strictly to the in-memory/JVM-local design without external Redis/MQ.
 */
@Service
public class RateLimitingService {

    private final Map<UUID, Bucket> userBuckets = new ConcurrentHashMap<>();

    @Value("${seatlock.rate-limit.capacity:10}")
    private long capacity;

    @Value("${seatlock.rate-limit.refill-tokens:10}")
    private long refillTokens;

    @Value("${seatlock.rate-limit.refill-duration-seconds:10}")
    private long refillDurationSeconds;

    public boolean tryConsume(UUID userId) {
        if (userId == null) return true;
        Bucket bucket = userBuckets.computeIfAbsent(userId, this::createNewBucket);
        return bucket.tryConsume(1);
    }

    public Bucket resolveBucket(UUID userId) {
        return userBuckets.computeIfAbsent(userId, this::createNewBucket);
    }

    private Bucket createNewBucket(UUID userId) {
        Bandwidth limit = Bandwidth.classic(
                capacity,
                Refill.greedy(refillTokens, Duration.ofSeconds(refillDurationSeconds))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    public void clear() {
        userBuckets.clear();
    }
}
