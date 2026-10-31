package com.autoflow.infrastructure.persistence.repository;


import com.autoflow.infrastructure.persistence.entity.cliente.ClienteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<ClienteEntity, Long> {
    Optional<ClienteEntity> findByCpfCnpj(String cpfCnpj);

    boolean existsByCpfCnpj(String cpfCnpj);

    boolean existsByCpfCnpjAndIdNot(String cpfCnpj, Long id);

    Optional<ClienteEntity> findByUsuarioEmail(String usuarioEmail);

    Optional<ClienteEntity> findByUsuarioCpfCnpj(String usuarioCpfCnpj);
}
