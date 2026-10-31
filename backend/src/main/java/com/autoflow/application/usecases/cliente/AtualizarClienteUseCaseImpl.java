package com.autoflow.application.usecases.cliente;

import com.autoflow.application.exception.ClienteDuplicadoException;
import com.autoflow.application.exception.ClienteNaoEncontradoException;
import com.autoflow.application.gateway.ClienteGateway;
import com.autoflow.application.input.cliente.ClienteInput;
import com.autoflow.application.output.cliente.ClienteOutput;
import com.autoflow.application.port.in.cliente.AtualizarClienteUseCase;
import com.autoflow.domain.cliente.Cliente;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AtualizarClienteUseCaseImpl implements AtualizarClienteUseCase {

    private final ClienteGateway clienteGateway;

    @Override
    public ClienteOutput execute(Long id, ClienteInput input) {
        var atual = clienteGateway.findById(id)
                .orElseThrow(() -> new ClienteNaoEncontradoException(
                        "Cliente não encontrado com o ID: " + id));

        Cliente.reconstituir(
                atual.id(),
                atual.nome(),
                atual.cpfCnpj(),
                atual.telefone(),
                atual.email(),
                atual.status()
        ).atualizar(input.nome(), input.cpfCnpj(), input.telefone(), input.email());

        if (clienteGateway.existsByCpfCnpjAndIdNot(input.cpfCnpj(), id)) {
            throw new ClienteDuplicadoException("CPF/CNPJ já cadastrado");
        }

        return clienteGateway.update(id, input);
    }
}
