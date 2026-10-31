package com.autoflow.application.input.cliente;

import com.autoflow.domain.cliente.ClienteStatus;

public record ClienteInput(
        String nome,
        String cpfCnpj,
        String telefone,
        String email,
        Long usuarioId,
        ClienteStatus status
) {

    public ClienteInput(String nome, String cpfCnpj, String telefone, String email) {
        this(nome, cpfCnpj, telefone, email, null, ClienteStatus.ATIVO);
    }

    public ClienteInput(String nome, String cpfCnpj, String telefone, String email, Long usuarioId) {
        this(nome, cpfCnpj, telefone, email, usuarioId, ClienteStatus.ATIVO);
    }
}
