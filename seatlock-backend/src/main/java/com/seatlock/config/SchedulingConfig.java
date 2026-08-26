package com.seatlock.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("seatlock-scheduler-");
        scheduler.setErrorHandler(t -> 
            org.slf4j.LoggerFactory.getLogger("SchedulerErrorHandler")
                .error("Scheduled task failed", t));
        return scheduler;
    }
}
