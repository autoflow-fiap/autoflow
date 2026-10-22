package com.autoflow.config.security.service;

import com.autoflow.application.policy.ClienteAtivoPolicy;
import com.autoflow.domain.usuario.RoleEnum;
import com.autoflow.infrastructure.persistence.entity.usuario.UsuarioEntity;
import com.autoflow.infrastructure.persistence.repository.UsuarioRepository;
import com.autoflow.infrastructure.security.service.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ClienteAtivoPolicy clienteAtivoPolicy;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    private UsuarioEntity usuario;

    @BeforeEach
    void setup() {

        usuario = new UsuarioEntity();
        usuario.setId(1L);
        usuario.setEmail("admin@email.com");
        usuario.setCpfCnpj("12345678980");
        usuario.setSenha("senhaCriptografada");
        usuario.setRole(RoleEnum.ADMIN);
    }

    @Test
    void deveCarregarUsuarioPorCpfCnpjParaLoginInterno() {

        when(usuarioRepository.findByCpfCnpj("12345678980"))
                .thenReturn(Optional.of(usuario));

        UserDetails resultado =
                customUserDetailsService.loadUserByUsername(
                        "12345678980"
                );

        assertNotNull(resultado);

        assertEquals(
                    "admin@email.com",
                resultado.getUsername()
        );

        assertEquals(
                "senhaCriptografada",
                resultado.getPassword()
        );

        assertTrue(
                resultado.getAuthorities()
                        .stream()
                        .anyMatch(authority ->
                                authority.getAuthority()
                                        .equals("ROLE_ADMIN"))
        );

        verify(usuarioRepository)
                .findByCpfCnpj("12345678980");
    }

    @Test
    void deveCarregarClienteAtivoHabilitado() {
        usuario.setEmail("cliente@email.com");
        usuario.setCpfCnpj("12345678980");
        usuario.setRole(RoleEnum.CLIENTE);
        when(usuarioRepository.findByCpfCnpj(usuario.getCpfCnpj())).thenReturn(Optional.of(usuario));
        when(clienteAtivoPolicy.podeAutenticar(usuario.getCpfCnpj(), true)).thenReturn(true);

        UserDetails resultado = customUserDetailsService.loadUserByCpfCnpj(usuario.getCpfCnpj());

        assertTrue(resultado.isEnabled());
    }

    @Test
    void deveCarregarClienteInativoDesabilitado() {
        usuario.setEmail("cliente@email.com");
        usuario.setCpfCnpj("12345678980");
        usuario.setRole(RoleEnum.CLIENTE);
        when(usuarioRepository.findByCpfCnpj(usuario.getCpfCnpj())).thenReturn(Optional.of(usuario));
        when(clienteAtivoPolicy.podeAutenticar(usuario.getCpfCnpj(), true)).thenReturn(false);

        UserDetails resultado = customUserDetailsService.loadUserByCpfCnpj(usuario.getCpfCnpj());

        assertFalse(resultado.isEnabled());
    }

    @Test
    void deveLancarExcecaoQuandoUsuarioNaoEncontrado() {

        when(usuarioRepository.findByCpfCnpj("12345678980"))
                .thenReturn(Optional.empty());

        UsernameNotFoundException exception =
                assertThrows(
                        UsernameNotFoundException.class,
                        () -> customUserDetailsService
                                .loadUserByCpfCnpj(
                                        "12345678980"
                                )
                );

        assertEquals(
                "Usuário não encontrado",
                exception.getMessage()
        );

        verify(usuarioRepository)
                .findByCpfCnpj("12345678980");
    }
}
