package com.autoflow.presentation.ordemservico.reparoadicional;

import com.autoflow.application.input.ordemservico.reparoadicional.CriarReparoAdicionalCommand;
import com.autoflow.application.output.ordemservico.reparoadicional.CriarReparoAdicionalOutput;
import com.autoflow.application.port.in.ordemservico.reparoadicional.CriarReparoAdicionalUseCase;
import com.autoflow.application.policy.ClienteAtivoPolicy;
import com.autoflow.infrastructure.security.service.CustomUserDetailsService;
import com.autoflow.infrastructure.security.service.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReparoAdicionalController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        ReparoAdicionalRestMapperImpl.class,
        ReparoAdicionalControllerTest.MethodSecurityTestConfig.class,
        ReparoAdicionalControllerTest.SecurityExceptionHandler.class
})
class ReparoAdicionalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CriarReparoAdicionalUseCase criarReparoAdicionalUseCase;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private ClienteAtivoPolicy clienteAtivoPolicy;

    @Test
    @WithMockUser(username = "mecanico@autoflow.com", roles = "MECANICO")
    void deveCriarReparoAdicionalComoMecanico() throws Exception {
        when(criarReparoAdicionalUseCase.execute(any())).thenReturn(new CriarReparoAdicionalOutput(
                5L,
                20L,
                "http://localhost:8080/public/orcamentos/20?token=abc"
        ));

        executarPostValido()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reparoAdicionalId").value(5L))
                .andExpect(jsonPath("$.orcamentoId").value(20L))
                .andExpect(jsonPath("$.publicUrl").value("http://localhost:8080/public/orcamentos/20?token=abc"));

        ArgumentCaptor<CriarReparoAdicionalCommand> captor = ArgumentCaptor.forClass(CriarReparoAdicionalCommand.class);
        verify(criarReparoAdicionalUseCase).execute(captor.capture());
        CriarReparoAdicionalCommand command = captor.getValue();
        assertEquals("OS-123", command.numeroOs());
        assertEquals("mecanico@autoflow.com", command.emailMecanico());
        assertEquals(10L, command.servicos().getFirst().servicoId());
        assertEquals(7L, command.servicos().getFirst().itensNecessarios().getFirst().pecaInsumoId());
        assertEquals(2, command.servicos().getFirst().itensNecessarios().getFirst().quantidade());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deveCriarReparoAdicionalComoAdmin() throws Exception {
        when(criarReparoAdicionalUseCase.execute(any()))
                .thenReturn(new CriarReparoAdicionalOutput(5L, 20L, "url"));

        executarPostValido()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reparoAdicionalId").value(5L));

        verify(criarReparoAdicionalUseCase).execute(any());
    }

    private static Stream<String> requisicoesInvalidas() {
        return Stream.of(
                """
                        { "servicos": [] }
                        """,
                """
                        {
                          "servicos": [{
                            "itensNecessarios": [{ "pecaInsumoId": 7, "quantidade": 2 }]
                          }]
                        }
                        """,
                """
                        {
                          "servicos": [{ "servicoId": 10, "itensNecessarios": [] }]
                        }
                        """,
                """
                        {
                          "servicos": [{
                            "servicoId": 10,
                            "itensNecessarios": [{ "pecaInsumoId": 7, "quantidade": 0 }]
                          }]
                        }
                        """
        );
    }

    @ParameterizedTest
    @MethodSource("requisicoesInvalidas")
    @WithMockUser(roles = "MECANICO")
    void deveRetornarBadRequestParaRequisicaoInvalida(String json) throws Exception {
        executarPost(json).andExpect(status().isBadRequest());

        verifyNoInteractions(criarReparoAdicionalUseCase);
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    void deveRetornarForbiddenQuandoClienteTentarCriarReparoAdicional() throws Exception {
        executarPostValido().andExpect(status().isForbidden());

        verify(criarReparoAdicionalUseCase, never()).execute(any());
    }

    private org.springframework.test.web.servlet.ResultActions executarPostValido() throws Exception {
        return executarPost("""
                {
                  "servicos": [{
                    "servicoId": 10,
                    "itensNecessarios": [{ "pecaInsumoId": 7, "quantidade": 2 }]
                  }]
                }
                """);
    }

    private org.springframework.test.web.servlet.ResultActions executarPost(String json) throws Exception {
        return mockMvc.perform(post("/ordens-servico/{numeroOs}/reparos-adicionais", "OS-123")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @TestConfiguration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class MethodSecurityTestConfig implements WebMvcConfigurer {

        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new AuthenticationPrincipalArgumentResolver());
        }
    }

    @RestControllerAdvice
    static class SecurityExceptionHandler {

        @ExceptionHandler(AuthorizationDeniedException.class)
        ResponseEntity<Void> handleAuthorizationDenied() {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }
}
