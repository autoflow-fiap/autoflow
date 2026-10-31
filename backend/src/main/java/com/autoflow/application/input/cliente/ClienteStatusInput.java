package com.autoflow.application.input.cliente;

import com.autoflow.domain.cliente.ClienteStatus;

public record ClienteStatusInput(ClienteStatus status) {

    public ClienteStatusInput {
        if (status == null) {
            throw new IllegalArgumentException("O status do cliente é obrigatório.");
        }
    }
}
