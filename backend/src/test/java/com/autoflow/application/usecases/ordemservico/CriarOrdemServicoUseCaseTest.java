package com.autoflow.application.usecases.ordemservico;

import com.autoflow.application.gateway.NumeroOrdemServicoGateway;
import com.autoflow.application.gateway.OrdemServicoGateway;
import com.autoflow.application.gateway.ServicoGateway;
import com.autoflow.application.input.ordemservico.CriarOrdemServicoCommand;
import com.autoflow.application.input.veiculo.VeiculoInput;
import com.autoflow.application.output.cliente.ClienteOutput;
import com.autoflow.domain.cliente.ClienteStatus;
import com.autoflow.application.output.ordemservico.acompanhamento.TokenAcompanhamentoOutput;
import com.autoflow.application.output.veiculo.VeiculoOutput;
import com.autoflow.application.port.in.cliente.BuscarClientePorCpfCnpjUseCase;
import com.autoflow.application.port.in.ordemservico.acompanhamento.EnviarLinkAcompanhamentoUseCase;
import com.autoflow.application.port.in.ordemservico.acompanhamento.GerarTokenAcompanhamentoUseCase;
import com.autoflow.application.port.in.veiculo.BuscarOuCadastrarVeiculoUseCase;
import com.autoflow.domain.ordemservico.OrdemServico;
import com.autoflow.domain.servico.Servico;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CriarOrdemServicoUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-18T18:30:00Z"), ZoneOffset.UTC);

    @Mock BuscarClientePorCpfCnpjUseCase buscarCliente;
    @Mock BuscarOuCadastrarVeiculoUseCase buscarOuCadastrarVeiculo;
    @Mock ServicoGateway servicoGateway;
    @Mock OrdemServicoGateway ordemServicoGateway;
    @Mock RegistrarHistoricoStatusOsService registrarHistoricoStatusOs;
    @Mock GerarTokenAcompanhamentoUseCase gerarToken;
    @Mock EnviarLinkAcompanhamentoUseCase enviarLink;
    @Mock NumeroOrdemServicoGateway numeroOrdemServicoGateway;

    @Test
    void deveCriarOsComCommandNumeroEDataResolvidosForaDoDominio() {
        ClienteOutput cliente = ClienteOutput.builder()
                .id(1L).nome("Cliente").cpfCnpj("123").telefone("11999999999")
                .email("cliente@email.com").status(ClienteStatus.ATIVO).build();
        VeiculoOutput veiculo = new VeiculoOutput(2L, "ABC1D23", "Honda", "Civic", 2020, 1L);
        Servico servico = new Servico(10L, "Revisao", "Descricao", BigDecimal.TEN, true);

        when(buscarCliente.execute("123")).thenReturn(cliente);
        when(buscarOuCadastrarVeiculo.execute(eq(1L), any())).thenReturn(veiculo);
        when(servicoGateway.findById(10L)).thenReturn(Optional.of(servico));
        when(numeroOrdemServicoGateway.gerar()).thenReturn("OS-CONTROLADA");
        when(ordemServicoGateway.save(any())).thenAnswer(invocation -> {
            OrdemServico ordem = invocation.getArgument(0);
            ordem.setId(99L);
            return ordem;
        });
        when(gerarToken.execute(99L)).thenReturn(new TokenAcompanhamentoOutput("token", "hash"));

        var resultado = novoCasoDeUso().execute(new CriarOrdemServicoCommand(
                "123",
                new VeiculoInput("Honda", 2020, "ABC1D23", "Civic"),
                java.util.List.of(10L)));

        assertEquals("OS-CONTROLADA", resultado.ordemServico().getNumeroOs());
        assertEquals(LocalDateTime.now(CLOCK), resultado.ordemServico().getDataAbertura());
        assertEquals("token", resultado.tokenAcompanhamento());
        verify(ordemServicoGateway).save(argThat(os -> os.getServicosSolicitados().size() == 1));
    }

    @Test
    void falhaAoEnviarLinkNaoCancelaCriacaoDaOrdemDeServico() {
        ClienteOutput cliente = ClienteOutput.builder()
                .id(1L).nome("Cliente").cpfCnpj("123").telefone("11999999999")
                .email("cliente@email.com").status(ClienteStatus.ATIVO).build();
        VeiculoOutput veiculo = new VeiculoOutput(2L, "ABC1D23", "Honda", "Civic", 2020, 1L);
        Servico servico = new Servico(10L, "Revisao", "Descricao", BigDecimal.TEN, true);
        when(buscarCliente.execute("123")).thenReturn(cliente);
        when(buscarOuCadastrarVeiculo.execute(eq(1L), any())).thenReturn(veiculo);
        when(servicoGateway.findById(10L)).thenReturn(Optional.of(servico));
        when(numeroOrdemServicoGateway.gerar()).thenReturn("OS-CONTROLADA");
        when(ordemServicoGateway.save(any())).thenAnswer(invocation -> {
            OrdemServico ordem = invocation.getArgument(0);
            ordem.setId(99L);
            return ordem;
        });
        when(gerarToken.execute(99L)).thenReturn(new TokenAcompanhamentoOutput("token", "hash"));
        doThrow(new IllegalStateException("falha sintetica de notificacao"))
                .when(enviarLink).execute(any(), eq("token"));

        var resultado = novoCasoDeUso().execute(new CriarOrdemServicoCommand(
                "123", new VeiculoInput("Honda", 2020, "ABC1D23", "Civic"), java.util.List.of(10L)));

        assertEquals("OS-CONTROLADA", resultado.ordemServico().getNumeroOs());
        assertEquals("token", resultado.tokenAcompanhamento());
        verify(ordemServicoGateway).save(any());
    }

    @Test
    void rejeitaComandoSemVeiculoOuSemServicoSolicitado() {
        var useCase = novoCasoDeUso();

        assertThrows(IllegalArgumentException.class, () -> useCase.execute(null));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(
                new CriarOrdemServicoCommand("123", null, java.util.List.of(10L))));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(
                new CriarOrdemServicoCommand("123", new VeiculoInput("Honda", 2020, "ABC1D23", "Civic"),
                        java.util.List.of())));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(
                new CriarOrdemServicoCommand("123", new VeiculoInput("Honda", 2020, "ABC1D23", "Civic"),
                        java.util.Arrays.asList(10L, null))));

        verifyNoInteractions(buscarCliente, buscarOuCadastrarVeiculo, servicoGateway, ordemServicoGateway);
    }

    private CriarOrdemServicoUseCaseImpl novoCasoDeUso() {
        return new CriarOrdemServicoUseCaseImpl(
                buscarCliente, buscarOuCadastrarVeiculo, servicoGateway, ordemServicoGateway,
                registrarHistoricoStatusOs, gerarToken, enviarLink, numeroOrdemServicoGateway, CLOCK);
    }
}
