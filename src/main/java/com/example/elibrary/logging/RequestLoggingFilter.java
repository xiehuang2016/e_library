package com.example.elibrary.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
        long startTime = System.nanoTime();
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try (ScopeValue ignored = ScopeValue.open(ScopeValue.REQUEST_ID, requestId)) {
            try {
                filterChain.doFilter(request, response);
            } finally {
                long elapsedMillis = (System.nanoTime() - startTime) / 1_000_000;
                log.info(
                        "http_request_completed method={} status={} durationMs={}",
                        request.getMethod(),
                        response.getStatus(),
                        elapsedMillis);
            }
        }
    }

    private String resolveRequestId(String suppliedRequestId) {
        if (suppliedRequestId != null && SAFE_REQUEST_ID.matcher(suppliedRequestId).matches()) {
            return suppliedRequestId;
        }
        return UUID.randomUUID().toString();
    }
}
