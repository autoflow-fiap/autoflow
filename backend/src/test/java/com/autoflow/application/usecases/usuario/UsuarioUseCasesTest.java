package com.autoflow.application.usecases.usuario;

import com.autoflow.application.exception.ApplicationException;
import com.autoflow.application.gateway.*;
import com.autoflow.application.input.usuario.LoginInput;
import com.autoflow.application.input.usuario.RegistroInput;
import com.autoflow.application.mapper.UsuarioApplicationMapper;
import com.autoflow.application.output.security.CurrentUser;
import com.autoflow.application.output.usuario.UsuarioOutput;
import com.autoflow.application.port.in.usuario.CadastrarClienteUseCase;
import com.autoflow.application.port.in.usuario.CadastrarUsuarioUseCase;
import com.autoflow.domain.usuario.RoleEnum;
import com.autoflow.domain.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioUseCasesTest {

    @Mock UsuarioGateway gateway;
    @Mock
    UsuarioApplicationMapper mapper;
    @Mock PasswordGateway passwordGateway;
    @Mock ClienteGateway clienteGateway;
    @Mock AuthenticationGateway authenticationGateway;
    @Mock TokenGateway tokenGateway;

    private Usuario usuario;
    private RegistroInput input;
    private UsuarioOutput output;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId(1L);
        usuario.setNome("Maria");
        usuario.setEmail("maria@autoflow.com");
        usuario.setCpfCnpj("12345678980");
        usuario.setRole(RoleEnum.MECANICO);
        input = new RegistroInput("Maria", usuario.getEmail(), "12345678980", "11999999999", "senha", RoleEnum.MECANICO);
        output = new UsuarioOutput(1L, "Maria", usuario.getEmail(), "12345678980", RoleEnum.MECANICO);
    }

    @Test
    void deveBuscarMecanicoEValidarIdERole() {
        var useCase = new BuscarMecanicoPorIdUseCaseImpl(gateway);
        when(gateway.findById(1L)).thenReturn(Optional.of(usuario));
        assertSame(usuario, useCase.execute(1L));

        when(gateway.findById(2L)).thenReturn(Optional.empty());
        assertEquals(ApplicationException.ErrorType.NOT_FOUND,
                assertThrows(ApplicationException.class, () -> useCase.execute(2L)).type());

        usuario.setRole(RoleEnum.CLIENTE);
        when(gateway.findById(3L)).thenReturn(Optional.of(usuario));
        assertEquals(ApplicationException.ErrorType.BAD_REQUEST,
                assertThrows(ApplicationException.class, () -> useCase.execute(3L)).type());
    }

    @Test
    void deveBuscarUsuarioPorEmail() {
        var useCase = new BuscarUsuarioPorEmailUseCaseImpl(gateway);
        when(gateway.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        assertSame(usuario, useCase.execute(usuario.getEmail()));
        when(gateway.findByEmail("ausente@email.com")).thenReturn(Optional.empty());
        assertEquals(ApplicationException.ErrorType.NOT_FOUND,
                assertThrows(ApplicationException.class,
                        () -> useCase.execute("ausente@email.com")).type());
    }

    @Test
    void deveListarUsuariosEMecanicos() {
        when(gateway.findAll()).thenReturn(List.of(usuario));
        when(gateway.findByRole(RoleEnum.MECANICO)).thenReturn(List.of(usuario));
        when(mapper.toOutput(List.of(usuario))).thenReturn(List.of(output));
        assertEquals(List.of(output), new ListarUsuariosUseCaseImpl(gateway, mapper).execute());
        assertEquals(List.of(output), new BuscarMecanicosUseCaseImpl(gateway, mapper).execute());
    }

    @Test
    void deveCadastrarClienteEImpedirDocumentoDuplicado() {
        var useCase = new CadastrarClienteUseCaseImpl(clienteGateway);
        when(clienteGateway.save(any())).thenReturn(null);
        assertNull(useCase.execute(input, usuario));

        when(clienteGateway.existsByCpfCnpj(input.cpfCnpj())).thenReturn(true);
        assertEquals(ApplicationException.ErrorType.CONFLICT,
                assertThrows(ApplicationException.class, () -> useCase.execute(input, usuario)).type());
    }

    @Test
    void deveCadastrarUsuarioEClienteQuandoAplicavel() {
        var cadastrarCliente = mock(CadastrarClienteUseCase.class);
        var useCase = new CadastrarUsuarioUseCaseImpl(gateway, passwordGateway, cadastrarCliente);
        when(passwordGateway.encode(input.senha())).thenReturn("senha-hash");
        when(gateway.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        assertEquals(output, useCase.execute(input));
        verifyNoInteractions(cadastrarCliente);

        var clienteInput = new RegistroInput("Cliente", "cliente@email.com", "123412421323", "119", "senha", RoleEnum.CLIENTE);
        UsuarioOutput clienteOutput = new UsuarioOutput(
                1L,
                "Cliente",
                "cliente@email.com",
                "123412421323",
                RoleEnum.CLIENTE
        );
        assertEquals(clienteOutput, useCase.execute(clienteInput));
        verify(cadastrarCliente).execute(eq(clienteInput), any(Usuario.class));

        when(gateway.existsByEmail(input.email())).thenReturn(true);
        assertEquals(ApplicationException.ErrorType.CONFLICT,
                assertThrows(ApplicationException.class, () -> useCase.execute(input)).type());
    }

    @Test
    void deveAplicarPermissoesAoCadastrarStaff() {
        var cadastrar = mock(CadastrarUsuarioUseCase.class);
        var currentUserGateway = mock(CurrentUserGateway.class);
        var useCase = new CadastrarStaffUseCaseImpl(cadastrar, currentUserGateway);
        when(currentUserGateway.getCurrentUser()).thenReturn(java.util.Optional.of(
                new CurrentUser("admin@email.com", RoleEnum.ADMIN)));
        when(cadastrar.execute(input)).thenReturn(output);
        assertEquals(output, useCase.execute(input));

        when(currentUserGateway.getCurrentUser()).thenReturn(java.util.Optional.of(
                new CurrentUser("atendente@email.com", RoleEnum.ATENDENTE)));
        assertEquals(ApplicationException.ErrorType.FORBIDDEN,
                assertThrows(ApplicationException.class,
                        () -> useCase.execute(input)).type());
    }

    @Test
    void devePermitirCadastroPublicoSomenteParaCliente() {
        var cadastrar = mock(CadastrarUsuarioUseCase.class);
        var useCase = new CadastrarUsuarioPublicoUseCaseImpl(cadastrar);
        var clienteInput = new RegistroInput(
                "Cliente",
                "cliente@email.com",
                "123",
                "119",
                "senha",
                RoleEnum.CLIENTE
        );
        when(cadastrar.execute(clienteInput)).thenReturn(output);

        assertEquals(output, useCase.execute(clienteInput));

        assertEquals(ApplicationException.ErrorType.FORBIDDEN,
                assertThrows(ApplicationException.class,
                        () -> useCase.execute(input)).type());
    }

    @Test
    void deveAutenticarEGerarToken() {
        var loginInput = new LoginInput(usuario.getCpfCnpj(), "senha");
        var useCase = new LoginUsuarioUseCaseImpl(authenticationGateway, gateway, tokenGateway);
        when(gateway.findByCpfCnpj(usuario.getCpfCnpj())).thenReturn(Optional.of(usuario));
        when(tokenGateway.generateToken(usuario.getCpfCnpj(), RoleEnum.MECANICO.name())).thenReturn("token");
        assertEquals("token", useCase.execute(loginInput).token());
        verify(authenticationGateway).authenticate(usuario.getCpfCnpj(), "senha");

        when(gateway.findByCpfCnpj(usuario.getCpfCnpj())).thenReturn(Optional.empty());
        assertThrows(java.util.NoSuchElementException.class, () -> useCase.execute(loginInput));
    }
}
