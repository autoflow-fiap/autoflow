package com.autoflow.application.gateway;

import java.util.Optional;

/**
 * Porta mínima de cliente necessária pelos casos de uso de veículo.
 */
public interface VeiculoClienteGateway {

    Optional<Long> findIdByCpfCnpj(String cpfCnpj);

    Optional<Long> findIdByUsuarioEmail(String email);

}
