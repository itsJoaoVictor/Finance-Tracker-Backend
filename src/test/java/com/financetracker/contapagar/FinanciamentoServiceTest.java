package com.financetracker.contapagar;

import com.financetracker.contapagar.dto.*;
import com.financetracker.contapagar.entity.*;
import com.financetracker.contapagar.enums.ModoParcelamento;
import com.financetracker.contapagar.enums.StatusOcorrencia;
import com.financetracker.contapagar.enums.TipoContaPagar;
import com.financetracker.contapagar.repository.*;
import com.financetracker.contapagar.service.FinanciamentoService;
import com.financetracker.transacao.entity.Transacao;
import com.financetracker.transacao.enums.TipoTransacao;
import com.financetracker.transacao.repository.TransacaoRepository;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanciamentoServiceTest {

    @Mock private FinanciamentoRepository financiamentoRepository;
    @Mock private FinanciamentoTransacaoRepository financiamentoTransacaoRepository;
    @Mock private ContaPagarModeloRepository modeloRepository;
    @Mock private ContaPagarOcorrenciaRepository ocorrenciaRepository;
    @Mock private ContaPagarAmortizacaoRepository amortizacaoRepository;
    @Mock private TransacaoRepository transacaoRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks private FinanciamentoService financiamentoService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setEmail("user@test.com");

        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn("user@test.com");
        SecurityContext sc = mock(SecurityContext.class);
        lenient().when(sc.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(sc);

        lenient().when(usuarioRepository.findByEmail("user@test.com")).thenReturn(Optional.of(usuario));
    }

    @Test
    void obterPorId_DeveConsolidarPagoAnteriorBoletosAmortizacoesTransacoesERestante() {
        UUID finId = UUID.randomUUID();
        Financiamento fin = new Financiamento();
        fin.setId(finId);
        fin.setUsuario(usuario);
        fin.setNome("Meu Apê MCMV");
        fin.setValorPagoAnteriormente(new BigDecimal("5000.00"));
        fin.setValorContratado(new BigDecimal("10000.00"));
        fin.setAtivo(true);

        ContaPagarModelo mParcelado = new ContaPagarModelo();
        mParcelado.setId(UUID.randomUUID());
        mParcelado.setNome("Entrada Construtora");
        mParcelado.setTipo(TipoContaPagar.PARCELADO);
        mParcelado.setModoParcelamento(ModoParcelamento.PERSONALIZADO);
        mParcelado.setDiaVencimento(10);
        mParcelado.setTotalParcelas(3);
        mParcelado.setFinanciamento(fin);

        ContaPagarModelo mAvulso = new ContaPagarModelo();
        mAvulso.setId(UUID.randomUUID());
        mAvulso.setNome("Sinal Avulso Pago");
        mAvulso.setTipo(TipoContaPagar.AVULSO);
        mAvulso.setDiaVencimento(15);
        mAvulso.setTotalParcelas(1);
        mAvulso.setFinanciamento(fin);

        ContaPagarOcorrencia ocPaga = new ContaPagarOcorrencia();
        ocPaga.setModelo(mParcelado);
        ocPaga.setStatus(StatusOcorrencia.PAGA);
        ocPaga.setValorPago(new BigDecimal("1950.00"));

        ContaPagarOcorrencia ocAberta = new ContaPagarOcorrencia();
        ocAberta.setModelo(mParcelado);
        ocAberta.setStatus(StatusOcorrencia.PENDENTE);
        ocAberta.setValorReal(new BigDecimal("1334.70"));

        ContaPagarOcorrencia ocAvulsoPago = new ContaPagarOcorrencia();
        ocAvulsoPago.setModelo(mAvulso);
        ocAvulsoPago.setStatus(StatusOcorrencia.PAGA);
        ocAvulsoPago.setValorPago(new BigDecimal("1000.00"));

        ContaPagarAmortizacao amort = new ContaPagarAmortizacao();
        amort.setModelo(mParcelado);
        amort.setValor(new BigDecimal("500.00"));
        amort.setDescontoObtido(new BigDecimal("250.00"));

        Transacao txSolta = new Transacao();
        txSolta.setId(UUID.randomUUID());
        txSolta.setDescricao("ITBI Prefeitura");
        txSolta.setValor(new BigDecimal("1550.00"));
        txSolta.setData(LocalDate.of(2026, 9, 10));
        txSolta.setTipo(TipoTransacao.PIX);
        txSolta.setAtivo(true);
        txSolta.setEstornada(false);

        FinanciamentoTransacao ft = new FinanciamentoTransacao();
        ft.setId(UUID.randomUUID());
        ft.setFinanciamento(fin);
        ft.setTransacao(txSolta);

        when(financiamentoRepository.findByIdAndUsuarioIdAndAtivoTrue(finId, usuario.getId())).thenReturn(Optional.of(fin));
        when(modeloRepository.findByUsuarioIdAndFinanciamentoIdAndAtivoTrueOrderByNomeAsc(usuario.getId(), finId))
                .thenReturn(List.of(mParcelado, mAvulso));
        when(ocorrenciaRepository.findByModeloIdIn(any())).thenReturn(List.of(ocPaga, ocAberta, ocAvulsoPago));
        when(amortizacaoRepository.findByModeloIdIn(any())).thenReturn(List.of(amort));
        when(financiamentoTransacaoRepository.findByFinanciamentoIdWithTransacao(finId)).thenReturn(List.of(ft));

        FinanciamentoResumoResponse resp = financiamentoService.obterPorId(finId);

        // Total pago = 5000 (anterior) + 1950 + 1000 (boletos) + 500 (amort) + 1550 (tx solta) = 10000.00
        assertEquals(new BigDecimal("10000.00"), resp.totalPago());
        assertEquals(new BigDecimal("1334.70"), resp.totalRestante());
        assertEquals(new BigDecimal("11334.70"), resp.valorTotal());
        assertEquals(new BigDecimal("10000.00"), resp.valorContratado());
        assertEquals(new BigDecimal("1334.70"), resp.jurosEncargosProjetados());
        assertEquals(13.35, resp.percentualJurosProjetados());
        assertEquals(new BigDecimal("250.00"), resp.totalDescontoObtido());
        assertEquals(2, resp.qtdModelosVinculados());
        assertEquals(1, resp.qtdTransacoesVinculadas());
        assertFalse(resp.concluido());
    }

    @Test
    void vincularItens_DeveBloquearTransacaoJaPertencenteAUmBoletoParaEvitarDuplicidade() {
        UUID finId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();

        Financiamento fin = new Financiamento();
        fin.setId(finId);
        fin.setUsuario(usuario);
        fin.setNome("Meu Apê");

        when(financiamentoRepository.findByIdAndUsuarioIdAndAtivoTrue(finId, usuario.getId())).thenReturn(Optional.of(fin));
        when(ocorrenciaRepository.findTransacaoIdsVinculadasByUsuarioId(usuario.getId())).thenReturn(Set.of(txId));
        when(amortizacaoRepository.findTransacaoIdsVinculadasByUsuarioId(usuario.getId())).thenReturn(Set.of());
        when(financiamentoTransacaoRepository.existsByTransacaoId(txId)).thenReturn(false);

        assertThrows(ResponseStatusException.class, () ->
                financiamentoService.vincularItens(finId, new VincularItensFinanciamentoRequest(List.of(), List.of(txId))));
        verify(financiamentoTransacaoRepository, never()).save(any());
    }

    @Test
    void excluirFinanciamento_DeveDesvincularModelosSemExcluirContas() {
        UUID finId = UUID.randomUUID();
        Financiamento fin = new Financiamento();
        fin.setId(finId);
        fin.setUsuario(usuario);
        fin.setNome("Meu Apê");
        fin.setAtivo(true);

        ContaPagarModelo modelo = new ContaPagarModelo();
        modelo.setId(UUID.randomUUID());
        modelo.setFinanciamento(fin);
        modelo.setAtivo(true);

        when(financiamentoRepository.findByIdAndUsuarioIdAndAtivoTrue(finId, usuario.getId())).thenReturn(Optional.of(fin));
        when(modeloRepository.findByUsuarioIdAndFinanciamentoIdAndAtivoTrueOrderByNomeAsc(usuario.getId(), finId))
                .thenReturn(List.of(modelo));
        when(financiamentoTransacaoRepository.findByFinanciamentoIdWithTransacao(finId)).thenReturn(List.of());

        financiamentoService.excluir(finId);

        assertNull(modelo.getFinanciamento());
        assertTrue(modelo.getAtivo());
        assertFalse(fin.getAtivo());
        verify(modeloRepository).save(modelo);
        verify(financiamentoRepository).save(fin);
    }
}
