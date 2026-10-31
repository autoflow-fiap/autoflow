package com.autoflow.integration;

import com.autoflow.integration.config.AbstractIT;
import com.autoflow.integration.utils.TestUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Veículo - Testes de Integração")
class VeiculoIT extends AbstractIT {

    private String adminToken;
    private String clienteToken;
    private String placa;

    @BeforeEach
    void configurar() {
        limparBancoDeDados();

        adminToken = registrarELogar(TestUtils.EMAIL_ADMIN, TestUtils.CPF_ATENDENTE, "ADMIN");

        // cliente com usuário vinculado
        clienteToken = registrarELogar(TestUtils.EMAIL_CLIENTE, TestUtils.CPF_CLIENTE, "CLIENTE");

        post("/clientes", TestUtils.clienteRequest("Dono do Carro", TestUtils.CPF_CLIENTE, TestUtils.EMAIL_CLIENTE), adminToken);

        placa = TestUtils.placaUnica();
    }

    private JsonNode content(ResponseEntity<String> response) {
        return parseJson(response.getBody()).get("content");
    }

    // ── cadastrar ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deve cadastrar veículo com sucesso")
    void deveCadastrarVeiculo() {
        var body = TestUtils.veiculoRequest(TestUtils.CPF_CLIENTE, placa, "Honda", "Civic", 2022);

        ResponseEntity<String> response = post("/veiculos", body, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode json = parseJson(response.getBody());
        assertThat(json.get("placa").asText()).isEqualTo(placa);
        assertThat(json.get("marca").asText()).isEqualTo("Honda");
    }

    @Test
    @DisplayName("deve retornar 409 ao cadastrar placa duplicada")
    void deveRetornar409PlacaDuplicada() {
        post("/veiculos", TestUtils.veiculoRequest(TestUtils.CPF_CLIENTE, placa, "Hyundai", "HB20", 2020), adminToken);

        ResponseEntity<String> response = post("/veiculos",
                TestUtils.veiculoRequest(TestUtils.CPF_CLIENTE, placa, "Kia", "Sportage", 2021), adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("deve retornar 404 ao cadastrar veículo para cliente inexistente")
    void deveRetornar404ClienteInexistente() {
        Map<String, Object> body = Map.of(
                "cpfCnpj", "98765432100",
                "placa", placa,
                "marca", "BMW",
                "modelo", "X5",
                "ano", 2023
        );

        ResponseEntity<String> response = post("/veiculos", body, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("deve retornar 400 com formato de placa inválido")
    void deveRetornar400PlacaInvalida() {
        var body = TestUtils.veiculoRequest(TestUtils.CPF_CLIENTE, "PLACA-INVALIDA", "Toyota", "Corolla", 2022);

        ResponseEntity<String> response = post("/veiculos", body, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── listar ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deve listar todos os veículos")
    void deveListarVeiculos() {
        post("/veiculos", TestUtils.veiculoRequest(TestUtils.CPF_CLIENTE, placa, "Ford", "Ka", 2019), adminToken);

        ResponseEntity<String> response = get("/veiculos", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = content(response);
        assertThat(json.isArray()).isTrue();
        assertThat(json.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("deve buscar veículo por ID")
    void deveBuscarVeiculo() {
        ResponseEntity<String> criado = post("/veiculos",
                TestUtils.veiculoRequest(TestUtils.CPF_CLIENTE, placa, "VW", "Gol", 2018), adminToken);
        Long veiculoId = parseJson(criado.getBody()).get("id").asLong();

        ResponseEntity<String> response = get("/veiculos/" + veiculoId, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parseJson(response.getBody()).get("id").asLong()).isEqualTo(veiculoId);
    }

    @Test
    @DisplayName("deve retornar 404 para veículo inexistente")
    void deveRetornar404VeiculoInexistente() {
        ResponseEntity<String> response = get("/veiculos/999999", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── filtro dinâmico ───────────────────────────────────────────────────────

    @Test
    @DisplayName("admin deve listar todos os veículos sem filtro")
    void deveListarTodosComoAdmin() {
        post("/veiculos", TestUtils.veiculoRequest(TestUtils.CPF_CLIENTE, placa, "Honda", "Fit", 2021), adminToken);

        ResponseEntity<String> response = get("/veiculos", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(content(response).isArray()).isTrue();
        assertThat(content(response).size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("deve filtrar por placa exata")
    void deveFiltrarPorPlaca() {
        String outraPlaca = TestUtils.placaUnica();
        post("/veiculos", TestUtils.veiculoRequest(TestUtils.CPF_CLIENTE, placa, "Honda", "Fit", 2021), adminToken);
        post("/veiculos", TestUtils.veiculoRequest(TestUtils.CPF_CLIENTE, outraPlaca, "Toyota", "Corolla", 2020), adminToken);

        ResponseEntity<String> response = get("/veiculos?placa=" + placa, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = content(response);
        assertThat(json.size()).isEqualTo(1);
        assertThat(json.get(0).get("placa").asText()).isEqualTo(placa);
    }

    @Test
    @DisplayName("deve filtrar por marca (case insensitive, parcial)")
    void deveFiltrarPorMarca() {
        String outraPlaca = TestUtils.placaUnica();
        post("/veiculos", TestUtils.veiculoRequest(TestUtils.CPF_CLIENTE, placa, "Honda", "Fit", 2021), adminToken);
        post("/veiculos", TestUtils.veiculoRequest(TestUtils.CPF_CLIENTE, outraPlaca, "Toyota", "Corolla", 2020), adminToken);

        ResponseEntity<String> response = get("/veiculos?marca=honda", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = content(response);
        assertThat(json.size()).isEqualTo(1);
        assertThat(json.get(0).get("marca").asText()).isEqualTo("Honda");
    }

    @Test
    @DisplayName("deve filtrar por ano")
    void deveFiltrarPorAno() {
        String outraPlaca = TestUtils.placaUnica();
        post("/veiculos", TestUtils.veiculoRequest(TestUtils.CPF_CLIENTE, placa, "Honda", "Fit", 2021), adminToken);
        post("/veiculos", TestUtils.veiculoRequest(TestUtils.CPF_CLIENTE, outraPlaca, "Toyota", "Corolla", 2019), adminToken);

        ResponseEntity<String> response = get("/veiculos?ano=2021", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = content(response);
        assertThat(json.size()).isEqualTo(1);
        assertThat(json.get(0).get("ano").asInt()).isEqualTo(2021);
    }

    @Test
    @DisplayName("deve retornar lista vazia quando filtro não bate com nenhum veículo")
    void deveRetornarListaVaziaQuandoFiltroSemResultado() {
        post("/veiculos", TestUtils.veiculoRequest(TestUtils.CPF_CLIENTE, placa, "Honda", "Fit", 2021), adminToken);

        ResponseEntity<String> response = get("/veiculos?marca=Lamborghini", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(content(response).size()).isZero();
    }

    // ── isolamento de CLIENTE ─────────────────────────────────────────────────

    @Test
    @DisplayName("cliente deve ver apenas seus próprios veículos na listagem")
    void deveListarApenasVeiculosDoClienteLogado() {
        String cpfCliente2 = "98765432100";
        registrarELogar(TestUtils.emailUnico(), cpfCliente2, "CLIENTE");
        post("/clientes", TestUtils.clienteRequest("Outro Cliente", cpfCliente2, "outro2@test.com"), adminToken);

        String placaOutro = TestUtils.placaUnica();
        post("/veiculos", TestUtils.veiculoRequest(TestUtils.CPF_CLIENTE, placa, "Honda", "Fit", 2021), adminToken);
        post("/veiculos", TestUtils.veiculoRequest(cpfCliente2, placaOutro, "Toyota", "Corolla", 2020), adminToken);

        ResponseEntity<String> response = get("/veiculos", clienteToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = content(response);
        assertThat(json.size()).isEqualTo(1);
        assertThat(json.get(0).get("placa").asText()).isEqualTo(placa);
    }

    @Test
    @DisplayName("cliente deve ver seu próprio veículo por ID")
    void devePermitirClienteVerSeuVeiculoPorId() {
        ResponseEntity<String> criado = post("/veiculos",
                TestUtils.veiculoRequest(TestUtils.CPF_CLIENTE, placa, "Honda", "Fit", 2021), adminToken);
        long veiculoId = parseJson(criado.getBody()).get("id").asLong();

        ResponseEntity<String> response = get("/veiculos/" + veiculoId, clienteToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("cliente não deve ver veículo de outro cliente por ID — deve retornar 403")
    void deveRetornar403QuandoClienteTentarVerVeiculoDeOutro() {
        String cpfCliente2 = "98765432100";
        registrarELogar(TestUtils.emailUnico(), cpfCliente2, "CLIENTE");
        post("/clientes", TestUtils.clienteRequest("Outro Cliente", cpfCliente2, "outro3@test.com"), adminToken);

        String placaOutro = TestUtils.placaUnica();
        ResponseEntity<String> criado = post("/veiculos",
                TestUtils.veiculoRequest(cpfCliente2, placaOutro, "Toyota", "Yaris", 2022), adminToken);
        long veiculoDeOutro = parseJson(criado.getBody()).get("id").asLong();

        ResponseEntity<String> response = get("/veiculos/" + veiculoDeOutro, clienteToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── atualizar ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("admin deve atualizar qualquer veículo com sucesso")
    void deveAtualizarVeiculoComoAdmin() {
        ResponseEntity<String> criado = post("/veiculos",
                TestUtils.veiculoRequest(TestUtils.CPF_CLIENTE, placa, "Fiat", "Uno", 2015), adminToken);
        long veiculoId = parseJson(criado.getBody()).get("id").asLong();

        String novaPlaca = TestUtils.placaUnica();
        var atualizacao = TestUtils.veiculoUpdateRequest(novaPlaca, "Fiat", "Uno Atualizado", 2015);

        ResponseEntity<String> response = patch("/veiculos/" + veiculoId + "/atualizacao", atualizacao, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parseJson(response.getBody()).get("modelo").asText()).isEqualTo("Uno Atualizado");
        assertThat(parseJson(response.getBody()).get("placa").asText()).isEqualTo(novaPlaca);
    }

    @Test
    @DisplayName("cliente deve atualizar seu próprio veículo")
    void devePermitirClienteAtualizarSeuVeiculo() {
        ResponseEntity<String> criado = post("/veiculos",
                TestUtils.veiculoRequest(TestUtils.CPF_CLIENTE, placa, "Chevrolet", "Onix", 2020), adminToken);
        long veiculoId = parseJson(criado.getBody()).get("id").asLong();

        String novaPlaca = TestUtils.placaUnica();
        var atualizacao = TestUtils.veiculoUpdateRequest(novaPlaca, "Chevrolet", "Onix Plus", 2020);

        ResponseEntity<String> response = patch("/veiculos/" + veiculoId + "/atualizacao", atualizacao, clienteToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parseJson(response.getBody()).get("modelo").asText()).isEqualTo("Onix Plus");
    }

    @Test
    @DisplayName("cliente não deve atualizar veículo de outro cliente — deve retornar 403")
    void deveRetornar403QuandoClienteTentarAtualizarVeiculoDeOutroCliente() {
        // segundo cliente
        String cpfCliente2 = "98765432100";
        registrarELogar(TestUtils.emailUnico(), cpfCliente2, "CLIENTE");
        post("/clientes", TestUtils.clienteRequest("Outro Cliente", cpfCliente2, "outro@test.com"), adminToken);

        String placaOutroCliente = TestUtils.placaUnica();
        ResponseEntity<String> criado = post("/veiculos",
                TestUtils.veiculoRequest(cpfCliente2, placaOutroCliente, "Toyota", "Yaris", 2022), adminToken);
        long veiculoDeOutro = parseJson(criado.getBody()).get("id").asLong();

        var tentativa = TestUtils.veiculoUpdateRequest(TestUtils.placaUnica(), "Toyota", "Hackeado", 2022);

        ResponseEntity<String> response = patch("/veiculos/" + veiculoDeOutro + "/atualizacao", tentativa, clienteToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("deve retornar 409 ao atualizar com placa de outro veículo")
    void deveRetornar409AoAtualizarComPlacaDuplicada() {
        String outraPlaca = TestUtils.placaUnica();
        post("/veiculos", TestUtils.veiculoRequest(TestUtils.CPF_CLIENTE, outraPlaca, "Ford", "EcoSport", 2019), adminToken);

        ResponseEntity<String> criado = post("/veiculos",
                TestUtils.veiculoRequest(TestUtils.CPF_CLIENTE, placa, "Honda", "HR-V", 2021), adminToken);
        long veiculoId = parseJson(criado.getBody()).get("id").asLong();

        var atualizacao = TestUtils.veiculoUpdateRequest(outraPlaca, "Honda", "HR-V", 2021);

        ResponseEntity<String> response = patch("/veiculos/" + veiculoId + "/atualizacao", atualizacao, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ── deletar ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deve deletar veículo com sucesso")
    void deveDeletarVeiculo() {
        ResponseEntity<String> criado = post("/veiculos",
                TestUtils.veiculoRequest(TestUtils.CPF_CLIENTE, placa, "Chev", "Onix", 2021), adminToken);
        long veiculoId = parseJson(criado.getBody()).get("id").asLong();

        ResponseEntity<String> response = delete("/veiculos/" + veiculoId, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
