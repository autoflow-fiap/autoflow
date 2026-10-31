package com.autoflow.application.gateway;

import com.autoflow.application.input.cliente.ClienteInput;
import com.autoflow.application.output.cliente.ClienteOutput;
import com.autoflow.domain.cliente.ClienteStatus;

import java.util.List;
import java.util.Optional;


public interface ClienteGateway {

    ClienteOutput save(ClienteInput input);

    ClienteOutput update(Long id, ClienteInput input);

    ClienteOutput updateStatus(Long id, ClienteStatus status);

    Optional<ClienteOutput> findById(Long id);


    Optional<ClienteOutput> findByCpfCnpj(String cpfCnpj);

    boolean existsByCpfCnpj(String cpfCnpj);

    boolean existsByCpfCnpjAndIdNot(String cpfCnpj, Long id);

    Optional<ClienteOutput> findByUsuarioEmail(String usuarioEmail);

    Optional<ClienteOutput> findByUsuarioCpfCnpj(String usuarioCpfCnpj);


    List<ClienteOutput> findAll();


    void deleteById(Long id);


    boolean existsById(Long id);
}
