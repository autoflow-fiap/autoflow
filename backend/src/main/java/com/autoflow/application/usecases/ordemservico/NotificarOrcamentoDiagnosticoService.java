package com.autoflow.application.usecases.ordemservico;

import com.autoflow.application.gateway.OrcamentoNotificacaoGateway;
import com.autoflow.application.input.notificacao.OrcamentoNotificacao;
import com.autoflow.application.output.orcamento.OrcamentoPublicacao;
import com.autoflow.domain.orcamento.Orcamento;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificarOrcamentoDiagnosticoService {

    private final OrcamentoNotificacaoGateway notificacaoGateway;

    public NotificarOrcamentoDiagnosticoService(OrcamentoNotificacaoGateway notificacaoGateway) {
        this.notificacaoGateway = notificacaoGateway;
    }

    public void tentarNotificar(Orcamento orcamento, OrcamentoPublicacao publicacao) {
        try {
            var cliente = orcamento.getCliente();
            notificacaoGateway.notificar(new OrcamentoNotificacao(
                    orcamento.getId(), orcamento.getTipo(), orcamento.getNumeroOs(),
                    cliente.getNome(), cliente.getEmail(), publicacao.urlPdf(), publicacao.urlDecisao()));
        } catch (Exception exception) {
            log.error("Falha ao notificar cliente sobre orçamento da ordem de serviço", exception);
        }
    }
}
