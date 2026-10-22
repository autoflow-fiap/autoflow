package com.autoflow.infrastructure.observability;

import com.newrelic.api.agent.NewRelic;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {
    static final String HEADER_NAME = "X-Request-ID";
    static final String MDC_KEY = "request.id";
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isHealthProbe(request.getRequestURI())) {
            try {
                NewRelic.ignoreTransaction();
            } catch (RuntimeException | LinkageError ignored) {
                // APM filtering must never affect Kubernetes health probes.
            }
        }
        String requestId = validatedRequestId(request.getHeader(HEADER_NAME));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private boolean isHealthProbe(String requestUri) {
        return requestUri.equals("/actuator/health") || requestUri.startsWith("/actuator/health/");
    }

    static String validatedRequestId(String candidate) {
        return candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()
                ? candidate
                : UUID.randomUUID().toString();
    }
}
