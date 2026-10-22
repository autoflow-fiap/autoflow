package com.autoflow.infrastructure.persistence.adapters;

import com.autoflow.application.gateway.UsuarioGateway;
import com.autoflow.domain.usuario.RoleEnum;
import com.autoflow.domain.usuario.Usuario;
import com.autoflow.infrastructure.persistence.entity.usuario.UsuarioEntity;
import com.autoflow.infrastructure.persistence.mapper.UsuarioPersistenceMapper;
import com.autoflow.infrastructure.persistence.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UsuarioRepositoryAdapter implements UsuarioGateway {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioPersistenceMapper usuarioPersistenceMapper;

    @Override
    public List<Usuario> findAll() {
        return usuarioRepository.findAll().stream()
                .map(usuarioPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public List<Usuario> findByRole(RoleEnum roleEnum) {
        return usuarioRepository.findByRole(roleEnum).stream()
                .map(usuarioPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Usuario> findById(Long mecanicoId) {
        return usuarioRepository.findById(mecanicoId)
                .map(usuarioPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Usuario> findByEmail(String email) {
        return usuarioRepository.findByEmail(email)
                .map(usuarioPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Usuario> findByCpfCnpj(String cpfCnpj) {
        return usuarioRepository.findByCpfCnpj(cpfCnpj)
                .map(usuarioPersistenceMapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return usuarioRepository.existsByEmail(email);
    }

    @Override
    public Usuario save(Usuario usuario) {
        UsuarioEntity entity = usuarioPersistenceMapper.toEntity(usuario);
        return usuarioPersistenceMapper.toDomain(usuarioRepository.save(entity));
    }
}
