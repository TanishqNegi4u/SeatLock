package com.seatlock.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatlock.service.RateLimitingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@Order(3)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitingService rateLimitingService;
    private final ObjectMapper objectMapper;

    public RateLimitingFilter(RateLimitingService rateLimitingService, ObjectMapper objectMapper) {
        this.rateLimitingService = rateLimitingService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Rate limit POST /api/events/*/seats/*/lock and POST /api/events/*/book
        boolean isRateLimitedPath = "POST".equalsIgnoreCase(method) &&
                (path.matches("/api/events/\\d+/seats/\\d+/lock") || path.matches("/api/events/\\d+/book"));

        if (isRateLimitedPath) {
            UUID userId = (UUID) request.getAttribute(UserSessionFilter.USER_ID_ATTRIBUTE);
            if (userId != null && !rateLimitingService.tryConsume(userId)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setHeader("Retry-After", "10");

                String traceId = MDC.get("traceId");
                Map<String, Object> errorBody = Map.of(
                        "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                        "error", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                        "message", "Rate limit exceeded (10 req / 10s). Please try again shortly.",
                        "traceId", traceId != null ? traceId : "",
                        "timestamp", Instant.now().toString()
                );
                response.getWriter().write(objectMapper.writeValueAsString(errorBody));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
