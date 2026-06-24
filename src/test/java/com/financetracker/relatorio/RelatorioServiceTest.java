package com.financetracker.relatorio;

import com.financetracker.relatorio.dto.RelatorioCategoriaResponse;
import com.financetracker.relatorio.dto.RelatorioFluxoCaixaResponse;
import com.financetracker.relatorio.exception.ExportLimitExceededException;
import com.financetracker.relatorio.exception.InvalidPeriodException;
import com.financetracker.relatorio.service.RelatorioService;
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
import org.springframework.http.ResponseEntity;
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
class RelatorioServiceTest {

    @Mock private TransacaoRepository transacaoRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks private RelatorioService relatorioService;

    private Usuario usuario;
    private UUID usuarioId;

    @BeforeEach
    void setUp() {
        usuarioId = UUID.randomUUID();
        usuario = new Usuario();
        usuario.setId(usuarioId);
        usuario.setEmail("test@email.com");

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("test@email.com");
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        when(usuarioRepository.findByEmail("test@email.com")).thenReturn(Optional.of(usuario));
    }

    @Test
    void obterGastosPorCategoria_DeveRetornarDadosAgregados() {
        // Arrange
        LocalDate inicio = LocalDate.of(2026, 5, 1);
        LocalDate fim = LocalDate.of(2026, 5, 31);

        Transacao t1 = new Transacao();
        t1.setId(UUID.randomUUID());
        t1.setValor(BigDecimal.valueOf(100));
        t1.setTipo(TipoTransacao.SAQUE);
        t1.setData(LocalDate.of(2026, 5, 5));

        when(transacaoRepository.findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(
                usuarioId, inicio, fim)).thenReturn(List.of(t1));

        // Act
        RelatorioCategoriaResponse response = relatorioService.obterGastosPorCategoria(inicio, fim, null);

        // Assert
        assertNotNull(response);
        assertEquals(inicio, response.periodo().dataInicio());
        assertEquals(fim, response.periodo().dataFim());
    }

    @Test
    void obterFluxoCaixa_DeveRetornar12Meses() {
        // Act
        List<RelatorioFluxoCaixaResponse> response = relatorioService.obterFluxoCaixa(2026);

        // Assert
        assertNotNull(response);
        assertEquals(12, response.size());
    }

    @Test
    void validarPeriodo_DeveLancarExcecao_QuandoInicioMaiorQueFim() {
        LocalDate inicio = LocalDate.of(2026, 6, 15);
        LocalDate fim = LocalDate.of(2026, 6, 1);

        assertThrows(InvalidPeriodException.class,
                () -> relatorioService.obterGastosPorCategoria(inicio, fim, null));
    }

    @Test
    void exportar_DeveRetornarCSV() {
        LocalDate inicio = LocalDate.of(2026, 1, 1);
        LocalDate fim = LocalDate.of(2026, 1, 31);

        Transacao t = new Transacao();
        t.setId(UUID.randomUUID());
        t.setDescricao("Teste");
        t.setValor(BigDecimal.valueOf(100));
        t.setTipo(TipoTransacao.SAQUE);
        t.setData(LocalDate.of(2026, 1, 5));

        when(transacaoRepository.findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(
                usuarioId, inicio, fim)).thenReturn(List.of(t));

        ResponseEntity<byte[]> response = relatorioService.exportarRelatorio("csv", inicio, fim);

        assertNotNull(response);
        assertTrue(response.getHeaders().getContentDisposition().toString().contains("relatorio-financeiro.csv"));
    }

    @Test
    void exportar_DeveLancarExcecao_QuandoPeriodoMaiorQue5Anos() {
        LocalDate inicio = LocalDate.of(2020, 1, 1);
        LocalDate fim = LocalDate.of(2026, 12, 31);

        assertThrows(ExportLimitExceededException.class,
                () -> relatorioService.exportarRelatorio("csv", inicio, fim));
    }
}
