package com.financetracker.contapagar;

import com.financetracker.cartao.repository.CartaoRepository;
import com.financetracker.categoria.entity.Categoria;
import com.financetracker.categoria.repository.CategoriaRepository;
import com.financetracker.conta.entity.Conta;
import com.financetracker.conta.repository.ContaRepository;
import com.financetracker.contapagar.dto.*;
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
import com.financetracker.ia.repository.IaInsightRepository;
import com.financetracker.transacao.entity.Transacao;
import com.financetracker.transacao.enums.TipoTransacao;
import com.financetracker.transacao.repository.TransacaoRepository;
import com.financetracker.transacao.service.TransacaoService;
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
}
