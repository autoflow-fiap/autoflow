package com.autoflow.infrastructure.security.service;

import com.autoflow.application.policy.ClienteAtivoPolicy;
import com.autoflow.domain.usuario.RoleEnum;
import com.autoflow.infrastructure.persistence.entity.usuario.UsuarioEntity;
import com.autoflow.infrastructure.persistence.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final ClienteAtivoPolicy clienteAtivoPolicy;

    @Override
    public UserDetails loadUserByUsername(String cpfCnpj)
            throws UsernameNotFoundException {
        return loadUserByCpfCnpj(cpfCnpj);
    }

    public UserDetails loadUserByCpfCnpj(String cpfCnpj)
            throws UsernameNotFoundException {

        return toUserDetails(usuarioRepository.findByCpfCnpj(cpfCnpj)
                .orElseThrow(() ->
                        new UsernameNotFoundException("Usuário não encontrado")));
    }

    private UserDetails toUserDetails(UsuarioEntity usuarioEntity) {

        boolean clienteAtivo = clienteAtivoPolicy.podeAutenticar(
                usuarioEntity.getCpfCnpj(),
                RoleEnum.CLIENTE.equals(usuarioEntity.getRole()));

        return User.withUsername(usuarioEntity.getEmail())
                .password(usuarioEntity.getSenha())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + usuarioEntity.getRole().name())))
                .disabled(!clienteAtivo)
                .build();
    }
}
