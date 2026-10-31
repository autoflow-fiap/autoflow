package com.autoflow.infrastructure.security;

import com.autoflow.application.output.security.CurrentUser;
import com.autoflow.domain.usuario.RoleEnum;
import com.autoflow.infrastructure.security.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityGatewayAdaptersTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deveDelegarAutenticacaoAoAuthenticationManager() {
        AuthenticationGatewayAdapter adapter = new AuthenticationGatewayAdapter(authenticationManager);

        adapter.authenticate("52998224725", "senha");

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void deveDelegarGeracaoDeTokenAoJwtServiceComSubjectCpfCnpj() {
        when(jwtService.gerarToken("12345678980", "CLIENTE")).thenReturn("token");
        TokenGatewayAdapter adapter = new TokenGatewayAdapter(jwtService);

        assertEquals("token", adapter.generateToken("12345678980", "CLIENTE"));

        verify(jwtService).gerarToken("12345678980", "CLIENTE");
    }

    @Test
    void deveDelegarHashDaSenhaAoPasswordEncoder() {
        when(passwordEncoder.encode("senha")).thenReturn("hash");

        assertEquals("hash", new PasswordGatewayAdapter(passwordEncoder).encode("senha"));

        verify(passwordEncoder).encode("senha");
    }

    @Test
    void deveResolverUsuarioAtualAPartirDaRoleDoContexto() {
        var authentication = new UsernamePasswordAuthenticationToken(
            User.withUsername("cliente@email.com")
                        .password("ignored")
                        .roles("CLIENTE")
                        .build(),
                null,
                List.of(() -> "ROLE_CLIENTE")
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CurrentUser currentUser = new CurrentUserGatewayAdapter()
                .getCurrentUser()
                .orElseThrow();

        assertEquals("cliente@email.com", currentUser.email());
        assertTrue(currentUser.hasRole(RoleEnum.CLIENTE));
    }

    @Test
    void deveRetornarAusenteQuandoNaoHaUsuarioComRoleConhecida() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "anonymous",
                        null,
                        List.of(() -> "ROLE_ANONYMOUS")
                )
        );

        assertTrue(new CurrentUserGatewayAdapter().getCurrentUser().isEmpty());
    }
}
