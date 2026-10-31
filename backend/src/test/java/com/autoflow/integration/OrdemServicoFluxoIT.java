package com.autoflow.integration;

import com.autoflow.integration.config.AbstractIT;
import com.autoflow.integration.utils.TestUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o ciclo de vida completo de uma Ordem de Serviço em ordem:
 * criar → atribuir mecânico → diagnóstico → itens → laudo → finalizar diagnóstico
 * → aprovar orçamento → executar → finalizar → entregar
 */
@DisplayName("Ordem de Serviço - Fluxo Completo de Integração")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrdemServicoFluxoIT extends AbstractIT {

    private String adminToken;
    private String mecanicoToken;
    private String clienteToken;

    private Long mecanicoId;
    private String mecanicoEmail;
    private Long servicoId;
    private Long servicoId2;
    private Long pecaId;
    private String numeroOs;
    private Long servicoSolicitadoId;
    private Long orcamentoId;
    private String numeroOsReparo;

    @BeforeAll
    void configurarAmbiente() {
        // Limpa e cria dados base
        limparBancoDeDados();

        adminToken   = registrarELogar(TestUtils.EMAIL_ADMIN,     TestUtils.CPF_ATENDENTE, "ADMIN");
        clienteToken = registrarELogar(TestUtils.EMAIL_CLIENTE,   TestUtils.CPF_CLIENTE,   "CLIENTE");

        mecanicoEmail = TestUtils.EMAIL_MECANICO;
        mecanicoToken = registrarELogar(mecanicoEmail, TestUtils.CPF_MECANICO, "MECANICO");
        mecanicoId = jdbcTemplate.queryForObject(
                "SELECT id FROM usuarios WHERE email = ?", Long.class, mecanicoEmail);

        registrarELogar(TestUtils.EMAIL_ATENDENTE, TestUtils.CPF_CLIENTE_2, "ATENDENTE");

        // Cria cliente
        post("/clientes", TestUtils.clienteRequest("José Santos", TestUtils.CPF_CLIENTE, "jose@test.com"), adminToken);

        // Cria serviço
        ResponseEntity<String> svcResp = post("/servicos",
                TestUtils.servicoRequest("Troca de Óleo e Filtro", "Troca completa", 200.00), adminToken);
        servicoId = parseJson(svcResp.getBody()).get("id").asLong();

        // Cria peça com estoque suficiente
        ResponseEntity<String> pecaResp = post("/peca-insumo",
                TestUtils.pecaRequest("Filtro de Óleo", 10, 45.00, "PECA"), adminToken);
        pecaId = parseJson(pecaResp.getBody()).get("id").asLong();

        // Cria segundo serviço (usado nos testes de adição de serviço e reparo adicional)
        ResponseEntity<String> svcResp2 = post("/servicos",
                TestUtils.servicoRequest("Alinhamento 3D", "Alinhamento de rodas", 90.00), adminToken);
        servicoId2 = parseJson(svcResp2.getBody()).get("id").asLong();

        // Cria OS secundária e avança até EM_EXECUCAO para os testes de reparo adicional
        var osReparoRequest = Map.of(
                "cpfCnpj", TestUtils.CPF_CLIENTE,
                "veiculo", Map.of("placa", "REP1234", "marca", "Ford", "modelo", "Ka", "ano", 2020),
                "servicosSolicitados", List.of(Map.of("servicoId", servicoId))
        );
        JsonNode osReparoJson = parseJson(post("/ordens-servico", osReparoRequest, adminToken).getBody());
        numeroOsReparo    = osReparoJson.get("numeroOs").asText();
        long servicoOsReparoId = osReparoJson.get("servicos").get(0).get("servicoId").asLong();

        patch("/ordens-servico/" + numeroOsReparo + "/mecanico",
                TestUtils.incluirMecanicoRequest(mecanicoId, mecanicoEmail), adminToken);
        patch("/ordens-servico/" + numeroOsReparo + "/diagnostico/iniciar", null, mecanicoToken);
        patch("/ordens-servico/" + numeroOsReparo + "/servicos/" + servicoOsReparoId + "/itens-necessarios",
                TestUtils.itensNecessariosRequest(pecaId, 1), mecanicoToken);
        patch("/ordens-servico/" + numeroOsReparo + "/diagnostico/laudo",
                TestUtils.registrarLaudoRequest("Revisão adicional necessária."), mecanicoToken);
        ResponseEntity<String> finReparoResp = patch("/ordens-servico/" + numeroOsReparo + "/diagnostico/finalizar", null, mecanicoToken);
        long orcReparoId = parseJson(finReparoResp.getBody()).get("orcamentoId").asLong();
        post("/orcamentos/" + orcReparoId + "/aprovar", null, clienteToken);
        patch("/ordens-servico/" + numeroOsReparo + "/servicos/" + servicoOsReparoId + "/iniciar", null, mecanicoToken);
    }

    @Test
    @Order(1)
    @DisplayName("1 - deve criar ordem de serviço com sucesso")
    void deveCriarOrdemServico() {

        // Usa uma placa fixa para este fluxo
        var requestFixo = Map.of(
                "cpfCnpj", TestUtils.CPF_CLIENTE,
                "veiculo", Map.of("placa", "ABC1234", "marca", "Toyota", "modelo", "Corolla", "ano", 2021),
                "servicosSolicitados", List.of(Map.of("servicoId", servicoId))
        );

        ResponseEntity<String> response = post("/ordens-servico", requestFixo, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode json = parseJson(response.getBody());
        assertThat(json.get("status").asText()).isEqualTo("RECEBIDA");
        assertThat(json.get("numeroOs").asText()).isNotBlank();

        numeroOs = json.get("numeroOs").asText();
        servicoSolicitadoId = json.get("servicos").get(0).get("servicoId").asLong();
    }

    @Test
    @Order(2)
    @DisplayName("2 - deve atribuir mecânico à OS")
    void deveAtribuirMecanico() {
        var request = TestUtils.incluirMecanicoRequest(mecanicoId, mecanicoEmail);

        ResponseEntity<String> response = patch("/ordens-servico/" + numeroOs + "/mecanico", request, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    @Order(3)
    @DisplayName("3 - deve iniciar diagnóstico")
    void deveIniciarDiagnostico() {
        ResponseEntity<String> response = patch("/ordens-servico/" + numeroOs + "/diagnostico/iniciar", null, mecanicoToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(parseJson(response.getBody()).get("status").asText()).isEqualTo("EM_DIAGNOSTICO");
    }

    @Test
    @Order(4)
    @DisplayName("4 - deve registrar itens necessários ao serviço")
    void deveRegistrarItensNecessarios() {
        var itens = TestUtils.itensNecessariosRequest(pecaId, 2);
        String url = "/ordens-servico/" + numeroOs + "/servicos/" + servicoSolicitadoId + "/itens-necessarios";

        ResponseEntity<String> response = patch(url, itens, mecanicoToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode servico = parseJson(response.getBody()).get("servicos").get(0);
        assertThat(servico.get("itensNecessarios").isArray()).isTrue();
        assertThat(servico.get("itensNecessarios").size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(5)
    @DisplayName("5 - deve registrar laudo do diagnóstico")
    void deveRegistrarLaudo() {
        var request = TestUtils.registrarLaudoRequest("Motor com desgaste. Troca de óleo e filtro necessária.");

        ResponseEntity<String> response = patch("/ordens-servico/" + numeroOs + "/diagnostico/laudo", request, mecanicoToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    @Order(6)
    @DisplayName("6 - deve finalizar diagnóstico e gerar orçamento")
    void deveFinalizarDiagnostico() {
        ResponseEntity<String> response = patch("/ordens-servico/" + numeroOs + "/diagnostico/finalizar", null, mecanicoToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode json = parseJson(response.getBody());
        assertThat(json.get("orcamentoId").asLong()).isGreaterThan(0);
        assertThat(json.get("ordemServico").get("status").asText()).isEqualTo("AGUARDANDO_APROVACAO");

        orcamentoId = json.get("orcamentoId").asLong();
    }

    @Test
    @Order(7)
    @DisplayName("7 - deve consultar orçamento gerado")
    void deveConsultarOrcamento() {
        ResponseEntity<String> response = get("/orcamentos/" + orcamentoId, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = parseJson(response.getBody());
        assertThat(json.get("status").asText()).isEqualTo("DISPONIVEL");
        assertThat(json.get("totalGeral").decimalValue()).isPositive();
    }

    @Test
    @Order(8)
    @DisplayName("8 - cliente deve aprovar orçamento e OS passa para EM_EXECUCAO")
    void deveAprovarOrcamento() {
        ResponseEntity<String> response = post("/orcamentos/" + orcamentoId + "/aprovar", null, clienteToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parseJson(response.getBody()).get("status").asText()).isEqualTo("APROVADO");
    }

    @Test
    @Order(9)
    @DisplayName("9 - deve iniciar execução do serviço")
    void deveIniciarServico() {
        int estoqueAntes = jdbcTemplate.queryForObject(
                "SELECT quantidade FROM pecas_insumos WHERE id = ?", Integer.class, pecaId);

        ResponseEntity<String> response = patch(
                "/ordens-servico/" + numeroOs + "/servicos/" + servicoSolicitadoId + "/iniciar", null, mecanicoToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode servico = parseJson(response.getBody()).get("servicos").get(0);
        assertThat(servico.get("status").asText()).isEqualTo("EM_EXECUCAO");
        assertThat(servico.get("itensNecessarios").get(0).get("status").asText())
                .isEqualTo("UTILIZADO");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT quantidade FROM pecas_insumos WHERE id = ?", Integer.class, pecaId))
                .isEqualTo(estoqueAntes - 2);
    }

    @Test
    @Order(10)
    @DisplayName("10 - deve finalizar serviço e OS passa para FINALIZADA automaticamente")
    void deveFinalizarServico() {
        ResponseEntity<String> response = patch(
                "/ordens-servico/" + numeroOs + "/servicos/" + servicoSolicitadoId + "/finalizar", null, mecanicoToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode json = parseJson(response.getBody());
        JsonNode servico = json.get("servicos").get(0);
        assertThat(servico.get("status").asText()).isEqualTo("FINALIZADO");
        assertThat(json.get("status").asText()).isEqualTo("FINALIZADA");
    }

    @Test
    @Order(11)
    @DisplayName("11 - deve entregar OS e status muda para ENTREGUE")
    void deveEntregarOs() {
        ResponseEntity<String> response = patch("/ordens-servico/" + numeroOs + "/entregar", null, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(parseJson(response.getBody()).get("status").asText()).isEqualTo("ENTREGUE");

        Long ordemServicoId = jdbcTemplate.queryForObject(
                "SELECT id FROM ordem_servico WHERE numero_os = ?", Long.class, numeroOs);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM ordem_servico_status_historico
                WHERE ordem_servico_id = ? AND status = 'ENTREGUE'
                """, Integer.class, ordemServicoId))
                .isEqualTo(1);
    }

    @Test
    @Order(12)
    @DisplayName("12 - deve listar somente OS operacionais e manter a OS entregue persistida")
    void deveListarOrdens() {
        ResponseEntity<String> response = get("/ordens-servico", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = parseJson(response.getBody());
        JsonNode content = json.get("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content).allSatisfy(os -> assertThat(os.get("status").asText())
                .isIn("EM_EXECUCAO", "AGUARDANDO_APROVACAO", "EM_DIAGNOSTICO", "RECEBIDA"));
        assertThat(content.findValuesAsText("numeroOs")).doesNotContain(numeroOs);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ordem_servico WHERE numero_os = ? AND status = 'ENTREGUE'",
                Integer.class,
                numeroOs
        )).isEqualTo(1);
    }

    @Test
    @Order(13)
    @DisplayName("13 - cliente deve visualizar suas ordens de serviço")
    void clienteDeveVerSuasOrdens() {
        ResponseEntity<String> response = get("/clientes/me/ordens-servico", clienteToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = parseJson(response.getBody());
        assertThat(json.isArray()).isTrue();
        assertThat(json.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(50)
    @DisplayName("N1 - deve retornar 404 ao criar OS com cliente inexistente")
    void deveRetornar404OsCpfInexistente() {
        var request = Map.of(
                "cpfCnpj", "98765432100",
                "veiculo", Map.of("placa", "ZZZ9999", "marca", "X", "modelo", "Y", "ano", 2020),
                "servicosSolicitados", List.of(Map.of("servicoId", servicoId))
        );

        ResponseEntity<String> response = post("/ordens-servico", request, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(51)
    @DisplayName("N2 - deve retornar 400 ao criar OS sem serviços")
    void deveRetornar400OsSemServicos() {
        var request = Map.of(
                "cpfCnpj", TestUtils.CPF_CLIENTE,
                "veiculo", Map.of("placa", "ZZZ8888", "marca", "X", "modelo", "Y", "ano", 2020),
                "servicosSolicitados", List.of()
        );

        ResponseEntity<String> response = post("/ordens-servico", request, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(52)
    @DisplayName("N3 - deve retornar 403 quando cliente tenta criar OS")
    void deveRetornar403ClienteCriaOs() {
        var request = Map.of(
                "cpfCnpj", TestUtils.CPF_CLIENTE,
                "veiculo", Map.of("placa", "ZZZ7777", "marca", "X", "modelo", "Y", "ano", 2020),
                "servicosSolicitados", List.of(Map.of("servicoId", servicoId))
        );

        ResponseEntity<String> response = post("/ordens-servico", request, clienteToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(53)
    @DisplayName("N4 - deve retornar 404 para OS inexistente")
    void deveRetornar404OsInexistente() {
        ResponseEntity<String> response = patch("/ordens-servico/OS-INVALIDA/diagnostico/iniciar", null, mecanicoToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(54)
    @DisplayName("N5 - deve retornar 403 sem token ao tentar listar OS")
    void deveRetornar403SemTokenOs() {
        ResponseEntity<String> response = restTemplate.getForEntity("/ordens-servico", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(55)
    @DisplayName("N9 - não deve iniciar serviço antes da aprovação do orçamento")
    void deveImpedirIniciarServicoAntesDaAprovacaoDoOrcamento() {
        JsonNode os = parseJson(post("/ordens-servico", Map.of(
                "cpfCnpj", TestUtils.CPF_CLIENTE,
                "veiculo", Map.of(
                        "placa", TestUtils.placaUnica(),
                        "marca", "Honda",
                        "modelo", "City",
                        "ano", 2023
                ),
                "servicosSolicitados", List.of(Map.of("servicoId", servicoId))
        ), adminToken).getBody());
        String novaOs = os.get("numeroOs").asText();
        Long novoServicoId = os.get("servicos").get(0).get("servicoId").asLong();

        ResponseEntity<String> response = patch(
                "/ordens-servico/" + novaOs + "/servicos/" + novoServicoId + "/iniciar",
                null,
                mecanicoToken
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(parseJson(response.getBody()).get("erro").asText())
                .isEqualTo("O serviço só pode ser iniciado após a aprovação do orçamento.");
    }

    @Test
    @Order(56)
    @DisplayName("N10 - não deve entregar OS que ainda não foi finalizada")
    void deveImpedirEntregaAntesDaFinalizacao() {
        JsonNode os = parseJson(post("/ordens-servico", Map.of(
                "cpfCnpj", TestUtils.CPF_CLIENTE,
                "veiculo", Map.of(
                        "placa", TestUtils.placaUnica(),
                        "marca", "Honda",
                        "modelo", "Fit",
                        "ano", 2022
                ),
                "servicosSolicitados", List.of(Map.of("servicoId", servicoId))
        ), adminToken).getBody());
        String novaOs = os.get("numeroOs").asText();

        ResponseEntity<String> response = patch("/ordens-servico/" + novaOs + "/entregar", null, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(parseJson(response.getBody()).get("erro").asText())
                .contains("Transicao invalida");
    }

    @Test
    @Order(60)
    @DisplayName("14 - deve buscar ordem de serviço pelo número")
    void deveBuscarOrdemServicoPorNumero() {
        ResponseEntity<String> response = get("/ordens-servico/" + numeroOs, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = parseJson(response.getBody());
        assertThat(json.get("numeroOs").asText()).isEqualTo(numeroOs);
        assertThat(json.get("status").asText()).isEqualTo("ENTREGUE");
        assertThat(json.get("servicos").isArray()).isTrue();
        assertThat(json.get("servicos").size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(61)
    @DisplayName("N6 - deve retornar 404 ao buscar OS com número inexistente")
    void deveRetornar404AoBuscarOsInexistente() {
        ResponseEntity<String> response = get("/ordens-servico/OS-INVALIDA-9999", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(62)
    @DisplayName("15 - deve adicionar serviço a uma OS existente")
    void deveAdicionarServicoNaOs() {
        var osRequest = Map.of(
                "cpfCnpj", TestUtils.CPF_CLIENTE,
                "veiculo", Map.of("placa", "ADD1234", "marca", "Fiat", "modelo", "Uno", "ano", 2019),
                "servicosSolicitados", List.of(Map.of("servicoId", servicoId))
        );
        String novaOs = parseJson(post("/ordens-servico", osRequest, adminToken).getBody()).get("numeroOs").asText();

        ResponseEntity<String> response = post("/ordens-servico/" + novaOs + "/servicos",
                List.of(Map.of("servicoId", servicoId2)), adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode json = parseJson(response.getBody());
        assertThat(json.get("servicos").isArray()).isTrue();
        assertThat(json.get("servicos").size()).isEqualTo(2);
    }

    @Test
    @Order(63)
    @DisplayName("16 - deve criar reparo adicional em OS com serviço em execução")
    void deveCriarReparoAdicional() {
        var reparoRequest = Map.of(
                "servicos", List.of(Map.of(
                        "servicoId", servicoId2,
                        "itensNecessarios", List.of(Map.of("pecaInsumoId", pecaId, "quantidade", 1))
                ))
        );

        ResponseEntity<String> response = post(
                "/ordens-servico/" + numeroOsReparo + "/reparos-adicionais", reparoRequest, mecanicoToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode json = parseJson(response.getBody());
        assertThat(json.get("reparoAdicionalId").asLong()).isGreaterThan(0);
        assertThat(json.get("orcamentoId").asLong()).isGreaterThan(0);
    }

    @Test
    @Order(64)
    @DisplayName("N7 - deve rejeitar reparo adicional em OS finalizada ou entregue")
    void deveRejeitarReparoAdicionalEmOsFinalizada() {
        var reparoRequest = Map.of(
                "servicos", List.of(Map.of(
                        "servicoId", servicoId,
                        "itensNecessarios", List.of(Map.of("pecaInsumoId", pecaId, "quantidade", 1))
                ))
        );

        ResponseEntity<String> response = post(
                "/ordens-servico/" + numeroOs + "/reparos-adicionais", reparoRequest, mecanicoToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(parseJson(response.getBody()).get("erro").asText())
                .isEqualTo("Não é possível registrar reparo adicional em uma OS finalizada.");
    }

    @Test
    @Order(70)
    @DisplayName("17 - admin deve consultar tempo médio de finalização das ordens de serviço")
    void adminDeveConsultarTempoMedioOs() {
        ResponseEntity<String> response = get("/ordens-servico/metricas/tempo-medio", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = parseJson(response.getBody());
        assertThat(json.get("quantidadeOrdensFinalizadas").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(json.get("tempoMedioSegundos")).isNotNull();
    }

    @Test
    @Order(71)
    @DisplayName("N8 - cliente não deve acessar métricas de tempo médio das OSs")
    void clienteNaoDeveAcessarMetricasOs() {
        ResponseEntity<String> response = get("/ordens-servico/metricas/tempo-medio", clienteToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
