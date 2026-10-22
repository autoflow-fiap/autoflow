package com.autoflow.infrastructure.observability;

import com.autoflow.application.output.ordemservico.OrdemServicoCriadaOutput;
import com.autoflow.application.output.ordemservico.FinalizarDiagnosticoOutput;
import com.autoflow.domain.ordemservico.Diagnostico;
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
    private final NewRelicBusinessTelemetry telemetry = new NewRelicBusinessTelemetry("test", "test-service");

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

            ArgumentCaptor<Map> attributes = ArgumentCaptor.forClass(Map.class);
            verify(insights).recordCustomEvent(eq("AutoFlowOrderCreated"), attributes.capture());
            assertThat(attributes.getValue())
                    .containsEntry("environment", "test")
                    .containsEntry("service", "test-service");
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
                // completedAt continua no formato local persistido (UTC sem indicador de fuso).
                .containsEntry("completedAt", "2026-01-01T10:01:30")
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

    @Test
    void ignoresUnsupportedResultsAndIncompleteOrReversedDiagnosticTimestamps() {
        OrdemServico withoutDiagnosis = new OrdemServico();
        OrdemServico withIncompleteDiagnosis = new OrdemServico();
        withIncompleteDiagnosis.setDiagnostico(new Diagnostico());
        OrdemServico withReversedDiagnosis = new OrdemServico();
        Diagnostico reversed = new Diagnostico();
        reversed.setIniciadoEm(LocalDateTime.parse("2026-01-01T10:00:00"));
        reversed.setConcluidoEm(LocalDateTime.parse("2026-01-01T09:00:00"));
        withReversedDiagnosis.setDiagnostico(reversed);

        try (MockedStatic<NewRelic> newRelic = mockStatic(NewRelic.class)) {
            telemetry.recordSuccessfulUseCase("unknown", new Object());
            telemetry.recordSuccessfulUseCase("FinalizarDiagnosticoUseCaseImpl", withoutDiagnosis);
            telemetry.recordSuccessfulUseCase("FinalizarDiagnosticoUseCaseImpl", withIncompleteDiagnosis);
            telemetry.recordSuccessfulUseCase("FinalizarDiagnosticoUseCaseImpl", withReversedDiagnosis);
            telemetry.recordSuccessfulUseCase("unknown", new FinalizarDiagnosticoOutput(null, null, null));
            newRelic.verifyNoInteractions();
        }
    }

    @Test
    void recordsSuccessfulDiagnosticDurationAndFailureEventsWithSafeAttributes() {
        LocalDateTime started = LocalDateTime.parse("2026-01-01T10:00:00");
        Diagnostico diagnostico = new Diagnostico();
        diagnostico.setIniciadoEm(started);
        diagnostico.setConcluidoEm(started.plusSeconds(45));
        OrdemServico order = new OrdemServico();
        order.setDiagnostico(diagnostico);
        Agent agent = mock(Agent.class);
        Insights insights = mock(Insights.class);
        when(agent.getInsights()).thenReturn(insights);

        try (MockedStatic<NewRelic> newRelic = mockStatic(NewRelic.class)) {
            newRelic.when(NewRelic::getAgent).thenReturn(agent);
            telemetry.recordSuccessfulUseCase("FinalizarDiagnosticoUseCaseImpl", order);
            telemetry.recordIntegrationFailure("email", "send", new IllegalStateException("private details"));
            telemetry.recordIntegrationFailure("email", "send", null);
            telemetry.recordOrderProcessingFailure("not-an-order-operation", new RuntimeException());
            telemetry.recordOrderProcessingFailure("IniciarServicoUseCaseImpl", null);
        }

        ArgumentCaptor<Map> attributes = ArgumentCaptor.forClass(Map.class);
        verify(insights).recordCustomEvent(eq("AutoFlowOrderStageCompleted"), attributes.capture());
        assertThat(attributes.getValue()).containsEntry("stage", "DIAGNOSTICO")
                .containsEntry("durationSeconds", 45L);
        verify(insights, org.mockito.Mockito.times(2))
                .recordCustomEvent(eq("AutoFlowIntegrationFailure"), attributes.capture());
        assertThat(attributes.getAllValues().get(1)).containsEntry("failureType", "IllegalStateException")
                .doesNotContainValue("private details");
        assertThat(attributes.getAllValues().get(2)).containsEntry("failureType", "UnknownFailure");
        verify(insights).recordCustomEvent(eq("AutoFlowOrderProcessingFailure"), attributes.capture());
        assertThat(attributes.getAllValues().get(3)).containsEntry("operation", "START_EXECUTION")
                .containsEntry("failureType", "UnknownFailure");
    }

    @Test
    void mapsEverySupportedOrderFailureToItsStableOperationName() {
        Agent agent = mock(Agent.class);
        Insights insights = mock(Insights.class);
        when(agent.getInsights()).thenReturn(insights);
        Map<String, String> operationNames = Map.of(
                "CriarOrdemServicoUseCaseImpl", "CREATE_ORDER",
                "FinalizarDiagnosticoUseCaseImpl", "COMPLETE_DIAGNOSIS",
                "FinalizarServicoUseCaseImpl", "COMPLETE_EXECUTION",
                "EntregarOrdemServicoUseCaseImpl", "DELIVER_ORDER",
                "IniciarDiagnosticoUseCaseImpl", "START_DIAGNOSIS",
                "IniciarServicoUseCaseImpl", "START_EXECUTION");

        try (MockedStatic<NewRelic> newRelic = mockStatic(NewRelic.class)) {
            newRelic.when(NewRelic::getAgent).thenReturn(agent);
            operationNames.keySet().forEach(operation ->
                    telemetry.recordOrderProcessingFailure(operation, new IllegalStateException()));
        }

        ArgumentCaptor<Map> attributes = ArgumentCaptor.forClass(Map.class);
        verify(insights, org.mockito.Mockito.times(operationNames.size()))
                .recordCustomEvent(eq("AutoFlowOrderProcessingFailure"), attributes.capture());
        assertThat(attributes.getAllValues()).extracting(event -> event.get("operation"))
                .containsExactlyInAnyOrderElementsOf(operationNames.values());
    }

    private OrdemServico newOrder() {
        LocalDateTime now = LocalDateTime.parse("2026-01-01T10:00:00");
        return OrdemServico.reconstituir(7L, "synthetic", null, null, null, now, null,
                java.util.List.of(), null, null, null, now, null, null, null, null);
    }
}
