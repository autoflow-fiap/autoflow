package com.autoflow.integration;

import com.autoflow.integration.config.AbstractIT;
import com.autoflow.integration.utils.TestUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Ordem de Serviço - Consulta de status")
class OrdemServicoStatusIT extends AbstractIT {

    private String adminToken;
    private String titularToken;
    private String outroClienteToken;
    private String mecanicoToken;
    private String numeroOs;
    private Long mecanicoId;
    private Long servicoId;

    @BeforeEach
    void configurar() {
        limparBancoDeDados();

        adminToken = registrarELogar(TestUtils.EMAIL_ADMIN, TestUtils.CPF_ATENDENTE, "ADMIN");
        mecanicoToken = registrarELogar(TestUtils.EMAIL_MECANICO, TestUtils.CPF_MECANICO, "MECANICO");
        titularToken = registrarELogar(TestUtils.EMAIL_CLIENTE, TestUtils.CPF_CLIENTE, "CLIENTE");
        outroClienteToken = registrarELogar(
                "outro.cliente@autoflow.test", TestUtils.CPF_CLIENTE_2, "CLIENTE");

        mecanicoId = jdbcTemplate.queryForObject(
                "SELECT id FROM usuarios WHERE email = ?",
                Long.class,
                TestUtils.EMAIL_MECANICO);

        JsonNode servico = parseJson(post(
                "/servicos",
                TestUtils.servicoRequest("Troca de óleo", "Serviço de teste", 200.00),
                adminToken).getBody());
        servicoId = servico.get("id").asLong();

        JsonNode ordemServico = parseJson(post(
                "/ordens-servico",
                TestUtils.criarOsRequest(TestUtils.CPF_CLIENTE, "STT1234", List.of(servicoId)),
                adminToken).getBody());
        numeroOs = ordemServico.get("numeroOs").asText();

        prepararOsEmExecucao(ordemServico.get("servicos").get(0).get("servicoId").asLong());
    }

    @Test
    @DisplayName("usuário autorizado deve consultar status EM_EXECUCAO com resposta enxuta")
    void deveConsultarStatusEmExecucao() {
        ResponseEntity<String> response = get("/ordens-servico/" + numeroOs + "/status", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertRespostaEnxuta(parseJson(response.getBody()), "EM_EXECUCAO");
    }

    @Test
    @DisplayName("aprovação do orçamento deve registrar a transição para EM_EXECUCAO")
    void aprovacaoDoOrcamentoDeveRegistrarTransicaoNoHistorico() {
        Long ordemServicoId = jdbcTemplate.queryForObject(
                "SELECT id FROM ordem_servico WHERE numero_os = ?",
                Long.class,
                numeroOs);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM ordem_servico_status_historico
                WHERE ordem_servico_id = ? AND status = 'EM_EXECUCAO'
                """, Integer.class, ordemServicoId)).isEqualTo(1);
    }

    @Test
    @DisplayName("usuário autorizado deve consultar status ENTREGUE com resposta enxuta")
    void deveConsultarStatusEntregue() {
        patch("/ordens-servico/" + numeroOs + "/servicos/" + servicoId + "/finalizar", null, mecanicoToken);
        patch("/ordens-servico/" + numeroOs + "/entregar", null, adminToken);

        ResponseEntity<String> response = get("/ordens-servico/" + numeroOs + "/status", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertRespostaEnxuta(parseJson(response.getBody()), "ENTREGUE");
    }

    @Test
    @DisplayName("deve retornar 404 no padrão da consulta de detalhe para OS inexistente")
    void deveRetornar404ParaOsInexistente() {
        ResponseEntity<String> response = get("/ordens-servico/OS-NAO-EXISTE/status", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(parseJson(response.getBody()).get("erro").asText())
                .isEqualTo("Ordem de serviço não encontrada.");
    }

    @Test
    @DisplayName("cliente titular deve consultar o status da própria OS")
    void clienteTitularDeveConsultarStatus() {
        ResponseEntity<String> response = get("/ordens-servico/" + numeroOs + "/status", titularToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertRespostaEnxuta(parseJson(response.getBody()), "EM_EXECUCAO");
    }

    @Test
    @DisplayName("cliente não titular deve receber 403 ao consultar status")
    void clienteNaoTitularDeveReceber403() {
        ResponseEntity<String> response = get("/ordens-servico/" + numeroOs + "/status", outroClienteToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(parseJson(response.getBody()).get("erro").asText())
                .isEqualTo("Você não tem permissão para acessar esta ordem de serviço.");
    }

    @Test
    @DisplayName("detalhamento existente deve preservar seu contrato")
    void devePreservarDetalhamentoExistente() {
        ResponseEntity<String> response = get("/ordens-servico/" + numeroOs, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = parseJson(response.getBody());
        assertThat(json.get("numeroOs").asText()).isEqualTo(numeroOs);
        assertThat(json.get("status").asText()).isEqualTo("EM_EXECUCAO");
        assertThat(json.get("id")).isNotNull();
        assertThat(json.get("cliente")).isNotNull();
        assertThat(json.get("veiculo")).isNotNull();
        assertThat(json.get("servicos").isArray()).isTrue();
    }

    @Test
    @DisplayName("não deve permitir reutilizar veículo de outro cliente na criação da OS")
    void deveBloquearVeiculoDeOutroClienteNaCriacaoDaOs() {
        ResponseEntity<String> response = post(
                "/ordens-servico",
                TestUtils.criarOsRequest(TestUtils.CPF_CLIENTE_2, "STT1234", List.of(servicoId)),
                adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(parseJson(response.getBody()).get("erro").asText())
                .isEqualTo("Placa já cadastrada para outro cliente.");
    }

    private void prepararOsEmExecucao(Long servicoSolicitadoId) {
        patch("/ordens-servico/" + numeroOs + "/mecanico",
                TestUtils.incluirMecanicoRequest(mecanicoId, TestUtils.EMAIL_MECANICO),
                adminToken);
        patch("/ordens-servico/" + numeroOs + "/diagnostico/iniciar", null, mecanicoToken);
        patch("/ordens-servico/" + numeroOs + "/diagnostico/laudo",
                TestUtils.registrarLaudoRequest("Laudo de teste"),
                mecanicoToken);
        JsonNode diagnosticoFinalizado = parseJson(patch(
                "/ordens-servico/" + numeroOs + "/diagnostico/finalizar",
                null,
                mecanicoToken).getBody());
        Long orcamentoId = diagnosticoFinalizado.get("orcamentoId").asLong();
        post("/orcamentos/" + orcamentoId + "/aprovar", null, titularToken);
        patch("/ordens-servico/" + numeroOs + "/servicos/" + servicoSolicitadoId + "/iniciar",
                null,
                mecanicoToken);
    }

    private void assertRespostaEnxuta(JsonNode json, String status) {
        List<String> campos = new ArrayList<>();
        json.fieldNames().forEachRemaining(campos::add);

        assertThat(campos).containsExactlyInAnyOrder("numeroOs", "status", "ultimaAtualizacao");
        assertThat(json.get("numeroOs").asText()).isEqualTo(numeroOs);
        assertThat(json.get("status").asText()).isEqualTo(status);
        assertThat(json.get("ultimaAtualizacao").asText()).isNotBlank();
        assertThat(json.has("id")).isFalse();
        assertThat(json.has("cliente")).isFalse();
        assertThat(json.has("veiculo")).isFalse();
        assertThat(json.has("servicos")).isFalse();
        assertThat(json.has("orcamentoAtual")).isFalse();
        assertThat(json.has("diagnostico")).isFalse();
    }
}
