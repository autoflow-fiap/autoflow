package com.autoflow.application.output.cliente;

import com.autoflow.domain.cliente.ClienteStatus;
import lombok.Builder;

import java.util.List;

@Builder
public record ClienteOutput(
        Long id,
        String nome,
        String cpfCnpj,
        String telefone,
        String email,
        ClienteStatus status,
        List<ClienteVeiculoOutput> veiculos
) {
}
