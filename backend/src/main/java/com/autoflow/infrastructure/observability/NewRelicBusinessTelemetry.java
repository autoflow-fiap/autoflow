package com.autoflow.infrastructure.observability;

import com.autoflow.application.output.ordemservico.OrdemServicoCriadaOutput;
import com.autoflow.application.output.ordemservico.FinalizarDiagnosticoOutput;
import com.autoflow.domain.ordemservico.Diagnostico;
import com.autoflow.domain.ordemservico.OrdemServico;
import com.newrelic.api.agent.NewRelic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/** Best-effort New Relic custom events. No identifiers or user-provided values are emitted. */
@Component
public class NewRelicBusinessTelemetry {
    private static final Logger log = LoggerFactory.getLogger(NewRelicBusinessTelemetry.class);
    private static final Set<String> ORDER_OPERATIONS = Set.of(
            "CriarOrdemServicoUseCaseImpl", "FinalizarDiagnosticoUseCaseImpl",
            "FinalizarServicoUseCaseImpl", "EntregarOrdemServicoUseCaseImpl",
            "IniciarDiagnosticoUseCaseImpl", "IniciarServicoUseCaseImpl");
    private final String environment;

    public NewRelicBusinessTelemetry(
            @Value("${app.observability.environment:local}") String environment) {
        this.environment = environment;
    }

    public void recordSuccessfulUseCase(String operation, Object result) {
        if (result instanceof OrdemServicoCriadaOutput created) {
            OrdemServico os = created.ordemServico();
            emit("AutoFlowOrderCreated", Map.of(
                    "environment", environment,
                    "service", "autoflow-backend",
                    "createdAt", os.getDataAbertura().toString()));
            return;
        }
        if (result instanceof FinalizarDiagnosticoOutput completed) {
            OrdemServico os = completed.ordemServico();
            if (os != null && os.getDiagnostico() != null) {
                Diagnostico diagnostico = os.getDiagnostico();
                recordStage("DIAGNOSTICO", diagnostico.getIniciadoEm(), diagnostico.getConcluidoEm());
            }
            return;
        }
        if (!(result instanceof OrdemServico os)) return;

        if ("FinalizarDiagnosticoUseCaseImpl".equals(operation)) {
            Diagnostico diagnostico = os.getDiagnostico();
            if (diagnostico != null) {
                recordStage("DIAGNOSTICO", diagnostico.getIniciadoEm(), diagnostico.getConcluidoEm());
            }
        } else if ("FinalizarServicoUseCaseImpl".equals(operation)
                && os.getFinalizadaEm() != null) {
            recordStage("EXECUCAO", os.getExecucaoIniciadaEm(), os.getFinalizadaEm());
        } else if ("EntregarOrdemServicoUseCaseImpl".equals(operation)) {
            recordStage("FINALIZACAO", os.getFinalizadaEm(), os.getEntregueEm());
        }
    }

    public void recordIntegrationFailure(String integration, String operation, Throwable failure) {
        emit("AutoFlowIntegrationFailure", Map.of(
                "environment", environment,
                "integration", integration,
                "operation", operation,
                "failureType", safeFailureType(failure),
                "failureCode", "INTEGRATION_FAILURE"));
    }

    public void recordOrderProcessingFailure(String operation, Throwable failure) {
        if (!ORDER_OPERATIONS.contains(operation)) return;
        emit("AutoFlowOrderProcessingFailure", Map.of(
                "environment", environment,
                "operation", operationName(operation),
                "failureType", safeFailureType(failure),
                "failureCode", "UNEXPECTED_PROCESSING_ERROR"));
    }

    private void recordStage(String stage, LocalDateTime started, LocalDateTime completed) {
        if (started == null || completed == null || completed.isBefore(started)) return;
        long durationSeconds = Duration.between(started, completed).getSeconds();
        emit("AutoFlowOrderStageCompleted", Map.of(
                "environment", environment,
                "stage", stage,
                "durationSeconds", durationSeconds,
                "completedAt", completed.toString()));
    }

    private void emit(String eventName, Map<String, Object> attributes) {
        try {
            NewRelic.getAgent().getInsights().recordCustomEvent(eventName, attributes);
        } catch (RuntimeException | LinkageError exception) {
            // Telemetry must never change the functional outcome; never log vendor error details.
            log.warn("Falha ao registrar telemetria de negócio New Relic");
        }
    }

    private static String operationName(String operation) {
        return switch (operation) {
            case "CriarOrdemServicoUseCaseImpl" -> "CREATE_ORDER";
            case "FinalizarDiagnosticoUseCaseImpl" -> "COMPLETE_DIAGNOSIS";
            case "FinalizarServicoUseCaseImpl" -> "COMPLETE_EXECUTION";
            case "EntregarOrdemServicoUseCaseImpl" -> "DELIVER_ORDER";
            case "IniciarDiagnosticoUseCaseImpl" -> "START_DIAGNOSIS";
            case "IniciarServicoUseCaseImpl" -> "START_EXECUTION";
            default -> "ORDER_PROCESSING";
        };
    }

    private static String safeFailureType(Throwable failure) {
        return failure == null ? "UnknownFailure" : failure.getClass().getSimpleName();
    }
}
