package com.autoflow.application.gateway;

import com.autoflow.domain.usuario.RoleEnum;
import com.autoflow.domain.usuario.Usuario;

import java.util.List;
import java.util.Optional;

public interface UsuarioGateway {

    List<Usuario> findAll();

    List<Usuario> findByRole(RoleEnum roleEnum);

    Optional<Usuario> findById(Long mecanicoId);

    Optional<Usuario> findByEmail(String email);

    Optional<Usuario> findByCpfCnpj(String cpfCnpj);

    boolean existsByEmail(String email);

    Usuario save(Usuario usuario);

}
