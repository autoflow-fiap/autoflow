package com.autoflow.application.usecases.orcamento;

import com.autoflow.application.exception.ApplicationException;
import com.autoflow.application.gateway.OrcamentoGateway;
import com.autoflow.application.gateway.UsuarioGateway;
import com.autoflow.application.input.orcamento.OrcamentoFiltro;
import com.autoflow.domain.usuario.RoleEnum;
import com.autoflow.domain.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsultarOrcamentosUseCaseTest {
    @Mock OrcamentoGateway orcamentoGateway;
    @Mock UsuarioGateway usuarioGateway;
    @InjectMocks ConsultarOrcamentosUseCaseImpl useCase;

    @Test
    void deveForcarEmailDoClienteNoFiltro() {
        Usuario usuario = usuario("cliente@exemplo.com", RoleEnum.CLIENTE);
        OrcamentoFiltro esperado = new OrcamentoFiltro(null, null, null, "cliente@exemplo.com", null, null);
        when(usuarioGateway.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(orcamentoGateway.findAll(esperado)).thenReturn(List.of());

        assertTrue(useCase.execute(usuario.getEmail(), null).isEmpty());
        verify(orcamentoGateway).findAll(esperado);
    }

    @Test
    void deveNegarFiltroDeOutroCliente() {
        Usuario usuario = usuario("cliente@exemplo.com", RoleEnum.CLIENTE);
        var email = usuario.getEmail();
        when(usuarioGateway.findByEmail(email)).thenReturn(Optional.of(usuario));
        OrcamentoFiltro filtro = new OrcamentoFiltro(null, null, null, "outro@exemplo.com", null, null);

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> useCase.execute(email, filtro));
        assertEquals(ApplicationException.ErrorType.FORBIDDEN, exception.type());
        verifyNoInteractions(orcamentoGateway);
    }

    private Usuario usuario(String email, RoleEnum role) {
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setCpfCnpj("12345678901");
        usuario.setRole(role);
        return usuario;
    }
}
