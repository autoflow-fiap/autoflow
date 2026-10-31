package com.autoflow.presentation.cliente.request;

import com.autoflow.domain.cliente.ClienteStatus;
import jakarta.validation.constraints.NotNull;

public record ClienteStatusRequest(
        @NotNull(message = "O status é obrigatório") ClienteStatus status
) {
}
