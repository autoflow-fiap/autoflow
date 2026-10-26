package com.autoflow.infrastructure.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LogJsonCanaryTest {
    private static final String CPF = "12345678909";
    private static final String CNPJ = "12345678000195";
    private static final String JWT = "eyJhbGciOiJIUzI1NiJ9.synthetic.signature";
    private static final String AUTHORIZATION = "Bearer synthetic-authorization-token";
    private static final String QUERY_TOKEN = "synthetic-query-token";

    @Test
    void throwableConverterOmitsFieldWhenEventHasNoException() {
        LoggingEvent event = new LoggingEvent();

        assertThat(new SafeThrowableConverter().convert(event)).isNull();
    }

    @Test
    void emitsValidJsonAndExcludesSensitiveMdcAndExceptionCanaries() throws Exception {
        LoggerContext context = new LoggerContext();
        LogstashEncoder encoder = new LogstashEncoder();
        encoder.setContext(context);
        encoder.setIncludeMdc(true);
        encoder.addIncludeMdcKeyName("request.id");
        encoder.addIncludeMdcKeyName("trace.id");
        encoder.addIncludeMdcKeyName("span.id");
        encoder.setThrowableConverter(new SafeThrowableConverter());
        encoder.start();

        LoggingEvent event = new LoggingEvent();
        event.setLoggerName("synthetic.canary");
        event.setLevel(Level.ERROR);
        event.setMessage("Synthetic observability canary");
        event.setTimeStamp(System.currentTimeMillis());
        event.setMDCPropertyMap(Map.of(
                "request.id", "synthetic-request-01",
                "Authorization", AUTHORIZATION,
                "jwt", JWT,
                "cpf", CPF,
                "cnpj", CNPJ,
                "query.token", QUERY_TOKEN));
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException(
                String.join(" ", CPF, CNPJ, JWT, AUTHORIZATION, QUERY_TOKEN))));

        String json = new String(encoder.encode(event), StandardCharsets.UTF_8).trim();
        var payload = new ObjectMapper().readTree(json);

        assertThat(payload.get("request.id").asText()).isEqualTo("synthetic-request-01");
        assertThat(json).doesNotContain(CPF, CNPJ, JWT, AUTHORIZATION, QUERY_TOKEN);
        assertThat(payload.has("trace.id")).isFalse();
        assertThat(payload.has("span.id")).isFalse();

        encoder.stop();
        context.stop();
    }
}
