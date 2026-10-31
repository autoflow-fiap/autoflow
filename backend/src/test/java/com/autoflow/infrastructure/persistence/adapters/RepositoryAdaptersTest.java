package com.autoflow.infrastructure.persistence.adapters;

import com.autoflow.application.input.PageQuery;
import com.autoflow.application.input.ordemservico.OrdemServicoFiltroInput;
import com.autoflow.application.input.pecainsumo.PecaInsumoFiltro;
import com.autoflow.application.input.pecainsumo.PecaInsumoInput;
import com.autoflow.application.output.pecainsumo.EstoqueItemOutput;
import com.autoflow.application.output.pecainsumo.PecaInsumoOutput;
import com.autoflow.domain.orcamento.Orcamento;
import com.autoflow.domain.orcamento.StatusOrcamento;
import com.autoflow.domain.orcamento.TipoOrcamento;
import com.autoflow.domain.ordemservico.HistoricoStatusOs;
import com.autoflow.domain.ordemservico.OrdemServico;
import com.autoflow.domain.pecainsumo.CategoriaPecaInsumo;
import com.autoflow.domain.usuario.RoleEnum;
import com.autoflow.infrastructure.persistence.entity.orcamento.OrcamentoPersistenceEntity;
import com.autoflow.infrastructure.persistence.entity.ordemservico.HistoricoStatusOsEntity;
import com.autoflow.infrastructure.persistence.entity.ordemservico.OrdemServicoEntity;
import com.autoflow.infrastructure.persistence.entity.pecainsumo.PecaInsumoEntity;
import com.autoflow.infrastructure.persistence.entity.usuario.UsuarioEntity;
import com.autoflow.infrastructure.persistence.mapper.OrcamentoPersistenceMapper;
import com.autoflow.infrastructure.persistence.mapper.PecaInsumoPersistenceMapper;
import com.autoflow.infrastructure.persistence.mapper.UsuarioPersistenceMapper;
import com.autoflow.infrastructure.persistence.mapper.ordemservico.HistoricoStatusOsPersistenceMapper;
import com.autoflow.infrastructure.persistence.mapper.ordemservico.OrdemServicoPersistenceMapper;
import com.autoflow.infrastructure.persistence.repository.*;
import com.autoflow.infrastructure.persistence.repository.historico.HistoricoStatusOsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepositoryAdaptersTest {

    @Mock PecaInsumoRepository pecaRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock HistoricoStatusOsRepository historicoRepository;
    @Mock OrcamentoRepository orcamentoRepository;
    @Mock OrdemServicoRepository ordemRepository;
    @Mock ServicoSolicitadoRepository servicoSolicitadoRepository;
    @Mock HistoricoStatusOsPersistenceMapper historicoMapper;
    @Mock OrdemServicoPersistenceMapper ordemMapper;
    @Mock OrcamentoPersistenceMapper orcamentoMapper;

    @Test
    void pecaAdapterDeveDelegarTodasOperacoes() {
         var adapter = new PecaInsumoAdapter(pecaRepository, new PecaInsumoPersistenceMapper());
        var entity = new PecaInsumoEntity();
        entity.setId(1L);
        entity.setTipo(CategoriaPecaInsumo.PECA);
        var input = new PecaInsumoInput("Filtro", null, 0, CategoriaPecaInsumo.PECA);
        var output = new PecaInsumoOutput(1L, null, null, 0, CategoriaPecaInsumo.PECA);


        when(pecaRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(pecaRepository.findByNomeIgnoreCase("Filtro")).thenReturn(Optional.of(entity));
        when(pecaRepository.save(any(PecaInsumoEntity.class))).thenReturn(entity);
        when(pecaRepository.findAll()).thenReturn(List.of(entity));
        when(pecaRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(pecaRepository.existsById(1L)).thenReturn(true);
        when(pecaRepository.findAllById(List.of(1L))).thenReturn(List.of(entity));
        when(pecaRepository.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(entity));
        var estoque = new EstoqueItemOutput(1L, null, CategoriaPecaInsumo.PECA, null, 0);

         assertEquals(Optional.of(output), adapter.findById(1L));
         assertEquals(Optional.of(output), adapter.findByNomeIgnoreCase("Filtro"));
         assertEquals(output, adapter.save(input));
         assertEquals(List.of(output), adapter.findAll());
         assertEquals(List.of(output), adapter.findAll(new PecaInsumoFiltro(null, null), new PageQuery(0, 10)).content());
        assertTrue(adapter.existsById(1L));
        assertEquals(List.of(estoque), adapter.findAllById(List.of(1L)));
        assertEquals(List.of(estoque), adapter.findAllByIdForUpdate(List.of(1L)));
        adapter.deleteById(1L);
        adapter.saveAll(List.of(estoque));

        verify(pecaRepository).deleteById(1L);
        verify(pecaRepository).saveAll(List.of(entity));
    }

    @Test
    void pecaAdapterNaoDevePersistirListaDeEstoqueVazia() {
         var adapter = new PecaInsumoAdapter(pecaRepository, new PecaInsumoPersistenceMapper());

        adapter.saveAll(List.of());

        verifyNoInteractions(pecaRepository);
    }

    @Test
    void pecaAdapterDeveFalharQuandoEstoqueNaoExistir() {
         var adapter = new PecaInsumoAdapter(pecaRepository, new PecaInsumoPersistenceMapper());
        var estoque = new EstoqueItemOutput(9L, "Item", CategoriaPecaInsumo.PECA, null, 1);
        when(pecaRepository.findAllById(List.of(9L))).thenReturn(List.of());
        var itens = List.of(estoque);

        assertThrows(IllegalStateException.class, () -> adapter.saveAll(itens));

        verify(pecaRepository, never()).saveAll(any());
    }

    @Test
    void usuarioAdapterDeveDelegarConsultas() {
        var mapper = Mappers.getMapper(UsuarioPersistenceMapper.class);
        var adapter = new UsuarioRepositoryAdapter(usuarioRepository, mapper);
        var usuario = new UsuarioEntity();
        usuario.setRole(RoleEnum.MECANICO);
        when(usuarioRepository.findAll()).thenReturn(List.of(usuario));
        when(usuarioRepository.findByRole(RoleEnum.MECANICO)).thenReturn(List.of(usuario));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByEmail("mecanico@email.com")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByCpfCnpj("52998224725")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.existsByEmail("mecanico@email.com")).thenReturn(true);

        assertEquals(RoleEnum.MECANICO, adapter.findAll().getFirst().getRole());
        assertEquals(RoleEnum.MECANICO, adapter.findByRole(RoleEnum.MECANICO).getFirst().getRole());
        assertTrue(adapter.findById(1L).isPresent());
        assertTrue(adapter.findByEmail("mecanico@email.com").isPresent());
        assertTrue(adapter.findByCpfCnpj("52998224725").isPresent());
        assertTrue(adapter.existsByEmail("mecanico@email.com"));
    }

    @Test
    void historicoAdapterDeveDelegarPersistenciaEOrdenacao() {
         var adapter = new HistoricoStatusOsRepositoryAdapter(historicoRepository, historicoMapper);
         var historico = new HistoricoStatusOs();
         var historicoEntity = new HistoricoStatusOsEntity();
         when(historicoMapper.toEntity(historico)).thenReturn(historicoEntity);
         when(historicoRepository.save(historicoEntity)).thenReturn(historicoEntity);
         when(historicoMapper.toDomain(historicoEntity)).thenReturn(historico);
         when(historicoRepository.findByOrdemServicoIdOrderByRegistradoEmAsc(1L))
                 .thenReturn(List.of(historicoEntity));
         when(historicoRepository.findByNumeroOsOrderByRegistradoEmAsc("OS-1"))
                 .thenReturn(List.of(historicoEntity));

        assertSame(historico, adapter.save(historico));
        assertEquals(List.of(historico), adapter.findByOrdemServicoIdOrderByRegistradoEmAsc(1L));
        assertEquals(List.of(historico), adapter.findByNumeroOsOrderByRegistradoEmAsc("OS-1"));
    }

    @Test
    void orcamentoAdapterDeveDelegarTodasConsultas() {
         var adapter = new OrcamentoRepositoryAdapter(orcamentoRepository, orcamentoMapper);
         var orcamento = new Orcamento();
         var orcamentoEntity = new OrcamentoPersistenceEntity();
         var esperado = Optional.of(orcamento);
         when(orcamentoMapper.toEntity(orcamento)).thenReturn(orcamentoEntity);
         when(orcamentoMapper.toDomain(orcamentoEntity)).thenReturn(orcamento);
         when(orcamentoRepository.save(orcamentoEntity)).thenReturn(orcamentoEntity);
         when(orcamentoRepository.findById(1L)).thenReturn(Optional.of(orcamentoEntity));
        when(orcamentoRepository.findTopByOrdemServicoIdAndTipoOrderByVersaoDesc(1L, TipoOrcamento.PRINCIPAL))
                 .thenReturn(Optional.of(orcamentoEntity));
        when(orcamentoRepository.findTopByNumeroOsAndTipoOrderByVersaoDesc("OS-1", TipoOrcamento.PRINCIPAL))
                 .thenReturn(Optional.of(orcamentoEntity));
        when(orcamentoRepository.findByOrdemServicoIdAndStatus(1L, StatusOrcamento.APROVADO))
                 .thenReturn(Optional.of(orcamentoEntity));
        when(orcamentoRepository.findByNumeroOsAndStatus("OS-1", StatusOrcamento.APROVADO))
                 .thenReturn(Optional.of(orcamentoEntity));
         when(orcamentoRepository.findTopByNumeroOsOrderByVersaoDesc("OS-1"))
                 .thenReturn(Optional.of(orcamentoEntity));

        assertSame(orcamento, adapter.save(orcamento));
        assertEquals(esperado, adapter.findById(1L));
        assertEquals(esperado, adapter.findTopByOrdemServicoIdAndTipoOrderByVersaoDesc(1L, TipoOrcamento.PRINCIPAL));
        assertEquals(esperado, adapter.findTopByNumeroOsAndTipoOrderByVersaoDesc("OS-1", TipoOrcamento.PRINCIPAL));
        assertEquals(esperado, adapter.findByOrdemServicoIdAndStatus(1L, StatusOrcamento.APROVADO));
        assertEquals(esperado, adapter.findByNumeroOsAndStatus("OS-1", StatusOrcamento.APROVADO));
        assertEquals(esperado, adapter.findTopByNumeroOsOrderByVersaoDesc("OS-1"));
    }

    @Test
    void ordemServicoAdapterDeveDelegarTodasConsultas() {
         var adapter = new OrdemServicoRepositoryAdapter(ordemRepository, ordemMapper);
         var ordem = new OrdemServico();
         var ordemEntity = new OrdemServicoEntity();
         var esperado = Optional.of(ordem);
         when(ordemMapper.toEntity(ordem)).thenReturn(ordemEntity);
         when(ordemMapper.toDomain(ordemEntity)).thenReturn(ordem);


         when(ordemRepository.save(ordemEntity)).thenReturn(ordemEntity);
         when(ordemRepository.findById(1L)).thenReturn(Optional.of(ordemEntity));
         when(ordemRepository.findByNumeroOs("OS-1")).thenReturn(Optional.of(ordemEntity));
         when(ordemRepository.findByNumeroOsForUpdate("OS-1")).thenReturn(Optional.of(ordemEntity));
         when(ordemRepository.findByCliente_IdOrderByDataAberturaDesc(2L)).thenReturn(List.of(ordemEntity));
         when(ordemRepository.findAllByOrderByDataAberturaDesc()).thenReturn(List.of(ordemEntity));
         when(ordemRepository.findAll(any(Specification.class), any(Pageable.class)))
                 .thenReturn(new PageImpl<>(List.of(ordemEntity)));

        assertSame(ordem, adapter.save(ordem));
        assertEquals(esperado, adapter.findById(1L));
        assertEquals(esperado, adapter.findByNumeroOs("OS-1"));
        assertEquals(esperado, adapter.findByNumeroOsForUpdate("OS-1"));
        assertEquals(List.of(ordem), adapter.findByClienteIdOrderByDataAberturaDesc(2L));
        assertEquals(List.of(ordem), adapter.findAllByOrderByDataAberturaDesc());
        assertEquals(List.of(ordem), adapter.findAll(
                new OrdemServicoFiltroInput(null, null, null), null, new PageQuery(0, 10)).content());

        var pageableCaptor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(ordemRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertTrue(pageable.getSort().isUnsorted());
    }

    @Test
    void metricsAdapterDeveOcultarProjectionsDosCasosDeUso() {
        var ordemProjection = mock(TempoMedioOrdemServicoProjection.class);
        var servicoProjection = mock(TempoMedioServicoProjection.class);
        when(ordemProjection.getQuantidadeOrdensFinalizadas()).thenReturn(4L);
        when(ordemProjection.getTempoMedioSegundos()).thenReturn(1800.0);
        when(servicoProjection.getServicoId()).thenReturn(7L);
        when(servicoProjection.getNomeServico()).thenReturn("Alinhamento");
        when(servicoProjection.getQuantidadeExecucoes()).thenReturn(3L);
        when(servicoProjection.getTempoMedioSegundos()).thenReturn(900.0);
        when(ordemRepository.calcularTempoMedioFinalizacao()).thenReturn(ordemProjection);
        when(servicoSolicitadoRepository.calcularTempoMedioPorServico())
                .thenReturn(List.of(servicoProjection));

        var adapter = new MetricsRepositoryAdapter(ordemRepository, servicoSolicitadoRepository);
        var ordem = adapter.calcularTempoMedioOrdensServico();
        var servicos = adapter.calcularTempoMedioPorServico();

        assertEquals(4L, ordem.quantidadeOrdensFinalizadas());
        assertEquals(1800.0, ordem.tempoMedioSegundos());
        assertEquals(7L, servicos.getFirst().servicoId());
        assertEquals("Alinhamento", servicos.getFirst().nomeServico());
        assertEquals(3L, servicos.getFirst().quantidadeExecucoes());
        assertEquals(900.0, servicos.getFirst().tempoMedioSegundos());
    }

    @Test
    void metricsAdapterDevePreservarTempoNuloQuandoProjectionDeOrdensForNula() {
        when(ordemRepository.calcularTempoMedioFinalizacao()).thenReturn(null);

        var ordem = new MetricsRepositoryAdapter(ordemRepository, servicoSolicitadoRepository)
                .calcularTempoMedioOrdensServico();

        assertEquals(0L, ordem.quantidadeOrdensFinalizadas());
        org.junit.jupiter.api.Assertions.assertNull(ordem.tempoMedioSegundos());
    }
}
