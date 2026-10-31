package com.autoflow.presentation.usuario;

import com.autoflow.application.input.usuario.LoginInput;
import com.autoflow.application.input.usuario.RegistroInput;
import com.autoflow.application.output.usuario.LoginOutput;
import com.autoflow.application.output.usuario.UsuarioOutput;
import com.autoflow.application.port.in.usuario.BuscarMecanicosUseCase;
import com.autoflow.application.port.in.usuario.CadastrarUsuarioPublicoUseCase;
import com.autoflow.application.port.in.usuario.ListarUsuariosUseCase;
import com.autoflow.application.port.in.usuario.LoginUsuarioUseCase;
import com.autoflow.presentation.usuario.request.LoginRequest;
import com.autoflow.presentation.usuario.request.RegistroRequest;
import com.autoflow.presentation.usuario.response.LoginResponse;
import com.autoflow.presentation.usuario.response.UsuarioCadastroResponse;
import com.autoflow.presentation.usuario.response.UsuarioResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "autenticação", description = "Endpoints para gerenciamento da autenticação dos usuários")
public class UsuarioController {

    private final CadastrarUsuarioPublicoUseCase cadastrarUsuarioPublicoUseCase;
    private final LoginUsuarioUseCase loginUsuarioUseCase;
    private final ListarUsuariosUseCase listarUsuariosUseCase;
    private final BuscarMecanicosUseCase buscarMecanicosUseCase;
    private final UsuarioControllerMapper usuarioMapper;


    @Operation(summary = "Cadastrar um usuario", description = "Retorna as informações do usuario cadastrado")
    @ApiResponse(responseCode = "201", description = "Usuário cadastrado com sucesso")
    @ApiResponse(responseCode = "409", description = "CPF/CNPJ ou email ja foram cadastrados")
    @PostMapping("/cadastro")
    public ResponseEntity<UsuarioCadastroResponse> cadastrar(@Valid @RequestBody RegistroRequest request) {
        RegistroInput input = usuarioMapper.toInput(request);
        UsuarioOutput execute = cadastrarUsuarioPublicoUseCase.execute(input);

        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioMapper.toCadastroResponse(execute));
    }

    @Operation(summary = "Autenticar o usuario", description = "Autentica o usuario cadastrado")
    @ApiResponse(responseCode = "200", description = "Token retornado com sucesso")
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        LoginInput input = new LoginInput(request.cpfCnpj(), request.senha());
        LoginOutput output = loginUsuarioUseCase.execute(input);
        return new LoginResponse(output.token());

    }

    @Operation(summary = "Listar todos usuarios cadastrados no sistema", description = "Retorna a lista dos usuarios cadastrados")
    @ApiResponse(responseCode = "200", description = "Usuarios retornados com sucesso")
    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    @ApiResponse(responseCode = "403", description = "Usuário sem permissão para executar a operação")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/usuarios")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UsuarioResponse>> listarUsuarios() {
        List<UsuarioOutput> todosUsuarios = listarUsuariosUseCase.execute();
        return ResponseEntity.ok(usuarioMapper.toResponse(todosUsuarios));
    }

    @Operation(summary = "Listar todos mecanicos cadastrados no sistema", description = "Retorna a lista dos mecanicos cadastrados")
    @ApiResponse(responseCode = "200", description = "Mecanicos retornados com sucesso")
    @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    @ApiResponse(responseCode = "403", description = "Usuário sem permissão para executar a operação")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/mecanicos")
    @PreAuthorize("hasAnyRole('ADMIN','ATENDENTE')")
    public ResponseEntity<List<UsuarioResponse>> listarMecanicos() {
        List<UsuarioOutput> todosMecanicos = buscarMecanicosUseCase.execute();
        return ResponseEntity.ok(usuarioMapper.toResponse(todosMecanicos));
    }
}
