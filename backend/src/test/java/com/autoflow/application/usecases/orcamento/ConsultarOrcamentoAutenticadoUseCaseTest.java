package com.autoflow.application.usecases.orcamento;

import com.autoflow.application.exception.ApplicationException;
import com.autoflow.application.gateway.OrcamentoGateway;
import com.autoflow.application.gateway.UsuarioGateway;
import com.autoflow.domain.orcamento.ClienteOrcamentoSnapshot;
import com.autoflow.domain.orcamento.Orcamento;
import com.autoflow.domain.usuario.RoleEnum;
import com.autoflow.domain.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultarOrcamentoAutenticadoUseCaseTest {

    @Mock
    private OrcamentoGateway orcamentoGateway;

    @Mock
    private UsuarioGateway usuarioGateway;

    @InjectMocks
    private ConsultarOrcamentoAutenticadoUseCaseImpl useCase;

    @Test
    void devePermitirClienteConsultarSeuProprioOrcamento() {
        var orcamento = orcamentoDoCliente("cliente@autoflow.com");
        var usuario = usuario(RoleEnum.CLIENTE, "cliente@autoflow.com");
        when(orcamentoGateway.findById(1L)).thenReturn(Optional.of(orcamento));
        when(usuarioGateway.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));

        assertSame(orcamento, useCase.execute(1L, usuario.getEmail()));
    }

    @Test
    void deveNegarClienteConsultarOrcamentoDeOutroCliente() {
        var orcamento = orcamentoDoCliente("outro@autoflow.com");
        var usuario = usuario(RoleEnum.CLIENTE, "cliente@autoflow.com");
        var email = usuario.getEmail();
        when(orcamentoGateway.findById(1L)).thenReturn(Optional.of(orcamento));
        when(usuarioGateway.findByEmail(email)).thenReturn(Optional.of(usuario));

        var exception = assertThrows(ApplicationException.class,
            () -> useCase.execute(1L, email));

        assertEquals(ApplicationException.ErrorType.FORBIDDEN, exception.type());
    }

    @Test
    void devePermitirEquipeConsultarOrcamentoDeQualquerCliente() {
        var orcamento = orcamentoDoCliente("cliente@autoflow.com");
        var usuario = usuario(RoleEnum.ATENDENTE, "atendente@autoflow.com");
        when(orcamentoGateway.findById(1L)).thenReturn(Optional.of(orcamento));
        when(usuarioGateway.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));

        assertSame(orcamento, useCase.execute(1L, usuario.getEmail()));
    }

    @Test
    void deveRetornar404QuandoOrcamentoNaoExiste() {
        when(orcamentoGateway.findById(1L)).thenReturn(Optional.empty());

        var exception = assertThrows(ApplicationException.class,
                () -> useCase.execute(1L, "cliente@autoflow.com"));

        assertEquals(ApplicationException.ErrorType.NOT_FOUND, exception.type());
    }

    private Orcamento orcamentoDoCliente(String email) {
        var orcamento = new Orcamento();
        orcamento.setCliente(ClienteOrcamentoSnapshot.builder().email(email).build());
        return orcamento;
    }

    private Usuario usuario(RoleEnum role, String email) {
        var usuario = new Usuario();
        usuario.setRole(role);
        usuario.setEmail(email);
        usuario.setCpfCnpj("12345678901");
        return usuario;
    }
}
