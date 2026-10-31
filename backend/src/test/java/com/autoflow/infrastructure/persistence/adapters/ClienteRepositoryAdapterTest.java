package com.autoflow.infrastructure.persistence.adapters;

import com.autoflow.application.input.cliente.ClienteInput;
import com.autoflow.application.output.cliente.ClienteOutput;
import com.autoflow.domain.cliente.ClienteStatus;
import com.autoflow.infrastructure.persistence.entity.cliente.ClienteEntity;
import com.autoflow.infrastructure.persistence.entity.usuario.UsuarioEntity;
import com.autoflow.infrastructure.persistence.mapper.ClienteMapper;
import com.autoflow.infrastructure.persistence.repository.ClienteRepository;
import com.autoflow.infrastructure.persistence.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteRepositoryAdapterTest {

    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ClienteMapper clienteMapper;

    @Test
    void deveSalvarClienteSemUsuarioAssociado() {
        ClienteRepositoryAdapter adapter = adapter();
        ClienteInput input = new ClienteInput("Bruno", "12345678901", "11999999999", "bruno@email.com");
        ClienteEntity entity = new ClienteEntity();
        ClienteOutput output = ClienteOutput.builder().id(1L).nome("Bruno").status(ClienteStatus.ATIVO).build();

        when(clienteMapper.mapToEntity(input)).thenReturn(entity);
        when(clienteRepository.save(entity)).thenReturn(entity);
        when(clienteMapper.mapToOutput(entity)).thenReturn(output);

        assertEquals(output, adapter.save(input));
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void deveSalvarClienteComUsuarioAssociado() {
        ClienteRepositoryAdapter adapter = adapter();
        ClienteInput input = new ClienteInput("Bruno", "12345678901", "11999999999", "bruno@email.com", 7L);
        ClienteEntity entity = new ClienteEntity();
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setId(7L);
        ClienteOutput output = ClienteOutput.builder().id(1L).nome("Bruno").status(ClienteStatus.ATIVO).build();

        when(clienteMapper.mapToEntity(input)).thenReturn(entity);
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(usuario));
        when(clienteRepository.save(entity)).thenReturn(entity);
        when(clienteMapper.mapToOutput(entity)).thenReturn(output);

        assertEquals(output, adapter.save(input));
        assertEquals(usuario, entity.getUsuario());
    }

    @Test
    void deveAtualizarClienteEUsuarioAssociado() {
        ClienteRepositoryAdapter adapter = adapter();
        ClienteInput input = new ClienteInput("Novo Nome", "12345678901", "11999999999", "novo@email.com");
        ClienteEntity entity = new ClienteEntity();
        UsuarioEntity usuario = new UsuarioEntity();
        entity.setUsuario(usuario);
        ClienteOutput output = ClienteOutput.builder().id(1L).nome("Novo Nome").status(ClienteStatus.ATIVO).build();

        when(clienteRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(clienteRepository.save(entity)).thenReturn(entity);
        when(clienteMapper.mapToOutput(entity)).thenReturn(output);

        assertEquals(output, adapter.update(1L, input));
        verify(usuarioRepository).save(usuario);
        assertEquals("novo@email.com", usuario.getEmail());
    }

    @Test
    void deveAtualizarClienteSemUsuarioAssociado() {
        ClienteRepositoryAdapter adapter = adapter();
        ClienteInput input = new ClienteInput("Novo Nome", "12345678901", "11999999999", "novo@email.com");
        ClienteEntity entity = new ClienteEntity();
        ClienteOutput output = ClienteOutput.builder().id(1L).nome("Novo Nome").status(ClienteStatus.ATIVO).build();

        when(clienteRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(clienteRepository.save(entity)).thenReturn(entity);
        when(clienteMapper.mapToOutput(entity)).thenReturn(output);

        assertEquals(output, adapter.update(1L, input));
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void deveAtualizarSomenteStatusPreservandoRelacionamentos() {
        ClienteRepositoryAdapter adapter = adapter();
        ClienteEntity entity = new ClienteEntity();
        var veiculos = List.of(new com.autoflow.infrastructure.persistence.entity.veiculo.VeiculoEntity());
        entity.setVeiculos(veiculos);
        ClienteOutput output = ClienteOutput.builder().id(1L).status(ClienteStatus.INATIVO).build();

        when(clienteRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(clienteRepository.save(entity)).thenReturn(entity);
        when(clienteMapper.mapToOutput(entity)).thenReturn(output);

        assertEquals(output, adapter.updateStatus(1L, ClienteStatus.INATIVO));
        assertEquals(ClienteStatus.INATIVO, entity.getStatus());
        assertSame(veiculos, entity.getVeiculos());
        verify(clienteMapper, never()).updateEntity(any(), any());
    }

    @Test
    void deveDelegarConsultasEExclusao() {
        ClienteRepositoryAdapter adapter = adapter();
        ClienteEntity entity = new ClienteEntity();
        ClienteOutput output = ClienteOutput.builder().id(1L).status(ClienteStatus.ATIVO).build();

        when(clienteRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(clienteRepository.findByCpfCnpj("12345678901")).thenReturn(Optional.of(entity));
        when(clienteRepository.findByUsuarioEmail("email")).thenReturn(Optional.of(entity));
        when(clienteRepository.findAll()).thenReturn(List.of(entity));
        when(clienteMapper.mapToOutput(entity)).thenReturn(output);
        when(clienteMapper.mapToListOutput(List.of(entity))).thenReturn(List.of(output));

        assertEquals(Optional.of(output), adapter.findById(1L));
        assertEquals(Optional.of(output), adapter.findByCpfCnpj("12345678901"));
        assertEquals(Optional.of(output), adapter.findByUsuarioEmail("email"));
        assertEquals(List.of(output), adapter.findAll());
        adapter.deleteById(1L);

        verify(clienteRepository).deleteById(1L);
    }

    private ClienteRepositoryAdapter adapter() {
        return new ClienteRepositoryAdapter(clienteRepository, usuarioRepository, clienteMapper);
    }
}
