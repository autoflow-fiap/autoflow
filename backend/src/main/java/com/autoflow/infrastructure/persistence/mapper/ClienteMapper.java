package com.autoflow.infrastructure.persistence.mapper;


import com.autoflow.application.input.cliente.ClienteInput;
import com.autoflow.application.output.cliente.ClienteOutput;
import com.autoflow.infrastructure.persistence.entity.cliente.ClienteEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ClienteMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "usuario", ignore = true)
    @Mapping(target = "veiculos", ignore = true)
    ClienteEntity mapToEntity(ClienteInput request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "usuario", ignore = true)
    @Mapping(target = "veiculos", ignore = true)
    @Mapping(target = "status", ignore = true)
    void updateEntity(ClienteInput request, @MappingTarget ClienteEntity entity);

    ClienteOutput mapToOutput(ClienteEntity clienteEntity);

    List<ClienteOutput> mapToListOutput(List<ClienteEntity> clientes);
}
