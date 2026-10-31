package com.autoflow.infrastructure.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newrelic.api.agent.NewRelic;
import jakarta.servlet.FilterChain;
import net.logstash.logback.encoder.LogstashEncoder;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class RequestCorrelationFilterTest {
    private final RequestCorrelationFilter filter = new RequestCorrelationFilter(false);

    @Test
    void usesValidatedRequestIdInResponseAndClearsMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "demo-request.42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        org.mockito.Mockito.doAnswer(invocation -> {
            assertThat(MDC.get("request.id")).isEqualTo("demo-request.42");
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Request-ID")).isEqualTo("demo-request.42");
        verify(chain).doFilter(request, response);
        assertThat(MDC.get("request.id")).isNull();
    }

    @Test
    void traceparentIsNotCopiedToMdcOrInventedByTheRequestFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        org.mockito.Mockito.doAnswer(invocation -> {
            assertThat(MDC.get("request.id")).isNotBlank();
            assertThat(MDC.get("trace.id")).isNull();
            assertThat(MDC.get("span.id")).isNull();
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Request-ID")).isNotBlank();
        assertThat(MDC.get("trace.id")).isNull();
        assertThat(MDC.get("span.id")).isNull();
    }

    @Test
    void replacesMalformedOrOversizedRequestId() {
        String generated = RequestCorrelationFilter.validatedRequestId("bad\nrequest");
        assertThat(UUID.fromString(generated)).isNotNull();
        assertThat(RequestCorrelationFilter.validatedRequestId("x".repeat(129)))
                .matches(value -> UUID.fromString(value) != null);
    }

    @Test
    void ignoresActuatorHealthTransactionsFromApm() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health/liveness");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        try (MockedStatic<NewRelic> newRelic = mockStatic(NewRelic.class)) {
            filter.doFilter(request, response, chain);

            newRelic.verify(NewRelic::ignoreTransaction);
        }
    }

    @Test
    void healthProbeStillRunsWhenApmIgnoreFailsAndExactHealthPathIsIgnored() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        try (MockedStatic<NewRelic> newRelic = mockStatic(NewRelic.class)) {
            newRelic.when(NewRelic::ignoreTransaction).thenThrow(new IllegalStateException("vendor detail"));

            filter.doFilter(request, response, chain);

            newRelic.verify(NewRelic::ignoreTransaction);
            verify(chain).doFilter(request, response);
            assertThat(response.getHeader("X-Request-ID")).isNotBlank();
            assertThat(MDC.get("request.id")).isNull();
        }
    }

    @Test
    void doesNotIgnoreNonHealthActuatorRoutes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/info");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        try (MockedStatic<NewRelic> newRelic = mockStatic(NewRelic.class)) {
            filter.doFilter(request, response, chain);
            newRelic.verifyNoInteractions();
        }
    }

    @Test
    void doesNotLogCompletionWhenFeatureFlagIsDisabled() throws Exception {
        try (LogCapture capture = new LogCapture()) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/clientes/me");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            assertThat(capture.events.list).isEmpty();
        }
    }

    @Test
    void logsOnlySafeStructuredCompletionFieldsAndKeepsRequestIdInMdcUntilEmission() throws Exception {
        String cpf = "12345678909";
        String cnpj = "12345678000195";
        String jwt = "eyJhbGciOiJIUzI1NiJ9.synthetic.signature";
        String authorization = "Bearer synthetic-authorization-token";
        String email = "cliente@example.com";

        try (LogCapture capture = new LogCapture()) {
            RequestCorrelationFilter enabledFilter = new RequestCorrelationFilter(true);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/clientes/me");
            request.addHeader("X-Request-ID", "homolog-request.42");
            request.addHeader("Authorization", authorization);
            request.setQueryString("cpf=" + cpf + "&cnpj=" + cnpj + "&token=" + jwt + "&email=" + email);
            request.setContent(("{\"cpf\":\"" + cpf + "\",\"email\":\"" + email + "\"}")
                    .getBytes(StandardCharsets.UTF_8));
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);
            doAnswer(invocation -> {
                assertThat(MDC.get("request.id")).isEqualTo("homolog-request.42");
                response.setStatus(202);
                return null;
            }).when(chain).doFilter(request, response);

            enabledFilter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(202);
            assertThat(MDC.get("request.id")).isNull();
            assertThat(capture.events.list).hasSize(1);
            ILoggingEvent event = capture.events.list.getFirst();
            assertThat(event.getLevel()).isEqualTo(Level.INFO);

            JsonNode payload = toJson(event);
            assertThat(payload.get("message").asText()).isEqualTo("HTTP request completed");
            assertThat(payload.get("http.method").asText()).isEqualTo("GET");
            assertThat(payload.get("http.route").asText()).isEqualTo("/clientes/me");
            assertThat(payload.get("http.status_code").asInt()).isEqualTo(202);
            assertThat(payload.get("duration_ms").asLong()).isGreaterThanOrEqualTo(0);
            assertThat(payload.get("request.id").asText()).isEqualTo("homolog-request.42");
            assertThat(payload.has("trace.id")).isFalse();
            assertThat(payload.has("span.id")).isFalse();
            assertThat(payload.toString()).doesNotContain(cpf, cnpj, jwt, authorization, email);
        }
    }

    @Test
    void excludesHealthchecksOtherMethodsAndUnselectedRoutesEvenWhenEnabled() throws Exception {
        RequestCorrelationFilter enabledFilter = new RequestCorrelationFilter(true);
        try (LogCapture capture = new LogCapture()) {
            assertNoCompletionLog(enabledFilter, "GET", "/actuator/health");
            assertNoCompletionLog(enabledFilter, "GET", "/clientes/me/ordens-servico");
            assertNoCompletionLog(enabledFilter, "GET", "/clientes/12345678909");
            assertNoCompletionLog(enabledFilter, "POST", "/clientes/me");

            assertThat(capture.events.list).isEmpty();
        }
    }

    private static void assertNoCompletionLog(RequestCorrelationFilter enabledFilter, String method, String route)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, route);
        MockHttpServletResponse response = new MockHttpServletResponse();
        enabledFilter.doFilter(request, response, mock(FilterChain.class));
    }

    private JsonNode toJson(ILoggingEvent event) throws Exception {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        LogstashEncoder encoder = new LogstashEncoder();
        encoder.setContext(context);
        encoder.setIncludeMdc(true);
        encoder.addIncludeMdcKeyName("request.id");
        encoder.addIncludeMdcKeyName("trace.id");
        encoder.addIncludeMdcKeyName("span.id");
        encoder.start();
        try {
            String json = new String(encoder.encode(event), StandardCharsets.UTF_8).trim();
            return new ObjectMapper().readTree(json);
        } finally {
            encoder.stop();
        }
    }

    private static final class LogCapture implements AutoCloseable {
        private final Logger logger = (Logger) LoggerFactory.getLogger(RequestCorrelationFilter.class);
        private final ListAppender<ILoggingEvent> events = new ListAppender<>();

        private LogCapture() {
            events.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
            events.start();
            logger.addAppender(events);
        }

        @Override
        public void close() {
            logger.detachAppender(events);
            events.stop();
        }
    }
}
