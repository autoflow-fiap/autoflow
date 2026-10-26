package com.autoflow.application.usecases.ordemservico;

import com.autoflow.application.gateway.OrcamentoNotificacaoGateway;
import com.autoflow.application.input.notificacao.OrcamentoNotificacao;
import com.autoflow.application.output.orcamento.OrcamentoPublicacao;
import com.autoflow.domain.orcamento.ClienteOrcamentoSnapshot;
import com.autoflow.domain.orcamento.Orcamento;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificarOrcamentoDiagnosticoServiceTest {

    @Test
    void enviaNotificacaoComDadosDoOrcamentoEPublicacao() {
        OrcamentoNotificacaoGateway gateway = mock(OrcamentoNotificacaoGateway.class);
        Orcamento orcamento = orcamentoComCliente();
        OrcamentoPublicacao publicacao = new OrcamentoPublicacao("https://example.test/orcamento.pdf",
                "https://example.test/decisao");

        new NotificarOrcamentoDiagnosticoService(gateway).tentarNotificar(orcamento, publicacao);

        verify(gateway).notificar(any(OrcamentoNotificacao.class));
    }

    @Test
    void absorveFalhaDoGatewayParaNaoInterromperOFluxoDeDiagnostico() {
        OrcamentoNotificacaoGateway gateway = mock(OrcamentoNotificacaoGateway.class);
        doThrow(new IllegalStateException("detalhe privado")).when(gateway).notificar(any());

        new NotificarOrcamentoDiagnosticoService(gateway).tentarNotificar(
                orcamentoComCliente(), new OrcamentoPublicacao("pdf", "decisao"));

        verify(gateway).notificar(any(OrcamentoNotificacao.class));
    }

    private Orcamento orcamentoComCliente() {
        ClienteOrcamentoSnapshot cliente = mock(ClienteOrcamentoSnapshot.class);
        when(cliente.getNome()).thenReturn("Cliente de teste");
        when(cliente.getEmail()).thenReturn("cliente@example.test");
        Orcamento orcamento = mock(Orcamento.class);
        when(orcamento.getCliente()).thenReturn(cliente);
        when(orcamento.getId()).thenReturn(10L);
        when(orcamento.getNumeroOs()).thenReturn("OS-DE-TESTE");
        return orcamento;
    }
}
