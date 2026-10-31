package com.autoflow.infrastructure.persistence.adapters;

import com.autoflow.application.exception.ClienteNaoEncontradoException;
import com.autoflow.application.gateway.ClienteGateway;
import com.autoflow.application.input.cliente.ClienteInput;
import com.autoflow.application.output.cliente.ClienteOutput;
import com.autoflow.domain.cliente.ClienteStatus;
import com.autoflow.infrastructure.persistence.entity.cliente.ClienteEntity;
import com.autoflow.infrastructure.persistence.entity.usuario.UsuarioEntity;
import com.autoflow.infrastructure.persistence.mapper.ClienteMapper;
import com.autoflow.infrastructure.persistence.repository.ClienteRepository;
import com.autoflow.infrastructure.persistence.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ClienteRepositoryAdapter implements ClienteGateway {

    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteMapper clienteMapper;

    @Override
    public ClienteOutput save(ClienteInput input) {
        ClienteEntity cliente = clienteMapper.mapToEntity(input);
        associarUsuario(input, cliente);
        return clienteMapper.mapToOutput(clienteRepository.save(cliente));
    }

    @Override
    @Transactional
    public ClienteOutput update(Long id, ClienteInput input) {
        ClienteEntity cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ClienteNaoEncontradoException("Cliente não encontrado com o ID: " + id));
        clienteMapper.updateEntity(input, cliente);
        if (cliente.getUsuario() != null) {
            cliente.getUsuario().setNome(input.nome());
            cliente.getUsuario().setEmail(input.email());
            usuarioRepository.save(cliente.getUsuario());
        }
        return clienteMapper.mapToOutput(clienteRepository.save(cliente));
    }

    @Override
    @Transactional
    public ClienteOutput updateStatus(Long id, ClienteStatus status) {
        ClienteEntity cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ClienteNaoEncontradoException("Cliente não encontrado com o ID: " + id));
        cliente.setStatus(status);
        return clienteMapper.mapToOutput(clienteRepository.save(cliente));
    }

    @Override
    public Optional<ClienteOutput> findById(Long id) {
        return clienteRepository.findById(id).map(clienteMapper::mapToOutput);
    }

    @Override
    public Optional<ClienteOutput> findByCpfCnpj(String cpfCnpj) {
        return clienteRepository.findByCpfCnpj(cpfCnpj).map(clienteMapper::mapToOutput);
    }

    @Override
    public boolean existsByCpfCnpj(String cpfCnpj) {
        return clienteRepository.existsByCpfCnpj(cpfCnpj);
    }

    @Override
    public boolean existsByCpfCnpjAndIdNot(String cpfCnpj, Long id) {
        return clienteRepository.existsByCpfCnpjAndIdNot(cpfCnpj, id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClienteOutput> findByUsuarioEmail(String usuarioEmail) {
        return clienteRepository.findByUsuarioEmail(usuarioEmail).map(clienteMapper::mapToOutput);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClienteOutput> findByUsuarioCpfCnpj(String usuarioCpfCnpj) {
        return clienteRepository.findByUsuarioCpfCnpj(usuarioCpfCnpj).map(clienteMapper::mapToOutput);
    }

    @Override
    public List<ClienteOutput> findAll() {
        return clienteMapper.mapToListOutput(clienteRepository.findAll());
    }

    @Override
    public void deleteById(Long id) {
        clienteRepository.deleteById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return clienteRepository.existsById(id);
    }

    private void associarUsuario(ClienteInput input, ClienteEntity cliente) {
        if (input.usuarioId() == null) {
            return;
        }

        UsuarioEntity usuario = usuarioRepository.findById(input.usuarioId())
                .orElseThrow(() -> new IllegalStateException("Usuário não encontrado durante o cadastro do cliente"));
        cliente.setUsuario(usuario);
    }
}
