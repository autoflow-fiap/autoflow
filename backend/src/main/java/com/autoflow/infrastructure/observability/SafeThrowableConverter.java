package com.autoflow.infrastructure.observability;

import ch.qos.logback.classic.pattern.ThrowableHandlingConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** Emits only the exception class; library messages and stack frames may contain secrets or PII. */
public class SafeThrowableConverter extends ThrowableHandlingConverter {
    @Override
    public String convert(ILoggingEvent event) {
        return event.getThrowableProxy() == null ? null : event.getThrowableProxy().getClassName();
    }
}
