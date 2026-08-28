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
import com.financetracker.ia.domain.IaInsight;
import com.financetracker.ia.repository.IaInsightRepository;
import com.financetracker.dashboard.repository.DashboardLayoutRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

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

    private LocalDate[] getDatasPeriodo(String periodo) {
        LocalDate hoje = LocalDate.now();
        if ("ULTIMOS_30_DIAS".equals(periodo)) {
            return new LocalDate[]{hoje.minusDays(30), hoje};
        } else if ("MES_ANTERIOR".equals(periodo)) {
            LocalDate mesAnterior = hoje.minusMonths(1);
            return new LocalDate[]{
                    mesAnterior.withDayOfMonth(1),
                    mesAnterior.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth())
            };
        } else {
            return new LocalDate[]{
                    hoje.withDayOfMonth(1),
                    hoje.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth())
            };
        }
    }

    @Transactional(readOnly = true)
    public DashboardResumoResponse.PreferenciasLayout obterPreferencias() {
        Usuario usuario = getAuthenticatedUsuario();
        return getPreferenciasLayout(usuario.getId());
    }

    @Transactional(readOnly = true)
    public DashboardResumoResponse.Kpis obterKpis(String periodo) {
        List<DashboardResumoResponse.ContaDashboard> contasDash = obterContasDashboard(periodo);
        List<DashboardResumoResponse.CartaoDashboard> cartoesDash = obterCartoesDashboard(periodo);

        BigDecimal saldoTotal = contasDash.stream()
                .map(DashboardResumoResponse.ContaDashboard::saldo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal faturaTotal = cartoesDash.stream()
                .map(DashboardResumoResponse.CartaoDashboard::faturaAtual)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
        BigDecimal limiteTotalDisponivel = cartoesDash.stream()
                .map(DashboardResumoResponse.CartaoDashboard::limiteDisponivel)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DashboardResumoResponse.Kpis(saldoTotal, faturaTotal, limiteTotalDisponivel);
    }

    @Transactional(readOnly = true)
    public List<DashboardResumoResponse.ContaDashboard> obterContasDashboard(String periodo) {
        LocalDate fim = getDatasPeriodo(periodo)[1];
        List<ContaResponse> contas = contaService.listar();
        return contas.stream().map(c -> {
            BigDecimal origens = transacaoRepository.sumValorByContaOrigemAndDataAfter(c.id(), fim);
            BigDecimal destinos = transacaoRepository.sumValorByContaDestinoAndDataAfter(c.id(), fim);
            BigDecimal saldoHistorico = c.saldo().add(origens).subtract(destinos);
            return new DashboardResumoResponse.ContaDashboard(
                    c.id(), c.nome(), c.tipo().name(), saldoHistorico, c.corHexadecimal());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<DashboardResumoResponse.CartaoDashboard> obterCartoesDashboard(String periodo) {
        UUID usuarioId = getAuthenticatedUsuario().getId();
        LocalDate fim = getDatasPeriodo(periodo)[1];
        List<CartaoResponse> cartoes = cartaoService.listar();
        
        return cartoes.stream().map(c -> {
            BigDecimal fatura;
            if ("MES_ANTERIOR".equals(periodo)) {
                LocalDate mesRef = LocalDate.now().minusMonths(1).withDayOfMonth(1);
                fatura = faturaRepository.findByCartaoIdAndUsuarioIdAndMesReferencia(c.id(), usuarioId, mesRef)
                        .map(Fatura::getValorTotal)
                        .orElse(BigDecimal.ZERO);
            } else {
                fatura = c.faturaEstimada() != null ? c.faturaEstimada() : BigDecimal.ZERO;
            }
            BigDecimal cartaoCompra = transacaoRepository.sumValorByCartaoAndDataAfterAndTipo(c.id(), TipoTransacao.COMPRA_CREDITO, fim);
            BigDecimal cartaoPagamento = transacaoRepository.sumValorByCartaoAndDataAfterAndTipo(c.id(), TipoTransacao.PAGAMENTO_CREDITO, fim);
            BigDecimal limiteAtual = c.limiteDisponivel() != null ? c.limiteDisponivel() : BigDecimal.ZERO;
            BigDecimal limiteHistorico = limiteAtual.add(cartaoCompra).subtract(cartaoPagamento);
            return new DashboardResumoResponse.CartaoDashboard(
                    c.id(), c.nome(), fatura, limiteHistorico, c.corHexadecimal());
        }).toList();
    }

    @Transactional(readOnly = true)
    public DashboardResumoResponse.Projecao15Dias obterProjecao15Dias() {
        UUID usuarioId = getAuthenticatedUsuario().getId();
        List<ContaResponse> contas = contaService.listar();
        return calcularProjecao15Dias(usuarioId, contas);
    }

    @Transactional(readOnly = true)
    public List<DashboardResumoResponse.TransacaoDashboard> obterUltimasTransacoes(String periodo) {
        UUID usuarioId = getAuthenticatedUsuario().getId();
        LocalDate[] datas = getDatasPeriodo(periodo);
        Page<Transacao> page = transacaoRepository.findFiltered(
                usuarioId, Arrays.asList(TipoTransacao.values()), "%", datas[0], datas[1], PageRequest.of(0, 10));
                
        return page.stream()
                .map(t -> new DashboardResumoResponse.TransacaoDashboard(
                        t.getId(),
                        t.getDescricao(),
                        t.getValor(),
                        t.getTipo().name(),
                        t.getCategoria() != null ? t.getCategoria().getNome() : null,
                        t.getCategoria() != null ? t.getCategoria().getIcone() : null,
                        t.getCategoria() != null ? t.getCategoria().getCorHexadecimal() : null,
                        t.getData() != null ? t.getData().atStartOfDay() : t.getCriadoEm()
                )).toList();
    }

    @Transactional(readOnly = true)
    public List<DashboardResumoResponse.InsightDashboard> obterInsights() {
        UUID usuarioId = getAuthenticatedUsuario().getId();
        return buscarInsightsAtivos(usuarioId);
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

    private List<DashboardResumoResponse.InsightDashboard> buscarInsightsAtivos(UUID usuarioId) {
        Set<String> tiposExclusivosCartoes = Set.of(
                "CARTAO_PREVISAO",
                "MELHOR_CARTAO",
                "AVISO_FECHAMENTO",
                "ESTOURO_FATURA",
                "CONCENTRACAO_GASTOS_FATURA",
                "OTIMIZACAO_PARCELAMENTO"
        );

        List<IaInsight> insights = iaInsightRepository
                .findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuarioId);

        return insights.stream()
                .filter(i -> !tiposExclusivosCartoes.contains(i.getTipo().name()))
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