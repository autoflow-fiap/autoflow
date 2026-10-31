package com.autoflow.infrastructure.persistence.adapters;

import com.autoflow.application.gateway.VeiculoClienteGateway;
import com.autoflow.infrastructure.persistence.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class VeiculoClienteRepositoryAdapter implements VeiculoClienteGateway {

    private final ClienteRepository clienteRepository;

    @Override
    public Optional<Long> findIdByCpfCnpj(String cpfCnpj) {
        return clienteRepository.findByCpfCnpj(cpfCnpj).map(cliente -> cliente.getId());
    }

    @Override
    public Optional<Long> findIdByUsuarioEmail(String email) {
        return clienteRepository.findByUsuarioEmail(email).map(cliente -> cliente.getId());
    }

}
