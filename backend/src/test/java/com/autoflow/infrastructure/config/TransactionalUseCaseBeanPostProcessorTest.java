package com.autoflow.infrastructure.config;

import com.autoflow.application.transaction.TransactionalUseCase;
import com.autoflow.infrastructure.observability.NewRelicBusinessTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TransactionalUseCaseBeanPostProcessorTest {
    @Test
    void runsBusinessTelemetryOnlyAfterSuccessfulCommit() {
        NewRelicBusinessTelemetry telemetry = mock(NewRelicBusinessTelemetry.class);
        TransactionalUseCaseBeanPostProcessor processor = new TransactionalUseCaseBeanPostProcessor(
                new TestTransactionManager(), telemetry);
        TransactionalFixture fixture = new TransactionalFixture();
        TransactionalFixture proxy = (TransactionalFixture) processor.postProcessAfterInitialization(fixture, "fixture");

        Object result = proxy.succeed();

        verify(telemetry).recordSuccessfulUseCase("TransactionalFixture", result);
    }

    @Test
    void doesNotPublishBusinessTelemetryWhenTransactionRollsBack() {
        NewRelicBusinessTelemetry telemetry = mock(NewRelicBusinessTelemetry.class);
        TransactionalUseCaseBeanPostProcessor processor = new TransactionalUseCaseBeanPostProcessor(
                new TestTransactionManager(), telemetry);
        TransactionalFixture fixture = (TransactionalFixture) processor.postProcessAfterInitialization(
                new TransactionalFixture(), "fixture");

        try {
            fixture.fail();
        } catch (IllegalStateException ignored) {
            // The original operation failure must be preserved.
        }

        verify(telemetry, never()).recordSuccessfulUseCase("TransactionalFixture", null);
    }

    public static class TransactionalFixture {
        @TransactionalUseCase
        public Object succeed() {
            return new Object();
        }

        @TransactionalUseCase
        public Object fail() {
            throw new IllegalStateException("synthetic failure");
        }
    }

    private static class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
