package com.financetracker.ia;

import com.financetracker.cartao.entity.Cartao;
import com.financetracker.cartao.repository.CartaoRepository;
import com.financetracker.ia.dto.AnaliseCartaoSimulacaoDTO;
import com.financetracker.ia.dto.ProjecaoCartaoDTO;
import com.financetracker.ia.dto.ProjecaoCartoesResponse;
import com.financetracker.ia.dto.SimulacaoCompraRequest;
import com.financetracker.ia.dto.SimulacaoCompraResponse;
import com.financetracker.ia.service.IaServiceCartao;
import com.financetracker.ia.service.IaServicePlanejadorCompras;
import com.financetracker.transacao.repository.FaturaRepository;
import com.financetracker.transacao.repository.TransacaoRepository;
import com.financetracker.usuario.entity.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class IaServicePlanejadorComprasTest {

    @InjectMocks
    private IaServicePlanejadorCompras iaServicePlanejadorCompras;

    @Mock
    private TransacaoRepository transacaoRepository;

    @Mock
    private FaturaRepository faturaRepository;

    @Mock
    private CartaoRepository cartaoRepository;

    @Mock
    private IaServiceCartao iaServiceCartao;

    private Usuario usuario;
    private UUID cartaoId;
    private Cartao cartao;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        usuario = new Usuario("userTeste", "teste@example.com", "Senha123!");
        usuario.setId(UUID.randomUUID());

        cartaoId = UUID.randomUUID();
        cartao = new Cartao();
        cartao.setId(cartaoId);
        cartao.setUsuario(usuario);
        cartao.setNome("Banco do Brasil");
        cartao.setDiaFechamento(5);
        cartao.setDiaVencimento(12);
        cartao.setAtivo(true);

        when(transacaoRepository.sumReceitasValidasPorPeriodo(eq(usuario.getId()), any(), any()))
                .thenReturn(BigDecimal.valueOf(5000.00));
        when(transacaoRepository.sumDespesasBasicasPorPeriodo(eq(usuario.getId()), any(), any()))
                .thenReturn(BigDecimal.valueOf(2000.00));
        when(faturaRepository.sumValorTotalByUsuarioAndMesReferencia(eq(usuario.getId()), any()))
                .thenReturn(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("1. Trava de Limite em Tempo Real: deve reprovar compra quando limite disponível for insuficiente")
    public void deveReprovarCompraQuandoLimiteForInsuficiente() {
        cartao.setLimite(BigDecimal.valueOf(3000.00));
        cartao.setLimiteDisponivel(BigDecimal.valueOf(2000.00)); // Limite de apenas 2000

        when(cartaoRepository.findByIdAndUsuarioIdAndAtivoTrue(cartaoId, usuario.getId()))
                .thenReturn(Optional.of(cartao));

        // Tentar comprar TV de 3500 em 10x de 350
        SimulacaoCompraRequest request = new SimulacaoCompraRequest("TV 4K", BigDecimal.valueOf(3500.00), 10, cartaoId);

        SimulacaoCompraResponse response = iaServicePlanejadorCompras.simularCompra(usuario, request);

        assertFalse(response.viavel(), "A compra deve ser inviável devido ao estouro de limite");
        assertNotNull(response.analiseCartao());
        assertFalse(response.analiseCartao().limiteAprovado());
        assertEquals(BigDecimal.valueOf(2000.00), response.analiseCartao().limiteDisponivelAtual());
        assertEquals(BigDecimal.valueOf(-1500.00), response.analiseCartao().limiteAposCompra());
        assertTrue(response.mensagemRecomendacao().contains("Compra Recusada pelo Limite"));
    }

    @Test
    @DisplayName("2. Smart Timing: deve indicar o melhor dia para comprar e dias de fôlego extra")
    public void deveIndicarSmartTimingQuandoLimiteSuficiente() {
        cartao.setLimite(BigDecimal.valueOf(10000.00));
        cartao.setLimiteDisponivel(BigDecimal.valueOf(8000.00));

        when(cartaoRepository.findByIdAndUsuarioIdAndAtivoTrue(cartaoId, usuario.getId()))
                .thenReturn(Optional.of(cartao));

        SimulacaoCompraRequest request = new SimulacaoCompraRequest("Smartphone", BigDecimal.valueOf(2000.00), 10, cartaoId);

        SimulacaoCompraResponse response = iaServicePlanejadorCompras.simularCompra(usuario, request);

        assertTrue(response.viavel());
        AnaliseCartaoSimulacaoDTO analise = response.analiseCartao();
        assertNotNull(analise);
        assertTrue(analise.limiteAprovado());
        assertEquals(37, analise.diasGanhoFolego()); // 30 dias do ciclo + (12 - 5) dias de carência = 37 dias de fôlego!
        assertNotNull(analise.melhorDiaCompra());
        assertTrue(response.mensagemRecomendacao().contains("Limite Aprovado"));
    }

    @Test
    @DisplayName("3. Projeção de Comportamento do Limite: deve simular o efeito bola de neve destravando o limite mês a mês")
    public void deveProjetarEfeitoBolaDeNeveDoLimiteMesAMes() {
        cartao.setLimite(BigDecimal.valueOf(5000.00));
        cartao.setLimiteDisponivel(BigDecimal.valueOf(4000.00));

        when(cartaoRepository.findByIdAndUsuarioIdAndAtivoTrue(cartaoId, usuario.getId()))
                .thenReturn(Optional.of(cartao));

        // Compra de 1000 em 5x de 200
        SimulacaoCompraRequest request = new SimulacaoCompraRequest("Cadeira Ergonômica", BigDecimal.valueOf(1000.00), 5, cartaoId);

        SimulacaoCompraResponse response = iaServicePlanejadorCompras.simularCompra(usuario, request);

        assertTrue(response.viavel());
        assertNotNull(response.simulacoesMesAMes());
        assertFalse(response.simulacoesMesAMes().isEmpty());

        // Mês 0: limite disponível (4000) - compra (1000) + 0 parcelas pagas = 3000
        assertEquals(0, BigDecimal.valueOf(3000.00).compareTo(response.simulacoesMesAMes().get(0).limiteRestanteCartao()));

        // Mês 1: limite disponível (4000) - compra (1000) + 1 parcela paga (200) = 3200
        assertEquals(0, BigDecimal.valueOf(3200.00).compareTo(response.simulacoesMesAMes().get(1).limiteRestanteCartao()));

        // Mês 2: limite disponível (4000) - compra (1000) + 2 parcelas pagas (400) = 3400
        assertEquals(0, BigDecimal.valueOf(3400.00).compareTo(response.simulacoesMesAMes().get(2).limiteRestanteCartao()));
    }

    @Test
    @DisplayName("4. Integração Projeção de Cartões: deve usar a projeção de fechamento no mês atual e o maior valor (fixo ou média histórica) nos meses futuros")
    public void deveUsarProjecaoFechamentoDoIaServiceCartao() {
        when(cartaoRepository.findByIdAndUsuarioIdAndAtivoTrue(cartaoId, usuario.getId()))
                .thenReturn(Optional.of(cartao));

        ProjecaoCartaoDTO dto = new ProjecaoCartaoDTO(
                cartaoId, "Nubank", "#820ad1", "ABERTA",
                BigDecimal.valueOf(587.17), BigDecimal.valueOf(1446.33), false,
                null, BigDecimal.valueOf(1396.25), 6,
                null, "DENTRO", "Msg", 31, 15
        );
        ProjecaoCartoesResponse responseProj = new ProjecaoCartoesResponse(java.util.List.of(dto), 1, false);

        when(iaServiceCartao.projetarFaturasParaUsuario(usuario)).thenReturn(responseProj);

        // Compra de 1000 em 10x de 100
        SimulacaoCompraRequest request = new SimulacaoCompraRequest("TV 4K", BigDecimal.valueOf(1000.00), 10, cartaoId);
        SimulacaoCompraResponse response = iaServicePlanejadorCompras.simularCompra(usuario, request);

        assertNotNull(response);
        assertFalse(response.simulacoesMesAMes().isEmpty());
        // Mês 0 deve ter faturasProjetadas = 1446.33 (projeção de fechamento da IA para o mês atual)
        assertEquals(0, BigDecimal.valueOf(1446.33).compareTo(response.simulacoesMesAMes().get(0).faturasProjetadas()));
        assertEquals(0, BigDecimal.valueOf(1446.33).compareTo(response.simulacoesMesAMes().get(0).faturasProjetadasCartao()));
        // Mês 1 deve ter faturasProjetadas = 1396.25 (média histórica total, pois faturasFixas no banco é 0 ou menor)
        assertEquals(0, BigDecimal.valueOf(1396.25).compareTo(response.simulacoesMesAMes().get(1).faturasProjetadas()));
        assertEquals(0, BigDecimal.valueOf(1396.25).compareTo(response.simulacoesMesAMes().get(1).faturasProjetadasCartao()));
    }
}
