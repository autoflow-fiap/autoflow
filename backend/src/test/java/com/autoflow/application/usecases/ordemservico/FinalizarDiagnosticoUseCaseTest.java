package com.autoflow.application.usecases.ordemservico;

import com.autoflow.application.gateway.*;
import com.autoflow.application.output.orcamento.OrcamentoPublicacao;
import com.autoflow.application.policy.OrdemServicoAccessPolicy;
import com.autoflow.application.usecases.orcamento.OrcamentoFactory;
import com.autoflow.domain.orcamento.ClienteOrcamentoSnapshot;
import com.autoflow.domain.orcamento.Orcamento;
import com.autoflow.domain.orcamento.TipoOrcamento;
import com.autoflow.domain.ordemservico.Diagnostico;
import com.autoflow.domain.ordemservico.OrdemServico;
import com.autoflow.domain.ordemservico.StatusOrdemServico;
import com.autoflow.domain.usuario.RoleEnum;
import com.autoflow.domain.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinalizarDiagnosticoUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-18T15:30:00Z"), ZoneOffset.UTC);

    @Mock private OrdemServicoGateway ordemServicoGateway;
    @Mock private UsuarioGateway usuarioGateway;
    @Mock private OrdemServicoAccessPolicy accessPolicy;
    @Mock private OrcamentoVersioningGateway versioningGateway;
    @Mock private OrcamentoFactory orcamentoFactory;
    @Mock private OrcamentoGateway orcamentoGateway;
    @Mock private OrcamentoPublicacaoGateway publicacaoGateway;
    @Mock private OrcamentoNotificacaoGateway notificacaoGateway;
    @Mock private RegistrarHistoricoStatusOsService registrarHistoricoStatusOs;

    @Test
    void deveFinalizarDiagnosticoSemValidarPermissaoParaAdmin() {
        var os = ordemEmDiagnostico();
        var admin = usuario(RoleEnum.ADMIN);
        configurarFluxo(os, admin);

        var resultado = novoCasoDeUso().execute("OS-1", admin.getEmail());

        assertEquals(StatusOrdemServico.AGUARDANDO_APROVACAO, resultado.ordemServico().getStatus());
        verify(accessPolicy, never()).validarPodeAlterarDiagnostico(any(), any());
    }

    @Test
    void deveValidarPermissaoAoFinalizarDiagnosticoComoMecanico() {
        var os = ordemEmDiagnostico();
        var mecanico = usuario(RoleEnum.MECANICO);
        configurarFluxo(os, mecanico);

        novoCasoDeUso().execute("OS-1", mecanico.getEmail());

        verify(accessPolicy).validarPodeAlterarDiagnostico(os, mecanico);
    }

    private FinalizarDiagnosticoUseCaseImpl novoCasoDeUso() {
        return new FinalizarDiagnosticoUseCaseImpl(
                ordemServicoGateway, usuarioGateway, accessPolicy, versioningGateway,
                orcamentoFactory, orcamentoGateway, publicacaoGateway,
                notificacaoGateway, registrarHistoricoStatusOs, CLOCK
        );
    }

    private void configurarFluxo(OrdemServico os, Usuario usuario) {
        var orcamento = new Orcamento();
        orcamento.setId(10L);
        orcamento.setCliente(new ClienteOrcamentoSnapshot("Cliente", "123", "cliente@autoflow.com", null));
        when(ordemServicoGateway.findByNumeroOs("OS-1")).thenReturn(Optional.of(os));
        when(usuarioGateway.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(versioningGateway.proximaVersaoPorNumeroOs("OS-1", TipoOrcamento.PRINCIPAL)).thenReturn(1);
        when(orcamentoFactory.criarPrincipalDisponivel(eq(os), eq(1), any(LocalDateTime.class)))
                .thenReturn(orcamento);
        when(orcamentoGateway.save(orcamento)).thenReturn(orcamento);
        when(publicacaoGateway.publicarComLinks(10L))
                .thenReturn(new OrcamentoPublicacao("https://autoflow.test/orcamento", "https://autoflow.test/decisao"));
        when(ordemServicoGateway.save(os)).thenReturn(os);
    }

    private OrdemServico ordemEmDiagnostico() {
        var os = new OrdemServico();
        os.setId(1L);
        os.setNumeroOs("OS-1");
        os.setStatus(StatusOrdemServico.EM_DIAGNOSTICO);
        var diagnostico = new Diagnostico();
        diagnostico.setLaudo("Laudo concluido");
        os.setDiagnostico(diagnostico);
        return os;
    }

    private Usuario usuario(RoleEnum role) {
        var usuario = new Usuario();
        usuario.setEmail(role == RoleEnum.ADMIN ? "admin@autoflow.com" : "mecanico@autoflow.com");
        usuario.setCpfCnpj(role == RoleEnum.ADMIN ? "52998224725" : "12345678909");
        usuario.setRole(role);
        return usuario;
    }
}
