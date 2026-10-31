package com.autoflow.infrastructure.persistence.entity.cliente;

import com.autoflow.domain.usuario.RoleEnum;
import com.autoflow.domain.cliente.ClienteStatus;
import com.autoflow.infrastructure.persistence.entity.usuario.UsuarioEntity;
import com.autoflow.infrastructure.persistence.entity.veiculo.VeiculoEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ClienteEntityTest {

    private ClienteEntity cliente;
    private UsuarioEntity usuario;

    @BeforeEach
    void setUp() {
        usuario = new UsuarioEntity();
        usuario.setId(1L);
        usuario.setNome("João Silva");
        usuario.setEmail("joao@example.com");
        usuario.setRole(RoleEnum.CLIENTE);

        cliente = new ClienteEntity();
        cliente.setId(1L);
        cliente.setNome("João Silva");
        cliente.setCpfCnpj("12345678901");
        cliente.setEmail("joao@example.com");
        cliente.setTelefone("11999999999");
        cliente.setUsuario(usuario);
        cliente.setVeiculos(new ArrayList<>());
    }

    @Test
    void deveArmazenarDadosDoCliente() {
        assertNotNull(cliente);
        assertEquals(1L, cliente.getId());
        assertEquals("João Silva", cliente.getNome());
        assertEquals("12345678901", cliente.getCpfCnpj());
        assertEquals("joao@example.com", cliente.getEmail());
        assertEquals("11999999999", cliente.getTelefone());
        assertEquals(ClienteStatus.ATIVO, cliente.getStatus());
    }

    @Test
    void deveAssociarUsuario() {
        assertNotNull(cliente.getUsuario());
        assertEquals("João Silva", cliente.getUsuario().getNome());
        assertEquals(RoleEnum.CLIENTE, cliente.getUsuario().getRole());
    }

    @Test
    void deveAssociarVeiculo() {
        VeiculoEntity veiculo = new VeiculoEntity();
        veiculo.setId(1L);
        veiculo.setMarca("Toyota");
        veiculo.setPlaca("ABC1234");
        veiculo.setCliente(cliente);

        cliente.getVeiculos().add(veiculo);

        assertTrue(cliente.getVeiculos().contains(veiculo));
        assertEquals("Toyota", cliente.getVeiculos().get(0).getMarca());
    }

    @Test
    void deveAceitarOsDoisStatus() {
        cliente.setStatus(ClienteStatus.INATIVO);
        assertEquals(ClienteStatus.INATIVO, cliente.getStatus());

        cliente.setStatus(ClienteStatus.ATIVO);
        assertEquals(ClienteStatus.ATIVO, cliente.getStatus());
    }
}
