package com.autoflow.presentation.pecainsumo;

import com.autoflow.application.input.pecainsumo.PecaInsumoInput;
import com.autoflow.application.output.PageResult;
import com.autoflow.application.output.pecainsumo.PecaInsumoOutput;
import com.autoflow.application.port.in.pecainsumo.*;
import com.autoflow.application.policy.ClienteAtivoPolicy;
import com.autoflow.domain.pecainsumo.CategoriaPecaInsumo;
import com.autoflow.infrastructure.security.service.CustomUserDetailsService;
import com.autoflow.infrastructure.security.service.JwtService;
import com.autoflow.presentation.pecainsumo.request.PecaInsumoRequest;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PecaInsumoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        PecaInsumoControllerMapperImpl.class,
        PecaInsumoControllerTest.MethodSecurityTestConfig.class,
        PecaInsumoControllerTest.SecurityExceptionHandler.class
})
class PecaInsumoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @MockitoBean
    private CadastrarPecaInsumoUseCase cadastrarPecaInsumoUseCase;

    @MockitoBean
    private BuscarPecaInsumoPorIdUseCase buscarPecaInsumoPorIdUseCase;

    @MockitoBean
    private ListarPecaInsumoPaginadoUseCase listarPecaInsumoPaginadoUseCase;

    @MockitoBean
    private AtualizarPecaInsumoUseCase atualizarPecaInsumoUseCase;

    @MockitoBean
    private DeletarPecaInsumoUseCase deletarPecaInsumoUseCase;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private ClienteAtivoPolicy clienteAtivoPolicy;

    @Test
    @WithMockUser(roles = "ADMIN")
    void deveCadastrarPeca() throws Exception {

        PecaInsumoRequest request =
                new PecaInsumoRequest(
                        "Filtro",
                        BigDecimal.valueOf(120),
                        10,
                        CategoriaPecaInsumo.PECA
                        );

        PecaInsumoOutput output =
                new PecaInsumoOutput(
                        1L,
                        "Filtro",
                        BigDecimal.valueOf(120),
                        10,
                        CategoriaPecaInsumo.PECA

                        );

        when(cadastrarPecaInsumoUseCase.execute(any(PecaInsumoInput.class)))
                .thenReturn(output);

        mockMvc.perform(post("/peca-insumo")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Filtro"));

        verify(cadastrarPecaInsumoUseCase)
                .execute(any(PecaInsumoInput.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deveBuscarPorId() throws Exception {

        PecaInsumoOutput output =
                new PecaInsumoOutput(
                        1L,
                        "Filtro",
                        BigDecimal.valueOf(120),
                        10,
                        CategoriaPecaInsumo.PECA);

        when(buscarPecaInsumoPorIdUseCase.execute(1L))
                .thenReturn(output);

        mockMvc.perform(get("/peca-insumo/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Filtro"));

        verify(buscarPecaInsumoPorIdUseCase).execute(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deveListarPecas() throws Exception {

        PecaInsumoOutput output =
                new PecaInsumoOutput(
                        1L,
                        "Filtro",
                        BigDecimal.valueOf(120),
                        10,
                        CategoriaPecaInsumo.PECA);

        when(listarPecaInsumoPaginadoUseCase.execute(
                any(),
                any(),
                any()))
                .thenReturn(new PageResult<>(List.of(output), 1, 0, 10));

        mockMvc.perform(get("/peca-insumo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nome")
                        .value("Filtro"));

        verify(listarPecaInsumoPaginadoUseCase)
                .execute(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deveAtualizarPeca() throws Exception {

        PecaInsumoRequest request =
                new PecaInsumoRequest(
                        "Filtro",
                        BigDecimal.valueOf(150),
                        15,
                        CategoriaPecaInsumo.PECA

                        );

        PecaInsumoOutput output =
                new PecaInsumoOutput(
                        1L,
                        "Filtro",
                        BigDecimal.valueOf(150),
                        15,
                        CategoriaPecaInsumo.PECA);

        when(atualizarPecaInsumoUseCase.execute(
                eq(1L),
                any(PecaInsumoInput.class)))
                .thenReturn(output);

        mockMvc.perform(patch("/peca-insumo/1/atualizacao")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidade").value(15));

        verify(atualizarPecaInsumoUseCase)
                .execute(eq(1L), any(PecaInsumoInput.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deveDeletarPeca() throws Exception {

        doNothing().when(deletarPecaInsumoUseCase)
                .execute(1L);

        mockMvc.perform(delete("/peca-insumo/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$")
                        .value("peca/insumo deletado com sucesso"));

        verify(deletarPecaInsumoUseCase)
                .execute(1L);
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    void deveRetornarForbiddenQuandoClienteCadastrarPeca() throws Exception {

        PecaInsumoRequest request =
                new PecaInsumoRequest(
                        "Filtro",
                        BigDecimal.valueOf(120),
                        10,
                        CategoriaPecaInsumo.PECA
                        );

        mockMvc.perform(post("/peca-insumo")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cadastrarPecaInsumoUseCase);
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
