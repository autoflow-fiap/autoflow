package com.autoflow.domain.cliente;

public record Cliente(
        Long id,
        String nome,
        String cpfCnpj,
        String telefone,
        String email,
        ClienteStatus status
) {
    public Cliente(Long id, String nome, String cpfCnpj, String telefone, String email) {
        this(id, nome, cpfCnpj, telefone, email, ClienteStatus.ATIVO);
    }

    public Cliente {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome do cliente é obrigatório.");
        }
        if (cpfCnpj == null || cpfCnpj.isBlank()) {
            throw new IllegalArgumentException("O CPF/CNPJ do cliente é obrigatório.");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("O email do cliente é obrigatório.");
        }
        if (status == null) {
            throw new IllegalArgumentException("O status do cliente é obrigatório.");
        }
    }

    public static Cliente criar(String nome, String cpfCnpj, String telefone, String email) {
        return new Cliente(null, nome, cpfCnpj, telefone, email, ClienteStatus.ATIVO);
    }

    public static Cliente reconstituir(
            Long id,
            String nome,
            String cpfCnpj,
            String telefone,
            String email) {
        return new Cliente(id, nome, cpfCnpj, telefone, email, ClienteStatus.ATIVO);
    }

    public static Cliente reconstituir(
            Long id,
            String nome,
            String cpfCnpj,
            String telefone,
            String email,
            ClienteStatus status) {
        return new Cliente(id, nome, cpfCnpj, telefone, email, status);
    }

    public Cliente atualizar(String nome, String cpfCnpj, String telefone, String email) {
        return new Cliente(id, nome, cpfCnpj, telefone, email, status);
    }

    public Cliente alterarStatus(ClienteStatus novoStatus) {
        return new Cliente(id, nome, cpfCnpj, telefone, email, novoStatus);
    }
}
