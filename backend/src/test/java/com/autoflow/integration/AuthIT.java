package com.autoflow.integration;

import com.autoflow.integration.config.AbstractIT;
import com.autoflow.integration.utils.TestUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Auth - Testes de Integração")
class AuthIT extends AbstractIT {

    @BeforeEach
    void limpar() {
        limparBancoDeDados();
    }

    @Test
    @DisplayName("deve registrar usuário com sucesso e retornar 201")
    void deveRegistrarUsuario() {
        Map<String, Object> request = TestUtils.registroRequest(
                "João Silva", TestUtils.EMAIL_CLIENTE, TestUtils.CPF_CLIENTE, "CLIENTE");

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/cadastro", jsonEntity(request), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = parseJson(response.getBody());
        assertThat(body.get("email").asText()).isEqualTo(TestUtils.EMAIL_CLIENTE);
        assertThat(body.get("role").asText()).isEqualTo("CLIENTE");

    }

    @Test
    @DisplayName("deve autenticar rota protegida com subject CPF/CNPJ")
    void deveAutenticarComCpfCnpjNoSubject() {
        restTemplate.postForEntity("/auth/cadastro", jsonEntity(
                TestUtils.registroRequest("Maria", TestUtils.EMAIL_CLIENTE, TestUtils.CPF_CLIENTE, "CLIENTE")
        ), String.class);

        String token = gerarToken(TestUtils.CPF_CLIENTE, "CLIENTE");
        ResponseEntity<String> response = get("/clientes/me", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = parseJson(response.getBody());
        assertThat(body.get("email").asText()).isEqualTo(TestUtils.EMAIL_CLIENTE);
    }

    @ParameterizedTest(name = "deve manter login por CPF/CNPJ para o perfil {0}")
    @CsvSource({
            "ADMIN,admin.login@autoflow.test,52998224725",
            "ATENDENTE,atendente.login@autoflow.test,16899535009",
            "MECANICO,mecanico.login@autoflow.test,12345678909"
    })
    void deveManterLoginPorCpfCnpjESenha(
            String role,
            String email,
            String cpfCnpj
    ) {
        jdbcTemplate.update(
                "INSERT INTO usuarios (nome, cpf_cnpj, email, senha, role) VALUES (?, ?, ?, ?, ?)",
                "Usuario Interno", cpfCnpj, email, passwordEncoder.encode(TestUtils.SENHA_PADRAO), role
        );

        ResponseEntity<String> login = restTemplate.postForEntity(
                "/auth/login",
                jsonEntity(Map.of("cpfCnpj", cpfCnpj, "senha", TestUtils.SENHA_PADRAO)),
                String.class
        );

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = parseJson(login.getBody()).get("token").asText();
        assertThat(token).isNotBlank();

        ResponseEntity<String> protectedResponse = get("/auth/mecanicos", token);
        if ("MECANICO".equals(role)) {
            assertThat(protectedResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        } else {
            assertThat(protectedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    @DisplayName("não deve confiar no perfil informado pelo JWT serverless")
    void naoDeveConfiarNoRoleDoTokenServerless() {
        restTemplate.postForEntity("/auth/cadastro", jsonEntity(
                TestUtils.registroRequest("Maria", TestUtils.EMAIL_CLIENTE, TestUtils.CPF_CLIENTE, "CLIENTE")
        ), String.class);

        String tokenComRoleAdmin = gerarToken(TestUtils.CPF_CLIENTE, "ADMIN");

        assertThat(get("/clientes/me", tokenComRoleAdmin).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(get("/auth/usuarios", tokenComRoleAdmin).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("deve rejeitar token legado com email no subject")
    void deveRejeitarTokenLegadoComEmailNoSubject() {
        String token = gerarToken("inexistente@test.com", "CLIENTE");

        ResponseEntity<String> response = get("/clientes/me", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("deve retornar 409 ao registrar email duplicado")
    void deveRetornar409ComEmailDuplicado() {
        Map<String, Object> request = TestUtils.registroRequest(
                "Pedro", TestUtils.EMAIL_CLIENTE, TestUtils.CPF_ATENDENTE, "CLIENTE");
        restTemplate.postForEntity("/auth/cadastro", jsonEntity(request), String.class);

        Map<String, Object> duplicado = TestUtils.registroRequest(
                "Pedro 2", TestUtils.EMAIL_CLIENTE, TestUtils.CPF_MECANICO, "CLIENTE");
        ResponseEntity<String> response = restTemplate.postForEntity("/auth/cadastro", jsonEntity(duplicado), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("deve retornar 400 com senha fraca")
    void deveRetornar400ComSenhaFraca() {
        Map<String, Object> request = Map.of(
                "nome", "Ana",
                "email", TestUtils.emailUnico(),
                "cpfCnpj", TestUtils.CPF_ATENDENTE,
                "telefone", "11999999999",
                "senha", "senha123",
                "role", "CLIENTE"
        );

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/cadastro", jsonEntity(request), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("deve retornar 403 ao acessar endpoint protegido sem JWT (Spring Security sem AuthenticationEntryPoint customizado)")
    void deveRetornar403SemJwt() {
        ResponseEntity<String> response = restTemplate.getForEntity("/clientes", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("deve retornar 403 ao usar token JWT inválido")
    void deveRetornar403ComTokenInvalido() {
        ResponseEntity<String> response = get("/clientes", "token.invalido.aqui");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("deve retornar 403 ao usar token JWT malformado")
    void deveRetornar403ComTokenMalformado() {
        ResponseEntity<String> response = get("/clientes", "Bearer invalido");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("admin deve listar todos os usuários cadastrados")
    void adminDeveListarUsuarios() {
        String adminToken = registrarELogar(TestUtils.EMAIL_ADMIN, TestUtils.CPF_ATENDENTE, "ADMIN");
        registrarELogar(TestUtils.EMAIL_MECANICO, TestUtils.CPF_MECANICO, "MECANICO");
        registrarELogar(TestUtils.EMAIL_CLIENTE, TestUtils.CPF_CLIENTE, "CLIENTE");

        ResponseEntity<String> response = get("/auth/usuarios", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = parseJson(response.getBody());
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("não-admin não deve listar usuários")
    void mecanicoNaoDeveListarUsuarios() {
        String mecanicoToken = registrarELogar(TestUtils.EMAIL_MECANICO, TestUtils.CPF_MECANICO, "MECANICO");

        ResponseEntity<String> response = get("/auth/usuarios", mecanicoToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("admin deve listar apenas mecânicos")
    void adminDeveListarMecanicos() {
        String adminToken = registrarELogar(TestUtils.EMAIL_ADMIN, TestUtils.CPF_ATENDENTE, "ADMIN");
        registrarELogar(TestUtils.EMAIL_MECANICO, TestUtils.CPF_MECANICO, "MECANICO");
        registrarELogar(TestUtils.EMAIL_CLIENTE, TestUtils.CPF_CLIENTE, "CLIENTE");

        ResponseEntity<String> response = get("/auth/mecanicos", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = parseJson(response.getBody());
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isGreaterThanOrEqualTo(1);
        body.forEach(u -> assertThat(u.get("role").asText()).isEqualTo("MECANICO"));
    }

    @Test
    @DisplayName("cliente não deve listar mecânicos")
    void clienteNaoDeveListarMecanicos() {
        String clienteToken = registrarELogar(TestUtils.EMAIL_CLIENTE, TestUtils.CPF_CLIENTE, "CLIENTE");

        ResponseEntity<String> response = get("/auth/mecanicos", clienteToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
