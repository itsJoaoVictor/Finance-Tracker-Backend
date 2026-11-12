package com.financetracker.ia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.assinatura.repository.AssinaturaRepository;
import com.financetracker.cartao.entity.Cartao;
import com.financetracker.cartao.repository.CartaoRepository;
import com.financetracker.ia.dto.FolgaLimiteItem;
import com.financetracker.ia.dto.FolgaLimiteResponse;
import com.financetracker.ia.repository.IaInsightRepository;
import com.financetracker.ia.service.IaServiceCartao;
import com.financetracker.transacao.entity.Fatura;
import com.financetracker.transacao.entity.Transacao;
import com.financetracker.transacao.enums.StatusFatura;
import com.financetracker.transacao.enums.TipoTransacao;
import com.financetracker.transacao.repository.FaturaRepository;
import com.financetracker.transacao.repository.TransacaoRepository;
import com.financetracker.usuario.entity.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class IaServiceCartaoFolgaTest {

    private IaServiceCartao iaServiceCartao;

    @Mock
    private IaInsightRepository iaInsightRepository;

    @Mock
    private CartaoRepository cartaoRepository;

    @Mock
    private FaturaRepository faturaRepository;

    @Mock
    private TransacaoRepository transacaoRepository;

    @Mock
    private AssinaturaRepository assinaturaRepository;

    private Usuario usuario;
    private Cartao nubank;
    private Fatura faturaAberta;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        iaServiceCartao = new IaServiceCartao(
                iaInsightRepository,
                cartaoRepository,
                faturaRepository,
                transacaoRepository,
                assinaturaRepository,
                new ObjectMapper()
        );

        usuario = new Usuario("userTeste", "teste@example.com", "Senha123!");
        usuario.setId(UUID.randomUUID());

        nubank = new Cartao();
        nubank.setId(UUID.randomUUID());
        nubank.setUsuario(usuario);
        nubank.setNome("Nubank");
        nubank.setDiaFechamento(15);
        nubank.setDiaVencimento(22);
        nubank.setAtivo(true);

        faturaAberta = new Fatura();
        faturaAberta.setId(UUID.randomUUID());
        faturaAberta.setCartao(nubank);
        faturaAberta.setUsuario(usuario);
        faturaAberta.setStatus(StatusFatura.ABERTA);
        faturaAberta.setMesReferencia(LocalDate.now().withDayOfMonth(1));

        when(cartaoRepository.findByUsuarioIdAndAtivoTrue(usuario.getId()))
                .thenReturn(List.of(nubank));
        when(faturaRepository.findByCartaoIdAndUsuarioIdAndMesReferencia(eq(nubank.getId()), eq(usuario.getId()), any()))
                .thenReturn(Optional.of(faturaAberta));
    }

    private Transacao criarCompraCredito(String descricao, BigDecimal valor, Integer parcela, Integer totalParcelas, boolean estornada) {
        Transacao t = new Transacao();
        t.setId(UUID.randomUUID());
        t.setUsuario(usuario);
        t.setCartao(nubank);
        t.setFatura(faturaAberta);
        t.setTipo(TipoTransacao.COMPRA_CREDITO);
        t.setDescricao(descricao);
        t.setValor(valor);
        t.setNumeroParcela(parcela);
        t.setTotalParcelas(totalParcelas);
        t.setEstornada(estornada);
        t.setAtivo(true);
        t.setData(LocalDate.now());
        return t;
    }

    @Test
    @DisplayName("Deve retornar parcelamentos quitados e compras à vista agrupadas, excluindo assinaturas e estornos")
    public void deveRetornarParcelamentosEComprasAVistaAgrupadas() {
        Transacao parcelaQuitada = criarCompraCredito("UpWork", BigDecimal.valueOf(22.36), 4, 4, false);
        Transacao parcelaEmAndamento = criarCompraCredito("Notebook", BigDecimal.valueOf(300.00), 2, 10, false);
        Transacao avista1 = criarCompraCredito("Supermercado", BigDecimal.valueOf(180.50), null, null, false);
        Transacao avista2 = criarCompraCredito("Farmácia", BigDecimal.valueOf(45.00), 1, 1, false);
        Transacao assinatura = criarCompraCredito("Assinatura: Spotify", BigDecimal.valueOf(21.90), 1, 1, false);
        Transacao estornada = criarCompraCredito("Compra Cancelada", BigDecimal.valueOf(500.00), 1, 1, true);

        when(transacaoRepository.findByFaturaIdAndAtivoTrue(faturaAberta.getId()))
                .thenReturn(List.of(parcelaQuitada, parcelaEmAndamento, avista1, avista2, assinatura, estornada));

        FolgaLimiteResponse response = iaServiceCartao.processarOtimizacaoParcelamentoParaUsuario(usuario);
        assertEquals(2, response.items().size());

        FolgaLimiteItem itemParcela = response.items().stream()
                .filter(i -> "PARCELAMENTO".equals(i.tipo()))
                .findFirst()
                .orElseThrow();
        assertEquals("UpWork", itemParcela.descricao());
        assertEquals(0, BigDecimal.valueOf(22.36).compareTo(itemParcela.impactoMensal()));

        FolgaLimiteItem itemAVista = response.items().stream()
                .filter(i -> "A_VISTA_AGRUPADO".equals(i.tipo()))
                .findFirst()
                .orElseThrow();
        assertEquals("Compras à vista (1x)", itemAVista.descricao());
        assertEquals(2, itemAVista.quantidadeCompras());
        assertEquals(0, BigDecimal.valueOf(225.50).compareTo(itemAVista.impactoMensal()));
        assertEquals(2, itemAVista.detalhes().size());
        assertEquals("Supermercado", itemAVista.detalhes().get(0).descricao());
        assertEquals("Farmácia", itemAVista.detalhes().get(1).descricao());
    }
}
