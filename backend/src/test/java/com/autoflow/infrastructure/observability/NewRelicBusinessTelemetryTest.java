package com.autoflow.infrastructure.observability;

import com.autoflow.application.output.ordemservico.OrdemServicoCriadaOutput;
import com.autoflow.domain.ordemservico.OrdemServico;
import com.autoflow.domain.ordemservico.StatusOrdemServico;
import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Agent;
import com.newrelic.api.agent.Insights;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;
import java.util.Map;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

class NewRelicBusinessTelemetryTest {
    private final NewRelicBusinessTelemetry telemetry = new NewRelicBusinessTelemetry("test");

    @Test
    void emitsCreationAfterSuccessfulUseCaseWithoutOrderIdentifiers() {
        LocalDateTime createdAt = LocalDateTime.parse("2026-01-01T10:00:00");
        OrdemServico order = OrdemServico.reconstituir(23L, "OS-23", null, null, null,
                createdAt, null, java.util.List.of(), null, null, null, createdAt,
                null, null, null, null);

        try (MockedStatic<NewRelic> newRelic = mockStatic(NewRelic.class)) {
            Agent agent = mock(Agent.class);
            Insights insights = mock(Insights.class);
            newRelic.when(NewRelic::getAgent).thenReturn(agent);
            when(agent.getInsights()).thenReturn(insights);
            telemetry.recordSuccessfulUseCase("CriarOrdemServicoUseCaseImpl",
                    new OrdemServicoCriadaOutput(order, "synthetic-token"));

            verify(insights).recordCustomEvent(eq("AutoFlowOrderCreated"), anyMap());
        }
    }

    @Test
    void ignoresInvalidOrNegativeStageIntervals() {
        OrdemServico order = OrdemServico.reconstituir(1L, "synthetic", null, null, null,
                LocalDateTime.parse("2026-01-01T10:00:00"), null, java.util.List.of(), null,
                LocalDateTime.parse("2026-01-01T09:00:00"), null, null, null, null, null, null);

        try (MockedStatic<NewRelic> newRelic = mockStatic(NewRelic.class)) {
            telemetry.recordSuccessfulUseCase("FinalizarServicoUseCaseImpl", order);
            newRelic.verifyNoInteractions();
        }
    }

    @Test
    void finalizationDurationUsesDeliveredMinusFinalizedAndContainsNoOrderId() {
        LocalDateTime finalizedAt = LocalDateTime.parse("2026-01-01T10:00:00");
        OrdemServico order = new OrdemServico();
        order.setId(991L);
        order.setNumeroOs("OS-991");
        order.setStatus(StatusOrdemServico.ENTREGUE);
        order.setFinalizadaEm(finalizedAt);
        order.setEntregueEm(finalizedAt.plusSeconds(90));
        Agent agent = mock(Agent.class);
        Insights insights = mock(Insights.class);
        ArgumentCaptor<Map> attributes = ArgumentCaptor.forClass(Map.class);

        try (MockedStatic<NewRelic> newRelic = mockStatic(NewRelic.class)) {
            newRelic.when(NewRelic::getAgent).thenReturn(agent);
            when(agent.getInsights()).thenReturn(insights);

            telemetry.recordSuccessfulUseCase("EntregarOrdemServicoUseCaseImpl", order);

            verify(insights).recordCustomEvent(eq("AutoFlowOrderStageCompleted"), attributes.capture());
        }
        assertThat(attributes.getValue()).containsEntry("stage", "FINALIZACAO")
                .containsEntry("durationSeconds", 90L)
                .doesNotContainKeys("id", "numeroOs", "orderId");
    }

    @Test
    void telemetryFailureDoesNotEscapeToBusinessCaller() {
        Agent agent = mock(Agent.class);
        Insights insights = mock(Insights.class);
        when(agent.getInsights()).thenReturn(insights);
        doThrow(new IllegalStateException("synthetic vendor failure"))
                .when(insights).recordCustomEvent(eq("AutoFlowOrderCreated"), anyMap());

        try (MockedStatic<NewRelic> newRelic = mockStatic(NewRelic.class)) {
            newRelic.when(NewRelic::getAgent).thenReturn(agent);
            telemetry.recordSuccessfulUseCase("CriarOrdemServicoUseCaseImpl",
                    new OrdemServicoCriadaOutput(newOrder(), "not-logged-token"));
        }
    }

    private OrdemServico newOrder() {
        LocalDateTime now = LocalDateTime.parse("2026-01-01T10:00:00");
        return OrdemServico.reconstituir(7L, "synthetic", null, null, null, now, null,
                java.util.List.of(), null, null, null, now, null, null, null, null);
    }
}
