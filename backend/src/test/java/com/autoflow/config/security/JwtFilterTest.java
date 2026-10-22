package com.autoflow.config.security;

import com.autoflow.application.policy.ClienteAtivoPolicy;
import com.autoflow.infrastructure.security.JwtFilter;
import com.autoflow.infrastructure.security.service.CustomUserDetailsService;
import com.autoflow.infrastructure.security.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private ClienteAtivoPolicy clienteAtivoPolicy;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtFilter jwtFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private UserDetails userDetails;

    @BeforeEach
    void setup() {

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        SecurityContextHolder.clearContext();

        userDetails = new User(
                "usuario@email.com",
                "123456",
                List.of()
        );

        lenient().when(clienteAtivoPolicy.podeAutenticar(anyString(), anyBoolean())).thenReturn(true);
    }

    @Test
    void deveContinuarFiltroQuandoAuthorizationForNulo() {

        assertDoesNotThrow(this::executarFiltro);

        assertDoesNotThrow(this::verificarFiltroContinuou);

        verifyNoInteractions(
                jwtService,
                userDetailsService
        );
    }

    @Test
    void deveContinuarFiltroQuandoHeaderNaoForBearer() {

        request.addHeader(
                "Authorization",
                "Basic abc123"
        );

        assertDoesNotThrow(this::executarFiltro);

        assertDoesNotThrow(this::verificarFiltroContinuou);

        verifyNoInteractions(
                jwtService,
                userDetailsService
        );
    }

    @Test
    void deveAutenticarUsuarioQuandoTokenForValido() {

        String token = "jwt-token";

        request.addHeader(
                "Authorization",
                "Bearer " + token
        );

        when(jwtService.tokenValido(token))
                .thenReturn(true);

        when(jwtService.extrairCpfCnpj(token))
                .thenReturn("12345678980");

        when(userDetailsService.loadUserByCpfCnpj("12345678980"))
                .thenReturn(userDetails);

        assertDoesNotThrow(this::executarFiltro);

        assertNotNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        assertEquals(
                "usuario@email.com",
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getName()
        );

        verify(jwtService)
                .extrairCpfCnpj(token);

        verify(jwtService)
                .tokenValido(token);

        verify(userDetailsService)
                .loadUserByCpfCnpj("12345678980");

        assertDoesNotThrow(this::verificarFiltroContinuou);
    }

    @Test
        void naoDeveAutenticarQuandoCpfCnpjForNulo() {

        String token = "jwt-token";

        request.addHeader(
                "Authorization",
                "Bearer " + token
        );

        when(jwtService.tokenValido(token)).thenReturn(true);
        when(jwtService.extrairCpfCnpj(token)).thenReturn(null);

        assertDoesNotThrow(this::executarFiltro);

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        verify(jwtService)
                .extrairCpfCnpj(token);

        verifyNoInteractions(userDetailsService);

        assertDoesNotThrow(this::verificarFiltroContinuou);
    }

        @Test
        void naoDeveAutenticarQuandoSubjectNaoForCpfCnpjCadastrado() {
                String token = "jwt-token";
                String subjectEmailLegado = "usuario@email.com";
                request.addHeader("Authorization", "Bearer " + token);
                when(jwtService.tokenValido(token)).thenReturn(true);
                when(jwtService.extrairCpfCnpj(token)).thenReturn(subjectEmailLegado);
                when(userDetailsService.loadUserByCpfCnpj(subjectEmailLegado))
                                .thenThrow(new UsernameNotFoundException("Usuário não encontrado"));

                assertDoesNotThrow(this::executarFiltro);
                assertNull(SecurityContextHolder.getContext().getAuthentication());
                verify(userDetailsService).loadUserByCpfCnpj(subjectEmailLegado);
                assertDoesNotThrow(this::verificarFiltroContinuou);
        }

    @Test
    void naoDeveAutenticarQuandoTokenForInvalido() {

        String token = "jwt-token";

        request.addHeader(
                "Authorization",
                "Bearer " + token
        );

        when(jwtService.tokenValido(token))
                .thenReturn(false);

        assertDoesNotThrow(this::executarFiltro);

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        verify(jwtService, never()).extrairCpfCnpj(token);
        verifyNoInteractions(userDetailsService);

        assertDoesNotThrow(this::verificarFiltroContinuou);
    }

    @Test
    void naoDeveCarregarUsuarioQuandoJaExistirAutenticacao() {

        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "usuario",
                                null,
                                List.of()
                        )
                );

        String token = "jwt-token";

        request.addHeader(
                "Authorization",
                "Bearer " + token
        );

        assertDoesNotThrow(this::executarFiltro);

        verifyNoInteractions(jwtService);
        verifyNoInteractions(userDetailsService);

        assertDoesNotThrow(this::verificarFiltroContinuou);
    }

    @Test
    void deveUsarAuthoritiesAtuaisDoUsuarioEmVezDaRoleDoToken() {

        String token = "jwt-token";

        request.addHeader(
                "Authorization",
                "Bearer " + token
        );

        UserDetails usuarioComRoleAtualizada = new User(
                "cliente@email.com",
                "123456",
                List.of(() -> "ROLE_CLIENTE")
        );

        when(jwtService.extrairCpfCnpj(token)).thenReturn("12345678980");
        when(userDetailsService.loadUserByCpfCnpj("12345678980"))
                .thenReturn(usuarioComRoleAtualizada);
        when(jwtService.tokenValido(token)).thenReturn(true);

        assertDoesNotThrow(this::executarFiltro);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("cliente@email.com", auth.getName());
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CLIENTE")));
        assertFalse(auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        verify(jwtService, never()).extrairRole(token);
    }

    @Test
    void naoDeveAutenticarQuandoClienteFoiInativado() throws Exception {
        String token = "jwt-token";
        request.addHeader("Authorization", "Bearer " + token);

        UserDetails cliente = User.withUsername("cliente@email.com")
                .password("123456")
                .roles("CLIENTE")
                .build();
        when(jwtService.tokenValido(token)).thenReturn(true);
        when(jwtService.extrairCpfCnpj(token)).thenReturn("12345678980");
        when(userDetailsService.loadUserByCpfCnpj("12345678980")).thenReturn(cliente);
        when(clienteAtivoPolicy.podeAutenticar("12345678980", true)).thenReturn(false);

        executarFiltro();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(403, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @ParameterizedTest(name = "Deve ignorar o filtro JWT para a rota: {0}")
    @ValueSource(strings = {
            "/public/ordens-servico/acompanhamento",
            "/public/outra-rota",
            "/auth/login",
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/swagger-ui/index.html",
            "/v3/api-docs/swagger-config",
            "/swagger-ui.html"
    })
    void shouldNotFilterDeveRetornarTrueParaRotasPublicas(String rota) {
        request.setRequestURI(rota);

        assertTrue(jwtFilter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilterDeveRetornarFalseParaOutrasRotas()
            throws ServletException {

        request.setRequestURI("/api/clientes");
        assertFalse(jwtFilter.shouldNotFilter(request));
    }

    private void executarFiltro() throws ServletException, IOException {
        jwtFilter.doFilterInternal(request, response, filterChain);
    }

    private void verificarFiltroContinuou() throws ServletException, IOException {
        verify(filterChain).doFilter(request, response);
    }
}
