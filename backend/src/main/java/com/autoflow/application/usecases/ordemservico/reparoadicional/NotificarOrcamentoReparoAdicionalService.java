package com.autoflow.application.usecases.ordemservico.reparoadicional;

import com.autoflow.application.gateway.OrcamentoNotificacaoGateway;
import com.autoflow.application.input.notificacao.OrcamentoNotificacao;
import com.autoflow.application.output.orcamento.OrcamentoPublicacao;
import com.autoflow.domain.orcamento.Orcamento;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificarOrcamentoReparoAdicionalService {

    private final OrcamentoNotificacaoGateway gateway;

    public NotificarOrcamentoReparoAdicionalService(OrcamentoNotificacaoGateway gateway) {
        this.gateway = gateway;
    }

    public void tentarNotificar(Orcamento orcamento, OrcamentoPublicacao publicacao) {
        try {
            var cliente = orcamento.getCliente();
            gateway.notificar(new OrcamentoNotificacao(orcamento.getId(), orcamento.getTipo(), orcamento.getNumeroOs(),
                    cliente.getNome(), cliente.getEmail(), publicacao.urlPdf(), publicacao.urlDecisao()));
        } catch (Exception exception) {
            log.error("Falha ao notificar cliente sobre orçamento complementar", exception);
        }
    }
}
