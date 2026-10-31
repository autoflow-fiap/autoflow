package com.autoflow.infrastructure.observability;

import com.newrelic.api.agent.NewRelic;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {
    static final String HEADER_NAME = "X-Request-ID";
    static final String MDC_KEY = "request.id";
    private static final String HTTP_LOGGING_ENABLED_PROPERTY = "app.observability.http.request.logging.enabled";
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestCorrelationFilter.class);
    private static final String SELECTED_METHOD = "GET";
    private static final String SELECTED_ROUTE = "/clientes/me";
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private final boolean requestCompletionLoggingEnabled;

    public RequestCorrelationFilter(
            @Value("${" + HTTP_LOGGING_ENABLED_PROPERTY + ":false}") boolean requestCompletionLoggingEnabled) {
        this.requestCompletionLoggingEnabled = requestCompletionLoggingEnabled;
    }

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
        boolean selectedForLogging = requestCompletionLoggingEnabled && isSelectedRequest(request);
        long startNanos = selectedForLogging ? System.nanoTime() : 0;
        try {
            filterChain.doFilter(request, response);
        } finally {
            try {
                if (selectedForLogging) {
                    logRequestCompletion(response, System.nanoTime() - startNanos);
                }
            } finally {
                MDC.remove(MDC_KEY);
            }
        }
    }

    private boolean isSelectedRequest(HttpServletRequest request) {
        return SELECTED_METHOD.equals(request.getMethod()) && SELECTED_ROUTE.equals(request.getRequestURI());
    }

    private void logRequestCompletion(HttpServletResponse response, long elapsedNanos) {
        long durationMillis = Math.max(0, elapsedNanos / 1_000_000);
        try {
            LOGGER.info("HTTP request completed",
                    StructuredArguments.keyValue("http.method", SELECTED_METHOD),
                    StructuredArguments.keyValue("http.route", SELECTED_ROUTE),
                    StructuredArguments.keyValue("http.status_code", response.getStatus()),
                    StructuredArguments.keyValue("duration_ms", durationMillis));
        } catch (RuntimeException ignored) {
            // A observabilidade não deve alterar o resultado da requisição.
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
