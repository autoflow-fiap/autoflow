package com.autoflow.application.usecases.ordemservico.acompanhamento;

import com.autoflow.application.exception.ApplicationException;
import com.autoflow.application.gateway.HistoricoStatusOsGateway;
import com.autoflow.application.gateway.OrcamentoGateway;
import com.autoflow.application.gateway.OrdemServicoGateway;
import com.autoflow.application.gateway.VeiculoClienteGateway;
import com.autoflow.application.output.ordemservico.acompanhamento.AcompanhamentoOrdemServicoOutput;
import com.autoflow.application.output.ordemservico.acompanhamento.HistoricoStatusOsOutput;
import com.autoflow.application.output.ordemservico.acompanhamento.OrcamentoResumoOutput;
import com.autoflow.application.output.ordemservico.acompanhamento.ServicoSolicitadoOutput;
import com.autoflow.application.port.in.ordemservico.acompanhamento.AcompanharOrdemServicoUseCase;
import com.autoflow.application.usecases.ordemservico.StatusOrdemServicoMensagemPolicy;
import com.autoflow.domain.orcamento.Orcamento;
import com.autoflow.domain.orcamento.StatusOrcamento;
import com.autoflow.domain.ordemservico.HistoricoStatusOs;
import com.autoflow.domain.ordemservico.OrdemServico;
import lombok.RequiredArgsConstructor;

import java.util.List;


@RequiredArgsConstructor
public class AcompanharOrdemServicoUseCaseImpl implements AcompanharOrdemServicoUseCase {

    private final VeiculoClienteGateway clienteGateway;
    private final OrdemServicoGateway ordemServicoGateway;
    private final OrcamentoGateway orcamentoGateway;
    private final HistoricoStatusOsGateway historicoStatusOsGateway;

    @Override
        public List<AcompanhamentoOrdemServicoOutput> execute(String emailCliente) {
                Long clienteId = clienteGateway.findIdByUsuarioEmail(emailCliente)
                .orElseThrow(() -> ApplicationException.notFound(
                        "Cliente autenticado não encontrado."));

        return ordemServicoGateway
                .findByClienteIdOrderByDataAberturaDesc(clienteId)
                .stream()
                .map(this::montarAcompanhamento)
                .toList();
    }

    private AcompanhamentoOrdemServicoOutput montarAcompanhamento(
            OrdemServico ordemServico) {
        Orcamento orcamentoAtual = buscarOrcamentoAtual(ordemServico.getNumeroOs());
        List<HistoricoStatusOs> historico = historicoStatusOsGateway
                .findByNumeroOsOrderByRegistradoEmAsc(ordemServico.getNumeroOs());

        return new AcompanhamentoOrdemServicoOutput(
                ordemServico.getNumeroOs(),
                ordemServico.getVeiculoPlaca(),
                ordemServico.getStatus(),
                ordemServico.getDataAbertura(),
                ordemServico.getUltimaAtualizacao(),
                ordemServico.getServicosSolicitados().stream()
                        .map(ServicoSolicitadoOutput::from)
                        .toList(),
                orcamentoAtual == null ? null : OrcamentoResumoOutput.from(orcamentoAtual),
                orcamentoAtual == null ? null : orcamentoAtual.getStatus(),
                StatusOrdemServicoMensagemPolicy.mensagem(ordemServico.getStatus()),
                historico.stream().map(HistoricoStatusOsOutput::from).toList());
    }

    private Orcamento buscarOrcamentoAtual(String numeroOs) {
        return orcamentoGateway.findByNumeroOsAndStatus(numeroOs, StatusOrcamento.DISPONIVEL)
                .or(() -> orcamentoGateway.findTopByNumeroOsOrderByVersaoDesc(numeroOs))
                .orElse(null);
    }
}
