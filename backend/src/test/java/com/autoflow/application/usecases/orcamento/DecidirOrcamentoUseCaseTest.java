package com.autoflow.application.usecases.orcamento;

import com.autoflow.application.exception.ApplicationException;
import com.autoflow.application.gateway.OrcamentoGateway;
import com.autoflow.application.gateway.OrcamentoPublicacaoGateway;
import com.autoflow.application.gateway.UsuarioGateway;
import com.autoflow.application.port.in.orcamento.AprovarOrcamentoUseCase;
import com.autoflow.application.port.in.orcamento.RecusarOrcamentoUseCase;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DecidirOrcamentoUseCaseTest {

    @Mock private OrcamentoGateway orcamentoGateway;
    @Mock
    private OrcamentoPublicacaoGateway publicacaoGateway;
    @Mock private UsuarioGateway usuarioGateway;
    @Mock private AprovarOrcamentoUseCase aprovarOrcamentoUseCase;
    @Mock private RecusarOrcamentoUseCase recusarOrcamentoUseCase;
    @InjectMocks private DecidirOrcamentoUseCaseImpl useCase;

    @Test
    void deveAprovarAposAutorizarUsuario() {
        Orcamento orcamento = orcamento();
        when(orcamentoGateway.findByIdForUpdate(10L)).thenReturn(Optional.of(orcamento));
        when(usuarioGateway.findByEmail("cliente@exemplo.com")).thenReturn(Optional.of(usuario("Maria", RoleEnum.CLIENTE)));
        when(aprovarOrcamentoUseCase.execute(orcamento, "Maria")).thenReturn(orcamento);

        assertSame(orcamento, useCase.aprovarComoUsuario(10L, "cliente@exemplo.com"));

        verify(aprovarOrcamentoUseCase).execute(orcamento, "Maria");
    }

    @Test
    void deveRecusarAposAutorizarUsuario() {
        Orcamento orcamento = orcamento();
        when(orcamentoGateway.findByIdForUpdate(10L)).thenReturn(Optional.of(orcamento));
        when(usuarioGateway.findByEmail("cliente@exemplo.com")).thenReturn(Optional.of(usuario("Maria", RoleEnum.CLIENTE)));
        when(recusarOrcamentoUseCase.execute(orcamento, "Não quero", "Maria")).thenReturn(orcamento);

        assertSame(orcamento, useCase.recusarComoUsuario(10L, "Não quero", "cliente@exemplo.com"));

        verify(recusarOrcamentoUseCase).execute(orcamento, "Não quero", "Maria");
    }

    @Test
    void deveNegarDecisaoDeClienteSobreOrcamentoDeOutroCliente() {
        Orcamento orcamento = orcamento();
        when(orcamentoGateway.findByIdForUpdate(10L)).thenReturn(Optional.of(orcamento));
        when(usuarioGateway.findByEmail("outro@exemplo.com")).thenReturn(Optional.of(usuario("Outro", RoleEnum.CLIENTE)));

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> useCase.aprovarComoUsuario(10L, "outro@exemplo.com"));

        assertEquals(ApplicationException.ErrorType.FORBIDDEN, exception.type());
    }

    @Test
    void deveNegarDecisaoDeMecanicoMesmoQuandoOrcamentoExiste() {
        Orcamento orcamento = orcamento();
        when(orcamentoGateway.findByIdForUpdate(10L)).thenReturn(Optional.of(orcamento));
        when(usuarioGateway.findByEmail("mecanico@exemplo.com"))
                .thenReturn(Optional.of(usuario("Mecânico", RoleEnum.MECANICO)));

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> useCase.aprovarComoUsuario(10L, "mecanico@exemplo.com"));

        assertEquals(ApplicationException.ErrorType.FORBIDDEN, exception.type());
    }

    @Test
    void deveAprovarPorAcompanhamentoComNomeDoCliente() {
        Orcamento orcamento = orcamento();
        when(orcamentoGateway.findByIdForUpdate(10L)).thenReturn(Optional.of(orcamento));
        when(aprovarOrcamentoUseCase.execute(orcamento, "Cliente")).thenReturn(orcamento);

        assertSame(orcamento, useCase.aprovarDaOrdem(10L, "OS-123"));

        verify(aprovarOrcamentoUseCase).execute(orcamento, "Cliente");
    }

    @Test
    void deveAprovarComTokenPublicoERegistrarNomeInformado() {
        Orcamento orcamento = orcamento();
        when(orcamentoGateway.findByIdForUpdate(10L)).thenReturn(Optional.of(orcamento));
        when(publicacaoGateway.validarToken(orcamento, "token-valido")).thenReturn(true);
        when(aprovarOrcamentoUseCase.execute(orcamento, "Maria")).thenReturn(orcamento);

        assertSame(orcamento, useCase.aprovarComoToken(10L, "token-valido", "Maria"));

        verify(aprovarOrcamentoUseCase).execute(orcamento, "Maria");
    }

    @Test
    void deveRecusarComTokenPublicoSemNomeUsandoNomeDoCliente() {
        Orcamento orcamento = orcamento();
        when(orcamentoGateway.findByIdForUpdate(10L)).thenReturn(Optional.of(orcamento));
        when(publicacaoGateway.validarToken(orcamento, "token-valido")).thenReturn(true);
        when(recusarOrcamentoUseCase.execute(orcamento, "Muito caro", "Cliente")).thenReturn(orcamento);

        assertSame(orcamento, useCase.recusarComoToken(10L, "token-valido", "Muito caro", null));

        verify(recusarOrcamentoUseCase).execute(orcamento, "Muito caro", "Cliente");
    }

    @Test
    void deveRejeitarDecisaoPublicaQuandoTokenForInvalido() {
        Orcamento orcamento = orcamento();
        when(orcamentoGateway.findByIdForUpdate(10L)).thenReturn(Optional.of(orcamento));
        when(publicacaoGateway.validarToken(orcamento, "token-invalido")).thenReturn(false);

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> useCase.aprovarComoToken(10L, "token-invalido", null));

        assertEquals(ApplicationException.ErrorType.UNAUTHORIZED, exception.type());
        verifyNoInteractions(aprovarOrcamentoUseCase, recusarOrcamentoUseCase);
    }

    private Orcamento orcamento() {
        Orcamento orcamento = new Orcamento();
        orcamento.setId(10L);
        orcamento.setNumeroOs("OS-123");
        orcamento.setCliente(ClienteOrcamentoSnapshot.builder().nome("Cliente").email("cliente@exemplo.com").build());
        return orcamento;
    }

    private Usuario usuario(String nome, RoleEnum role) {
        Usuario usuario = new Usuario();
        usuario.setNome(nome);
        usuario.setRole(role);
        usuario.setEmail(switch (nome) {
            case "Maria" -> "cliente@exemplo.com";
            case "Outro" -> "outro@exemplo.com";
            default -> "mecanico@exemplo.com";
        });
        return usuario;
    }
}
