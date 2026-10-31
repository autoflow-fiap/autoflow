package com.autoflow.application.output.usuario;

import com.autoflow.domain.usuario.RoleEnum;
import lombok.Builder;

@Builder
public record UsuarioOutput(Long id,
                            String nome,
                            String email,
                            String cpfCnpj,
                            RoleEnum role) {
}
