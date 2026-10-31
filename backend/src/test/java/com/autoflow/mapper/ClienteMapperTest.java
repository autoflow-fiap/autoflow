package com.autoflow.mapper;

import com.autoflow.application.input.cliente.ClienteInput;
import com.autoflow.application.output.cliente.ClienteOutput;
import com.autoflow.domain.cliente.ClienteStatus;
import com.autoflow.infrastructure.persistence.entity.cliente.ClienteEntity;
import com.autoflow.infrastructure.persistence.mapper.ClienteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ClienteMapperTest {

    private ClienteMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(ClienteMapper.class);
    }

    @Test
    void shouldMapClienteInputToClienteEntity() {
        ClienteInput input = new ClienteInput("Nome Teste", "12345678901", "999999999", "teste@email.com");

        ClienteEntity entity = mapper.mapToEntity(input);

        assertNotNull(entity);
        assertEquals(input.nome(), entity.getNome());
        assertEquals(input.cpfCnpj(), entity.getCpfCnpj());
        assertEquals(input.telefone(), entity.getTelefone());
        assertEquals(input.email(), entity.getEmail());
        assertEquals(ClienteStatus.ATIVO, entity.getStatus());
    }

    @Test
    void shouldMapClienteEntityToClienteOutput() {
        ClienteEntity entity = new ClienteEntity();
        entity.setId(1L);
        entity.setNome("Nome Teste");
        entity.setCpfCnpj("12345678901");
        entity.setTelefone("999999999");
        entity.setEmail("teste@email.com");
        entity.setStatus(ClienteStatus.INATIVO);

        ClienteOutput output = mapper.mapToOutput(entity);

        assertNotNull(output);
        assertEquals(entity.getId(), output.id());
        assertEquals(entity.getNome(), output.nome());
        assertEquals(entity.getCpfCnpj(), output.cpfCnpj());
        assertEquals(entity.getTelefone(), output.telefone());
        assertEquals(entity.getEmail(), output.email());
        assertEquals(ClienteStatus.INATIVO, output.status());
    }

    @Test
    void shouldUpdateClienteEntityFromClienteInput() {
        ClienteInput input = new ClienteInput("Nome Atualizado", "98765432109", "777777777", "atualizado@email.com");
        ClienteEntity entity = new ClienteEntity();
        entity.setId(1L);
        entity.setStatus(ClienteStatus.INATIVO);

        mapper.updateEntity(input, entity);

        assertEquals(1L, entity.getId());
        assertEquals(input.nome(), entity.getNome());
        assertEquals(input.cpfCnpj(), entity.getCpfCnpj());
        assertEquals(input.telefone(), entity.getTelefone());
        assertEquals(input.email(), entity.getEmail());
        assertEquals(ClienteStatus.INATIVO, entity.getStatus());
    }

    @Test
    void shouldMapListOfClienteEntitiesToListOfClienteOutputs() {
        ClienteEntity first = new ClienteEntity();
        first.setId(1L);
        first.setNome("Entity 1");
        ClienteEntity second = new ClienteEntity();
        second.setId(2L);
        second.setNome("Entity 2");

        List<ClienteOutput> outputs = mapper.mapToListOutput(List.of(first, second));

        assertEquals(2, outputs.size());
        assertEquals("Entity 1", outputs.get(0).nome());
        assertEquals("Entity 2", outputs.get(1).nome());
    }
}
