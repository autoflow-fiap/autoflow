package com.autoflow.infrastructure.persistence.repository;

import com.autoflow.domain.usuario.RoleEnum;
import com.autoflow.infrastructure.persistence.entity.usuario.UsuarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<UsuarioEntity, Long> {

    Optional<UsuarioEntity> findByEmail(String email);
    Optional<UsuarioEntity> findByCpfCnpj(String cpfCnpj);

    boolean existsByEmail(String email);

    List<UsuarioEntity> findByRole(RoleEnum role);
}
