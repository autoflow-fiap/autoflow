package com.autoflow.integration.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.autoflow.infrastructure.security.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIT {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean
    @SuppressWarnings("unused")
    private JavaMailSender mailSender;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtService jwtService;

    protected void limparBancoDeDados() {
        jdbcTemplate.execute("""
            DO $$ DECLARE r RECORD;
            BEGIN
              FOR r IN (SELECT tablename FROM pg_tables
                        WHERE schemaname = 'public'
                          AND tablename != 'flyway_schema_history')
              LOOP
                EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || ' RESTART IDENTITY CASCADE';
              END LOOP;
            END $$;
        """);
    }

    protected String registrarELogar(String email, String cpfCnpj, String role) {
        if ("CLIENTE".equals(role)) {
            Map<String, Object> registro = Map.of(
                    "nome", "Usuario Teste",
                    "email", email,
                    "cpfCnpj", cpfCnpj,
                    "telefone", "11999999999",
                    "senha", "Senha@1234",
                    "role", role
            );
            restTemplate.postForEntity("/auth/cadastro", jsonEntity(registro), String.class);
        } else {
            // /auth/cadastro restringe cadastro público a CLIENTE; inserimos staff diretamente
            jdbcTemplate.update(
                    "INSERT INTO usuarios (nome, cpf_cnpj, email, senha, role) VALUES (?, ?, ?, ?, ?)",
                    "Usuario Teste", cpfCnpj, email, passwordEncoder.encode("Senha@1234"), role
            );
        }

        return gerarToken(cpfCnpj, role);
    }

    protected String gerarToken(String cpfCnpj, String role) {
        return jwtService.gerarToken(cpfCnpj, role);
    }

    protected HttpEntity<Object> jsonEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    protected HttpEntity<Object> authEntity(String token) {
        return authEntity(null, token);
    }

    protected HttpEntity<Object> authEntity(Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    protected String extrairCampo(String json, String campo) {
        try {
            return objectMapper.readTree(json).get(campo).asText();
        } catch (Exception e) {
            throw new RuntimeException("Campo '" + campo + "' não encontrado em: " + json, e);
        }
    }

    protected JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("JSON inválido: " + json, e);
        }
    }

    protected ResponseEntity<String> get(String url, String token) {
        return restTemplate.exchange(url, HttpMethod.GET, authEntity(token), String.class);
    }

    protected ResponseEntity<String> post(String url, Object body, String token) {
        return restTemplate.exchange(url, HttpMethod.POST, authEntity(body, token), String.class);
    }

    protected ResponseEntity<String> patch(String url, Object body, String token) {
        return restTemplate.exchange(url, HttpMethod.PATCH, authEntity(body, token), String.class);
    }

    protected ResponseEntity<String> delete(String url, String token) {
        return restTemplate.exchange(url, HttpMethod.DELETE, authEntity(token), String.class);
    }
}