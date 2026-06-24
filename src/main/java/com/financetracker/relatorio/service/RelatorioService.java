package com.financetracker.relatorio.service;

import com.financetracker.categoria.entity.Categoria;
import com.financetracker.categoria.repository.CategoriaRepository;
import com.financetracker.relatorio.dto.RelatorioCategoriaResponse;
import com.financetracker.relatorio.dto.RelatorioFluxoCaixaResponse;
import com.financetracker.relatorio.exception.ExportLimitExceededException;
import com.financetracker.relatorio.exception.InvalidPeriodException;
import com.financetracker.transacao.entity.Transacao;
import com.financetracker.transacao.enums.TipoTransacao;
import com.financetracker.transacao.repository.TransacaoRepository;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RelatorioService {

    private final TransacaoRepository transacaoRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;

    public RelatorioService(TransacaoRepository transacaoRepository,
                            CategoriaRepository categoriaRepository,
                            UsuarioRepository usuarioRepository) {
        this.transacaoRepository = transacaoRepository;
        this.categoriaRepository = categoriaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    private Usuario getAuthenticatedUsuario() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autenticado"));
    }

    @Transactional(readOnly = true)
    public RelatorioCategoriaResponse obterGastosPorCategoria(
            LocalDate dataInicio, LocalDate dataFim, String tipo) {
        validarPeriodo(dataInicio, dataFim);
        Usuario usuario = getAuthenticatedUsuario();

        List<TipoTransacao> tipos;
        if (tipo != null && !tipo.isBlank()) {
            tipos = List.of(TipoTransacao.valueOf(tipo));
        } else {
            // Despesas por padrão
            tipos = List.of(TipoTransacao.SAQUE, TipoTransacao.PIX, TipoTransacao.COMPRA_CREDITO);
        }

        // Buscar transações do período
        List<Transacao> transacoes = transacaoRepository
                .findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(
                        usuario.getId(), dataInicio, dataFim);

        // Filtrar por tipo e agrupar por categoria
        Map<UUID, Categoria> categoriaMap = categoriaRepository.findAtivasByUsuarioId(usuario.getId())
                .stream().collect(Collectors.toMap(c -> c.getId().toString() != null ? c.getId() : UUID.randomUUID(),
                        c -> c, (a, b) -> a));

        Map<String, Categoria> categorias = new HashMap<>();
        for (Categoria cat : categoriaMap.values()) {
            categorias.put(cat.getId().toString(), cat);
        }

        Map<String, BigDecimal> gastosPorCategoria = new HashMap<>();
        Map<String, String> coresPorCategoria = new HashMap<>();

        for (Transacao t : transacoes) {
            if (!tipos.contains(t.getTipo())) continue;
            if (t.getCategoria() == null) continue;

            String catId = t.getCategoria().getId().toString();
            gastosPorCategoria.merge(catId, t.getValor(), BigDecimal::add);
            coresPorCategoria.putIfAbsent(catId, t.getCategoria().getCorHexadecimal());
        }

        BigDecimal totalConsolidado = gastosPorCategoria.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<RelatorioCategoriaResponse.CategoriaRelatorio> categoriasRelatorio = gastosPorCategoria.entrySet()
                .stream()
                .map(entry -> {
                    String catId = entry.getKey();
                    BigDecimal valor = entry.getValue();
                    BigDecimal percentual = totalConsolidado.compareTo(BigDecimal.ZERO) > 0
                            ? valor.multiply(BigDecimal.valueOf(100))
                                    .divide(totalConsolidado, 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    Categoria cat = categorias.get(catId);
                    String nome = cat != null ? cat.getNome() : "Sem categoria";
                    String cor = coresPorCategoria.getOrDefault(catId, "#888888");
                    return new RelatorioCategoriaResponse.CategoriaRelatorio(
                            catId, nome, cor, valor, percentual);
                })
                .sorted((a, b) -> b.valorTotal().compareTo(a.valorTotal()))
                .toList();

        return new RelatorioCategoriaResponse(
                new RelatorioCategoriaResponse.Periodo(dataInicio, dataFim),
                totalConsolidado,
                categoriasRelatorio);
    }

    @Transactional(readOnly = true)
    public List<RelatorioFluxoCaixaResponse> obterFluxoCaixa(Integer ano) {
        Usuario usuario = getAuthenticatedUsuario();

        int anoBase = ano != null ? ano : LocalDate.now().getYear();
        LocalDate dataInicio = LocalDate.of(anoBase - 1, 1, 1); // Últimos 12 meses a partir do ano anterior
        LocalDate dataFim = LocalDate.of(anoBase, 12, 31);

        List<Transacao> transacoes = transacaoRepository
                .findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(
                        usuario.getId(), dataInicio, dataFim);

        // Agrupar por mês
        Map<YearMonth, List<Transacao>> porMes = transacoes.stream()
                .collect(Collectors.groupingBy(t -> YearMonth.from(t.getData())));

        List<RelatorioFluxoCaixaResponse> resultado = new ArrayList<>();
        LocalDate inicio = LocalDate.of(anoBase - 1, 1, 1);
        LocalDate fim = YearMonth.from(inicio).plusMonths(11).atEndOfMonth();

        for (int i = 0; i < 12; i++) {
            YearMonth ym = YearMonth.from(inicio.plusMonths(i));
            List<Transacao> mesTransacoes = porMes.getOrDefault(ym, List.of());

            BigDecimal receitas = mesTransacoes.stream()
                    .filter(t -> t.getTipo() == TipoTransacao.DEPOSITO)
                    .map(Transacao::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal despesas = mesTransacoes.stream()
                    .filter(t -> t.getTipo() == TipoTransacao.SAQUE
                            || t.getTipo() == TipoTransacao.PIX
                            || t.getTipo() == TipoTransacao.COMPRA_CREDITO)
                    .map(Transacao::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal saldo = receitas.subtract(despesas);

            resultado.add(new RelatorioFluxoCaixaResponse(
                    ym.toString(),
                    receitas,
                    despesas,
                    saldo));
        }

        return resultado;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportarRelatorio(String formato, LocalDate dataInicio, LocalDate dataFim) {
        validarPeriodo(dataInicio, dataFim);

        // Validar limite de 5 anos
        if (dataFim.toEpochDay() - dataInicio.toEpochDay() > 365 * 5) {
            throw new ExportLimitExceededException("Período máximo para exportação é de 5 anos.");
        }

        Usuario usuario = getAuthenticatedUsuario();
        List<Transacao> transacoes = transacaoRepository
                .findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(
                        usuario.getId(), dataInicio, dataFim);

        if ("csv".equalsIgnoreCase(formato)) {
            return gerarCsv(transacoes);
        } else if ("pdf".equalsIgnoreCase(formato)) {
            return gerarPdf(transacoes, dataInicio, dataFim);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato inválido. Use 'csv' ou 'pdf'.");
        }
    }

    private ResponseEntity<byte[]> gerarCsv(List<Transacao> transacoes) {
        StringBuilder sb = new StringBuilder();
        sb.append("ID;Data;Descrição;Valor;Categoria;Tipo\n");

        for (Transacao t : transacoes) {
            sb.append(t.getId()).append(";")
                    .append(t.getData()).append(";")
                    .append(escapeCsv(t.getDescricao())).append(";")
                    .append(t.getValor()).append(";")
                    .append(t.getCategoria() != null ? escapeCsv(t.getCategoria().getNome()) : "").append(";")
                    .append(t.getTipo().name()).append("\n");
        }

        byte[] bytes = sb.toString().getBytes();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"relatorio-financeiro.csv\"");
        headers.set(HttpHeaders.CONTENT_LENGTH, String.valueOf(bytes.length));

        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    private ResponseEntity<byte[]> gerarPdf(List<Transacao> transacoes, LocalDate dataInicio, LocalDate dataFim) {
        // PDF textual simples
        StringBuilder sb = new StringBuilder();
        sb.append("RELATÓRIO FINANCEIRO\n");
        sb.append("Período: ").append(dataInicio).append(" a ").append(dataFim).append("\n");
        sb.append("Gerado em: ").append(LocalDate.now()).append("\n");
        sb.append("=".repeat(60)).append("\n\n");

        BigDecimal totalReceitas = BigDecimal.ZERO;
        BigDecimal totalDespesas = BigDecimal.ZERO;

        for (Transacao t : transacoes) {
            String tipo = t.getTipo() == TipoTransacao.DEPOSITO ? "RECEITA" : "DESPESA";
            String linha = String.format("%s | %s | R$ %.2f | %s | %s",
                    t.getData(), tipo, t.getValor(),
                    t.getDescricao(),
                    t.getCategoria() != null ? t.getCategoria().getNome() : "");
            sb.append(linha).append("\n");

            if (t.getTipo() == TipoTransacao.DEPOSITO) {
                totalReceitas = totalReceitas.add(t.getValor());
            } else if (t.getTipo() == TipoTransacao.SAQUE || t.getTipo() == TipoTransacao.PIX
                    || t.getTipo() == TipoTransacao.COMPRA_CREDITO) {
                totalDespesas = totalDespesas.add(t.getValor());
            }
        }

        sb.append("\n").append("=".repeat(60)).append("\n");
        sb.append(String.format("Total Receitas: R$ %.2f\n", totalReceitas));
        sb.append(String.format("Total Despesas: R$ %.2f\n", totalDespesas));
        sb.append(String.format("Saldo Líquido: R$ %.2f\n", totalReceitas.subtract(totalDespesas)));

        byte[] bytes = sb.toString().getBytes();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/pdf"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"relatorio-financeiro.pdf\"");
        headers.set(HttpHeaders.CONTENT_LENGTH, String.valueOf(bytes.length));

        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    private void validarPeriodo(LocalDate dataInicio, LocalDate dataFim) {
        if (dataInicio != null && dataFim != null && dataInicio.isAfter(dataFim)) {
            throw new InvalidPeriodException("A data de início não pode ser posterior à data de fim.");
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(";") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}