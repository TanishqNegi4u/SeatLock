package com.seatlock.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns an anonymous session UUID to every user via a cookie.
 * If the cookie is missing, a new UUID is generated and set.
 * The userId is always stored as a request attribute for controllers to read.
 */
@Component
@Order(1)
public class UserSessionFilter extends OncePerRequestFilter {

    public static final String USER_ID_ATTRIBUTE = "seatlock.userId";
    public static final String COOKIE_NAME = "seatlock_user_id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        UUID userId = extractUserIdFromCookie(request);

        if (userId == null) {
            userId = UUID.randomUUID();
            Cookie cookie = new Cookie(COOKIE_NAME, userId.toString());
            cookie.setPath("/");
            cookie.setMaxAge(60 * 60 * 24); // 24 hours
            cookie.setHttpOnly(true);
            response.addCookie(cookie);
        }

        request.setAttribute(USER_ID_ATTRIBUTE, userId);
        filterChain.doFilter(request, response);
    }

    private UUID extractUserIdFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                try {
                    return UUID.fromString(cookie.getValue());
                } catch (IllegalArgumentException e) {
                    return null; // Malformed cookie, will regenerate
                }
            }
        }
        return null;
    }
}
