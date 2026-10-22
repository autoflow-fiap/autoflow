package com.autoflow.presentation.usuario;

import com.autoflow.application.input.usuario.LoginInput;
import com.autoflow.application.input.usuario.RegistroInput;
import com.autoflow.application.output.usuario.LoginOutput;
import com.autoflow.application.output.usuario.UsuarioOutput;
import com.autoflow.application.port.in.usuario.BuscarMecanicosUseCase;
import com.autoflow.application.port.in.usuario.CadastrarUsuarioPublicoUseCase;
import com.autoflow.application.port.in.usuario.ListarUsuariosUseCase;
import com.autoflow.application.port.in.usuario.LoginUsuarioUseCase;
import com.autoflow.domain.usuario.RoleEnum;
import com.autoflow.presentation.usuario.request.LoginRequest;
import com.autoflow.presentation.usuario.request.RegistroRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UsuarioControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CadastrarUsuarioPublicoUseCase cadastrarUsuarioPublicoUseCase;

    @Mock
    private LoginUsuarioUseCase loginUsuarioUseCase;

    @Mock
    private ListarUsuariosUseCase listarUsuariosUseCase;

    @Mock
    private BuscarMecanicosUseCase buscarMecanicosUseCase;

    private UsuarioControllerMapper usuarioMapper;

    @InjectMocks
    private UsuarioController usuarioController;

    @BeforeEach
    void setup() {

        usuarioMapper = Mappers.getMapper(UsuarioControllerMapper.class);

        usuarioController = new UsuarioController(
                cadastrarUsuarioPublicoUseCase,
                loginUsuarioUseCase,
                listarUsuariosUseCase,
                buscarMecanicosUseCase,
                usuarioMapper
        );

        mockMvc = MockMvcBuilders
                .standaloneSetup(usuarioController)
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new SecurityExceptionHandler())
                .build();
    }

    @Test
    void deveCadastrarUsuario() throws Exception {

        RegistroInput request = new RegistroInput(
                "Bruno",
                "usuario@email.com",
                "52998224725",
                "32131221",
                "Senha@123",
                RoleEnum.CLIENTE
        );

        UsuarioOutput output = UsuarioOutput.builder()
                .id(1L)
                .nome("Bruno")
                .email("usuario@email.com")
                .role(RoleEnum.CLIENTE)
                .build();

        when(cadastrarUsuarioPublicoUseCase.execute(request))
                .thenReturn(output);

        mockMvc.perform(post("/auth/cadastro")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Bruno"))
                .andExpect(jsonPath("$.email").value("usuario@email.com"))
                .andExpect(jsonPath("$.role").value("CLIENTE"));

        verify(cadastrarUsuarioPublicoUseCase).execute(request);
    }

    @Test
    void deveFazerLogin() throws Exception {

        LoginRequest request =
                new LoginRequest("52998224725", "senha123");
        LoginInput input = new LoginInput(request.cpfCnpj(), request.senha());

        when(loginUsuarioUseCase.execute(input))
                .thenReturn(new LoginOutput("token-jwt-mockado"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token")
                        .value("token-jwt-mockado"));

        verify(loginUsuarioUseCase).execute(input);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deveListarUsuarios() throws Exception {

        UsuarioOutput output = UsuarioOutput.builder()
                .id(1L)
                .nome("Bruno")
                .email("usuario@email.com")
                .role(RoleEnum.ADMIN)
                .build();

        when(listarUsuariosUseCase.execute())
                .thenReturn(List.of(output));

        mockMvc.perform(get("/auth/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nome").value("Bruno"))
                .andExpect(jsonPath("$[0].email").value("usuario@email.com"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"));

        verify(listarUsuariosUseCase).execute();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deveListarMecanicos() throws Exception {

        UsuarioOutput output = UsuarioOutput.builder()
                .id(1L)
                .nome("João")
                .email("joao@email.com")
                .role(RoleEnum.MECANICO)
                .build();

        when(buscarMecanicosUseCase.execute())
                .thenReturn(List.of(output));

        mockMvc.perform(get("/auth/mecanicos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nome").value("João"))
                .andExpect(jsonPath("$[0].email").value("joao@email.com"))
                .andExpect(jsonPath("$[0].role").value("MECANICO"));

        verify(buscarMecanicosUseCase).execute();
    }

    @Test
    void deveRetornarForbiddenAoCadastrarComRoleNaoCliente() throws Exception {

        RegistroRequest request = new RegistroRequest(
                "Admin",
                "admin@email.com",
                "52998224725",
                "32131221",
                "Senha@123",
                RoleEnum.ADMIN
        );

        when(cadastrarUsuarioPublicoUseCase.execute(any(RegistroInput.class)))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Cadastro público permite apenas a role CLIENTE"));

        mockMvc.perform(post("/auth/cadastro")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(cadastrarUsuarioPublicoUseCase).execute(any(RegistroInput.class));
    }

    @TestConfiguration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class MethodSecurityTestConfig implements WebMvcConfigurer {

        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new AuthenticationPrincipalArgumentResolver());
        }
    }

    @RestControllerAdvice
    static class SecurityExceptionHandler {

        @ExceptionHandler(AuthorizationDeniedException.class)
        ResponseEntity<Void> handleAuthorizationDenied() {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        @ExceptionHandler(ResponseStatusException.class)
        ResponseEntity<Void> handleResponseStatusException(ResponseStatusException exception) {
            return ResponseEntity.status(exception.getStatusCode()).build();
        }
    }
}
