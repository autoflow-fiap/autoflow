package com.autoflow.application.security;

import com.autoflow.application.gateway.CurrentUserGateway;
import com.autoflow.application.output.security.CurrentUser;
import com.autoflow.domain.usuario.RoleEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioAutenticadoServiceTest {

    @Mock
    private CurrentUserGateway currentUserGateway;

    private UsuarioAutenticadoService service;

    @BeforeEach
    void setUp() {
        service = new UsuarioAutenticadoService(currentUserGateway);
    }

    @Test
    void deveRetornarEmailDoUsuarioAutenticado() {
        when(currentUserGateway.getCurrentUser())
                .thenReturn(Optional.of(new CurrentUser("cliente@email.com", RoleEnum.CLIENTE)));

        assertEquals("cliente@email.com", service.getEmail());
    }

    @Test
    void deveRetornarTrueQuandoUsuarioForCliente() {
        when(currentUserGateway.getCurrentUser())
                .thenReturn(Optional.of(new CurrentUser("cliente@email.com", RoleEnum.CLIENTE)));

        assertTrue(service.isCliente());
    }

    @Test
    void deveRetornarFalseQuandoUsuarioNaoForCliente() {
        when(currentUserGateway.getCurrentUser())
                .thenReturn(Optional.of(new CurrentUser("admin@email.com", RoleEnum.ADMIN)));

        assertFalse(service.isCliente());
    }

    @Test
    void deveRetornarTrueQuandoUsuarioForAdministrador() {
        when(currentUserGateway.getCurrentUser())
                .thenReturn(Optional.of(new CurrentUser("admin@email.com", RoleEnum.ADMIN)));

        assertTrue(service.isAdministrador());
    }

    @Test
    void deveRetornarFalseQuandoUsuarioNaoForAdministrador() {
        when(currentUserGateway.getCurrentUser())
                .thenReturn(Optional.of(new CurrentUser("cliente@email.com", RoleEnum.CLIENTE)));

        assertFalse(service.isAdministrador());
    }
}
