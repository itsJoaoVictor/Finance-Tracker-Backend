package com.financetracker.dashboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.cartao.dto.CartaoResponse;
import com.financetracker.cartao.service.CartaoService;
import com.financetracker.conta.dto.ContaResponse;
import com.financetracker.conta.service.ContaService;
import com.financetracker.dashboard.dto.DashboardResumoResponse;
import com.financetracker.dashboard.dto.LayoutRequest;
import com.financetracker.dashboard.entity.DashboardLayout;
import com.financetracker.dashboard.exception.DashboardLoadException;
import com.financetracker.dashboard.repository.DashboardLayoutRepository;
import com.financetracker.ia.domain.IaInsight;
import com.financetracker.ia.repository.IaInsightRepository;
import com.financetracker.transacao.entity.AgendamentoTransacao;
import com.financetracker.transacao.entity.Fatura;
import com.financetracker.transacao.entity.Transacao;
import com.financetracker.transacao.enums.StatusFatura;
import com.financetracker.transacao.enums.TipoTransacao;
import com.financetracker.transacao.repository.AgendamentoTransacaoRepository;
import com.financetracker.transacao.repository.FaturaRepository;
import com.financetracker.transacao.repository.TransacaoRepository;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final ContaService contaService;
    private final CartaoService cartaoService;
    private final TransacaoRepository transacaoRepository;
    private final FaturaRepository faturaRepository;
    private final AgendamentoTransacaoRepository agendamentoRepository;
    private final IaInsightRepository iaInsightRepository;
    private final DashboardLayoutRepository dashboardLayoutRepository;
    private final UsuarioRepository usuarioRepository;
    private final ObjectMapper objectMapper;

    public DashboardService(ContaService contaService,
                            CartaoService cartaoService,
                            TransacaoRepository transacaoRepository,
                            FaturaRepository faturaRepository,
                            AgendamentoTransacaoRepository agendamentoRepository,
                            IaInsightRepository iaInsightRepository,
                            DashboardLayoutRepository dashboardLayoutRepository,
                            UsuarioRepository usuarioRepository,
                            ObjectMapper objectMapper) {
        this.contaService = contaService;
        this.cartaoService = cartaoService;
        this.transacaoRepository = transacaoRepository;
        this.faturaRepository = faturaRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.iaInsightRepository = iaInsightRepository;
        this.dashboardLayoutRepository = dashboardLayoutRepository;
        this.usuarioRepository = usuarioRepository;
        this.objectMapper = objectMapper;
    }

    private Usuario getAuthenticatedUsuario() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autenticado"));
    }

    @Transactional(readOnly = true)
    public DashboardResumoResponse obterResumo(String periodo) {
        Usuario usuario = getAuthenticatedUsuario();
        UUID usuarioId = usuario.getId();

        try {
            // Preferencias de layout
            DashboardResumoResponse.PreferenciasLayout preferencias = getPreferenciasLayout(usuarioId);

            // KPIs
            List<ContaResponse> contas = contaService.listar();
            BigDecimal saldoTotal = contas.stream()
                    .map(ContaResponse::saldo)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<CartaoResponse> cartoes = cartaoService.listar();
            BigDecimal faturaTotal = cartoes.stream()
                    .map(CartaoResponse::faturaEstimada)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal limiteTotalDisponivel = cartoes.stream()
                    .map(CartaoResponse::limiteDisponivel)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            DashboardResumoResponse.Kpis kpis = new DashboardResumoResponse.Kpis(
                    saldoTotal, faturaTotal, limiteTotalDisponivel);

            // Projeção 15 dias
            DashboardResumoResponse.Projecao15Dias projecao = calcularProjecao15Dias(usuarioId, contas);

            // Contas
            List<DashboardResumoResponse.ContaDashboard> contasDash = contas.stream()
                    .map(c -> new DashboardResumoResponse.ContaDashboard(
                            c.id(), c.nome(), c.tipo().name(), c.saldo(), c.corHexadecimal()))
                    .toList();

            // Cartoes
            List<DashboardResumoResponse.CartaoDashboard> cartoesDash = cartoes.stream()
                    .map(c -> new DashboardResumoResponse.CartaoDashboard(
                            c.id(), c.nome(), c.faturaEstimada(), c.limiteDisponivel(), c.corHexadecimal()))
                    .toList();

            // Últimas 5 transações
            List<DashboardResumoResponse.TransacaoDashboard> ultimasTransacoes = buscarUltimasTransacoes(usuarioId);

            // Insights ativos não lidos
            List<DashboardResumoResponse.InsightDashboard> insights = buscarInsightsAtivos(usuarioId);

            return new DashboardResumoResponse(preferencias, kpis, projecao, contasDash, cartoesDash,
                    ultimasTransacoes, insights);

        } catch (Exception e) {
            throw new DashboardLoadException("Erro ao carregar dados do dashboard: " + e.getMessage(), e);
        }
    }

    private DashboardResumoResponse.PreferenciasLayout getPreferenciasLayout(UUID usuarioId) {
        DashboardLayout layout = dashboardLayoutRepository.findByUsuarioId(usuarioId).orElse(null);
        if (layout == null) {
            return new DashboardResumoResponse.PreferenciasLayout(
                    List.of("kpis", "fluxoCaixaProjetado", "cartoes", "insights", "graficoDespesas", "ultimasTransacoes"),
                    List.of());
        }
        try {
            List<String> ordem = objectMapper.readValue(layout.getOrdemWidgets(), new TypeReference<>() {});
            List<String> ocultos = objectMapper.readValue(layout.getWidgetsOcultos(), new TypeReference<>() {});
            return new DashboardResumoResponse.PreferenciasLayout(ordem, ocultos);
        } catch (JsonProcessingException e) {
            return new DashboardResumoResponse.PreferenciasLayout(
                    List.of("kpis", "fluxoCaixaProjetado", "cartoes", "insights", "graficoDespesas", "ultimasTransacoes"),
                    List.of());
        }
    }

    private DashboardResumoResponse.Projecao15Dias calcularProjecao15Dias(
            UUID usuarioId, List<ContaResponse> contas) {
        LocalDate hoje = LocalDate.now();
        LocalDate dataLimite = hoje.plusDays(15);

        BigDecimal saldoAtual = contas.stream()
                .map(ContaResponse::saldo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Despesas/receitas de transações agendadas nos próximos 15 dias
        BigDecimal receitas = BigDecimal.ZERO;
        BigDecimal despesas = BigDecimal.ZERO;

        List<Transacao> transacoes = transacaoRepository
                .findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(usuarioId, hoje, dataLimite);
        for (Transacao t : transacoes) {
            if (t.getTipo() == TipoTransacao.DEPOSITO) {
                receitas = receitas.add(t.getValor());
            } else if (t.getTipo() == TipoTransacao.SAQUE || t.getTipo() == TipoTransacao.PIX) {
                despesas = despesas.add(t.getValor());
            }
        }

        // Agendamentos
        List<AgendamentoTransacao> agendamentos = agendamentoRepository
                .findByAtivoTrueAndDataProximaExecucaoLessThanEqual(dataLimite);
        for (AgendamentoTransacao a : agendamentos) {
            LocalDate prox = a.getDataProximaExecucao();
            while (!prox.isAfter(dataLimite)) {
                if (a.getTipo() == TipoTransacao.DEPOSITO) {
                    receitas = receitas.add(a.getValor());
                } else if (a.getTipo() == TipoTransacao.SAQUE || a.getTipo() == TipoTransacao.PIX) {
                    despesas = despesas.add(a.getValor());
                }
                prox = switch (a.getRecorrencia()) {
                    case SEMANAL -> prox.plusWeeks(1);
                    case QUINZENAL -> prox.plusDays(15);
                    case MENSAL -> prox.plusMonths(1);
                };
            }
        }

        // Faturas a vencer nos próximos 15 dias
        BigDecimal faturasPendentes = BigDecimal.ZERO;
        List<Fatura> faturas = faturaRepository.findByUsuarioIdAndStatus(usuarioId, StatusFatura.FECHADA);
        for (Fatura f : faturas) {
            if (!f.getDataVencimento().isAfter(dataLimite) && !f.getDataVencimento().isBefore(hoje)) {
                BigDecimal restante = f.getValorTotal().subtract(f.getValorPago());
                if (restante.compareTo(BigDecimal.ZERO) > 0) {
                    faturasPendentes = faturasPendentes.add(restante);
                }
            }
        }

        BigDecimal saldoProjetado = saldoAtual.add(receitas).subtract(despesas).subtract(faturasPendentes);

        // Determinar status e mensagem
        String status;
        StringBuilder msg = new StringBuilder();

        if (saldoProjetado.compareTo(BigDecimal.ZERO) < 0) {
            status = "CRITICO";
            msg.append("Seu saldo projetado para os próximos 15 dias é negativo (R$ ")
                    .append(String.format("%.2f", saldoProjetado))
                    .append("). ");
        } else if (saldoProjetado.compareTo(saldoAtual.multiply(BigDecimal.valueOf(0.1))) < 0) {
            status = "ATENCAO";
            msg.append("Seu saldo projetado para os próximos 15 dias é de R$ ")
                    .append(String.format("%.2f", saldoProjetado))
                    .append(". ");
        } else {
            status = "OK";
            msg.append("Seu saldo projetado para os próximos 15 dias é de R$ ")
                    .append(String.format("%.2f", saldoProjetado))
                    .append(". ");
        }

        if (faturasPendentes.compareTo(BigDecimal.ZERO) > 0) {
            msg.append("Fatura(s) de R$ ")
                    .append(String.format("%.2f", faturasPendentes))
                    .append(" vence(m) no período. ");
        }

        return new DashboardResumoResponse.Projecao15Dias(saldoProjetado, status, msg.toString().trim());
    }

    private List<DashboardResumoResponse.TransacaoDashboard> buscarUltimasTransacoes(UUID usuarioId) {
        List<Transacao> transacoes = transacaoRepository
                .findByUsuarioIdAndAtivoTrueOrderByDataDesc(usuarioId);

        return transacoes.stream()
                .filter(t -> t.getNumeroParcela() == null || t.getNumeroParcela() == 1)
                .limit(5)
                .map(t -> new DashboardResumoResponse.TransacaoDashboard(
                        t.getId(),
                        t.getDescricao(),
                        t.getValor(),
                        t.getTipo().name(),
                        t.getCategoria() != null ? t.getCategoria().getNome() : null,
                        t.getCategoria() != null ? t.getCategoria().getIcone() : null,
                        t.getCategoria() != null ? t.getCategoria().getCorHexadecimal() : null,
                        t.getCriadoEm()
                ))
                .toList();
    }

    private List<DashboardResumoResponse.InsightDashboard> buscarInsightsAtivos(UUID usuarioId) {
        List<IaInsight> insights = iaInsightRepository
                .findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioId);

        return insights.stream()
                .map(i -> new DashboardResumoResponse.InsightDashboard(
                        i.getId(),
                        i.getTipo().name(),
                        i.getTitulo(),
                        i.getMensagem(),
                        i.getCriadoEm()
                ))
                .toList();
    }

    @Transactional
    public void salvarLayout(LayoutRequest request) {
        Usuario usuario = getAuthenticatedUsuario();

        DashboardLayout layout = dashboardLayoutRepository
                .findByUsuarioId(usuario.getId())
                .orElse(new DashboardLayout(usuario.getId()));

        try {
            layout.setOrdemWidgets(objectMapper.writeValueAsString(request.ordemWidgets()));
            layout.setWidgetsOcultos(objectMapper.writeValueAsString(request.widgetsOcultos()));
        } catch (JsonProcessingException e) {
            throw new DashboardLoadException("Erro ao processar layout", e);
        }

        dashboardLayoutRepository.save(layout);
    }
}