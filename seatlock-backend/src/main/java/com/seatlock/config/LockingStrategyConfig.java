package com.seatlock.config;

import com.seatlock.service.locking.LockingStrategy;
import com.seatlock.service.locking.OptimisticLockingStrategy;
import com.seatlock.service.locking.PessimisticLockingStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LockingStrategyConfig {

    @Value("${seatlock.locking-strategy:PESSIMISTIC}")
    private String lockingStrategy;

    @Bean
    public LockingStrategy lockingStrategy(
            PessimisticLockingStrategy pessimistic,
            OptimisticLockingStrategy optimistic) {
        return switch (lockingStrategy.toUpperCase()) {
            case "OPTIMISTIC" -> optimistic;
            case "PESSIMISTIC" -> pessimistic;
            default -> throw new IllegalArgumentException(
                "Unknown locking strategy: " + lockingStrategy + ". Use PESSIMISTIC or OPTIMISTIC.");
        };
    }
}
