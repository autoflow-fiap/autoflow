package com.autoflow.presentation.usuario.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "o CPF/CNPJ é obrigatório") String cpfCnpj,
        @NotBlank(message = "a senha é obrigatória") String senha
) {
}
