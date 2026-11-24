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
import com.financetracker.contapagar.enums.DirecaoAbatimento;
import com.financetracker.contapagar.enums.ModoParcelamento;
import com.financetracker.contapagar.enums.StatusOcorrencia;
import com.financetracker.contapagar.enums.TipoContaPagar;
import com.financetracker.contapagar.repository.ContaPagarAmortizacaoRepository;
import com.financetracker.contapagar.repository.ContaPagarModeloRepository;
import com.financetracker.contapagar.repository.ContaPagarOcorrenciaRepository;
import com.financetracker.contapagar.repository.FinanciamentoRepository;
import com.financetracker.contapagar.service.ContaPagarService;
import com.financetracker.contapagar.service.NotificacaoExternaService;
import com.financetracker.contapagar.util.DiaUtilUtils;
import com.financetracker.contapagar.util.EstimadorParcelasUtils;
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
import org.mockito.ArgumentCaptor;
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
    @Mock private FinanciamentoRepository financiamentoRepository;
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
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        ContaPagarModeloResponse resp = contaPagarService.criarModelo(req);
        assertEquals("PARCELADO", resp.tipo());
        assertEquals("FIXO", resp.modoParcelamento());
        assertEquals(1, resp.intervaloMeses());
        assertEquals(360, resp.totalParcelas());
        assertEquals(358, resp.parcelaInicial());
        verify(ocorrenciaRepository, times(3)).save(any(ContaPagarOcorrencia.class));
    }

    @Test
    void criarModeloParcelado_ProgressivoDecrescenteSAC_ComValoresEstimados_DeveInterpolarLinearmente() {
        when(categoriaRepository.findById(categoria.getId())).thenReturn(Optional.of(categoria));
        when(modeloRepository.save(any())).thenAnswer(inv -> {
            ContaPagarModelo m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(ocorrenciaRepository.findByModeloIdAndMesReferencia(any(), any())).thenReturn(Optional.empty());
        when(ocorrenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ContaPagarModeloCriacaoRequest req = new ContaPagarModeloCriacaoRequest(
                "MCMV Caixa",
                "PARCELADO",
                categoria.getId(),
                "Meu Apê",
                15,
                new BigDecimal("740.00"),
                5,
                1,
                LocalDate.of(2027, 1, 1),
                null,
                false,
                null,
                null,
                null,
                "PROGRESSIVO",
                new BigDecimal("680.00"),
                1,
                true,
                null,
                null
        );

        ContaPagarModeloResponse resp = contaPagarService.criarModelo(req);
        assertEquals("PROGRESSIVO", resp.modoParcelamento());
        assertEquals(new BigDecimal("680.00"), resp.valorUltimaParcela());
        assertTrue(resp.valoresEstimados());

        ArgumentCaptor<ContaPagarOcorrencia> captor = ArgumentCaptor.forClass(ContaPagarOcorrencia.class);
        verify(ocorrenciaRepository, times(5)).save(captor.capture());
        List<ContaPagarOcorrencia> geradas = captor.getAllValues();

        assertEquals(new BigDecimal("740.00"), geradas.get(0).getValorEstimado());
        assertEquals(new BigDecimal("725.00"), geradas.get(1).getValorEstimado());
        assertEquals(new BigDecimal("710.00"), geradas.get(2).getValorEstimado());
        assertEquals(new BigDecimal("695.00"), geradas.get(3).getValorEstimado());
        assertEquals(new BigDecimal("680.00"), geradas.get(4).getValorEstimado());
        for (ContaPagarOcorrencia o : geradas) {
            assertNull(o.getValorReal());
            assertEquals(StatusOcorrencia.AGUARDANDO_VALOR, o.getStatus());
        }
    }

    @Test
    void criarModeloParcelado_ProgressivoCrescenteTaxaDeObra_DeveSubirLinearmente() {
        when(categoriaRepository.findById(categoria.getId())).thenReturn(Optional.of(categoria));
        when(modeloRepository.save(any())).thenAnswer(inv -> {
            ContaPagarModelo m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(ocorrenciaRepository.findByModeloIdAndMesReferencia(any(), any())).thenReturn(Optional.empty());
        when(ocorrenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ContaPagarModeloCriacaoRequest req = new ContaPagarModeloCriacaoRequest(
                "Taxa de Obra Caixa",
                "PARCELADO",
                categoria.getId(),
                "Meu Apê",
                15,
                new BigDecimal("200.00"),
                3,
                1,
                LocalDate.of(2026, 12, 1),
                null,
                false,
                null,
                null,
                null,
                "PROGRESSIVO",
                new BigDecimal("600.00"),
                1,
                true,
                null,
                null
        );

        contaPagarService.criarModelo(req);

        ArgumentCaptor<ContaPagarOcorrencia> captor = ArgumentCaptor.forClass(ContaPagarOcorrencia.class);
        verify(ocorrenciaRepository, times(3)).save(captor.capture());
        List<ContaPagarOcorrencia> geradas = captor.getAllValues();
        assertEquals(new BigDecimal("200.00"), geradas.get(0).getValorEstimado());
        assertEquals(new BigDecimal("400.00"), geradas.get(1).getValorEstimado());
        assertEquals(new BigDecimal("600.00"), geradas.get(2).getValorEstimado());
    }

    @Test
    void criarModeloParcelado_PersonalizadoAnual_DeveRespeitarIntervalo12MesesEValoresExatos() {
        when(categoriaRepository.findById(categoria.getId())).thenReturn(Optional.of(categoria));
        when(modeloRepository.save(any())).thenAnswer(inv -> {
            ContaPagarModelo m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(ocorrenciaRepository.findByModeloIdAndMesReferencia(any(), any())).thenReturn(Optional.empty());
        when(ocorrenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ContaPagarModeloCriacaoRequest req = new ContaPagarModeloCriacaoRequest(
                "Anuais Construtora",
                "PARCELADO",
                categoria.getId(),
                "Meu Apê",
                10,
                null,
                2,
                1,
                LocalDate.of(2026, 12, 1),
                null,
                false,
                null,
                null,
                null,
                "PERSONALIZADO",
                null,
                12,
                false,
                List.of(new BigDecimal("2337.61"), new BigDecimal("2257.36")),
                null
        );

        ContaPagarModeloResponse resp = contaPagarService.criarModelo(req);
        assertEquals("PERSONALIZADO", resp.modoParcelamento());
        assertEquals(12, resp.intervaloMeses());
        assertEquals(LocalDate.of(2027, 12, 1), resp.dataFim());
        assertEquals(new BigDecimal("2337.61"), resp.valorPadrao());
        assertEquals(new BigDecimal("2257.36"), resp.valorUltimaParcela());

        ArgumentCaptor<ContaPagarOcorrencia> captor = ArgumentCaptor.forClass(ContaPagarOcorrencia.class);
        verify(ocorrenciaRepository, times(2)).save(captor.capture());
        List<ContaPagarOcorrencia> geradas = captor.getAllValues();

        assertEquals(LocalDate.of(2026, 12, 1), geradas.get(0).getMesReferencia());
        assertEquals(new BigDecimal("2337.61"), geradas.get(0).getValorReal());
        assertEquals(StatusOcorrencia.PENDENTE, geradas.get(0).getStatus());

        assertEquals(LocalDate.of(2027, 12, 1), geradas.get(1).getMesReferencia());
        assertEquals(new BigDecimal("2257.36"), geradas.get(1).getValorReal());
    }

    @Test
    void criarModeloParcelado_ProgressivoComExcecaoCustomizada_DeveSobrescreverApenasExcecao() {
        when(categoriaRepository.findById(categoria.getId())).thenReturn(Optional.of(categoria));
        when(modeloRepository.save(any())).thenAnswer(inv -> {
            ContaPagarModelo m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(ocorrenciaRepository.findByModeloIdAndMesReferencia(any(), any())).thenReturn(Optional.empty());
        when(ocorrenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<BigDecimal> overrides = java.util.Arrays.asList(
                null,
                new BigDecimal("282.23"),
                null,
                null
        );

        ContaPagarModeloCriacaoRequest req = new ContaPagarModeloCriacaoRequest(
                "Mensais Construtora",
                "PARCELADO",
                categoria.getId(),
                "Meu Apê",
                10,
                new BigDecimal("685.00"),
                4,
                1,
                LocalDate.of(2026, 12, 1),
                null,
                false,
                null,
                null,
                null,
                "PROGRESSIVO",
                new BigDecimal("485.00"),
                1,
                false,
                overrides,
                null
        );

        contaPagarService.criarModelo(req);

        ArgumentCaptor<ContaPagarOcorrencia> captor = ArgumentCaptor.forClass(ContaPagarOcorrencia.class);
        verify(ocorrenciaRepository, times(4)).save(captor.capture());
        List<ContaPagarOcorrencia> geradas = captor.getAllValues();

        assertEquals(new BigDecimal("685.00"), geradas.get(0).getValorReal());
        assertEquals(new BigDecimal("282.23"), geradas.get(1).getValorReal());
        assertEquals(new BigDecimal("551.67"), geradas.get(2).getValorReal());
        assertEquals(new BigDecimal("485.00"), geradas.get(3).getValorReal());
    }

    @Test
    void editarModeloParcelado_ProgressivoAplicarFuturas_DeveRecalcularCurvaApenasNasAbertas() {
        UUID modeloId = UUID.randomUUID();
        LocalDate mesAtual = DiaUtilUtils.hoje().withDayOfMonth(1);

        ContaPagarModelo modelo = new ContaPagarModelo();
        modelo.setId(modeloId);
        modelo.setUsuario(usuario);
        modelo.setNome("Taxa de Obra");
        modelo.setTipo(TipoContaPagar.PARCELADO);
        modelo.setModoParcelamento(ModoParcelamento.PROGRESSIVO);
        modelo.setCategoria(categoria);
        modelo.setDiaVencimento(15);
        modelo.setDataInicio(mesAtual.minusMonths(1));

        ContaPagarOcorrencia paga = new ContaPagarOcorrencia();
        paga.setId(UUID.randomUUID());
        paga.setModelo(modelo);
        paga.setMesReferencia(mesAtual.minusMonths(1));
        paga.setStatus(StatusOcorrencia.PAGA);
        paga.setValorPago(new BigDecimal("150.00"));

        ContaPagarOcorrencia f1 = new ContaPagarOcorrencia();
        f1.setId(UUID.randomUUID());
        f1.setModelo(modelo);
        f1.setMesReferencia(mesAtual);
        f1.setStatus(StatusOcorrencia.AGUARDANDO_VALOR);

        ContaPagarOcorrencia f2 = new ContaPagarOcorrencia();
        f2.setId(UUID.randomUUID());
        f2.setModelo(modelo);
        f2.setMesReferencia(mesAtual.plusMonths(1));
        f2.setStatus(StatusOcorrencia.AGUARDANDO_VALOR);

        ContaPagarOcorrencia f3 = new ContaPagarOcorrencia();
        f3.setId(UUID.randomUUID());
        f3.setModelo(modelo);
        f3.setMesReferencia(mesAtual.plusMonths(2));
        f3.setStatus(StatusOcorrencia.AGUARDANDO_VALOR);

        when(modeloRepository.findByIdAndUsuarioIdAndAtivoTrue(modeloId, usuario.getId())).thenReturn(Optional.of(modelo));
        when(categoriaRepository.findById(categoria.getId())).thenReturn(Optional.of(categoria));
        when(ocorrenciaRepository.findByModeloIdOrderByMesReferenciaAsc(modeloId)).thenReturn(List.of(paga, f1, f2, f3));
        when(modeloRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ocorrenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ContaPagarModeloEdicaoRequest req = new ContaPagarModeloEdicaoRequest(
                "Taxa de Obra",
                categoria.getId(),
                "Meu Apê",
                15,
                new BigDecimal("300.00"),
                null,
                false,
                null,
                true,
                "PROGRESSIVO",
                new BigDecimal("700.00"),
                true,
                null
        );

        contaPagarService.editarModelo(modeloId, req);

        assertEquals(new BigDecimal("150.00"), paga.getValorPago());
        assertEquals(new BigDecimal("300.00"), f1.getValorEstimado());
        assertEquals(new BigDecimal("500.00"), f2.getValorEstimado());
        assertEquals(new BigDecimal("700.00"), f3.getValorEstimado());
        assertNull(f1.getValorReal());
        assertEquals(StatusOcorrencia.AGUARDANDO_VALOR, f1.getStatus());
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
                BigDecimal.valueOf(10000), LocalDate.now(), "EXTERNA", null, "REDUZIR_PRAZO", 2, null, "FGTS",
                null, null, null);

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

    @Test
    void estimadorParcelas_TaxaDeObraCrescenteAcelerando_DeveSeguirRitmoERespeitarTeto() {
        List<BigDecimal> projecao = EstimadorParcelasUtils.projetarParcelasFuturas(
                ModoParcelamento.PROGRESSIVO,
                new BigDecimal("150.00"),
                new BigDecimal("700.00"),
                List.of(new BigDecimal("150.00"), new BigDecimal("240.00"), new BigDecimal("350.00")),
                7
        );

        assertEquals(7, projecao.size());
        // Passo ponderado = (1*90 + 2*110)/3 = 103.3333; planejado = (700-350)/7 = 50.00; blend = 87.3333
        assertEquals(new BigDecimal("437.33"), projecao.get(0));
        assertEquals(new BigDecimal("524.67"), projecao.get(1));
        assertEquals(new BigDecimal("612.00"), projecao.get(2));
        assertEquals(new BigDecimal("699.33"), projecao.get(3));
        // Últimas 3 travam no teto de 700.00
        assertEquals(new BigDecimal("700.00"), projecao.get(4));
        assertEquals(new BigDecimal("700.00"), projecao.get(5));
        assertEquals(new BigDecimal("700.00"), projecao.get(6));
    }

    @Test
    void estimadorParcelas_SACDecrescenteComAltaDeTR_DeveReancorarSuavementeAteUltima() {
        List<BigDecimal> projecao = EstimadorParcelasUtils.projetarParcelasFuturas(
                ModoParcelamento.PROGRESSIVO,
                new BigDecimal("740.00"),
                new BigDecimal("680.00"),
                List.of(new BigDecimal("760.00")),
                4
        );

        assertEquals(List.of(
                new BigDecimal("740.00"),
                new BigDecimal("720.00"),
                new BigDecimal("700.00"),
                new BigDecimal("680.00")
        ), projecao);
    }

    @Test
    void estimadorParcelas_FixoEstimadoMCMV_DeveUsarEwmaComHistorico() {
        List<BigDecimal> projecao = EstimadorParcelasUtils.projetarParcelasFuturas(
                ModoParcelamento.FIXO,
                new BigDecimal("709.00"),
                null,
                List.of(new BigDecimal("725.00"), new BigDecimal("728.00")),
                3
        );

        // step1: 0.65*725 + 0.35*709 = 719.40; step2: 0.65*728 + 0.35*719.40 = 724.99
        assertEquals(List.of(
                new BigDecimal("724.99"),
                new BigDecimal("724.99"),
                new BigDecimal("724.99")
        ), projecao);
    }

    @Test
    void recalibrarParcelasFuturas_DeveUsarValorRealSemMultaENaoAlterarPersonalizado() {
        UUID modeloId = UUID.randomUUID();
        ContaPagarModelo modelo = new ContaPagarModelo();
        modelo.setId(modeloId);
        modelo.setUsuario(usuario);
        modelo.setNome("MCMV Caixa");
        modelo.setTipo(TipoContaPagar.PARCELADO);
        modelo.setModoParcelamento(ModoParcelamento.FIXO);
        modelo.setValoresEstimados(true);
        modelo.setValorPadrao(new BigDecimal("700.00"));
        modelo.setCategoria(categoria);

        ContaPagarOcorrencia p1 = new ContaPagarOcorrencia();
        p1.setId(UUID.randomUUID());
        p1.setModelo(modelo);
        p1.setMesReferencia(LocalDate.of(2027, 1, 1));
        p1.setStatus(StatusOcorrencia.PAGA);
        p1.setValorReal(new BigDecimal("700.00"));
        p1.setValorPago(new BigDecimal("750.00")); // R$ 50 de multa por atraso não deve distorcer projeção

        ContaPagarOcorrencia f1 = new ContaPagarOcorrencia();
        f1.setId(UUID.randomUUID());
        f1.setModelo(modelo);
        f1.setMesReferencia(LocalDate.of(2027, 2, 1));
        f1.setStatus(StatusOcorrencia.AGUARDANDO_VALOR);
        f1.setValorEstimado(new BigDecimal("710.00"));

        when(ocorrenciaRepository.findByModeloIdOrderByMesReferenciaAsc(modeloId)).thenReturn(List.of(p1, f1));
        when(ocorrenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        contaPagarService.recalibrarParcelasFuturasPorHistorico(modelo);

        assertEquals(new BigDecimal("700.00"), f1.getValorEstimado());
        verify(ocorrenciaRepository, times(1)).save(f1);

        // No modo PERSONALIZADO, não deve recalibrar automaticamente
        modelo.setModoParcelamento(ModoParcelamento.PERSONALIZADO);
        f1.setValorEstimado(new BigDecimal("282.23"));
        contaPagarService.recalibrarParcelasFuturasPorHistorico(modelo);
        assertEquals(new BigDecimal("282.23"), f1.getValorEstimado());
    }

    @Test
    void atualizarOcorrencia_ConfirmarValorRealEmParceladoEstimado_DeveRecalibrarFuturas() {
        UUID modeloId = UUID.randomUUID();
        ContaPagarModelo modelo = new ContaPagarModelo();
        modelo.setId(modeloId);
        modelo.setUsuario(usuario);
        modelo.setNome("Taxa de Obra");
        modelo.setTipo(TipoContaPagar.PARCELADO);
        modelo.setModoParcelamento(ModoParcelamento.PROGRESSIVO);
        modelo.setValoresEstimados(true);
        modelo.setValorPadrao(new BigDecimal("150.00"));
        modelo.setValorUltimaParcela(new BigDecimal("600.00"));
        modelo.setCategoria(categoria);

        ContaPagarOcorrencia o1 = new ContaPagarOcorrencia();
        o1.setId(UUID.randomUUID());
        o1.setUsuario(usuario);
        o1.setModelo(modelo);
        o1.setMesReferencia(LocalDate.of(2026, 12, 1));
        o1.setDataVencimento(LocalDate.of(2026, 12, 15));
        o1.setStatus(StatusOcorrencia.AGUARDANDO_VALOR);
        o1.setValorEstimado(new BigDecimal("150.00"));

        ContaPagarOcorrencia o2 = new ContaPagarOcorrencia();
        o2.setId(UUID.randomUUID());
        o2.setUsuario(usuario);
        o2.setModelo(modelo);
        o2.setMesReferencia(LocalDate.of(2027, 1, 1));
        o2.setDataVencimento(LocalDate.of(2027, 1, 15));
        o2.setStatus(StatusOcorrencia.AGUARDANDO_VALOR);
        o2.setValorEstimado(new BigDecimal("375.00"));

        ContaPagarOcorrencia o3 = new ContaPagarOcorrencia();
        o3.setId(UUID.randomUUID());
        o3.setUsuario(usuario);
        o3.setModelo(modelo);
        o3.setMesReferencia(LocalDate.of(2027, 2, 1));
        o3.setDataVencimento(LocalDate.of(2027, 2, 15));
        o3.setStatus(StatusOcorrencia.AGUARDANDO_VALOR);
        o3.setValorEstimado(new BigDecimal("600.00"));

        when(ocorrenciaRepository.findByIdAndUsuarioId(o1.getId(), usuario.getId())).thenReturn(Optional.of(o1));
        when(ocorrenciaRepository.findByModeloIdOrderByMesReferenciaAsc(modeloId)).thenReturn(List.of(o1, o2, o3));
        when(ocorrenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AtualizarOcorrenciaRequest req = new AtualizarOcorrenciaRequest(
                new BigDecimal("300.00"), null, null, null, "Obra avançou mais rápido");

        contaPagarService.atualizarOcorrencia(o1.getId(), req);

        // 1 observação real (300) -> reancora até teto 600 em 2 passos: 450 e 600
        assertEquals(StatusOcorrencia.PENDENTE, o1.getStatus());
        assertEquals(new BigDecimal("450.00"), o2.getValorEstimado());
        assertEquals(new BigDecimal("600.00"), o3.getValorEstimado());
    }

    @Test
    void amortizarFinanciamento_UltimasParcelas_DeveCalcularDescontoAutomaticamenteEAtualizarDataFim() {
        UUID modeloId = UUID.randomUUID();
        ContaPagarModelo modelo = new ContaPagarModelo();
        modelo.setId(modeloId);
        modelo.setUsuario(usuario);
        modelo.setNome("MCMV Caixa");
        modelo.setTipo(TipoContaPagar.PARCELADO);
        modelo.setModoParcelamento(ModoParcelamento.PROGRESSIVO);
        modelo.setValoresEstimados(true);
        modelo.setCategoria(categoria);
        modelo.setDiaVencimento(15);
        modelo.setTotalParcelas(4);
        modelo.setParcelaInicial(1);
        modelo.setDataInicio(LocalDate.of(2027, 1, 1));
        modelo.setValorPadrao(new BigDecimal("740.00"));
        modelo.setValorUltimaParcela(new BigDecimal("680.00"));

        ContaPagarOcorrencia o1 = new ContaPagarOcorrencia();
        o1.setId(UUID.randomUUID()); o1.setModelo(modelo); o1.setStatus(StatusOcorrencia.AGUARDANDO_VALOR);
        o1.setMesReferencia(LocalDate.of(2027, 1, 1)); o1.setDataVencimento(LocalDate.of(2027, 1, 15));
        o1.setValorEstimado(new BigDecimal("740.00"));

        ContaPagarOcorrencia o2 = new ContaPagarOcorrencia();
        o2.setId(UUID.randomUUID()); o2.setModelo(modelo); o2.setStatus(StatusOcorrencia.AGUARDANDO_VALOR);
        o2.setMesReferencia(LocalDate.of(2027, 2, 1)); o2.setDataVencimento(LocalDate.of(2027, 2, 15));
        o2.setValorEstimado(new BigDecimal("720.00"));

        ContaPagarOcorrencia o3 = new ContaPagarOcorrencia();
        o3.setId(UUID.randomUUID()); o3.setModelo(modelo); o3.setStatus(StatusOcorrencia.AGUARDANDO_VALOR);
        o3.setMesReferencia(LocalDate.of(2027, 3, 1)); o3.setDataVencimento(LocalDate.of(2027, 3, 15));
        o3.setValorEstimado(new BigDecimal("700.00"));

        ContaPagarOcorrencia o4 = new ContaPagarOcorrencia();
        o4.setId(UUID.randomUUID()); o4.setModelo(modelo); o4.setStatus(StatusOcorrencia.AGUARDANDO_VALOR);
        o4.setMesReferencia(LocalDate.of(2027, 4, 1)); o4.setDataVencimento(LocalDate.of(2027, 4, 15));
        o4.setValorEstimado(new BigDecimal("680.00"));

        when(modeloRepository.findByIdAndUsuarioIdAndAtivoTrue(modeloId, usuario.getId())).thenReturn(Optional.of(modelo));
        when(ocorrenciaRepository.findByModeloIdOrderByMesReferenciaAsc(modeloId)).thenReturn(List.of(o1, o2, o3, o4));
        when(amortizacaoRepository.findByModeloIdOrderByDataDesc(modeloId)).thenReturn(List.of());

        // Paga R$ 500 para eliminar as últimas 2 parcelas (700 + 680 = 1380 -> economia automática = 880)
        AmortizarRequest req = new AmortizarRequest(
                new BigDecimal("500.00"), LocalDate.of(2026, 12, 10), "EXTERNA", null,
                "REDUZIR_PRAZO", 2, null, "Amortização com desconto", "ULTIMAS", null, null);

        contaPagarService.amortizarFinanciamento(modeloId, req);

        assertEquals(StatusOcorrencia.AGUARDANDO_VALOR, o1.getStatus());
        assertEquals(StatusOcorrencia.AGUARDANDO_VALOR, o2.getStatus());
        assertEquals(StatusOcorrencia.CANCELADA, o3.getStatus());
        assertEquals(StatusOcorrencia.CANCELADA, o4.getStatus());
        assertEquals(2, modelo.getTotalParcelas());
        assertEquals(LocalDate.of(2027, 2, 1), modelo.getDataFim());
        assertEquals(new BigDecimal("720.00"), modelo.getValorUltimaParcela());

        ArgumentCaptor<ContaPagarAmortizacao> captor = ArgumentCaptor.forClass(ContaPagarAmortizacao.class);
        verify(amortizacaoRepository).save(captor.capture());
        ContaPagarAmortizacao salva = captor.getValue();
        assertEquals(DirecaoAbatimento.ULTIMAS, salva.getDirecaoAbatimento());
        assertEquals(new BigDecimal("1380.00"), salva.getValorOriginalAbatido());
        assertEquals(new BigDecimal("880.00"), salva.getDescontoObtido());
    }

    @Test
    void amortizarFinanciamento_ProximasParcelasComDescontoManual_DeveCancelarPrimeirasERespeitarDesconto() {
        UUID modeloId = UUID.randomUUID();
        ContaPagarModelo modelo = new ContaPagarModelo();
        modelo.setId(modeloId);
        modelo.setUsuario(usuario);
        modelo.setNome("Mensais Construtora");
        modelo.setTipo(TipoContaPagar.PARCELADO);
        modelo.setCategoria(categoria);
        modelo.setDiaVencimento(10);
        modelo.setTotalParcelas(3);
        modelo.setParcelaInicial(1);
        modelo.setDataInicio(LocalDate.of(2026, 12, 1));
        modelo.setValorPadrao(new BigDecimal("680.00"));

        ContaPagarOcorrencia o1 = new ContaPagarOcorrencia();
        o1.setId(UUID.randomUUID()); o1.setModelo(modelo); o1.setStatus(StatusOcorrencia.PENDENTE);
        o1.setMesReferencia(LocalDate.of(2026, 12, 1)); o1.setDataVencimento(LocalDate.of(2026, 12, 10));
        o1.setValorReal(new BigDecimal("680.00"));

        ContaPagarOcorrencia o2 = new ContaPagarOcorrencia();
        o2.setId(UUID.randomUUID()); o2.setModelo(modelo); o2.setStatus(StatusOcorrencia.PENDENTE);
        o2.setMesReferencia(LocalDate.of(2027, 1, 1)); o2.setDataVencimento(LocalDate.of(2027, 1, 10));
        o2.setValorReal(new BigDecimal("680.00"));

        ContaPagarOcorrencia o3 = new ContaPagarOcorrencia();
        o3.setId(UUID.randomUUID()); o3.setModelo(modelo); o3.setStatus(StatusOcorrencia.PENDENTE);
        o3.setMesReferencia(LocalDate.of(2027, 2, 1)); o3.setDataVencimento(LocalDate.of(2027, 2, 10));
        o3.setValorReal(new BigDecimal("680.00"));

        when(modeloRepository.findByIdAndUsuarioIdAndAtivoTrue(modeloId, usuario.getId())).thenReturn(Optional.of(modelo));
        when(ocorrenciaRepository.findByModeloIdOrderByMesReferenciaAsc(modeloId)).thenReturn(List.of(o1, o2, o3));
        when(amortizacaoRepository.findByModeloIdOrderByDataDesc(modeloId)).thenReturn(List.of());

        AmortizarRequest req = new AmortizarRequest(
                new BigDecimal("600.00"), LocalDate.of(2026, 11, 25), "EXTERNA", null,
                "REDUZIR_PRAZO", 1, null, "Antecipação construtora", "PROXIMAS", new BigDecimal("85.50"), null);

        contaPagarService.amortizarFinanciamento(modeloId, req);

        assertEquals(StatusOcorrencia.CANCELADA, o1.getStatus());
        assertEquals(StatusOcorrencia.PENDENTE, o2.getStatus());
        assertEquals(StatusOcorrencia.PENDENTE, o3.getStatus());

        ArgumentCaptor<ContaPagarAmortizacao> captor = ArgumentCaptor.forClass(ContaPagarAmortizacao.class);
        verify(amortizacaoRepository).save(captor.capture());
        assertEquals(DirecaoAbatimento.PROXIMAS, captor.getValue().getDirecaoAbatimento());
        assertEquals(new BigDecimal("85.50"), captor.getValue().getDescontoObtido());
    }

    @Test
    void ajustarPrazoParcelamento_EncerrarAntecipadamente_DeveCancelarAbertasEManterHistoricoPago() {
        UUID modeloId = UUID.randomUUID();
        ContaPagarModelo modelo = new ContaPagarModelo();
        modelo.setId(modeloId);
        modelo.setUsuario(usuario);
        modelo.setNome("Taxa de Obra");
        modelo.setTipo(TipoContaPagar.PARCELADO);
        modelo.setCategoria(categoria);
        modelo.setDiaVencimento(15);
        modelo.setTotalParcelas(3);
        modelo.setParcelaInicial(1);
        modelo.setDataInicio(LocalDate.of(2026, 10, 1));

        ContaPagarOcorrencia paga = new ContaPagarOcorrencia();
        paga.setId(UUID.randomUUID()); paga.setModelo(modelo); paga.setStatus(StatusOcorrencia.PAGA);
        paga.setMesReferencia(LocalDate.of(2026, 10, 1)); paga.setDataVencimento(LocalDate.of(2026, 10, 15));
        paga.setValorPago(new BigDecimal("250.00"));

        ContaPagarOcorrencia f1 = new ContaPagarOcorrencia();
        f1.setId(UUID.randomUUID()); f1.setModelo(modelo); f1.setStatus(StatusOcorrencia.AGUARDANDO_VALOR);
        f1.setMesReferencia(LocalDate.of(2026, 11, 1)); f1.setDataVencimento(LocalDate.of(2026, 11, 15));
        f1.setValorEstimado(new BigDecimal("400.00"));

        ContaPagarOcorrencia f2 = new ContaPagarOcorrencia();
        f2.setId(UUID.randomUUID()); f2.setModelo(modelo); f2.setStatus(StatusOcorrencia.AGUARDANDO_VALOR);
        f2.setMesReferencia(LocalDate.of(2026, 12, 1)); f2.setDataVencimento(LocalDate.of(2026, 12, 15));
        f2.setValorEstimado(new BigDecimal("550.00"));

        when(modeloRepository.findByIdAndUsuarioIdAndAtivoTrue(modeloId, usuario.getId())).thenReturn(Optional.of(modelo));
        when(ocorrenciaRepository.findByModeloIdOrderByMesReferenciaAsc(modeloId)).thenReturn(List.of(paga, f1, f2));
        when(amortizacaoRepository.findByModeloIdOrderByDataDesc(modeloId)).thenReturn(List.of());

        FinanciamentoDetalheResponse resp = contaPagarService.ajustarPrazoParcelamento(
                modeloId, new AjustarPrazoRequest("ENCERRAR", null, null, "Chaves entregues antes"));

        assertTrue(resp.concluido());
        assertEquals(0, resp.parcelasRestantes());
        assertEquals(1, resp.parcelasPagas());
        assertEquals(2, resp.parcelasCanceladas());
        assertEquals(1, modelo.getTotalParcelas());
        assertEquals(LocalDate.of(2026, 10, 1), modelo.getDataFim());
        assertTrue(modelo.getAtivo());
        assertEquals(StatusOcorrencia.PAGA, paga.getStatus());
        assertEquals(StatusOcorrencia.CANCELADA, f1.getStatus());
        assertEquals(StatusOcorrencia.CANCELADA, f2.getStatus());
    }

    @Test
    void ajustarPrazoParcelamento_EstenderPrazo_DeveReativarCanceladasECriarNovas() {
        UUID modeloId = UUID.randomUUID();
        ContaPagarModelo modelo = new ContaPagarModelo();
        modelo.setId(modeloId);
        modelo.setUsuario(usuario);
        modelo.setNome("Taxa de Obra");
        modelo.setTipo(TipoContaPagar.PARCELADO);
        modelo.setValoresEstimados(true);
        modelo.setIntervaloMeses(1);
        modelo.setCategoria(categoria);
        modelo.setDiaVencimento(15);
        modelo.setTotalParcelas(1);
        modelo.setParcelaInicial(1);
        modelo.setDataInicio(LocalDate.of(2026, 11, 1));
        modelo.setValorPadrao(new BigDecimal("500.00"));

        ContaPagarOcorrencia ativa = new ContaPagarOcorrencia();
        ativa.setId(UUID.randomUUID()); ativa.setModelo(modelo); ativa.setStatus(StatusOcorrencia.AGUARDANDO_VALOR);
        ativa.setNumeroParcela(1); ativa.setMesReferencia(LocalDate.of(2026, 11, 1));
        ativa.setDataVencimento(LocalDate.of(2026, 11, 15)); ativa.setValorEstimado(new BigDecimal("500.00"));

        ContaPagarOcorrencia canc = new ContaPagarOcorrencia();
        canc.setId(UUID.randomUUID()); canc.setModelo(modelo); canc.setStatus(StatusOcorrencia.CANCELADA);
        canc.setNumeroParcela(2); canc.setMesReferencia(LocalDate.of(2026, 12, 1));
        canc.setDataVencimento(LocalDate.of(2026, 12, 15)); canc.setValorEstimado(new BigDecimal("500.00"));

        when(modeloRepository.findByIdAndUsuarioIdAndAtivoTrue(modeloId, usuario.getId())).thenReturn(Optional.of(modelo));
        when(ocorrenciaRepository.findByModeloIdOrderByMesReferenciaAsc(modeloId)).thenReturn(List.of(ativa, canc));
        when(amortizacaoRepository.findByModeloIdOrderByDataDesc(modeloId)).thenReturn(List.of());
        when(ocorrenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Estender +2 meses: deve reativar 12/2026 e criar nova 01/2027
        contaPagarService.ajustarPrazoParcelamento(
                modeloId, new AjustarPrazoRequest("ESTENDER", 2, new BigDecimal("650.00"), "Atraso na obra"));

        assertEquals(StatusOcorrencia.AGUARDANDO_VALOR, canc.getStatus());
        assertEquals(new BigDecimal("650.00"), canc.getValorEstimado());
        assertEquals(3, modelo.getTotalParcelas());
        assertEquals(LocalDate.of(2027, 1, 1), modelo.getDataFim());
        verify(ocorrenciaRepository, times(2)).save(any(ContaPagarOcorrencia.class));
    }
}
