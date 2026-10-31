package com.autoflow.application.port.in.cliente;

import com.autoflow.application.input.cliente.ClienteStatusInput;
import com.autoflow.application.output.cliente.ClienteOutput;

public interface AlterarStatusClienteUseCase {
    ClienteOutput execute(Long id, ClienteStatusInput input);
}
