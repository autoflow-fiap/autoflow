package com.autoflow.application.usecases.usuario;

import com.autoflow.application.gateway.AuthenticationGateway;
import com.autoflow.application.gateway.TokenGateway;
import com.autoflow.application.gateway.UsuarioGateway;
import com.autoflow.application.input.usuario.LoginInput;
import com.autoflow.application.output.usuario.LoginOutput;
import com.autoflow.application.port.in.usuario.LoginUsuarioUseCase;
import com.autoflow.domain.usuario.Usuario;
import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
public class LoginUsuarioUseCaseImpl implements LoginUsuarioUseCase {

    private final AuthenticationGateway authenticationGateway;
    private final UsuarioGateway usuarioGateway;
    private final TokenGateway tokenGateway;

    @Override
    public LoginOutput execute(LoginInput input) {

        authenticationGateway.authenticate(input.cpfCnpj(), input.senha());

        Usuario usuario = usuarioGateway
                .findByCpfCnpj(input.cpfCnpj())
                .orElseThrow();

        return new LoginOutput(tokenGateway.generateToken(
                usuario.getCpfCnpj(),
                usuario.getRole().name()
        ));
    }
}
