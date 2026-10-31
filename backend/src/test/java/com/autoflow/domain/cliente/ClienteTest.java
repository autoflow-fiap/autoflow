package com.autoflow.domain.cliente;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClienteTest {

    @Test
    void deveCriarClienteSemDependenciasDePersistencia() {
        Cliente cliente = Cliente.criar(
                "João Silva", "12345678901", "11999999999", "joao@example.com");

        assertEquals("João Silva", cliente.nome());
        assertEquals("12345678901", cliente.cpfCnpj());
        assertEquals("joao@example.com", cliente.email());
        assertEquals(null, cliente.id());
        assertEquals(ClienteStatus.ATIVO, cliente.status());
    }

    @Test
    void deveReconstituirClienteComIdentidade() {
        Cliente cliente = Cliente.reconstituir(
                1L, "João Silva", "12345678901", "11999999999", "joao@example.com", ClienteStatus.INATIVO);

        assertEquals(1L, cliente.id());
        assertEquals(ClienteStatus.INATIVO, cliente.status());
    }

    @Test
    void deveAtualizarDadosPreservandoIdentidade() {
        Cliente cliente = Cliente.reconstituir(
                1L, "João Silva", "12345678901", "11999999999", "joao@example.com");

        Cliente atualizado = cliente.atualizar(
                "Maria Silva", "98765432101", "11888888888", "maria@example.com");

        assertEquals(1L, atualizado.id());
        assertEquals("Maria Silva", atualizado.nome());
        assertEquals("98765432101", atualizado.cpfCnpj());
        assertEquals(ClienteStatus.ATIVO, atualizado.status());
    }

    @Test
    void devePermitirAlterarStatusNosDoisSentidos() {
        Cliente cliente = Cliente.reconstituir(
                1L, "João Silva", "12345678901", "11999999999", "joao@example.com", ClienteStatus.ATIVO);

        assertEquals(ClienteStatus.INATIVO, cliente.alterarStatus(ClienteStatus.INATIVO).status());
        assertEquals(ClienteStatus.ATIVO, cliente.alterarStatus(ClienteStatus.ATIVO).status());
    }

    @Test
    void deveRejeitarStatusNuloNaReconstituicao() {
        assertThrows(IllegalArgumentException.class, () -> Cliente.reconstituir(
                1L, "João Silva", "12345678901", "11999999999", "joao@example.com", null));
    }

    @Test
    void deveRejeitarNomeObrigatorioAusente() {
        assertThrows(IllegalArgumentException.class,
                () -> Cliente.criar(" ", "12345678901", null, "joao@example.com"));
    }

    @Test
    void deveRejeitarDocumentoObrigatorioAusente() {
        assertThrows(IllegalArgumentException.class,
                () -> Cliente.criar("João", "", null, "joao@example.com"));
    }

    @Test
    void deveRejeitarEmailObrigatorioAusente() {
        assertThrows(IllegalArgumentException.class,
                () -> Cliente.criar("João", "12345678901", null, null));
    }
}
