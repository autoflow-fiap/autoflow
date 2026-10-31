package com.autoflow.integration.utils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TestUtils {

    // CPFs matematicamente válidos para testes
    public static final String CPF_ATENDENTE   = "52998224725";
    public static final String CPF_ATENDENTE_2 = "16899535009";
    public static final String CPF_MECANICO    = "12345678909";
    public static final String CPF_CLIENTE     = "11144477735";
    public static final String CPF_CLIENTE_2   = "98765432100";
    public static final String CPF_INVALIDO    = "11111111111";

    public static final String EMAIL_ATENDENTE = "atendente@autoflow.test";
    public static final String EMAIL_MECANICO  = "mecanico@autoflow.test";
    public static final String EMAIL_CLIENTE   = "cliente@autoflow.test";
    public static final String EMAIL_ADMIN     = "admin@autoflow.test";
    public static final String SENHA_PADRAO    = "Senha@1234";

    private TestUtils() {}

    public static Map<String, Object> registroRequest(String nome, String email, String cpfCnpj, String role) {
        return Map.of(
                "nome", nome,
                "email", email,
                "cpfCnpj", cpfCnpj,
                "telefone", "11999999999",
                "senha", SENHA_PADRAO,
                "role", role
        );
    }

    public static Map<String, Object> clienteRequest(String nome, String cpfCnpj, String email) {
        return Map.of(
                "nome", nome,
                "cpfCnpj", cpfCnpj,
                "telefone", "11988887777",
                "email", email
        );
    }

    public static Map<String, Object> veiculoRequest(String cpfCnpj, String placa, String marca, String modelo, int ano) {
        return Map.of(
                "cpfCnpj", cpfCnpj,
                "placa", placa,
                "marca", marca,
                "modelo", modelo,
                "ano", ano
        );
    }

    public static Map<String, Object> veiculoUpdateRequest(String placa, String marca, String modelo, int ano) {
        return Map.of(
                "placa", placa,
                "marca", marca,
                "modelo", modelo,
                "ano", ano
        );
    }

    public static Map<String, Object> servicoRequest(String nome, String descricao, double valor) {
        return Map.of("nome", nome, "descricao", descricao, "valor", valor);
    }

    public static Map<String, Object> pecaRequest(String nome, int quantidade, double valor, String tipo) {
        return Map.of("nome", nome, "quantidade", quantidade, "valor", valor, "tipo", tipo);
    }

    public static Map<String, Object> criarOsRequest(String cpfCnpj, String placa, List<Long> servicoIds) {
        List<Map<String, Long>> servicos = servicoIds.stream()
                .map(id -> Map.of("servicoId", id))
                .toList();
        return Map.of(
                "cpfCnpj", cpfCnpj,
                "veiculo", Map.of("placa", placa, "marca", "Toyota", "modelo", "Corolla", "ano", 2020),
                "servicosSolicitados", servicos
        );
    }

    public static Map<String, Object> incluirMecanicoRequest(Long mecanicoId, String mecanicoEmail) {
        return Map.of("mecanicoId", mecanicoId, "mecanicoEmail", mecanicoEmail);
    }

    public static Map<String, Object> registrarLaudoRequest(String laudo) {
        return Map.of("laudo", laudo);
    }

    public static List<Map<String, Object>> itensNecessariosRequest(Long pecaId, int quantidade) {
        return List.of(Map.of("pecaInsumoId", pecaId, "quantidade", quantidade));
    }

    public static Map<String, Object> recusarOrcamentoRequest(String motivo) {
        return Map.of("motivo", motivo);
    }

    public static String placaUnica() {
        String uuid = UUID.randomUUID().toString().replaceAll("[^A-Z0-9]", "").toUpperCase();
        return "TST" + uuid.substring(0, 4);
    }

    public static String emailUnico() {
        return "user." + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    }
}