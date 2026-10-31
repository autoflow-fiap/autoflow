package com.autoflow.application.usecases.ordemservico;

import com.autoflow.application.exception.ApplicationException;
import com.autoflow.application.gateway.CurrentUserGateway;
import com.autoflow.application.gateway.OrdemServicoGateway;
import com.autoflow.application.gateway.VeiculoClienteGateway;
import com.autoflow.application.output.ordemservico.StatusOrdemServicoOutput;
import com.autoflow.application.port.in.ordemservico.ConsultarStatusOrdemServicoUseCase;
import com.autoflow.domain.ordemservico.OrdemServico;
import com.autoflow.domain.usuario.RoleEnum;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ConsultarStatusOrdemServicoUseCaseImpl implements ConsultarStatusOrdemServicoUseCase {

    private final OrdemServicoGateway ordemServicoGateway;
    private final CurrentUserGateway currentUserGateway;
    private final VeiculoClienteGateway clienteGateway;

    @Override
        public StatusOrdemServicoOutput execute(String numeroOs, String emailUsuarioAutenticado) {
        OrdemServico ordemServico = ordemServicoGateway.findByNumeroOs(numeroOs)
                .orElseThrow(() -> ApplicationException.notFound(
                        "Ordem de serviço não encontrada."
                ));

        var currentUser = currentUserGateway.getCurrentUser()
                .orElseThrow(() -> ApplicationException.unauthorized(
                        "Usuário não autenticado"
                ));

        if (RoleEnum.CLIENTE.equals(currentUser.role())) {
            validarTitularidade(ordemServico, emailUsuarioAutenticado);
        }

        return new StatusOrdemServicoOutput(
                ordemServico.getNumeroOs(),
                ordemServico.getStatus(),
                ordemServico.getUltimaAtualizacao());
    }

        private void validarTitularidade(OrdemServico ordemServico, String email) {
                Long clienteId = clienteGateway.findIdByUsuarioEmail(email)
                .orElseThrow(() -> ApplicationException.forbidden(
                        "Você não tem permissão para acessar esta ordem de serviço."
                ));

        if (!clienteId.equals(ordemServico.getClienteId())) {
            throw ApplicationException.forbidden(
                    "Você não tem permissão para acessar esta ordem de serviço."
            );
        }
    }
}
