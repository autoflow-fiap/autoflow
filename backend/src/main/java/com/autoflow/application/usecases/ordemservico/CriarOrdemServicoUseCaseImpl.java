package com.autoflow.application.usecases.ordemservico;

import com.autoflow.application.exception.ApplicationException;
import com.autoflow.application.gateway.NumeroOrdemServicoGateway;
import com.autoflow.application.gateway.OrdemServicoGateway;
import com.autoflow.application.gateway.ServicoGateway;
import com.autoflow.application.input.ordemservico.CriarOrdemServicoCommand;
import com.autoflow.application.mapper.ServicoApplicationMapper;
import com.autoflow.application.output.cliente.ClienteOutput;
import com.autoflow.application.output.ordemservico.OrdemServicoCriadaOutput;
import com.autoflow.application.output.ordemservico.acompanhamento.TokenAcompanhamentoOutput;
import com.autoflow.application.output.servico.ServicoOutput;
import com.autoflow.application.output.veiculo.VeiculoOutput;
import com.autoflow.application.port.in.cliente.BuscarClientePorCpfCnpjUseCase;
import com.autoflow.application.port.in.ordemservico.CriarOrdemServicoUseCase;
import com.autoflow.application.port.in.ordemservico.acompanhamento.EnviarLinkAcompanhamentoUseCase;
import com.autoflow.application.port.in.ordemservico.acompanhamento.GerarTokenAcompanhamentoUseCase;
import com.autoflow.application.port.in.veiculo.BuscarOuCadastrarVeiculoUseCase;
import com.autoflow.application.transaction.TransactionalUseCase;
import com.autoflow.domain.cliente.Cliente;
import com.autoflow.domain.ordemservico.OrdemServico;
import com.autoflow.domain.ordemservico.ServicoSolicitado;
import com.autoflow.domain.veiculo.Veiculo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j

@RequiredArgsConstructor
public class CriarOrdemServicoUseCaseImpl implements CriarOrdemServicoUseCase {

    private final BuscarClientePorCpfCnpjUseCase buscarCliente;
    private final BuscarOuCadastrarVeiculoUseCase buscarOuCadastrarVeiculo;
    private final ServicoGateway servicoGateway;
    private final OrdemServicoGateway ordemServicoGateway;
    private final RegistrarHistoricoStatusOsService registrarHistoricoStatusOs;
    private final GerarTokenAcompanhamentoUseCase gerarToken;
    private final EnviarLinkAcompanhamentoUseCase enviarLink;
    private final NumeroOrdemServicoGateway numeroOrdemServicoGateway;
    private final Clock clock;

    @TransactionalUseCase
    @Override
    public OrdemServicoCriadaOutput execute(CriarOrdemServicoCommand command) {
        validarCommand(command);
        ClienteOutput cliente = buscarCliente.execute(command.cpfCnpj());
        VeiculoOutput veiculo = buscarOuCadastrarVeiculo.execute(cliente.id(), command.veiculo());
        LocalDateTime agora = LocalDateTime.now(clock);
        OrdemServico os = OrdemServico.criar(
                Cliente.reconstituir(cliente.id(), cliente.nome(), cliente.cpfCnpj(), cliente.telefone(), cliente.email(), cliente.status()),
                new Veiculo(veiculo.id(), veiculo.placa(), veiculo.marca(), veiculo.modelo(), veiculo.ano()),
                numeroOrdemServicoGateway.gerar(), agora);
        os.adicionarServicosSolicitados(command.servicoIds().stream()
                .map(this::preencherServico).toList());
        OrdemServico salva = ordemServicoGateway.save(os);
        registrarHistoricoStatusOs.registrar(salva);
        TokenAcompanhamentoOutput token = gerarToken.execute(salva.getId());
        try {
            enviarLink.execute(salva, token.token());
        } catch (RuntimeException exception) {
            log.error("Não foi possível enviar o link de acompanhamento da ordem de serviço", exception);
        }
        return new OrdemServicoCriadaOutput(salva, token.token());
    }

    private ServicoSolicitado preencherServico(Long servicoId) {
        ServicoOutput servico = servicoGateway.findById(servicoId)
                .map(ServicoApplicationMapper::toOutput)
                .orElseThrow(() -> ApplicationException.notFound(
                        "Serviço não encontrado com o ID: " + servicoId));
        return ServicoSolicitado.criar(servico.getId(), servico.getNome(), servico.getValor());
    }

    private void validarCommand(CriarOrdemServicoCommand command) {
        if (command == null || command.veiculo() == null) {
            throw new IllegalArgumentException("Comando de criação da ordem de serviço é obrigatório.");
        }
        List<Long> servicos = command.servicoIds();
        if (servicos == null || servicos.isEmpty() || servicos.stream().anyMatch(id -> id == null)) {
            throw new IllegalArgumentException("A ordem de servico deve ter ao menos um servico solicitado.");
        }
    }
}
