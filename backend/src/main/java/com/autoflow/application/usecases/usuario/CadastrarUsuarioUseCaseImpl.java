package com.autoflow.application.usecases.usuario;

import com.autoflow.application.exception.ApplicationException;
import com.autoflow.application.gateway.PasswordGateway;
import com.autoflow.application.gateway.UsuarioGateway;
import com.autoflow.application.input.usuario.RegistroInput;
import com.autoflow.application.output.usuario.UsuarioOutput;
import com.autoflow.application.port.in.usuario.CadastrarClienteUseCase;
import com.autoflow.application.port.in.usuario.CadastrarUsuarioUseCase;
import com.autoflow.application.transaction.TransactionalUseCase;
import com.autoflow.domain.usuario.RoleEnum;
import com.autoflow.domain.usuario.Usuario;
import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
public class CadastrarUsuarioUseCaseImpl implements CadastrarUsuarioUseCase {

    private final UsuarioGateway usuarioGateway;
    private final PasswordGateway passwordGateway;
    private final CadastrarClienteUseCase cadastrarClienteUseCase;

    @TransactionalUseCase
    @Override
    public UsuarioOutput execute(RegistroInput request) {

        if (usuarioGateway.existsByEmail(request.email())) {
            throw ApplicationException.conflict("Email já cadastrado");
        }

        Usuario usuario = new Usuario();
        usuario.setNome(request.nome());
        usuario.setEmail(request.email());
        usuario.setSenha(passwordGateway.encode(request.senha()));
        usuario.setCpfCnpj(request.cpfCnpj());
        usuario.setRole(request.role());
        usuario = usuarioGateway.save(usuario);

        if (RoleEnum.CLIENTE.equals(request.role())) {
            cadastrarClienteUseCase.execute(request, usuario);
        }

        return new UsuarioOutput(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getCpfCnpj(),
                usuario.getRole()
        );
    }
}
