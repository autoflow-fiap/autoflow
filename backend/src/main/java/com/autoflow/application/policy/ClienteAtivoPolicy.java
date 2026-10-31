package com.autoflow.application.policy;

import com.autoflow.application.gateway.ClienteGateway;
import com.autoflow.domain.cliente.ClienteStatus;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ClienteAtivoPolicy {

    private final ClienteGateway clienteGateway;

    public boolean podeAutenticar(String cpfCnpj, boolean usuarioCliente) {
        if (!usuarioCliente) {
            return true;
        }

        return clienteGateway.findByUsuarioCpfCnpj(cpfCnpj)
                .map(cliente -> !ClienteStatus.INATIVO.equals(cliente.status()))
                .orElse(true);
    }
}
