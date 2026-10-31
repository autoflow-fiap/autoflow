package com.autoflow.presentation.ordemservico;

import com.autoflow.application.gateway.AcompanhamentoUrlGateway;
import com.autoflow.application.input.PageQuery;
import com.autoflow.application.input.ordemservico.CriarOrdemServicoCommand;
import com.autoflow.application.input.ordemservico.OrdemServicoFiltroInput;
import com.autoflow.application.output.PageResult;
import com.autoflow.application.output.ordemservico.*;
import com.autoflow.application.port.in.ordemservico.*;
import com.autoflow.application.policy.ClienteAtivoPolicy;
import com.autoflow.domain.cliente.Cliente;
import com.autoflow.domain.orcamento.Orcamento;
import com.autoflow.domain.orcamento.StatusOrcamento;
import com.autoflow.domain.orcamento.TipoOrcamento;
import com.autoflow.domain.ordemservico.*;
import com.autoflow.domain.veiculo.Veiculo;
import com.autoflow.infrastructure.security.service.CustomUserDetailsService;
import com.autoflow.infrastructure.security.service.JwtService;
import org.junit.jupiter.api.Test;
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
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        OrdemServicoAdministracaoController.class,
        OrdemServicoDiagnosticoController.class,
        OrdemServicoExecucaoController.class,
        OrdemServicoConsultaController.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import({
        ItensNecessariosControllerMapperImpl.class,
        ServicoSolicitadoControllerMapperImpl.class,
        OrdemServicoControllerTest.MethodSecurityTestConfig.class,
        OrdemServicoControllerTest.SecurityExceptionHandler.class
})
class OrdemServicoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CalcularTempoMedioOrdemServicoUseCase calcularTempoMedioOrdemServicoUseCase;

    @MockitoBean
    private ListarOrdensServicoUseCase listarOrdensServicoUseCase;

    @MockitoBean
    private DetalharOrdemServicoUseCase detalharOrdemServicoUseCase;

    @MockitoBean
    private ConsultarStatusOrdemServicoUseCase consultarStatusOrdemServicoUseCase;

    @MockitoBean
    private IniciarDiagnosticoUseCase iniciarDiagnosticoUseCase;

    @MockitoBean
    private RegistrarItensNecessariosUseCase registrarItensNecessariosUseCase;

    @MockitoBean
    private RegistrarLaudoUseCase registrarLaudoUseCase;

    @MockitoBean
    private IniciarServicoUseCase iniciarServicoUseCase;

    @MockitoBean
    private FinalizarServicoUseCase finalizarServicoUseCase;

    @MockitoBean
    private EntregarOrdemServicoUseCase entregarOrdemServicoUseCase;

    @MockitoBean
    private FinalizarDiagnosticoUseCase finalizarDiagnosticoUseCase;

    @MockitoBean
    private CriarOrdemServicoUseCase criarOrdemServicoUseCase;

    @MockitoBean
    private IncluirServicosUseCase incluirServicosUseCase;

    @MockitoBean
    private AtribuirMecanicoUseCase atribuirMecanicoUseCase;

    @MockitoBean
    private AcompanhamentoUrlGateway acompanhamentoUrlGateway;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private ClienteAtivoPolicy clienteAtivoPolicy;

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void deveCriarOrdemServico() throws Exception {
        OrdemServico ordemServico = criarOrdemServico(1L, 55L, "OS-123");

        when(criarOrdemServicoUseCase.execute(any(CriarOrdemServicoCommand.class)))
                .thenReturn(new OrdemServicoCriadaOutput(ordemServico, "token-acompanhamento"));

        mockMvc.perform(post("/ordens-servico")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cpfCnpj": "52998224725",
                                  "veiculo": {
                                    "placa": "NEX0517",
                                    "marca": "Honda",
                                    "modelo": "Civic",
                                    "ano": 2020
                                  },
                                  "servicosSolicitados": [
                                    {
                                      "servicoId": 10
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.numeroOs").value("OS-123"))
                .andExpect(jsonPath("$.status").value("RECEBIDA"))
                .andExpect(jsonPath("$.servicos[0].id").value(55L));

        ArgumentCaptor<CriarOrdemServicoCommand> commandCaptor = ArgumentCaptor.forClass(CriarOrdemServicoCommand.class);
        verify(criarOrdemServicoUseCase).execute(commandCaptor.capture());
        CriarOrdemServicoCommand command = commandCaptor.getValue();
        assertEquals("52998224725", command.cpfCnpj());
        assertEquals("NEX0517", command.veiculo().placa());
        assertEquals("Honda", command.veiculo().marca());
        assertEquals("Civic", command.veiculo().modelo());
        assertEquals(2020, command.veiculo().ano());
        assertEquals(List.of(10L), command.servicoIds());
    }

    @Test
    @WithMockUser(username = "mecanico@autoflow.com", roles = "MECANICO")
    void deveIncluirServicosNaOrdemServico() throws Exception {
        OrdemServico ordemServico = criarOrdemServico(1L, 55L, "OS-123");

        when(incluirServicosUseCase.execute(eq("OS-123"), anyList(), eq("mecanico@autoflow.com")))
                .thenReturn(ordemServico);

        mockMvc.perform(post("/ordens-servico/{numeroOs}/servicos", "OS-123")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "servicoId": 20
                                  }
                                ]
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(1L));

        verify(incluirServicosUseCase).execute(eq("OS-123"), anyList(), eq("mecanico@autoflow.com"));
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void deveAtribuirMecanicoPorEmail() throws Exception {
        OrdemServico ordemServico = criarOrdemServico(1L, 55L, "OS-123");

        when(atribuirMecanicoUseCase.execute(eq("OS-123"), isNull(), eq("mecanico@autoflow.com")))
                .thenReturn(ordemServico);

        mockMvc.perform(patch("/ordens-servico/{numeroOs}/mecanico", "OS-123")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mecanicoEmail": "mecanico@autoflow.com"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(1L));

        verify(atribuirMecanicoUseCase).execute("OS-123", null, "mecanico@autoflow.com");
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void deveAtribuirMecanicoPorId() throws Exception {
        OrdemServico ordemServico = criarOrdemServico(1L, 55L, "OS-123");

        when(atribuirMecanicoUseCase.execute(eq("OS-123"), eq(2L), isNull()))
                .thenReturn(ordemServico);

        mockMvc.perform(patch("/ordens-servico/{numeroOs}/mecanico", "OS-123")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mecanicoId": 2
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(1L));

        verify(atribuirMecanicoUseCase).execute("OS-123", 2L, null);
    }

    @Test
    @WithMockUser(username = "mecanico@autoflow.com", roles = "MECANICO")
    void deveRegistrarItensNecessariosComoMecanico() throws Exception {
        OrdemServico ordemServico = criarOrdemServico(1L, 55L, "OS-123");

        when(registrarItensNecessariosUseCase.execute(eq("342-sb"),eq(55L), eq("mecanico@autoflow.com"),anyList()))
                .thenReturn(ordemServico);

        mockMvc.perform(patch("/ordens-servico/{numeroOs}/servicos/{servicoOsId}/itens-necessarios", "342-sb", 55L)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "pecaInsumoId": 10,
                                    "quantidade": 2
                                  }
                                ]
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(1L));

        ArgumentCaptor<List<ItemNecessario>> itensCaptor = captorDeLista();
        verify(registrarItensNecessariosUseCase).execute(eq("342-sb"),
                eq(55L),
                eq("mecanico@autoflow.com"),
                itensCaptor.capture());
        assertEquals(10L, itensCaptor.getValue().getFirst().getPecaInsumoId());
        assertEquals(2, itensCaptor.getValue().getFirst().getQuantidade());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void deveBloquearInicioServicoParaAtendente() throws Exception {
        mockMvc.perform(patch("/ordens-servico/{numeroOs}/servicos/{servicoOsId}/iniciar", "OS-123", 55L)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(iniciarServicoUseCase, never()).execute(any(), any());
    }

    @Test
    @WithMockUser(roles = "MECANICO")
    void deveFinalizarServicoComoMecanico() throws Exception {
        OrdemServico ordemServico = criarOrdemServico(1L, 55L, "OS-123");
        ordemServico.getServicosSolicitados().getFirst().setStatus(StatusServicoOs.FINALIZADO);

        when(finalizarServicoUseCase.execute("OS-123", 55L)).thenReturn(ordemServico);

        mockMvc.perform(patch("/ordens-servico/{numeroOs}/servicos/{servicoOsId}/finalizar", "OS-123", 55L)
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.servicos[0].status").value("FINALIZADO"));

        verify(finalizarServicoUseCase).execute("OS-123", 55L);
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void deveEntregarOrdemServicoComoAtendente() throws Exception {
        OrdemServico ordemServico = criarOrdemServico(1L, 55L, "OS-123");
        ordemServico.setStatus(StatusOrdemServico.FINALIZADA);
        ordemServico.entregar(LocalDateTime.of(2026, 8, 18, 12, 30));

        when(entregarOrdemServicoUseCase.execute("OS-123")).thenReturn(ordemServico);

        mockMvc.perform(patch("/ordens-servico/{numeroOs}/entregar", "OS-123")
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("ENTREGUE"))
                .andExpect(jsonPath("$.entregueEm").exists());

        verify(entregarOrdemServicoUseCase).execute("OS-123");
    }

    @Test
    @WithMockUser(username = "admin@autoflow.com", roles = "ADMIN")
    void deveListarOrdensServicoComoAdmin() throws Exception {
        OrdemServico primeiraOrdem = criarOrdemServico(1L, 55L, "OS-123");
        OrdemServico segundaOrdem = criarOrdemServico(2L, 66L, "OS-456");
        var page = new PageResult<>(List.of(primeiraOrdem, segundaOrdem), 2, 0, 10);

        when(listarOrdensServicoUseCase.execute(any(OrdemServicoFiltroInput.class), any(PageQuery.class), anyString())).thenReturn(page);

        mockMvc.perform(get("/ordens-servico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.content[0].status").value("RECEBIDA"))
                .andExpect(jsonPath("$.content[0].clienteNome").value("Cliente 1"))
                .andExpect(jsonPath("$.content[0].servicos[0].id").value(55L))
                .andExpect(jsonPath("$.content[1].id").value(2L))
                .andExpect(jsonPath("$.content[1].numeroOs").value("OS-456"))
                .andExpect(jsonPath("$.content[1].servicos[0].id").value(66L))
                .andExpect(jsonPath("$.page.totalElements").value(2));

        verify(listarOrdensServicoUseCase).execute(any(OrdemServicoFiltroInput.class), any(PageQuery.class), eq("admin@autoflow.com"));
    }

    @Test
    @WithMockUser(username = "atendente@autoflow.com", roles = "ATENDENTE")
    void deveListarOrdensServicoComFiltroDeCliente() throws Exception {
        OrdemServico ordemServico = criarOrdemServico(1L, 55L, "OS-123");
        var page = new PageResult<>(List.of(ordemServico), 1, 0, 10);

        when(listarOrdensServicoUseCase.execute(any(OrdemServicoFiltroInput.class), any(PageQuery.class), anyString())).thenReturn(page);

        mockMvc.perform(get("/ordens-servico").param("cliente", "Cliente 1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].clienteNome").value("Cliente 1"))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        verify(listarOrdensServicoUseCase).execute(
                argThat(f -> "Cliente 1".equals(f.cliente())),
                any(PageQuery.class),
                anyString()
        );
    }

    @Test
    @WithMockUser(username = "atendente@autoflow.com", roles = "ATENDENTE")
    void deveListarOrdensServicoComFiltroDeStatus() throws Exception {
        OrdemServico ordemServico = criarOrdemServico(1L, 55L, "OS-123");
        var page = new PageResult<>(List.of(ordemServico), 1, 0, 10);

        when(listarOrdensServicoUseCase.execute(any(OrdemServicoFiltroInput.class), any(PageQuery.class), anyString())).thenReturn(page);

        mockMvc.perform(get("/ordens-servico").param("status", "RECEBIDA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("RECEBIDA"));

        verify(listarOrdensServicoUseCase).execute(
                argThat(f -> StatusOrdemServico.RECEBIDA.equals(f.status())),
                any(PageQuery.class),
                anyString()
        );
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void deveAceitarFiltroDeStatusFinalizadaParaConsultaOperacional() throws Exception {
        when(listarOrdensServicoUseCase.execute(any(OrdemServicoFiltroInput.class), any(PageQuery.class), anyString()))
                .thenReturn(new PageResult<>(List.of(), 0, 0, 10));

        mockMvc.perform(get("/ordens-servico").param("status", "FINALIZADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(0));

        verify(listarOrdensServicoUseCase).execute(
                argThat(f -> StatusOrdemServico.FINALIZADA.equals(f.status())),
                any(PageQuery.class),
                anyString()
        );
    }

    @Test
    @WithMockUser(username = "mecanico@autoflow.com", roles = "MECANICO")
    void deveListarOrdensServicoComoMecanicoPassandoSeuEmail() throws Exception {
        OrdemServico ordemServico = criarOrdemServico(1L, 55L, "OS-123");
        var page = new PageResult<>(List.of(ordemServico), 1, 0, 10);

        when(listarOrdensServicoUseCase.execute(any(OrdemServicoFiltroInput.class), any(PageQuery.class), eq("mecanico@autoflow.com"))).thenReturn(page);

        mockMvc.perform(get("/ordens-servico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));

        verify(listarOrdensServicoUseCase).execute(any(OrdemServicoFiltroInput.class), any(PageQuery.class), eq("mecanico@autoflow.com"));
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void deveDetalharOrdemServicoComoAtendente() throws Exception {
        OrdemServico ordemServico = criarOrdemServico(1L, 55L, "OS-123");
        Orcamento orcamento = criarOrcamento(10L, ordemServico.getNumeroOs());

        when(detalharOrdemServicoUseCase.execute("OS-123"))
                .thenReturn(new OrdemServicoDetalheOutput(ordemServico, orcamento));

        mockMvc.perform(get("/ordens-servico/{numeroOs}", "OS-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.numeroOs").value("OS-123"))
                .andExpect(jsonPath("$.status").value("RECEBIDA"))
                .andExpect(jsonPath("$.cliente.id").value(1L))
                .andExpect(jsonPath("$.cliente.nome").value("Cliente 1"))
                .andExpect(jsonPath("$.cliente.cpfCnpj").value("12345678901"))
                .andExpect(jsonPath("$.cliente.email").value("cliente1@exemplo.com"))
                .andExpect(jsonPath("$.cliente.telefone").value("11999999999"))
                .andExpect(jsonPath("$.veiculo.id").value(2L))
                .andExpect(jsonPath("$.veiculo.placa").value("ABC1D23"))
                .andExpect(jsonPath("$.veiculo.marca").value("Honda"))
                .andExpect(jsonPath("$.veiculo.modelo").value("Civic"))
                .andExpect(jsonPath("$.veiculo.ano").value(2020))
                .andExpect(jsonPath("$.servicos[0].id").value(55L))
                .andExpect(jsonPath("$.servicos[0].servicoId").value(10L))
                .andExpect(jsonPath("$.servicos[0].nome").value("Revisao"))
                .andExpect(jsonPath("$.orcamentoAtual.id").value(10L))
                .andExpect(jsonPath("$.orcamentoAtual.status").value("DISPONIVEL"))
                .andExpect(jsonPath("$.orcamentoAtual.totalGeral").value(100.00));

        verify(detalharOrdemServicoUseCase).execute("OS-123");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deveConsultarStatusComoAdmin() throws Exception {
        consultarStatus("EM_EXECUCAO");
        verify(consultarStatusOrdemServicoUseCase).execute("OS-123", "user");
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void deveConsultarStatusComoAtendente() throws Exception {
        consultarStatus("EM_DIAGNOSTICO");
        verify(consultarStatusOrdemServicoUseCase).execute("OS-123", "user");
    }

    @Test
    @WithMockUser(roles = "MECANICO")
    void deveConsultarStatusComoMecanico() throws Exception {
        consultarStatus("FINALIZADA");
        verify(consultarStatusOrdemServicoUseCase).execute("OS-123", "user");
    }

    @Test
    @WithMockUser(username = "cliente@autoflow.com", roles = "CLIENTE")
    void devePermitirClienteAlcancarConsultaDeStatus() throws Exception {
        consultarStatus("EM_EXECUCAO");
        verify(consultarStatusOrdemServicoUseCase).execute("OS-123", "cliente@autoflow.com");
    }

    @Test
    @WithAnonymousUser
    void deveRetornarForbiddenParaUsuarioNaoAutenticado() throws Exception {
        mockMvc.perform(get("/ordens-servico/{numeroOs}/status", "OS-123"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(consultarStatusOrdemServicoUseCase);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deveCalcularTempoMedioFinalizacaoComoAdmin() throws Exception {
        TempoMedioOrdemServicoOutput output = new TempoMedioOrdemServicoOutput(
                3L,
                7200.0,
                120.0,
                2.0
        );
        when(calcularTempoMedioOrdemServicoUseCase.execute()).thenReturn(output);

        mockMvc.perform(get("/ordens-servico/metricas/tempo-medio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidadeOrdensFinalizadas").value(3L))
                .andExpect(jsonPath("$.tempoMedioSegundos").value(7200.0))
                .andExpect(jsonPath("$.tempoMedioMinutos").value(120.0))
                .andExpect(jsonPath("$.tempoMedioHoras").value(2.0));

        verify(calcularTempoMedioOrdemServicoUseCase).execute();
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void deveRetornarForbiddenQuandoAtendenteTentarCalcularTempoMedioFinalizacao() throws Exception {
        mockMvc.perform(get("/ordens-servico/metricas/tempo-medio"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(calcularTempoMedioOrdemServicoUseCase);
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    void deveRetornarForbiddenQuandoClienteTentarListarOrdensServico() throws Exception {
        mockMvc.perform(get("/ordens-servico"))
                .andExpect(status().isForbidden());

        verify(listarOrdensServicoUseCase, never()).execute(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    void deveRetornarForbiddenQuandoClienteTentarDetalharOrdemServico() throws Exception {
        mockMvc.perform(get("/ordens-servico/{numeroOs}", "OS-123"))
                .andExpect(status().isForbidden());

        verify(detalharOrdemServicoUseCase, never()).execute(anyString());
    }

    @Test
    @WithMockUser(username = "mecanico@autoflow.com", roles = "MECANICO")
    void deveFinalizarDiagnosticoComoMecanico() throws Exception {
        OrdemServico ordemServico = criarOrdemServico(1L, 55L, "OS-123");
        ordemServico.setStatus(StatusOrdemServico.AGUARDANDO_APROVACAO);

        when(finalizarDiagnosticoUseCase.execute("OS-123", "mecanico@autoflow.com"))
                .thenReturn(new FinalizarDiagnosticoOutput(
                        ordemServico,
                        10L,
                        "http://localhost:8080/public/orcamentos/10?token=abc"
                ));

        mockMvc.perform(patch("/ordens-servico/{numeroOs}/diagnostico/finalizar", "OS-123")
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.ordemServico.id").value(1L))
                .andExpect(jsonPath("$.orcamentoId").value(10L));

        verify(finalizarDiagnosticoUseCase).execute("OS-123", "mecanico@autoflow.com");
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    void deveRetornarForbiddenQuandoClienteTentarIniciarServico() throws Exception {
        mockMvc.perform(patch("/ordens-servico/{numeroOs}/servicos/{servicoOsId}/iniciar", "OS-123", 55L)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(iniciarServicoUseCase, never()).execute(anyString(), anyLong());
    }

    @Test
    @WithMockUser(roles = "MECANICO")
    void deveRetornarForbiddenQuandoMecanicoTentarEntregarOrdemServico() throws Exception {
        mockMvc.perform(patch("/ordens-servico/{numeroOs}/entregar", 1L)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(entregarOrdemServicoUseCase, never()).execute(anyString());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void deveRetornarForbiddenQuandoAtendenteTentarRegistrarItensNecessarios() throws Exception {
        mockMvc.perform(patch("/ordens-servico/{numeroOs}/servicos/{servicoOsId}/itens-necessarios", "OS-123", 55L)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "pecaInsumoId": 10,
                                    "quantidade": 2
                                  }
                                ]
                                """))
                .andExpect(status().isForbidden());

        verify(registrarItensNecessariosUseCase, never()).execute(anyString(),anyLong(), anyString(), anyList());
    }

    @Test
    @WithMockUser(username = "mecanico@autoflow.com", roles = "MECANICO")
    void deveIniciarDiagnosticoComoMecanico() throws Exception {
        OrdemServico ordemServico = criarOrdemServico(1L, 55L, "OS-123");
        ordemServico.setStatus(StatusOrdemServico.EM_DIAGNOSTICO);

        when(iniciarDiagnosticoUseCase.execute("OS-123", "mecanico@autoflow.com"))
                .thenReturn(ordemServico);

        mockMvc.perform(patch("/ordens-servico/{numeroOs}/diagnostico/iniciar", "OS-123")
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("EM_DIAGNOSTICO"));

        verify(iniciarDiagnosticoUseCase).execute("OS-123", "mecanico@autoflow.com");
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void deveRetornarForbiddenQuandoAtendenteTentarIniciarDiagnostico() throws Exception {
        mockMvc.perform(patch("/ordens-servico/{numeroOs}/diagnostico/iniciar", "OS-123")
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(iniciarDiagnosticoUseCase, never()).execute(anyString(), anyString());
    }

    @Test
    @WithMockUser(username = "mecanico@autoflow.com", roles = "MECANICO")
    void deveRegistrarLaudoComoMecanico() throws Exception {
        OrdemServico ordemServico = criarOrdemServico(1L, 55L, "OS-123");

        when(registrarLaudoUseCase.execute("OS-123", "mecanico@autoflow.com", "Motor com desgaste"))
                .thenReturn(ordemServico);

        mockMvc.perform(patch("/ordens-servico/{numeroOs}/diagnostico/laudo", "OS-123")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "laudo": "Motor com desgaste"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(1L));

        verify(registrarLaudoUseCase).execute("OS-123", "mecanico@autoflow.com", "Motor com desgaste");
    }

    @Test
    @WithMockUser(roles = "MECANICO")
    void deveIniciarServicoComoMecanico() throws Exception {
        OrdemServico ordemServico = criarOrdemServico(1L, 55L, "OS-123");
        ordemServico.getServicosSolicitados().getFirst().setStatus(StatusServicoOs.EM_EXECUCAO);

        when(iniciarServicoUseCase.execute("OS-123", 55L)).thenReturn(ordemServico);

        mockMvc.perform(patch("/ordens-servico/{numeroOs}/servicos/{servicoOsId}/iniciar", "OS-123", 55L)
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.servicos[0].status").value("EM_EXECUCAO"));

        verify(iniciarServicoUseCase).execute("OS-123", 55L);
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void deveRetornarBadRequestQuandoCriarOrdemSemServicos() throws Exception {
        mockMvc.perform(post("/ordens-servico")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cpfCnpj": "52998224725",
                                  "veiculo": {
                                    "placa": "ABC1D23"
                                  },
                                  "servicosSolicitados": []
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(criarOrdemServicoUseCase);
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    void deveRetornarBadRequestQuandoCriarOrdemSemVeiculo() throws Exception {
        mockMvc.perform(post("/ordens-servico")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cpfCnpj": "52998224725",
                                  "servicosSolicitados": [
                                    {
                                      "servicoId": 10
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(criarOrdemServicoUseCase);
    }

    private OrdemServico criarOrdemServico(Long id, Long servicoOsId, String numeroOs) {
        Cliente cliente = Cliente.reconstituir(1L, "Cliente 1", "12345678901", "11999999999", "cliente1@exemplo.com");
        Veiculo veiculo = new Veiculo(2L, "ABC1D23", "Honda", "Civic", 2020);

        OrdemServico ordemServico = OrdemServico.criar(
                cliente, veiculo, numeroOs, LocalDateTime.of(2026, 8, 18, 12, 30));
        ServicoSolicitado servico = ServicoSolicitado.criar(10L, "Revisao", new BigDecimal("100.00"));
        servico.setId(servicoOsId);
        ordemServico.adicionarServicosSolicitados(List.of(servico));

        ordemServico.setId(id);
        ordemServico.setNumeroOs(numeroOs);
        ordemServico.setDataAbertura(LocalDateTime.of(2026, Month.MAY, 30, 10, 0));

        return ordemServico;
    }

    private Orcamento criarOrcamento(Long id, String numeroOs) {
        Orcamento orcamento = new Orcamento();
        orcamento.setId(id);
        orcamento.setNumeroOs(numeroOs);
        orcamento.setTipo(TipoOrcamento.PRINCIPAL);
        orcamento.setVersao(1);
        orcamento.setStatus(StatusOrcamento.DISPONIVEL);
        orcamento.setCriadoEm(LocalDateTime.of(2026, Month.MAY, 30, 11, 0));
        orcamento.setDisponibilizadoEm(LocalDateTime.of(2026, Month.MAY, 30, 12, 0));
        orcamento.setTotalServicos(new BigDecimal("100.00"));
        orcamento.setTotalItens(BigDecimal.ZERO);
        orcamento.setTotalGeral(new BigDecimal("100.00"));
        return orcamento;
    }

    private void consultarStatus(String status) throws Exception {
        when(consultarStatusOrdemServicoUseCase.execute(eq("OS-123"), anyString()))
                .thenReturn(new StatusOrdemServicoOutput(
                        "OS-123",
                        StatusOrdemServico.valueOf(status),
                        LocalDateTime.of(2026, Month.MAY, 30, 12, 0)));

        mockMvc.perform(get("/ordens-servico/{numeroOs}/status", "OS-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroOs").value("OS-123"))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.ultimaAtualizacao").value("2026-05-30T12:00:00"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.cliente").doesNotExist())
                .andExpect(jsonPath("$.veiculo").doesNotExist())
                .andExpect(jsonPath("$.servicos").doesNotExist())
                .andExpect(jsonPath("$.orcamentoAtual").doesNotExist())
                .andExpect(jsonPath("$.diagnostico").doesNotExist());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> ArgumentCaptor<List<T>> captorDeLista() {
        return ArgumentCaptor.forClass((Class) List.class);
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
