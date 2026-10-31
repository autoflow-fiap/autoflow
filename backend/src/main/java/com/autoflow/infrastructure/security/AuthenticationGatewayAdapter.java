package com.autoflow.infrastructure.security;

import com.autoflow.application.gateway.AuthenticationGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthenticationGatewayAdapter implements AuthenticationGateway {

    private final AuthenticationManager authenticationManager;

    @Override
    public void authenticate(String cpfCnpj, String senha) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(cpfCnpj, senha)
        );
    }
}
