package com.autoflow.infrastructure.observability;

import com.newrelic.api.agent.NewRelic;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.slf4j.MDC;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mockStatic;

class RequestCorrelationFilterTest {
    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

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
}
