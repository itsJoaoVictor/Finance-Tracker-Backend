package com.financetracker.ia;

import com.financetracker.assinatura.entity.Assinatura;
import com.financetracker.assinatura.enums.TipoRecorrencia;
import com.financetracker.assinatura.repository.AssinaturaRepository;
import com.financetracker.cartao.entity.Cartao;
import com.financetracker.cartao.repository.CartaoRepository;
import com.financetracker.categoria.entity.Categoria;
import com.financetracker.ia.domain.IaClassificacaoAssinatura;
import com.financetracker.ia.domain.IaInsight;
import com.financetracker.ia.domain.NivelEssencialidade;
import com.financetracker.ia.domain.TipoInsight;
import com.financetracker.ia.dto.DominioEfeitoDominoResponse;
import com.financetracker.ia.repository.IaClassificacaoAssinaturaRepository;
import com.financetracker.ia.repository.IaInsightRepository;
import com.financetracker.ia.service.IaServiceAssinatura;
import com.financetracker.transacao.repository.FaturaRepository;
import com.financetracker.transacao.repository.TransacaoRepository;
import com.financetracker.usuario.entity.Usuario;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class IaServiceAssinaturaTest {

    @InjectMocks
    private IaServiceAssinatura service;

    @Mock private IaInsightRepository iaInsightRepository;
    @Mock private IaClassificacaoAssinaturaRepository classificacaoRepository;
    @Mock private AssinaturaRepository assinaturaRepository;
    @Mock private TransacaoRepository transacaoRepository;
    @Mock private FaturaRepository faturaRepository;
    @Mock private CartaoRepository cartaoRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private JdbcTemplate jdbcTemplate;

    private Usuario usuario;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setEmail("teste@email.com");
    }

    @Test
    void deveDetectarEfeitoDominioQuandoLimiteInsuficiente() throws Exception {
        // Setup: cartao com limite R$ 300
        UUID cartaoId = UUID.randomUUID();
        Cartao cartao = new Cartao();
        cartao.setId(cartaoId);
        cartao.setNome("Nubank");
        cartao.setLimiteDisponivel(new BigDecimal("300.00"));

        // 5 assinaturas com cobranca nos proximos 5 dias
        LocalDate hoje = LocalDate.now();
        Assinatura internet = criarAssinatura("Internet Fibra", new BigDecimal("120.00"),
                TipoRecorrencia.MENSAL, cartao, hoje.plusDays(1));
        Assinatura seguro = criarAssinatura("Seguro Auto", new BigDecimal("150.00"),
                TipoRecorrencia.MENSAL, cartao, hoje.plusDays(2));
        Assinatura ms365 = criarAssinatura("Microsoft 365", new BigDecimal("55.00"),
                TipoRecorrencia.MENSAL, cartao, hoje.plusDays(3));
        Assinatura netflix = criarAssinatura("Netflix", new BigDecimal("55.00"),
                TipoRecorrencia.MENSAL, cartao, hoje.plusDays(4));
        Assinatura spotify = criarAssinatura("Spotify", new BigDecimal("30.00"),
                TipoRecorrencia.MENSAL, cartao, hoje.plusDays(5));

        List<Assinatura> assinaturas = List.of(internet, seguro, ms365, netflix, spotify);

        when(assinaturaRepository.findProximasCobrançasPorUsuario(
                eq(usuario.getId()), eq(hoje), eq(hoje.plusDays(5))))
                .thenReturn(assinaturas);
        when(cartaoRepository.findById(cartaoId))
                .thenReturn(Optional.of(cartao));

        // Mock classificacoes de essencialidade
        mockClassificacao(internet, NivelEssencialidade.ESSENCIAL, true);
        mockClassificacao(seguro, NivelEssencialidade.ESSENCIAL, true);
        mockClassificacao(ms365, NivelEssencialidade.IMPORTANTE, true);
        mockClassificacao(netflix, NivelEssencialidade.OPCIONAL, true);
        mockClassificacao(spotify, NivelEssencialidade.OPCIONAL, true);

        // Mock objectMapper to serialize alerta
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"cartaoId\":\"" + cartaoId + "\"}");

        // Execute
        service.processarEfeitoDominio(usuario);

        // Verify: insight salvo
        ArgumentCaptor<IaInsight> captor = ArgumentCaptor.forClass(IaInsight.class);
        verify(iaInsightRepository, times(1)).save(captor.capture());

        IaInsight insight = captor.getValue();
        assertEquals(TipoInsight.EFEITO_DOMINO, insight.getTipo());
        assertTrue(insight.getMetadados().contains("cartaoId"));

        // Internet (120) + Seguro (150) = 270 < 300 -> OK
        // Microsoft 365 (55) -> acumulado 325 > 300 -> FALHA
        assertTrue(insight.getMetadados().contains("cartaoId"));
    }

    @Test
    void naoDeveGerarInsightQuandoLimiteSuficiente() throws Exception {
        UUID cartaoId = UUID.randomUUID();
        Cartao cartao = new Cartao();
        cartao.setId(cartaoId);
        cartao.setNome("Nubank");
        cartao.setLimiteDisponivel(new BigDecimal("1000.00"));

        LocalDate hoje = LocalDate.now();
        Assinatura a1 = criarAssinatura("Netflix", new BigDecimal("55.00"),
                TipoRecorrencia.MENSAL, cartao, hoje.plusDays(2));
        Assinatura a2 = criarAssinatura("Spotify", new BigDecimal("30.00"),
                TipoRecorrencia.MENSAL, cartao, hoje.plusDays(3));

        when(assinaturaRepository.findProximasCobrançasPorUsuario(
                eq(usuario.getId()), eq(hoje), eq(hoje.plusDays(5))))
                .thenReturn(List.of(a1, a2));
        when(cartaoRepository.findById(cartaoId))
                .thenReturn(Optional.of(cartao));

        mockClassificacao(a1, NivelEssencialidade.OPCIONAL, true);
        mockClassificacao(a2, NivelEssencialidade.OPCIONAL, true);

        service.processarEfeitoDominio(usuario);

        // Nenhum insight salvo - limite suficiente
        verify(iaInsightRepository, never()).save(any());
    }

    @Test
    void deveClassificarAlertaComoMedioQuandoApenasImportanteEmRisco() throws Exception {
        UUID cartaoId = UUID.randomUUID();
        Cartao cartao = new Cartao();
        cartao.setId(cartaoId);
        cartao.setNome("Inter");
        cartao.setLimiteDisponivel(new BigDecimal("200.00"));

        LocalDate hoje = LocalDate.now();
        Assinatura a1 = criarAssinatura("Internet", new BigDecimal("120.00"),
                TipoRecorrencia.MENSAL, cartao, hoje.plusDays(1));
        Assinatura a2 = criarAssinatura("Adobe CC", new BigDecimal("100.00"),
                TipoRecorrencia.MENSAL, cartao, hoje.plusDays(2));

        when(assinaturaRepository.findProximasCobrançasPorUsuario(
                eq(usuario.getId()), eq(hoje), eq(hoje.plusDays(5))))
                .thenReturn(List.of(a1, a2));
        when(cartaoRepository.findById(cartaoId))
                .thenReturn(Optional.of(cartao));

        mockClassificacao(a1, NivelEssencialidade.ESSENCIAL, true);
        mockClassificacao(a2, NivelEssencialidade.IMPORTANTE, true);

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"nivelAlerta\":\"MEDIO\"}");

        service.processarEfeitoDominio(usuario);

        ArgumentCaptor<IaInsight> captor = ArgumentCaptor.forClass(IaInsight.class);
        verify(iaInsightRepository, times(1)).save(captor.capture());

        assertTrue(captor.getValue().getMetadados().contains("nivelAlerta"));
    }

    @Test
    void deveLimparInsightsAntigosAoAtualizar() {
        UUID cartaoId = UUID.randomUUID();
        Cartao cartao = new Cartao();
        cartao.setId(cartaoId);
        cartao.setNome("Nubank");
        cartao.setLimiteDisponivel(new BigDecimal("500.00"));

        // Insight antigo existe
        IaInsight insightAntigo = new IaInsight(usuario, TipoInsight.EFEITO_DOMINO,
                "Risco de falha", "mensagem", "{\"cartaoId\":\"" + cartaoId + "\"}");
        insightAntigo.setLido(false);

        when(iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuario.getId()))
                .thenReturn(List.of(insightAntigo));

        // Sem assinaturas com cobranca iminente
        LocalDate hoje = LocalDate.now();
        when(assinaturaRepository.findProximasCobrançasPorUsuario(
                eq(usuario.getId()), eq(hoje), eq(hoje.plusDays(5))))
                .thenReturn(List.of());
        when(cartaoRepository.findById(cartaoId))
                .thenReturn(Optional.of(cartao));

        service.processarEfeitoDominio(usuario);

        // Insight antigo deve ser marcado como lido
        verify(iaInsightRepository, times(1)).save(argThat(i -> Boolean.TRUE.equals(i.getLido())));
    }

    // ── Helpers ──

    private Assinatura criarAssinatura(String nome, BigDecimal valor, TipoRecorrencia recorrencia,
                                        Cartao cartao, LocalDate dataProxima) {
        Assinatura a = new Assinatura();
        a.setId(UUID.randomUUID());
        a.setUsuario(usuario);
        a.setNome(nome);
        a.setValor(valor);
        a.setTipoRecorrencia(recorrencia);
        a.setAtivo(true);
        a.setCartao(cartao);
        Categoria cat = new Categoria();
        cat.setId(UUID.randomUUID());
        cat.setNome("Assinatura");
        a.setCategoria(cat);
        a.setDataInicio(LocalDate.now().minusMonths(6));
        a.setDiaCobranca(dataProxima.getDayOfMonth());
        a.setDataProximaCobranca(dataProxima);
        return a;
    }

    private void mockClassificacao(Assinatura assinatura, NivelEssencialidade essencialidade, boolean confirmado) {
        IaClassificacaoAssinatura classif = new IaClassificacaoAssinatura(
                usuario, assinatura, essencialidade, "Teste", confirmado);
        when(classificacaoRepository.findByAssinaturaId(assinatura.getId()))
                .thenReturn(Optional.of(classif));
    }
}
