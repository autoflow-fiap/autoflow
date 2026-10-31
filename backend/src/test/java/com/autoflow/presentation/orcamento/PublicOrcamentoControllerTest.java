package com.autoflow.presentation.orcamento;

import com.autoflow.application.gateway.OrcamentoDocumentoGateway;
import com.autoflow.application.port.in.orcamento.ConsultarOrcamentoPorTokenUseCase;
import com.autoflow.application.port.in.orcamento.DecidirOrcamentoUseCase;
import com.autoflow.application.port.in.ordemservico.acompanhamento.AcessarOrcamentoAcompanhamentoUseCase;
import com.autoflow.application.policy.ClienteAtivoPolicy;
import com.autoflow.domain.orcamento.*;
import com.autoflow.infrastructure.security.service.CustomUserDetailsService;
import com.autoflow.infrastructure.security.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Month;

import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PublicOrcamentoController.class)
@AutoConfigureMockMvc(addFilters = false)
class PublicOrcamentoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrcamentoDocumentoGateway orcamentoDocumentoGateway;

    @MockitoBean
    private ConsultarOrcamentoPorTokenUseCase consultarOrcamentoPorTokenUseCase;

    @MockitoBean
    private DecidirOrcamentoUseCase decidirOrcamentoUseCase;

    @MockitoBean
    private AcessarOrcamentoAcompanhamentoUseCase acessarOrcamentoAcompanhamentoUseCase;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private ClienteAtivoPolicy clienteAtivoPolicy;

    @Test
    void deveBaixarPdfValidandoTokenPublico() throws Exception {
        Orcamento orcamento = baseOrcamento();
        byte[] pdf = "%PDF fake".getBytes();

        when(consultarOrcamentoPorTokenUseCase.execute(10L, "tok")).thenReturn(orcamento);
        when(orcamentoDocumentoGateway.gerarPdf(orcamento)).thenReturn(pdf);

        mockMvc.perform(get("/public/orcamentos/{id}/pdf", 10L)
                        .param("token", "tok"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"orcamento-10.pdf\""));

        verify(consultarOrcamentoPorTokenUseCase).execute(10L, "tok");
        verify(orcamentoDocumentoGateway).gerarPdf(orcamento);
    }

    @Test
    void deveConsultarOrcamentoPelaPaginaPublica() throws Exception {
        when(consultarOrcamentoPorTokenUseCase.execute(10L, "tok")).thenReturn(baseOrcamento());

        mockMvc.perform(get("/public/orcamentos/{id}", 10L).param("token", "tok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L));

        verify(consultarOrcamentoPorTokenUseCase).execute(10L, "tok");
    }

    @Test
    void deveAprovarOrcamentoComTokenPublico() throws Exception {
        Orcamento orcamento = baseOrcamento();
        orcamento.setStatus(StatusOrcamento.APROVADO);
        when(decidirOrcamentoUseCase.aprovarComoToken(10L, "tok", "Maria")).thenReturn(orcamento);

        mockMvc.perform(post("/public/orcamentos/{id}/aprovar", 10L)
                        .param("token", "tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Maria\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADO"));

        verify(decidirOrcamentoUseCase).aprovarComoToken(10L, "tok", "Maria");
    }

    @Test
    void deveRecusarOrcamentoComTokenPublicoEMotivo() throws Exception {
        Orcamento orcamento = baseOrcamento();
        orcamento.setStatus(StatusOrcamento.REPROVADO);
        when(decidirOrcamentoUseCase.recusarComoToken(10L, "tok", "Muito caro", "Maria"))
                .thenReturn(orcamento);

        mockMvc.perform(post("/public/orcamentos/{id}/recusar", 10L)
                        .param("token", "tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Muito caro\",\"nome\":\"Maria\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REPROVADO"));

        verify(decidirOrcamentoUseCase).recusarComoToken(10L, "tok", "Muito caro", "Maria");
    }

    @Test
    void deveRecusarOrcamentoComTokenPublicoSemBody() throws Exception {
        Orcamento orcamento = baseOrcamento();
        orcamento.setStatus(StatusOrcamento.REPROVADO);
        when(decidirOrcamentoUseCase.recusarComoToken(10L, "tok", null, null)).thenReturn(orcamento);

        mockMvc.perform(post("/public/orcamentos/{id}/recusar", 10L)
                        .param("token", "tok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REPROVADO"));

        verify(decidirOrcamentoUseCase).recusarComoToken(10L, "tok", null, null);
    }

    @Test
    void deveRetornarUnauthorizedQuandoTokenPublicoForInvalido() throws Exception {


        doThrow(new ResponseStatusException(UNAUTHORIZED, "Token invalido"))
                .when(consultarOrcamentoPorTokenUseCase)
                .execute(10L, "tok-invalido");

        mockMvc.perform(get("/public/orcamentos/{id}/pdf", 10L)
                        .param("token", "tok-invalido"))
                .andExpect(status().isUnauthorized());

        verify(consultarOrcamentoPorTokenUseCase).execute(10L, "tok-invalido");
    }

    @Test
    void deveBaixarPdfComTokenDeAcompanhamento() throws Exception {
        Orcamento orcamento = baseOrcamento();
        when(acessarOrcamentoAcompanhamentoUseCase.consultar(10L, "token-os")).thenReturn(orcamento);
        when(orcamentoDocumentoGateway.gerarPdf(orcamento)).thenReturn("%PDF".getBytes());

        mockMvc.perform(get("/public/orcamentos/{id}/pdf/acompanhamento", 10L)
                        .param("token", "token-os"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE));

        verify(acessarOrcamentoAcompanhamentoUseCase).consultar(10L, "token-os");
    }

    @Test
    void deveAprovarComTokenDeAcompanhamento() throws Exception {
        Orcamento orcamento = baseOrcamento();
        orcamento.setStatus(StatusOrcamento.APROVADO);
        when(acessarOrcamentoAcompanhamentoUseCase.aprovar(10L, "token-os")).thenReturn(orcamento);

        mockMvc.perform(post("/public/orcamentos/{id}/aprovar/acompanhamento", 10L)
                        .param("token", "token-os"))
                .andExpect(status().isOk());

        verify(acessarOrcamentoAcompanhamentoUseCase).aprovar(10L, "token-os");
    }

    private Orcamento baseOrcamento() {
        return Orcamento.builder()
                .id(10L)
                .ordemServicoId(1L)
                .numeroOs("OS-123")
                .tipo(TipoOrcamento.PRINCIPAL)
                .versao(1)
                .status(StatusOrcamento.DISPONIVEL)
                .criadoEm(LocalDateTime.of(2026, Month.MAY, 31, 10, 0))
                .disponibilizadoEm(LocalDateTime.of(2026, Month.MAY, 31, 10, 1))
                .totalServicos(new BigDecimal("100.00"))
                .totalItens(new BigDecimal("50.00"))
                .totalGeral(new BigDecimal("150.00"))
                .cliente(ClienteOrcamentoSnapshot.builder()
                        .nome("Cliente")
                        .cpfCnpj("12345678901")
                        .email("cliente@exemplo.com")
                        .telefone("11999999999")
                        .build())
                .veiculo(VeiculoOrcamentoSnapshot.builder()
                        .placa("ABC1234")
                        .marca("Fiat")
                        .modelo("Uno")
                        .ano(2020)
                        .build())
                .build();
    }
}
