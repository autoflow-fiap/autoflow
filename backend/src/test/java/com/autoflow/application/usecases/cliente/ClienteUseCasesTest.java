package com.autoflow.application.usecases.cliente;

import com.autoflow.application.exception.ClienteDuplicadoException;
import com.autoflow.application.exception.ClienteNaoEncontradoException;
import com.autoflow.application.gateway.ClienteGateway;
import com.autoflow.application.input.cliente.ClienteInput;
import com.autoflow.application.input.cliente.ClienteStatusInput;
import com.autoflow.application.output.cliente.ClienteOutput;
import com.autoflow.domain.cliente.ClienteStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteUseCasesTest {

    @Mock
    private ClienteGateway clienteGateway;

    @InjectMocks
    private CriarClienteUseCaseImpl criarClienteUseCase;

    @InjectMocks
    private BuscarClientePorEmailUseCaseImpl buscarClientePorEmailUseCase;

    @InjectMocks
    private BuscarClientePorIdUseCaseImpl buscarClientePorIdUseCase;

    @InjectMocks
    private ListarTodosClientesUseCaseImpl listarTodosClientesUseCase;

    @InjectMocks
    private AtualizarClienteUseCaseImpl atualizarClienteUseCase;

    @InjectMocks
    private DeletarClienteUseCaseImpl deletarClienteUseCase;

    @InjectMocks
    private ListarClienteUseCaseImpl listarClienteUseCase;

    @InjectMocks
    private BuscarClientePorCpfCnpjUseCaseImpl buscarClientePorCpfCnpjUseCase;

    private ClienteInput input;
    private ClienteOutput output;

    @BeforeEach
    void setup() {
        input = new ClienteInput("Bruno", "12345678901", "11999999999", "bruno@email.com");
        output = ClienteOutput.builder()
                .id(1L)
                .nome("Bruno")
                .cpfCnpj("12345678901")
                .telefone("11999999999")
                .email("bruno@email.com")
                .status(ClienteStatus.ATIVO)
                .build();
    }

    @Test
    void deveCriarClienteComSucesso() {
        when(clienteGateway.existsByCpfCnpj(input.cpfCnpj())).thenReturn(false);
        when(clienteGateway.save(input)).thenReturn(output);

        assertEquals(output, criarClienteUseCase.execute(input));

        verify(clienteGateway).save(input);
    }

    @Test
    void deveLancarConflictQuandoCpfCnpjJaExistir() {
        when(clienteGateway.existsByCpfCnpj(input.cpfCnpj())).thenReturn(true);

        assertThrows(ClienteDuplicadoException.class, () -> criarClienteUseCase.execute(input));

        verify(clienteGateway, never()).save(any());
    }

    @Test
    void deveBuscarClientePorEmail() {
        when(clienteGateway.findByUsuarioEmail(input.email())).thenReturn(Optional.of(output));

        assertEquals(output, buscarClientePorEmailUseCase.execute(input.email()));
    }

    @Test
    void deveBuscarClientePorId() {
        when(clienteGateway.findById(1L)).thenReturn(Optional.of(output));

        assertEquals(output, buscarClientePorIdUseCase.execute(1L));
    }

    @Test
    void deveBuscarClientePorCpfCnpj() {
        when(clienteGateway.findByCpfCnpj(input.cpfCnpj())).thenReturn(Optional.of(output));

        assertEquals(output, buscarClientePorCpfCnpjUseCase.execute(input.cpfCnpj()));
    }

    @Test
    void deveLancarNotFoundAoBuscarCpfCnpjInexistente() {
        when(clienteGateway.findByCpfCnpj(input.cpfCnpj())).thenReturn(Optional.empty());

        assertThrows(ClienteNaoEncontradoException.class,
                () -> buscarClientePorCpfCnpjUseCase.execute(input.cpfCnpj()));
    }

    @Test
    void deveListarTodosClientes() {
        when(clienteGateway.findAll()).thenReturn(List.of(output));

        assertEquals(List.of(output), listarTodosClientesUseCase.execute());
    }

    @Test
    void deveAtualizarCliente() {
        when(clienteGateway.findById(1L)).thenReturn(Optional.of(output));
        when(clienteGateway.existsByCpfCnpjAndIdNot(input.cpfCnpj(), 1L)).thenReturn(false);
        when(clienteGateway.update(1L, input)).thenReturn(output);

        assertEquals(output, atualizarClienteUseCase.execute(1L, input));

        verify(clienteGateway).update(1L, input);
    }

    @Test
    void deveLancarNotFoundAoAtualizarClienteInexistente() {
        when(clienteGateway.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ClienteNaoEncontradoException.class,
                () -> atualizarClienteUseCase.execute(999L, input));

        verify(clienteGateway, never()).update(anyLong(), any());
    }

    @Test
    void deveLancarConflictAoAtualizarCpfDuplicado() {
        when(clienteGateway.findById(1L)).thenReturn(Optional.of(output));
        when(clienteGateway.existsByCpfCnpjAndIdNot(input.cpfCnpj(), 1L)).thenReturn(true);

        assertThrows(ClienteDuplicadoException.class,
                () -> atualizarClienteUseCase.execute(1L, input));

        verify(clienteGateway, never()).update(anyLong(), any());
    }

    @Test
    void deveDeletarCliente() {
        when(clienteGateway.existsById(1L)).thenReturn(true);

        deletarClienteUseCase.execute(1L);

        verify(clienteGateway).deleteById(1L);
    }

    @Test
    void deveLancarNotFoundAoDeletarClienteInexistente() {
        when(clienteGateway.existsById(999L)).thenReturn(false);

        assertThrows(ClienteNaoEncontradoException.class,
                () -> deletarClienteUseCase.execute(999L));
    }

    @Test
    void deveAlterarStatusNosDoisSentidos() {
        var useCase = new AlterarStatusClienteUseCaseImpl(clienteGateway);
        ClienteOutput inativo = ClienteOutput.builder()
                .id(1L).nome("Bruno").cpfCnpj(input.cpfCnpj()).telefone(input.telefone())
                .email(input.email()).status(ClienteStatus.INATIVO).build();
        when(clienteGateway.findById(1L)).thenReturn(Optional.of(output));
        when(clienteGateway.updateStatus(1L, ClienteStatus.INATIVO)).thenReturn(inativo);

        assertEquals(inativo, useCase.execute(1L, new ClienteStatusInput(ClienteStatus.INATIVO)));
        verify(clienteGateway).updateStatus(1L, ClienteStatus.INATIVO);

        when(clienteGateway.findById(1L)).thenReturn(Optional.of(inativo));
        when(clienteGateway.updateStatus(1L, ClienteStatus.ATIVO)).thenReturn(output);
        assertEquals(output, useCase.execute(1L, new ClienteStatusInput(ClienteStatus.ATIVO)));
    }

    @Test
    void deveLancarNotFoundAoAlterarStatusDeClienteInexistente() {
        var useCase = new AlterarStatusClienteUseCaseImpl(clienteGateway);
        when(clienteGateway.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ClienteNaoEncontradoException.class,
                () -> useCase.execute(999L, new ClienteStatusInput(ClienteStatus.INATIVO)));

        verify(clienteGateway, never()).updateStatus(anyLong(), any());
    }

    @Test
    void deveBuscarPorCpfQuandoDocumentoPossuirOnzeDigitos() {
        when(clienteGateway.findByCpfCnpj(input.cpfCnpj())).thenReturn(Optional.of(output));

        assertEquals(output, listarClienteUseCase.execute(12345678901L));
    }

    @Test
    void deveBuscarPorIdQuandoDocumentoNaoForCpfOuCnpj() {
        when(clienteGateway.findById(1L)).thenReturn(Optional.of(output));

        assertEquals(output, listarClienteUseCase.execute(1L));
    }
}
