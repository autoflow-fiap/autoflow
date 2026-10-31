package com.autoflow.presentation.cliente;


import com.autoflow.application.input.cliente.ClienteInput;
import com.autoflow.application.output.cliente.ClienteOutput;
import com.autoflow.application.port.in.cliente.*;
import com.autoflow.presentation.cliente.mapper.ClienteControllerMapper;
import com.autoflow.presentation.cliente.request.ClienteRequest;
import com.autoflow.presentation.cliente.request.ClienteStatusRequest;
import com.autoflow.presentation.cliente.response.ClienteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
@Tag(name = "clientes", description = "Endpoints para gerenciamento de clientes")
@SecurityRequirement(name = "bearerAuth")
public class ClienteController {

    private final CriarClienteUseCase criarClienteUseCase;
    private final BuscarClientePorEmailUseCase buscarClientePorEmailUseCase;
    private final ListarTodosClientesUseCase listarTodosClientesUseCase;
    private final AtualizarClienteUseCase atualizarClienteUseCase;
    private final DeletarClienteUseCase deletarClienteUseCase;
    private final ListarClienteUseCase listarClienteUseCase;
    private final AlterarStatusClienteUseCase alterarStatusClienteUseCase;
    private final ClienteControllerMapper clienteMapper;


    @Operation(summary = "Retornar os dados do cliente autenticado", description = "Retorna as informações do cliente logado. Exclusivo para usuários com role CLIENTE.")
    @ApiResponse(responseCode = "200", description = "Dados do cliente retornados com sucesso")
    @ApiResponse(responseCode = "404", description = "Nenhum cliente vinculado ao usuário autenticado")
    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    @ApiResponse(responseCode = "403", description = "Usuário sem permissão — apenas CLIENTE pode acessar este endpoint")
    @GetMapping("/me")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<ClienteResponse> meuPerfil(@AuthenticationPrincipal UserDetails userDetails) {
        ClienteOutput output = buscarClientePorEmailUseCase.execute(userDetails.getUsername());
        ClienteResponse response = clienteMapper.toResponse(output);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Criar cadastro do cliente", description = "cria cadastro do cliente")
    @ApiResponse(responseCode = "200", description = "Cliente cadastrado com sucesso")
    @ApiResponse(responseCode = "409", description = "CPF/CNPJ ja cadastrado")
    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    @PostMapping
    public ResponseEntity<ClienteResponse> cadastrarCliente(@Valid @RequestBody ClienteRequest request) {

        ClienteInput input = clienteMapper.toInput(request);
        ClienteOutput output = criarClienteUseCase.execute(input);
        ClienteResponse response = clienteMapper.toResponse(output);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Listar um cliente pelo CPF/CNPJ ou ID", description = "Retorna os detalhes de um cliente específico")
    @ApiResponse(responseCode = "200", description = "Cliente encontrado com sucesso")
    @ApiResponse(responseCode = "404", description = "Cliente não encontrado")
    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    @ApiResponse(responseCode = "403", description = "Usuário sem permissão para executar a operação")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ATENDENTE', 'ADMIN')")
    public ResponseEntity<ClienteResponse> listar(@PathVariable Long id) {
        ClienteOutput output = listarClienteUseCase.execute(id);
        ClienteResponse response = clienteMapper.toResponse(output);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Listar todos os clientes cadastrados", description = "Retorna a lista de clientes cadastrados")
    @ApiResponse(responseCode = "200", description = "Clientes listados com sucesso")
    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    @ApiResponse(responseCode = "403", description = "Usuário sem permissão para executar a operação")
    @GetMapping
    @PreAuthorize("hasAnyRole('ATENDENTE', 'ADMIN')")
    public ResponseEntity<List<ClienteResponse>> listarTodosClientes() {
        List<ClienteOutput> outputs = listarTodosClientesUseCase.execute();
        List<ClienteResponse> responses = outputs.stream()
                .map(clienteMapper::toResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Atualizar as informações de um cliente", description = "Atualiza as informações de um cliente")
    @ApiResponse(responseCode = "200", description = "Cliente atualizado com sucesso")
    @ApiResponse(responseCode = "404", description = "Cliente não encontrado")
    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    @ApiResponse(responseCode = "403", description = "Usuário sem permissão para executar a operação")
    @PatchMapping("/{id}/atualizacao")
    @PreAuthorize("hasAnyRole('ATENDENTE', 'ADMIN')")
    public ResponseEntity<ClienteResponse> atualizar(@Valid @RequestBody ClienteRequest request, @PathVariable Long id) {
        ClienteInput input = clienteMapper.toInput(request);
        ClienteOutput output = atualizarClienteUseCase.execute(id, input);
        ClienteResponse response = clienteMapper.toResponse(output);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Alterar status do cliente", description = "Ativa ou desativa um cliente. Permissão: ADMIN.")
    @ApiResponse(responseCode = "200", description = "Status do cliente alterado com sucesso")
    @ApiResponse(responseCode = "400", description = "Status inválido")
    @ApiResponse(responseCode = "404", description = "Cliente não encontrado")
    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    @ApiResponse(responseCode = "403", description = "Usuário sem permissão para executar a operação")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClienteResponse> alterarStatus(
            @PathVariable Long id,
            @Valid @RequestBody ClienteStatusRequest request) {
        ClienteOutput output = alterarStatusClienteUseCase.execute(
                id,
                clienteMapper.toStatusInput(request));
        return ResponseEntity.ok(clienteMapper.toResponse(output));
    }

    @Operation(summary = "Deletar um cliente", description = "Deleta um cliente pelo seu ID")
    @ApiResponse(responseCode = "200", description = "Cliente deletado com sucesso")
    @ApiResponse(responseCode = "404", description = "Cliente não encontrado")
    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    @ApiResponse(responseCode = "403", description = "Usuário sem permissão para executar a operação")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deletar(@PathVariable Long id) {
        deletarClienteUseCase.execute(id);
        return ResponseEntity.ok().body("cliente deletado com sucesso");
    }


}
