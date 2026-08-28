package com.financetracker.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.dashboard.exception.DashboardLoadException;
import com.financetracker.dashboard.service.DashboardService;
import com.financetracker.dashboard.dto.DashboardResumoResponse;
import com.financetracker.dashboard.repository.DashboardLayoutRepository;
import com.financetracker.cartao.dto.CartaoResponse;
import com.financetracker.cartao.service.CartaoService;
import com.financetracker.conta.dto.ContaResponse;
import com.financetracker.conta.service.ContaService;
import com.financetracker.conta.model.TipoConta;
import com.financetracker.transacao.repository.AgendamentoTransacaoRepository;
import com.financetracker.transacao.repository.FaturaRepository;
import com.financetracker.transacao.repository.TransacaoRepository;
import com.financetracker.ia.repository.IaInsightRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private ContaService contaService;
    @Mock private CartaoService cartaoService;
    @Mock private IaInsightRepository iaInsightRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TransacaoRepository transacaoRepository;
    @Mock private FaturaRepository faturaRepository;
    @Mock private AgendamentoTransacaoRepository agendamentoRepository;
    @Mock private DashboardLayoutRepository dashboardLayoutRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private DashboardService dashboardService;

    private Usuario usuario;
    private UUID usuarioId;

    @BeforeEach
    void setUp() {
        usuarioId = UUID.randomUUID();
        usuario = new Usuario();
        usuario.setId(usuarioId);
        usuario.setEmail("test@email.com");

        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn("test@email.com");
        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        lenient().when(usuarioRepository.findByEmail("test@email.com")).thenReturn(Optional.of(usuario));
    }

    @Test
    void obterKpis_DeveRetornarKpisConsolidados() {
        // Arrange
        when(contaService.listar()).thenReturn(List.of(
                new ContaResponse(UUID.randomUUID(), "Nubank", TipoConta.CORRENTE, BigDecimal.valueOf(3000),
                        "#8A05BE", true, LocalDateTime.now()),
                new ContaResponse(UUID.randomUUID(), "Poupança", TipoConta.POUPANCA, BigDecimal.valueOf(1500.50),
                        "#005CA9", false, LocalDateTime.now())
        ));

        when(cartaoService.listar()).thenReturn(List.of(
                createCartaoResponse("Nubank", BigDecimal.valueOf(5000), BigDecimal.valueOf(4149.10), BigDecimal.valueOf(850.90)),
                createCartaoResponse("Itaú Click", BigDecimal.valueOf(5000), BigDecimal.valueOf(4570), BigDecimal.valueOf(430))
        ));

        when(transacaoRepository.sumValorByContaOrigemAndDataAfter(any(), any())).thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.sumValorByContaDestinoAndDataAfter(any(), any())).thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.sumValorByCartaoAndDataAfterAndTipo(any(), any(), any())).thenReturn(BigDecimal.ZERO);

        // Act
        DashboardResumoResponse.Kpis kpis = dashboardService.obterKpis("MES_ATUAL");

        // Assert
        assertNotNull(kpis);
        assertEquals(BigDecimal.valueOf(4500.50), kpis.saldoTotal());
        assertEquals(BigDecimal.valueOf(1280.90), kpis.faturaTotalCartoes());
    }

    private CartaoResponse createCartaoResponse(String nome, BigDecimal limite, BigDecimal limiteDisponivel, BigDecimal fatura) {
        return new CartaoResponse(
                UUID.randomUUID(), nome, limite, limiteDisponivel, 5, 10,
                UUID.randomUUID(), "#000", LocalDateTime.now(), fatura, "ABERTA", "2026-06");
    }
}
