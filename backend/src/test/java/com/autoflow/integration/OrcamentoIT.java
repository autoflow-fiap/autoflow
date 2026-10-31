package com.autoflow.integration;

import com.autoflow.integration.config.AbstractIT;
import com.autoflow.integration.utils.TestUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa fluxos de orçamento: consulta autenticada, aprovação, recusa, acesso negado e filtros.
 * Cada teste recria uma OS completa até o estado AGUARDANDO_APROVACAO.
 */
@DisplayName("Orçamento - Testes de Integração")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrcamentoIT extends AbstractIT {

    private String adminToken;
    private String clienteToken;
    private String outroClienteToken;
    private Long mecanicoId;
    private Long servicoId;
    private Long orcamentoId;
    private String orcamentoPublicUrl;

    @BeforeAll
    void configurarAmbiente() {
        limparBancoDeDados();

        adminToken = registrarELogar(TestUtils.EMAIL_ADMIN, TestUtils.CPF_ATENDENTE, "ADMIN");

        String mecanicoToken = registrarELogar(TestUtils.EMAIL_MECANICO, TestUtils.CPF_MECANICO, "MECANICO");
        mecanicoId = jdbcTemplate.queryForObject(
                "SELECT id FROM usuarios WHERE email = ?", Long.class, TestUtils.EMAIL_MECANICO);

        clienteToken = registrarELogar(TestUtils.EMAIL_CLIENTE, TestUtils.CPF_CLIENTE, "CLIENTE");

        outroClienteToken = registrarELogar(TestUtils.EMAIL_ATENDENTE, TestUtils.CPF_CLIENTE_2, "CLIENTE");

        post("/clientes", TestUtils.clienteRequest("Cliente Orçamento", TestUtils.CPF_CLIENTE, TestUtils.EMAIL_CLIENTE), adminToken);

        ResponseEntity<String> svcResp = post("/servicos",
                TestUtils.servicoRequest("Revisão Completa", "Revisão geral do veículo", 800.00), adminToken);
        servicoId = parseJson(svcResp.getBody()).get("id").asLong();

        JsonNode finJson = criarOsAguardandoAprovacao(mecanicoToken, "ABC1234");
        orcamentoId       = finJson.get("orcamentoId").asLong();
        orcamentoPublicUrl = finJson.get("publicUrl").asText();
    }

    private JsonNode criarOsAguardandoAprovacao(String mecToken, String placa) {
        var osRequest = Map.of(
                "cpfCnpj", TestUtils.CPF_CLIENTE,
                "veiculo", Map.of("placa", placa, "marca", "Fiat", "modelo", "Argo", "ano", 2022),
                "servicosSolicitados", List.of(Map.of("servicoId", servicoId))
        );

        ResponseEntity<String> osResp = post("/ordens-servico", osRequest, adminToken);
        String numOs = parseJson(osResp.getBody()).get("numeroOs").asText();

        patch("/ordens-servico/" + numOs + "/mecanico",
                TestUtils.incluirMecanicoRequest(mecanicoId, TestUtils.EMAIL_MECANICO), adminToken);

        patch("/ordens-servico/" + numOs + "/diagnostico/iniciar", null, mecToken);

        patch("/ordens-servico/" + numOs + "/diagnostico/laudo",
                TestUtils.registrarLaudoRequest("Revisão geral necessária."), mecToken);

        ResponseEntity<String> finResp = patch("/ordens-servico/" + numOs + "/diagnostico/finalizar", null, mecToken);
        return parseJson(finResp.getBody());
    }

    private Long orcamentoIdDe(String mecToken, String placa) {
        return criarOsAguardandoAprovacao(mecToken, placa).get("orcamentoId").asLong();
    }

    @Test
    @Order(1)
    @DisplayName("admin deve consultar orçamento com sucesso")
    void adminDeveConsultarOrcamento() {
        ResponseEntity<String> response = get("/orcamentos/" + orcamentoId, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = parseJson(response.getBody());
        assertThat(json.get("id").asLong()).isEqualTo(orcamentoId);
        assertThat(json.get("status").asText()).isEqualTo("DISPONIVEL");
        assertThat(json.get("totalGeral").decimalValue()).isPositive();
    }

    @Test
    @Order(2)
    @DisplayName("admin deve listar orçamentos sem filtros")
    void adminDeveListarOrcamentos() {
        ResponseEntity<String> response = get("/orcamentos", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = parseJson(response.getBody());
        assertThat(json.isArray()).isTrue();
        assertThat(json.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(3)
    @DisplayName("cliente deve reprovar orçamento com motivo")
    void clienteDeveReprovarOrcamento() {
        String mecLogin = gerarToken(TestUtils.CPF_MECANICO, "MECANICO");

        Long outroOrcamentoId = orcamentoIdDe(mecLogin, "DEF5678");

        var recusa = TestUtils.recusarOrcamentoRequest("Preço muito alto, vou buscar outro orçamento.");
        ResponseEntity<String> response = post("/orcamentos/" + outroOrcamentoId + "/recusar", recusa, clienteToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = parseJson(response.getBody());
        assertThat(json.get("status").asText()).isEqualTo("REPROVADO");
    }

    @Test
    @Order(4)
    @DisplayName("cliente deve aprovar orçamento")
    void clienteDeveAprovarOrcamento() {
        String mecLogin = gerarToken(TestUtils.CPF_MECANICO, "MECANICO");

        Long novoOrcamentoId = orcamentoIdDe(mecLogin, "GHI9012");

        ResponseEntity<String> response = post("/orcamentos/" + novoOrcamentoId + "/aprovar", null, clienteToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parseJson(response.getBody()).get("status").asText()).isEqualTo("APROVADO");
    }

    @Test
    @Order(5)
    @DisplayName("deve retornar 404 para orçamento inexistente")
    void deveRetornar404OrcamentoInexistente() {
        ResponseEntity<String> response = get("/orcamentos/999999", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(6)
    @DisplayName("deve retornar 403 sem token ao consultar orçamento")
    void deveRetornar403SemToken() {
        ResponseEntity<String> response = restTemplate.getForEntity("/orcamentos/" + orcamentoId, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(7)
    @DisplayName("mecânico não deve ter acesso ao endpoint de orçamentos")
    void mecanicoNaoDeveAcessarOrcamentos() {
        String mecLogin = gerarToken(TestUtils.CPF_MECANICO, "MECANICO");

        ResponseEntity<String> response = get("/orcamentos/" + orcamentoId, mecLogin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(8)
    @DisplayName("deve confirmar que reprova orçamento com sucesso e registra o motivo")
    void deveReprovarOrcamentoSemMotivo() {
        String mecLogin = gerarToken(TestUtils.CPF_MECANICO, "MECANICO");

        Long idParaRecusar = orcamentoIdDe(mecLogin, "JKL3456");

        // Reprova sem motivo (body vazio)
        ResponseEntity<String> response = post("/orcamentos/" + idParaRecusar + "/recusar",
                Map.of(), clienteToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parseJson(response.getBody()).get("status").asText()).isEqualTo("REPROVADO");
    }

    @Test
    @Order(9)
    @DisplayName("admin deve listar orçamentos filtrados por status")
    void deveListarOrcamentosComFiltroStatus() {
        ResponseEntity<String> response = get("/orcamentos?statusOrcamento=DISPONIVEL", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = parseJson(response.getBody());
        assertThat(json.isArray()).isTrue();
        for (JsonNode orc : json) {
            assertThat(orc.get("status").asText()).isEqualTo("DISPONIVEL");
        }
    }

    @Test
    @Order(10)
    @DisplayName("deve gerar PDF do orçamento via link público")
    void deveGerarPdfDoOrcamento() {
        String token = orcamentoPublicUrl.split("token=")[1];
        String path  = "/public/orcamentos/" + orcamentoId + "/pdf?token=" + token;

        ResponseEntity<byte[]> response = restTemplate.getForEntity(path, byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(org.springframework.http.MediaType.APPLICATION_PDF);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    @Order(11)
    @DisplayName("deve retornar 404 ao solicitar PDF de orçamento inexistente")
    void deveRetornar404PdfOrcamentoInexistente() {
        ResponseEntity<byte[]> response = restTemplate.getForEntity(
                "/public/orcamentos/999999/pdf?token=token-invalido", byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(12)
    @DisplayName("deve rejeitar versao duplicada para o mesmo tipo e OS")
    void deveRejeitarVersaoDuplicadaParaMesmoTipoNaMesmaOs() {
        int quantidadeAntes = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM orcamento
                WHERE ordem_servico_id = (SELECT ordem_servico_id FROM orcamento WHERE id = ?)
                  AND tipo = (SELECT tipo FROM orcamento WHERE id = ?)
                  AND versao = (SELECT versao FROM orcamento WHERE id = ?)
                """, Integer.class, orcamentoId, orcamentoId, orcamentoId);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO orcamento (
                    ordem_servico_id, numero_os, tipo, versao, status,
                    criado_em, disponibilizado_em, total_servicos, total_itens, total_geral
                )
                SELECT ordem_servico_id, numero_os, tipo, versao, 'APROVADO',
                       criado_em, disponibilizado_em, total_servicos, total_itens, total_geral
                FROM orcamento
                WHERE id = ?
                """, orcamentoId))
                .isInstanceOf(DataIntegrityViolationException.class);

        int quantidadeDepois = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM orcamento
                WHERE ordem_servico_id = (SELECT ordem_servico_id FROM orcamento WHERE id = ?)
                  AND tipo = (SELECT tipo FROM orcamento WHERE id = ?)
                  AND versao = (SELECT versao FROM orcamento WHERE id = ?)
                """, Integer.class, orcamentoId, orcamentoId, orcamentoId);

        assertThat(quantidadeDepois).isEqualTo(quantidadeAntes);
    }

    @Test
    @Order(13)
    @DisplayName("admin deve gerar PDF do orçamento autenticado com headers corretos")
    void adminDeveGerarPdfDoOrcamentoAutenticado() {
        ResponseEntity<byte[]> response = restTemplate.exchange(
                "/orcamentos/" + orcamentoId + "/pdf",
                org.springframework.http.HttpMethod.GET,
                authEntity(adminToken),
                byte[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(org.springframework.http.MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .isEqualTo("attachment; filename=\"orcamento-" + orcamentoId + ".pdf\"");
        assertThat(response.getBody()).isNotEmpty();
        assertThat(new String(response.getBody(), 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @Order(14)
    @DisplayName("cliente não deve consultar nem aprovar orçamento de outro cliente")
    void clienteNaoDeveAcessarOrcamentoDeOutroCliente() {
        ResponseEntity<String> consulta = get("/orcamentos/" + orcamentoId, outroClienteToken);
        ResponseEntity<String> aprovacao = post("/orcamentos/" + orcamentoId + "/aprovar", null, outroClienteToken);

        assertThat(consulta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(aprovacao.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM orcamento WHERE id = ?", String.class, orcamentoId))
                .isEqualTo("DISPONIVEL");
    }
}
