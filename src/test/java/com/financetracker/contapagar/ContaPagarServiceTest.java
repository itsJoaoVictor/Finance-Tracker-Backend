package com.financetracker.contapagar;

import com.financetracker.cartao.entity.Cartao;
import com.financetracker.cartao.repository.CartaoRepository;
import com.financetracker.categoria.entity.Categoria;
import com.financetracker.categoria.repository.CategoriaRepository;
import com.financetracker.conta.entity.Conta;
import com.financetracker.conta.repository.ContaRepository;
import com.financetracker.contapagar.dto.*;
import com.financetracker.contapagar.entity.ContaPagarAmortizacao;
import com.financetracker.contapagar.entity.ContaPagarModelo;
import com.financetracker.contapagar.entity.ContaPagarOcorrencia;
import com.financetracker.contapagar.enums.StatusOcorrencia;
import com.financetracker.contapagar.enums.TipoContaPagar;
import com.financetracker.contapagar.repository.ContaPagarAmortizacaoRepository;
import com.financetracker.contapagar.repository.ContaPagarModeloRepository;
import com.financetracker.contapagar.repository.ContaPagarOcorrenciaRepository;
import com.financetracker.contapagar.service.ContaPagarService;
import com.financetracker.contapagar.service.NotificacaoExternaService;
import com.financetracker.contapagar.util.DiaUtilUtils;
import com.financetracker.ia.domain.IaInsight;
import com.financetracker.ia.repository.IaInsightRepository;
import com.financetracker.transacao.dto.TransacaoResponse;
import com.financetracker.transacao.entity.Transacao;
import com.financetracker.transacao.enums.TipoTransacao;
import com.financetracker.transacao.exception.SaldoInsuficienteException;
import com.financetracker.transacao.repository.TransacaoRepository;
import com.financetracker.transacao.service.TransacaoService;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContaPagarServiceTest {

    @Mock private ContaPagarModeloRepository modeloRepository;
    @Mock private ContaPagarOcorrenciaRepository ocorrenciaRepository;
    @Mock private ContaPagarAmortizacaoRepository amortizacaoRepository;
    @Mock private CategoriaRepository categoriaRepository;
    @Mock private ContaRepository contaRepository;
    @Mock private CartaoRepository cartaoRepository;
    @Mock private TransacaoRepository transacaoRepository;
    @Mock private TransacaoService transacaoService;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private IaInsightRepository iaInsightRepository;
    @Mock private NotificacaoExternaService notificacaoExternaService;

    @InjectMocks private ContaPagarService contaPagarService;

    private Usuario usuario;
    private Categoria categoria;
    private Conta conta;
    private UUID contaId;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setEmail("user@test.com");

        categoria = new Categoria();
        categoria.setId(UUID.randomUUID());
        categoria.setNome("Moradia");
        categoria.setUsuario(usuario);

        contaId = UUID.randomUUID();
        conta = new Conta();
        conta.setNome("Conta Corrente");
        conta.setSaldo(BigDecimal.valueOf(5000));
        conta.setUsuario(usuario);

        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn("user@test.com");
        SecurityContext sc = mock(SecurityContext.class);
        lenient().when(sc.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(sc);

        lenient().when(usuarioRepository.findByEmail("user@test.com")).thenReturn(Optional.of(usuario));
    }

    @Test
    void diaUtilUtils_DeveAjustarFimDeSemanaEFevereiro() {
        // Fevereiro 2026 tem 28 dias. Dia 31 deve virar 28/02/2026 (sábado) -> próximo dia útil 02/03/2026 (segunda)
        LocalDate venc = DiaUtilUtils.calcularDataVencimento(LocalDate.of(2026, 2, 1), 31);
        assertEquals(LocalDate.of(2026, 3, 2), venc);
    }

    @Test
    void criarModeloParcelado_DeveGerarParcelasAPartirDaParcelaInicial() {
        when(categoriaRepository.findById(categoria.getId())).thenReturn(Optional.of(categoria));
        when(modeloRepository.save(any())).thenAnswer(inv -> {
            ContaPagarModelo m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(ocorrenciaRepository.findByModeloIdAndMesReferencia(any(), any())).thenReturn(Optional.empty());
        when(ocorrenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ContaPagarModeloCriacaoRequest req = new ContaPagarModeloCriacaoRequest(
                "Financiamento Apto",
                "PARCELADO",
                categoria.getId(),
                "Apto Novo",
                15,
                BigDecimal.valueOf(2400),
                360,
                358, // 3 parcelas restantes: 358, 359, 360
                LocalDate.of(2026, 11, 1),
                null,
                false,
                null,
                null,
                null
        );

        ContaPagarModeloResponse resp = contaPagarService.criarModelo(req);
        assertEquals("PARCELADO", resp.tipo());
        assertEquals(360, resp.totalParcelas());
        assertEquals(358, resp.parcelaInicial());
        verify(ocorrenciaRepository, times(3)).save(any(ContaPagarOcorrencia.class));
    }

    @Test
    void pagarOcorrencia_ViaConta_DeveDebitarSaldoECriarTransacaoPagamentoBoleto() {
        ContaPagarModelo modelo = new ContaPagarModelo();
        modelo.setId(UUID.randomUUID());
        modelo.setUsuario(usuario);
        modelo.setNome("Conta de Luz");
        modelo.setTipo(TipoContaPagar.VARIAVEL);
        modelo.setCategoria(categoria);
        modelo.setDiaVencimento(10);

        ContaPagarOcorrencia oc = new ContaPagarOcorrencia();
        oc.setId(UUID.randomUUID());
        oc.setUsuario(usuario);
        oc.setModelo(modelo);
        oc.setMesReferencia(LocalDate.of(2026, 11, 1));
        oc.setDataVencimento(LocalDate.of(2026, 11, 10));
        oc.setValorEstimado(BigDecimal.valueOf(200));
        oc.setStatus(StatusOcorrencia.AGUARDANDO_VALOR);

        when(ocorrenciaRepository.findByIdAndUsuarioId(oc.getId(), usuario.getId())).thenReturn(Optional.of(oc));
        when(contaRepository.findByIdAndUsuarioIdAndAtivoTrue(contaId, usuario.getId())).thenReturn(Optional.of(conta));
        when(transacaoRepository.save(any())).thenAnswer(inv -> {
            Transacao t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(ocorrenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ocorrenciaRepository.findByModeloIdAndStatusOrderByMesReferenciaDesc(any(), any())).thenReturn(List.of());

        PagarOcorrenciaRequest req = new PagarOcorrenciaRequest(
                "CONTA",
                contaId,
                null,
                LocalDate.of(2026, 11, 10),
                BigDecimal.valueOf(235.50),
                "12345.67890",
                "Pago no app"
        );

        ContaPagarOcorrenciaResponse res = contaPagarService.pagarOcorrencia(oc.getId(), req);

        assertEquals("PAGA", res.status());
        assertEquals(BigDecimal.valueOf(235.50), res.valorPago());
        assertEquals(BigDecimal.valueOf(4764.50), conta.getSaldo());
        verify(transacaoRepository).save(argThat(t -> t.getTipo() == TipoTransacao.PAGAMENTO_BOLETO));
    }

    @Test
    void pagarOcorrencia_ViaCartaoEmContaVariavel_DeveGerarCompraCredito() {
        ContaPagarModelo modelo = new ContaPagarModelo();
        modelo.setId(UUID.randomUUID());
        modelo.setUsuario(usuario);
        modelo.setNome("Conta de Água");
        modelo.setTipo(TipoContaPagar.VARIAVEL);
        modelo.setCategoria(categoria);
        modelo.setDiaVencimento(15);

        ContaPagarOcorrencia oc = new ContaPagarOcorrencia();
        oc.setId(UUID.randomUUID());
        oc.setUsuario(usuario);
        oc.setModelo(modelo);
        oc.setMesReferencia(LocalDate.of(2026, 11, 1));
        oc.setDataVencimento(LocalDate.of(2026, 11, 15));
        oc.setStatus(StatusOcorrencia.PENDENTE);

        UUID cartaoId = UUID.randomUUID();
        Cartao cartao = new Cartao();
        cartao.setId(cartaoId);
        cartao.setUsuario(usuario);

        UUID txId = UUID.randomUUID();
        Transacao txSalva = new Transacao();
        txSalva.setId(txId);
        txSalva.setTipo(TipoTransacao.COMPRA_CREDITO);

        TransacaoResponse txResp = new TransacaoResponse(
                txId, "Conta de Água - 11/2026", BigDecimal.valueOf(120),
                TipoTransacao.COMPRA_CREDITO.name(), null, null, null, null,
                cartaoId, null, categoria.getId(), categoria.getNome(),
                null, null, null, null,
                LocalDate.of(2026, 11, 15), 1, 1, null, false, List.of(), null, null
        );

        when(ocorrenciaRepository.findByIdAndUsuarioId(oc.getId(), usuario.getId())).thenReturn(Optional.of(oc));
        when(cartaoRepository.findByIdAndUsuarioIdAndAtivoTrue(cartaoId, usuario.getId())).thenReturn(Optional.of(cartao));
        when(transacaoService.criar(any())).thenReturn(txResp);
        when(transacaoRepository.findById(txId)).thenReturn(Optional.of(txSalva));
        when(ocorrenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ocorrenciaRepository.findByModeloIdAndStatusOrderByMesReferenciaDesc(any(), any())).thenReturn(List.of());

        PagarOcorrenciaRequest req = new PagarOcorrenciaRequest(
                "CARTAO", null, cartaoId, LocalDate.of(2026, 11, 15), BigDecimal.valueOf(120), null, null);

        ContaPagarOcorrenciaResponse res = contaPagarService.pagarOcorrencia(oc.getId(), req);
        assertEquals("PAGA", res.status());
        assertEquals(txId, res.transacaoId());
        verify(transacaoService).criar(argThat(r -> "COMPRA_CREDITO".equals(r.tipo())));
    }

    @Test
    void pagarOcorrencia_ViaCartaoEmParcelado_DeveLancar422() {
        ContaPagarModelo modelo = new ContaPagarModelo();
        modelo.setId(UUID.randomUUID());
        modelo.setUsuario(usuario);
        modelo.setNome("Financiamento");
        modelo.setTipo(TipoContaPagar.PARCELADO);
        modelo.setCategoria(categoria);

        ContaPagarOcorrencia oc = new ContaPagarOcorrencia();
        oc.setId(UUID.randomUUID());
        oc.setUsuario(usuario);
        oc.setModelo(modelo);
        oc.setStatus(StatusOcorrencia.PENDENTE);

        when(ocorrenciaRepository.findByIdAndUsuarioId(oc.getId(), usuario.getId())).thenReturn(Optional.of(oc));

        PagarOcorrenciaRequest req = new PagarOcorrenciaRequest(
                "CARTAO", null, UUID.randomUUID(), LocalDate.now(), BigDecimal.valueOf(1900), null, null);

        assertThrows(ResponseStatusException.class, () -> contaPagarService.pagarOcorrencia(oc.getId(), req));
    }

    @Test
    void pagarOcorrencia_SaldoInsuficiente_DeveLancarSaldoInsuficienteException() {
        conta.setSaldo(BigDecimal.valueOf(100));

        ContaPagarModelo modelo = new ContaPagarModelo();
        modelo.setId(UUID.randomUUID());
        modelo.setUsuario(usuario);
        modelo.setNome("Boleto Imóvel");
        modelo.setTipo(TipoContaPagar.PARCELADO);
        modelo.setCategoria(categoria);

        ContaPagarOcorrencia oc = new ContaPagarOcorrencia();
        oc.setId(UUID.randomUUID());
        oc.setUsuario(usuario);
        oc.setModelo(modelo);
        oc.setMesReferencia(LocalDate.of(2026, 11, 1));
        oc.setStatus(StatusOcorrencia.PENDENTE);

        when(ocorrenciaRepository.findByIdAndUsuarioId(oc.getId(), usuario.getId())).thenReturn(Optional.of(oc));
        when(contaRepository.findByIdAndUsuarioIdAndAtivoTrue(contaId, usuario.getId())).thenReturn(Optional.of(conta));

        PagarOcorrenciaRequest req = new PagarOcorrenciaRequest(
                "CONTA", contaId, null, LocalDate.now(), BigDecimal.valueOf(1900), null, null);

        assertThrows(SaldoInsuficienteException.class, () -> contaPagarService.pagarOcorrencia(oc.getId(), req));
    }

    @Test
    void atualizarOcorrencia_ConfirmarValorAcimaDaMedia_DeveMudarParaPendenteEGerarInsight() {
        ContaPagarModelo modelo = new ContaPagarModelo();
        modelo.setId(UUID.randomUUID());
        modelo.setUsuario(usuario);
        modelo.setNome("Luz");
        modelo.setTipo(TipoContaPagar.VARIAVEL);
        modelo.setCategoria(categoria);
        modelo.setValorPadrao(BigDecimal.valueOf(100));

        ContaPagarOcorrencia oc = new ContaPagarOcorrencia();
        oc.setId(UUID.randomUUID());
        oc.setUsuario(usuario);
        oc.setModelo(modelo);
        oc.setMesReferencia(LocalDate.of(2026, 11, 1));
        oc.setDataVencimento(LocalDate.of(2026, 11, 10));
        oc.setStatus(StatusOcorrencia.AGUARDANDO_VALOR);

        when(ocorrenciaRepository.findByIdAndUsuarioId(oc.getId(), usuario.getId())).thenReturn(Optional.of(oc));
        when(ocorrenciaRepository.findByModeloIdAndStatusOrderByMesReferenciaDesc(modelo.getId(), StatusOcorrencia.PAGA))
                .thenReturn(List.of());
        when(iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuario.getId()))
                .thenReturn(List.of());
        when(ocorrenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AtualizarOcorrenciaRequest req = new AtualizarOcorrenciaRequest(
                BigDecimal.valueOf(200), null, null, "12345", "Bandeira vermelha");

        ContaPagarOcorrenciaResponse res = contaPagarService.atualizarOcorrencia(oc.getId(), req);
        assertEquals("PENDENTE", res.status());
        assertEquals(BigDecimal.valueOf(200), res.valorReal());
        verify(iaInsightRepository).save(any(IaInsight.class));
    }

    @Test
    void amortizarFinanciamento_ExternaReduzirPrazo_DeveCancelarUltimasParcelas() {
        UUID modeloId = UUID.randomUUID();
        ContaPagarModelo modelo = new ContaPagarModelo();
        modelo.setId(modeloId);
        modelo.setUsuario(usuario);
        modelo.setNome("Financiamento Caixa");
        modelo.setTipo(TipoContaPagar.PARCELADO);
        modelo.setCategoria(categoria);
        modelo.setDiaVencimento(15);
        modelo.setTotalParcelas(10);
        modelo.setParcelaInicial(8);
        modelo.setDataInicio(LocalDate.of(2026, 11, 1));
        modelo.setValorPadrao(BigDecimal.valueOf(2000));

        ContaPagarOcorrencia o8 = new ContaPagarOcorrencia();
        o8.setId(UUID.randomUUID()); o8.setModelo(modelo); o8.setStatus(StatusOcorrencia.PENDENTE);
        o8.setMesReferencia(LocalDate.of(2026, 11, 1)); o8.setDataVencimento(LocalDate.of(2026, 11, 15));
        o8.setValorReal(BigDecimal.valueOf(2000));

        ContaPagarOcorrencia o9 = new ContaPagarOcorrencia();
        o9.setId(UUID.randomUUID()); o9.setModelo(modelo); o9.setStatus(StatusOcorrencia.PENDENTE);
        o9.setMesReferencia(LocalDate.of(2026, 12, 1)); o9.setDataVencimento(LocalDate.of(2026, 12, 15));
        o9.setValorReal(BigDecimal.valueOf(2000));

        ContaPagarOcorrencia o10 = new ContaPagarOcorrencia();
        o10.setId(UUID.randomUUID()); o10.setModelo(modelo); o10.setStatus(StatusOcorrencia.PENDENTE);
        o10.setMesReferencia(LocalDate.of(2027, 1, 1)); o10.setDataVencimento(LocalDate.of(2027, 1, 15));
        o10.setValorReal(BigDecimal.valueOf(2000));

        when(modeloRepository.findByIdAndUsuarioIdAndAtivoTrue(modeloId, usuario.getId())).thenReturn(Optional.of(modelo));
        when(ocorrenciaRepository.findByModeloIdOrderByMesReferenciaAsc(modeloId)).thenReturn(List.of(o8, o9, o10));
        when(amortizacaoRepository.findByModeloIdOrderByDataDesc(modeloId)).thenReturn(List.of());

        AmortizarRequest req = new AmortizarRequest(
                BigDecimal.valueOf(10000), LocalDate.now(), "EXTERNA", null, "REDUZIR_PRAZO", 2, null, "FGTS");

        contaPagarService.amortizarFinanciamento(modeloId, req);

        assertEquals(StatusOcorrencia.PENDENTE, o8.getStatus());
        assertEquals(StatusOcorrencia.CANCELADA, o9.getStatus());
        assertEquals(StatusOcorrencia.CANCELADA, o10.getStatus());
        assertEquals(8, modelo.getTotalParcelas());
        verify(amortizacaoRepository).save(any(ContaPagarAmortizacao.class));
        verify(transacaoRepository, never()).save(any());
    }

    @Test
    void processarRotinaDiaria_DebitoAutomaticoSemValor_NaoPagaENotifica() {
        LocalDate hoje = DiaUtilUtils.hoje();
        ContaPagarModelo modelo = new ContaPagarModelo();
        modelo.setId(UUID.randomUUID());
        modelo.setUsuario(usuario);
        modelo.setNome("Água");
        modelo.setTipo(TipoContaPagar.VARIAVEL);
        modelo.setCategoria(categoria);
        modelo.setDiaVencimento(hoje.getDayOfMonth());
        modelo.setDataInicio(hoje.withDayOfMonth(1));
        modelo.setDebitoAutomatico(true);
        modelo.setContaPadrao(conta);
        modelo.setAtivo(true);

        ContaPagarOcorrencia oc = new ContaPagarOcorrencia();
        oc.setId(UUID.randomUUID());
        oc.setUsuario(usuario);
        oc.setModelo(modelo);
        oc.setMesReferencia(hoje.withDayOfMonth(1));
        oc.setDataVencimento(hoje);
        oc.setStatus(StatusOcorrencia.AGUARDANDO_VALOR);

        when(modeloRepository.findByAtivoTrue()).thenReturn(List.of(modelo));
        when(ocorrenciaRepository.findByModeloIdAndMesReferencia(any(), any())).thenReturn(Optional.of(oc));
        when(ocorrenciaRepository.findAllPendentesAteData(any())).thenReturn(List.of(oc));
        when(iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuario.getId())).thenReturn(List.of());

        contaPagarService.processarRotinaDiaria();

        assertEquals(StatusOcorrencia.AGUARDANDO_VALOR, oc.getStatus());
        verify(notificacaoExternaService).notificarOcorrenciaSeNecessario(eq(oc), eq("DEBITO_SEM_VALOR"), anyString());
        verify(transacaoRepository, never()).save(any());
    }
}
