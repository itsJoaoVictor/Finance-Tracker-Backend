package com.financetracker.cartao;

import com.financetracker.assinatura.repository.AssinaturaRepository;
import com.financetracker.cartao.entity.Cartao;
import com.financetracker.cartao.repository.CartaoRepository;
import com.financetracker.cartao.service.CartaoService;
import com.financetracker.conta.repository.ContaRepository;
import com.financetracker.ia.repository.IaClassificacaoAssinaturaRepository;
import com.financetracker.transacao.dto.FaturaResponse;
import com.financetracker.transacao.dto.FaturaVencimentoRequest;
import com.financetracker.transacao.entity.Fatura;
import com.financetracker.transacao.enums.StatusFatura;
import com.financetracker.transacao.exception.FaturaNaoEncontradaException;
import com.financetracker.transacao.repository.FaturaRepository;
import com.financetracker.transacao.repository.TransacaoRepository;
import com.financetracker.transacao.util.FaturaDateUtils;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FaturaVencimentoAdjustmentTest {

    @Mock private CartaoRepository cartaoRepository;
    @Mock private ContaRepository contaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private FaturaRepository faturaRepository;
    @Mock private TransacaoRepository transacaoRepository;
    @Mock private AssinaturaRepository assinaturaRepository;
    @Mock private IaClassificacaoAssinaturaRepository iaClassificacaoAssinaturaRepository;

    private CartaoService cartaoService;
    private Usuario usuario;
    private Cartao cartao;
    private Fatura fatura;

    @BeforeEach
    void setUp() {
        cartaoService = new CartaoService(
                cartaoRepository,
                contaRepository,
                usuarioRepository,
                faturaRepository,
                transacaoRepository,
                assinaturaRepository,
                iaClassificacaoAssinaturaRepository
        );

        usuario = new Usuario("Teste", "teste@example.com", "Senha123!");
        usuario.setId(UUID.randomUUID());

        cartao = new Cartao();
        cartao.setId(UUID.randomUUID());
        cartao.setUsuario(usuario);
        cartao.setNome("Nubank");
        cartao.setDiaFechamento(21);
        cartao.setDiaVencimento(28);
        cartao.setAtivo(true);

        fatura = new Fatura();
        fatura.setId(UUID.randomUUID());
        fatura.setUsuario(usuario);
        fatura.setCartao(cartao);
        fatura.setMesReferencia(LocalDate.of(2026, 11, 1));
        fatura.setDataFechamento(LocalDate.of(2026, 11, 21));
        fatura.setDataVencimento(LocalDate.of(2026, 11, 28));
        fatura.setValorTotal(BigDecimal.valueOf(500.00));
        fatura.setValorPago(BigDecimal.ZERO);
        fatura.setStatus(StatusFatura.FECHADA);
        fatura.setRolladoOver(false);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("FaturaDateUtils — Prorroga sábado 28/11/2026 para segunda 30/11/2026 e faz clamp em fevereiro")
    void calcularDatasComDiaUtilEClamp() {
        LocalDate nov = LocalDate.of(2026, 11, 1);
        LocalDate fechNov = FaturaDateUtils.calcularDataFechamento(nov, 21);
        assertEquals(LocalDate.of(2026, 11, 21), fechNov);
        assertEquals(LocalDate.of(2026, 11, 28), FaturaDateUtils.calcularDataVencimentoPadrao(nov, fechNov, 28));
        assertEquals(LocalDate.of(2026, 11, 30), FaturaDateUtils.calcularDataVencimentoInicial(nov, fechNov, 28));

        // Fevereiro não tem dia 30 -> clamp para 28/02/2026 (sáb) -> próximo dia útil 02/03/2026
        LocalDate fev = LocalDate.of(2026, 2, 1);
        LocalDate fechFev = FaturaDateUtils.calcularDataFechamento(fev, 20);
        assertEquals(LocalDate.of(2026, 2, 28), FaturaDateUtils.calcularDataVencimentoPadrao(fev, fechFev, 30));
        assertEquals(LocalDate.of(2026, 3, 2), FaturaDateUtils.calcularDataVencimentoInicial(fev, fechFev, 30));
    }

    @Test
    @DisplayName("Ajuste pontual altera apenas a fatura escolhida e mantém diaVencimento original no cartão")
    void ajustarERestaurarVencimentoPontual() {
        when(usuarioRepository.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(cartaoRepository.findByIdAndUsuarioIdAndAtivoTrue(cartao.getId(), usuario.getId()))
                .thenReturn(Optional.of(cartao));
        when(faturaRepository.findByIdAndUsuarioId(fatura.getId(), usuario.getId()))
                .thenReturn(Optional.of(fatura));
        when(faturaRepository.save(any(Fatura.class))).thenAnswer(inv -> inv.getArgument(0));

        FaturaResponse ajustada = cartaoService.ajustarVencimentoFatura(
                cartao.getId(),
                fatura.getId(),
                new FaturaVencimentoRequest(LocalDate.of(2026, 11, 30), false)
        );

        assertEquals(LocalDate.of(2026, 11, 30), ajustada.dataVencimento());
        assertEquals(LocalDate.of(2026, 11, 28), ajustada.dataVencimentoPadrao());
        assertTrue(ajustada.vencimentoAjustado());
        assertEquals(28, cartao.getDiaVencimento());

        FaturaResponse restaurada = cartaoService.ajustarVencimentoFatura(
                cartao.getId(),
                fatura.getId(),
                new FaturaVencimentoRequest(null, true)
        );

        assertEquals(LocalDate.of(2026, 11, 28), restaurada.dataVencimento());
        assertFalse(restaurada.vencimentoAjustado());
    }

    @Test
    @DisplayName("Rejeita data anterior ao fechamento, fatura paga, rollover e fatura de outro cartão")
    void validarRegrasDeSeguranca() {
        when(usuarioRepository.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(cartaoRepository.findByIdAndUsuarioIdAndAtivoTrue(cartao.getId(), usuario.getId()))
                .thenReturn(Optional.of(cartao));
        when(faturaRepository.findByIdAndUsuarioId(fatura.getId(), usuario.getId()))
                .thenReturn(Optional.of(fatura));

        // Data anterior ao fechamento -> 422
        ResponseStatusException exData = assertThrows(ResponseStatusException.class, () ->
                cartaoService.ajustarVencimentoFatura(
                        cartao.getId(),
                        fatura.getId(),
                        new FaturaVencimentoRequest(LocalDate.of(2026, 11, 20), false)
                )
        );
        assertEquals(422, exData.getStatusCode().value());

        // Fatura já paga -> 422
        fatura.setStatus(StatusFatura.PAGA);
        ResponseStatusException exPaga = assertThrows(ResponseStatusException.class, () ->
                cartaoService.ajustarVencimentoFatura(
                        cartao.getId(),
                        fatura.getId(),
                        new FaturaVencimentoRequest(LocalDate.of(2026, 11, 30), false)
                )
        );
        assertEquals(422, exPaga.getStatusCode().value());

        // Fatura de outro cartão -> 404
        Cartao outroCartao = new Cartao();
        outroCartao.setId(UUID.randomUUID());
        fatura.setCartao(outroCartao);
        fatura.setStatus(StatusFatura.FECHADA);
        assertThrows(FaturaNaoEncontradaException.class, () ->
                cartaoService.ajustarVencimentoFatura(
                        cartao.getId(),
                        fatura.getId(),
                        new FaturaVencimentoRequest(LocalDate.of(2026, 11, 30), false)
                )
        );
    }
}
