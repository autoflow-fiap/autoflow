package com.autoflow.infrastructure.security;

import com.autoflow.application.gateway.TokenGateway;
import com.autoflow.infrastructure.security.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TokenGatewayAdapter implements TokenGateway {

    private final JwtService jwtService;

    @Override
    public String generateToken(String subject, String role) {
        return jwtService.gerarToken(subject, role);
    }
}
