package com.autoflow.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeedDemoDocumentsIT {

    private static final Map<String, String> DEMO_DOCUMENTS = Map.ofEntries(
            Map.entry("admin@autoflow.com", "52998224725"),
            Map.entry("atendente@autoflow.com", "16899535009"),
            Map.entry("mecanico1@autoflow.com", "93541134780"),
            Map.entry("mecanico2@autoflow.com", "15350946056"),
            Map.entry("cliente@autoflow.com", "11144477735"),
            Map.entry("mecanico3@autoflow.com", "58273619419"),
            Map.entry("atendente2@autoflow.com", "69427183519"),
            Map.entry("ana@exemplo.com", "80657439193"),
            Map.entry("pedro@exemplo.com", "74185296355"),
            Map.entry("contato@xyz.com.br", "80657439120452"),
            Map.entry("fernanda@exemplo.com", "63920481704")
    );
    private static final Map<String, String> DEMO_ROLES = Map.ofEntries(
            Map.entry("admin@autoflow.com", "ADMIN"),
            Map.entry("atendente@autoflow.com", "ATENDENTE"),
            Map.entry("mecanico1@autoflow.com", "MECANICO"),
            Map.entry("mecanico2@autoflow.com", "MECANICO"),
            Map.entry("cliente@autoflow.com", "CLIENTE"),
            Map.entry("mecanico3@autoflow.com", "MECANICO"),
            Map.entry("atendente2@autoflow.com", "ATENDENTE"),
            Map.entry("ana@exemplo.com", "CLIENTE"),
            Map.entry("pedro@exemplo.com", "CLIENTE"),
            Map.entry("contato@xyz.com.br", "CLIENTE"),
            Map.entry("fernanda@exemplo.com", "CLIENTE")
    );

    @Test
    void flywayAppliesV49AndV50OnceAndKeepsDemoDocumentsConsistentForAuthentication() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")) {
            postgres.start();

            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .target("49")
                    .load()
                    .migrate();

            Map<String, UserState> staffBeforeV50 = new HashMap<>();
            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("""
                         SELECT email, id, role, senha
                         FROM usuarios
                         WHERE email IN ('mecanico3@autoflow.com', 'atendente2@autoflow.com')
                         """)) {
                while (result.next()) {
                    staffBeforeV50.put(result.getString("email"), new UserState(
                            result.getLong("id"), result.getString("role"), result.getString("senha")));
                }
            }

            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement statement = connection.createStatement()) {
                try (ResultSet result = statement.executeQuery("""
                        SELECT count(*)
                        FROM usuarios u
                        JOIN clientes c ON c.usuario_id = u.id AND c.cpf_cnpj = u.cpf_cnpj
                        WHERE (u.email, u.cpf_cnpj) IN (
                            ('ana@exemplo.com', '80657439193'),
                            ('pedro@exemplo.com', '74185296355'),
                            ('contato@xyz.com.br', '80657439120452'),
                            ('fernanda@exemplo.com', '63920481704')
                        )
                        """)) {
                    assertTrue(result.next());
                    assertEquals(4, result.getInt(1));
                }

                try (ResultSet result = statement.executeQuery("""
                        SELECT count(*)
                        FROM ordem_servico os
                        JOIN clientes c ON c.id = os.cliente_id
                        WHERE c.email IN (
                            'ana@exemplo.com', 'pedro@exemplo.com',
                            'contato@xyz.com.br', 'fernanda@exemplo.com'
                        )
                          AND os.cliente_cpf_cnpj IS DISTINCT FROM c.cpf_cnpj
                        """)) {
                    assertTrue(result.next());
                    assertEquals(0, result.getInt(1));
                }

                try (ResultSet result = statement.executeQuery("""
                        SELECT count(DISTINCT c.email)
                        FROM ordem_servico os
                        JOIN clientes c ON c.id = os.cliente_id
                        WHERE c.email IN (
                            'ana@exemplo.com', 'pedro@exemplo.com',
                            'contato@xyz.com.br', 'fernanda@exemplo.com'
                        )
                        """)) {
                    assertTrue(result.next());
                    assertEquals(4, result.getInt(1));
                }

                try (ResultSet result = statement.executeQuery("""
                        SELECT count(*) FROM flyway_schema_history
                        WHERE version = '49' AND success = true
                        """)) {
                    assertTrue(result.next());
                    assertEquals(1, result.getInt(1));
                }

                try (ResultSet result = statement.executeQuery("""
                        SELECT count(*) FROM flyway_schema_history
                        WHERE version = '50' AND success = true
                        """)) {
                    assertTrue(result.next());
                    assertEquals(1, result.getInt(1));
                }

                try (ResultSet result = statement.executeQuery("""
                        SELECT email, role, cpf_cnpj, senha = crypt('Senha@1234', senha) AS senha_valida,
                               count(*) OVER (PARTITION BY cpf_cnpj) AS ocorrencias_documento
                        FROM usuarios
                        WHERE email IN (
                            'admin@autoflow.com', 'atendente@autoflow.com',
                            'mecanico1@autoflow.com', 'mecanico2@autoflow.com',
                            'cliente@autoflow.com', 'mecanico3@autoflow.com',
                            'atendente2@autoflow.com', 'ana@exemplo.com',
                            'pedro@exemplo.com', 'contato@xyz.com.br',
                            'fernanda@exemplo.com'
                        )
                        """)) {
                    int count = 0;
                    while (result.next()) {
                        String email = result.getString("email");
                        String document = result.getString("cpf_cnpj");
                        assertEquals(DEMO_DOCUMENTS.get(email), document, email);
                        assertEquals(DEMO_ROLES.get(email), result.getString("role"), email);
                        assertTrue(validDocument(document), "Documento inválido: " + email);
                        assertTrue(result.getBoolean("senha_valida"), "Senha seed alterada: " + email);
                        assertEquals(1, result.getInt("ocorrencias_documento"), "Documento duplicado: " + email);
                        count++;
                    }
                    assertEquals(DEMO_DOCUMENTS.size(), count);
                }

                try (ResultSet result = statement.executeQuery("""
                        SELECT u.email, u.id, u.role, u.senha, c.id AS cliente_id
                        FROM usuarios u
                        LEFT JOIN clientes c
                          ON regexp_replace(c.cpf_cnpj, '[^0-9]', '', 'g') =
                             regexp_replace(u.cpf_cnpj, '[^0-9]', '', 'g')
                        WHERE u.email IN ('mecanico3@autoflow.com', 'atendente2@autoflow.com')
                        """)) {
                    int count = 0;
                    while (result.next()) {
                        String email = result.getString("email");
                        assertEquals(DEMO_ROLES.get(email), result.getString("role"));
                        assertTrue(result.getObject("cliente_id") == null,
                                "Usuário de staff não deve ter cliente associado");
                        assertEquals(staffBeforeV50.get(email), new UserState(
                                result.getLong("id"), result.getString("role"), result.getString("senha")),
                                "V50 deve preservar ID, papel e senha");
                        count++;
                    }
                    assertEquals(2, count);
                }

                Flyway.configure()
                        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                        .locations("classpath:db/migration")
                        .load()
                        .migrate();

                try (ResultSet result = statement.executeQuery("""
                        SELECT count(*) FROM flyway_schema_history
                        WHERE version IN ('49', '50') AND success = true
                        """)) {
                    assertTrue(result.next());
                    assertEquals(2, result.getInt(1));
                }
            }
        }
    }

    private static boolean validDocument(String document) {
        if (document == null || !document.matches("\\d+") || document.chars().distinct().count() == 1) {
            return false;
        }
        if (document.length() == 11) {
            String first = calculateDigit(document.substring(0, 9), new int[]{10, 9, 8, 7, 6, 5, 4, 3, 2});
            String second = calculateDigit(document.substring(0, 10), new int[]{11, 10, 9, 8, 7, 6, 5, 4, 3, 2});
            return document.endsWith(first + second);
        }
        if (document.length() == 14) {
            String first = calculateDigit(document.substring(0, 12), new int[]{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
            String second = calculateDigit(document.substring(0, 13), new int[]{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
            return document.endsWith(first + second);
        }
        return false;
    }

    private static String calculateDigit(String base, int[] weights) {
        int total = 0;
        for (int i = 0; i < weights.length; i++) {
            total += Character.digit(base.charAt(i), 10) * weights[i];
        }
        int remainder = total % 11;
        return Integer.toString(remainder < 2 ? 0 : 11 - remainder);
    }

    private record UserState(long id, String role, String passwordHash) {}
}
