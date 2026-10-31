package com.autoflow.application.usecases.cliente;

import com.autoflow.application.exception.ClienteNaoEncontradoException;
import com.autoflow.application.gateway.ClienteGateway;
import com.autoflow.application.input.cliente.ClienteStatusInput;
import com.autoflow.application.output.cliente.ClienteOutput;
import com.autoflow.application.port.in.cliente.AlterarStatusClienteUseCase;
import com.autoflow.domain.cliente.Cliente;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AlterarStatusClienteUseCaseImpl implements AlterarStatusClienteUseCase {

    private final ClienteGateway clienteGateway;

    @Override
    public ClienteOutput execute(Long id, ClienteStatusInput input) {
        ClienteOutput atual = clienteGateway.findById(id)
                .orElseThrow(() -> new ClienteNaoEncontradoException(
                        "Cliente não encontrado com o ID: " + id));

        Cliente.reconstituir(
                atual.id(),
                atual.nome(),
                atual.cpfCnpj(),
                atual.telefone(),
                atual.email(),
                atual.status()
        ).alterarStatus(input.status());

        return clienteGateway.updateStatus(id, input.status());
    }
}
