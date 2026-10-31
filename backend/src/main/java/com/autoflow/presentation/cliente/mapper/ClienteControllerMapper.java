package com.autoflow.presentation.cliente.mapper;

import com.autoflow.application.input.cliente.ClienteInput;
import com.autoflow.application.input.cliente.ClienteStatusInput;
import com.autoflow.application.output.cliente.ClienteOutput;
import com.autoflow.application.output.cliente.ClienteVeiculoOutput;
import com.autoflow.presentation.cliente.request.ClienteRequest;
import com.autoflow.presentation.cliente.request.ClienteStatusRequest;
import com.autoflow.presentation.cliente.response.ClienteResponse;
import com.autoflow.presentation.veiculo.response.VeiculoClienteResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClienteControllerMapper {

    @Mapping(target = "usuarioId", ignore = true)
    @Mapping(target = "status", expression = "java(com.autoflow.domain.cliente.ClienteStatus.ATIVO)")
    ClienteInput toInput(ClienteRequest request);

    ClienteStatusInput toStatusInput(ClienteStatusRequest request);

    ClienteResponse toResponse(ClienteOutput output);

    VeiculoClienteResponse toResponse(ClienteVeiculoOutput output);
}
