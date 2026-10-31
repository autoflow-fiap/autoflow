package com.autoflow.presentation.cliente;

import com.autoflow.application.input.cliente.ClienteInput;
import com.autoflow.application.output.cliente.ClienteOutput;
import com.autoflow.application.port.in.cliente.*;
import com.autoflow.domain.cliente.ClienteStatus;
import com.autoflow.presentation.cliente.mapper.ClienteControllerMapper;
import com.autoflow.presentation.cliente.mapper.ClienteControllerMapperImpl;
import com.autoflow.presentation.cliente.request.ClienteRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ClienteControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CriarClienteUseCase criarClienteUseCase;

    @Mock
        private BuscarClientePorEmailUseCase buscarClientePorEmailUseCase;

    @Mock
    private BuscarClientePorIdUseCase buscarClientePorIdUseCase;

    @Mock
    private ListarTodosClientesUseCase listarTodosClientesUseCase;

    @Mock
    private AtualizarClienteUseCase atualizarClienteUseCase;

    @Mock
    private DeletarClienteUseCase deletarClienteUseCase;

    @Mock
    private ListarClienteUseCase listarClienteUseCase;

    @Mock
    private AlterarStatusClienteUseCase alterarStatusClienteUseCase;

    private ClienteControllerMapper clienteMapper;

    private ClienteController clienteController;

    private ClienteRequest clienteRequest;
    private ClienteOutput clienteOutput;

    @BeforeEach
    void setup() {

        clienteMapper = new ClienteControllerMapperImpl();

        clienteController = new ClienteController(
                criarClienteUseCase,
                buscarClientePorEmailUseCase,
                listarTodosClientesUseCase,
                atualizarClienteUseCase,
                deletarClienteUseCase,
                listarClienteUseCase,
                alterarStatusClienteUseCase,
                clienteMapper
        );

        mockMvc = MockMvcBuilders
                .standaloneSetup(clienteController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        clienteRequest = new ClienteRequest(
                "João Silva",
                "52998224725",
                "12312321321",
                "bruno@hotmail.com"
        );

        clienteOutput = ClienteOutput.builder()
                .id(1L)
                .nome("Bruno")
                .cpfCnpj("52998224725")
                .telefone("12312321321")
                .email("bruno@hotmail.com")
                .status(ClienteStatus.ATIVO)
                .build();
    }

    @Test
    void deveBuscarMeuPerfil() throws Exception {

        when(buscarClientePorEmailUseCase.execute("bruno@hotmail.com"))
                .thenReturn(clienteOutput);

        var userDetails = User.withUsername("bruno@hotmail.com")
                .password("")
                .roles("CLIENTE")
                .build();

        var auth = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );

        SecurityContextHolder.getContext().setAuthentication(auth);

        try {

            mockMvc.perform(get("/clientes/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.nome").value("Bruno"))
                    .andExpect(jsonPath("$.cpfCnpj").value("52998224725"))
                    .andExpect(jsonPath("$.telefone").value("12312321321"))
                    .andExpect(jsonPath("$.email").value("bruno@hotmail.com"))
                    .andExpect(jsonPath("$.status").value("ATIVO"));

            verify(buscarClientePorEmailUseCase)
                    .execute("bruno@hotmail.com");

        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void deveCadastrarCliente() throws Exception {

        when(criarClienteUseCase.execute(any(ClienteInput.class)))
                .thenReturn(clienteOutput);

        mockMvc.perform(post("/clientes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clienteRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Bruno"))
                .andExpect(jsonPath("$.cpfCnpj").value("52998224725"))
                .andExpect(jsonPath("$.telefone").value("12312321321"))
                .andExpect(jsonPath("$.email").value("bruno@hotmail.com"));

        ArgumentCaptor<ClienteInput> captor =
                ArgumentCaptor.forClass(ClienteInput.class);

        verify(criarClienteUseCase).execute(captor.capture());

        ClienteInput input = captor.getValue();

        assertEquals("João Silva", input.nome());
        assertEquals("52998224725", input.cpfCnpj());
        assertEquals("12312321321", input.telefone());
        assertEquals("bruno@hotmail.com", input.email());
        assertEquals(ClienteStatus.ATIVO, input.status());
    }

    @Test
    void deveListarClientePorDocumentoOuId() throws Exception {

        when(listarClienteUseCase.execute(12345678901L))
                .thenReturn(clienteOutput);

        mockMvc.perform(get("/clientes/12345678901"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(listarClienteUseCase)
                .execute(12345678901L);
    }

    @Test
    void deveListarTodosClientes() throws Exception {

        when(listarTodosClientesUseCase.execute())
                .thenReturn(List.of(clienteOutput));

        mockMvc.perform(get("/clientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nome").value("Bruno"));

        verify(listarTodosClientesUseCase).execute();
    }

    @Test
    void deveAtualizarCliente() throws Exception {

        when(atualizarClienteUseCase.execute(eq(1L), any(ClienteInput.class)))
                .thenReturn(clienteOutput);

        mockMvc.perform(patch("/clientes/1/atualizacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clienteRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        ArgumentCaptor<ClienteInput> captor =
                ArgumentCaptor.forClass(ClienteInput.class);

        verify(atualizarClienteUseCase)
                .execute(eq(1L), captor.capture());

        ClienteInput input = captor.getValue();

        assertEquals("João Silva", input.nome());
        assertEquals("52998224725", input.cpfCnpj());
        assertEquals("12312321321", input.telefone());
        assertEquals("bruno@hotmail.com", input.email());
        assertEquals(ClienteStatus.ATIVO, input.status());
    }

    @Test
    void deveAlterarStatusDoCliente() throws Exception {
        when(alterarStatusClienteUseCase.execute(eq(1L), any()))
                .thenReturn(clienteOutput);

        mockMvc.perform(patch("/clientes/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ATIVO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ATIVO"));

        verify(alterarStatusClienteUseCase).execute(eq(1L), any());
    }

    @Test
    void deveDeletarCliente() throws Exception {

        doNothing().when(deletarClienteUseCase).execute(1L);

        mockMvc.perform(delete("/clientes/1"))
                .andExpect(status().isOk());

        verify(deletarClienteUseCase)
                .execute(1L);
    }
}
