package com.autoflow.presentation.acompanhamento;


import com.autoflow.application.output.ordemservico.acompanhamento.AcompanhamentoOrdemServicoOutput;
import com.autoflow.application.port.in.ordemservico.acompanhamento.AcompanharOrdemServicoUseCase;
import com.autoflow.application.policy.ClienteAtivoPolicy;
import com.autoflow.domain.ordemservico.StatusOrdemServico;
import com.autoflow.infrastructure.security.service.CustomUserDetailsService;
import com.autoflow.infrastructure.security.service.JwtService;
import com.autoflow.presentation.ordemservico.acompanhamento.AcompanhamentoControllerMapperImpl;
import com.autoflow.presentation.ordemservico.acompanhamento.ClienteOrdemServicoController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
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

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ClienteOrdemServicoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        ClienteOrdemServicoControllerTest.MethodSecurityTestConfig.class,
        ClienteOrdemServicoControllerTest.SecurityExceptionHandler.class,
        AcompanhamentoControllerMapperImpl.class
})
class ClienteOrdemServicoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AcompanharOrdemServicoUseCase acompanharOrdemServicoUseCase;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private ClienteAtivoPolicy clienteAtivoPolicy;

    @Test
    @WithMockUser(username = "cliente@autoflow.com", roles = "CLIENTE")
    void deveListarOrdensDoClienteAutenticado() throws Exception {

        AcompanhamentoOrdemServicoOutput output =
                new AcompanhamentoOrdemServicoOutput(
                        "OS-123",
                        "ABC1D23",
                        StatusOrdemServico.RECEBIDA,
                        LocalDateTime.of(2026, Month.JULY, 6, 10, 0),
                        LocalDateTime.of(2026, Month.JULY, 6, 10, 5),
                        List.of(),
                        null,
                        null,
                        "Recebemos sua ordem de servico.",
                        List.of()
                );

        when(acompanharOrdemServicoUseCase.execute("cliente@autoflow.com"))
                .thenReturn(List.of(output));

        mockMvc.perform(get("/clientes/me/ordens-servico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].numeroOs").value("OS-123"))
                .andExpect(jsonPath("$[0].placa").value("ABC1D23"))
                .andExpect(jsonPath("$[0].statusAtual").value("RECEBIDA"))
                .andExpect(jsonPath("$[0].mensagemParaCliente")
                        .value("Recebemos sua ordem de servico."));

        verify(acompanharOrdemServicoUseCase)
                .execute("cliente@autoflow.com");
    }

    @Test
    @WithMockUser(username = "atendente@autoflow.com", roles = "ATENDENTE")
    void deveRetornarForbiddenParaUsuarioNaoCliente() throws Exception {

        mockMvc.perform(get("/clientes/me/ordens-servico"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(acompanharOrdemServicoUseCase);
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
