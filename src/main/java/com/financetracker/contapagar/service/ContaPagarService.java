package com.financetracker.contapagar.service;

import com.financetracker.cartao.entity.Cartao;
import com.financetracker.cartao.exception.CartaoNaoEncontradoException;
import com.financetracker.cartao.repository.CartaoRepository;
import com.financetracker.categoria.entity.Categoria;
import com.financetracker.categoria.exception.CategoriaNaoEncontradaException;
import com.financetracker.categoria.repository.CategoriaRepository;
import com.financetracker.conta.entity.Conta;
import com.financetracker.conta.exception.ContaNaoEncontradaException;
import com.financetracker.conta.repository.ContaRepository;
import com.financetracker.contapagar.dto.*;
import com.financetracker.contapagar.entity.ContaPagarAmortizacao;
import com.financetracker.contapagar.entity.ContaPagarModelo;
import com.financetracker.contapagar.entity.ContaPagarOcorrencia;
import com.financetracker.contapagar.enums.*;
import com.financetracker.contapagar.exception.ContaPagarNaoEncontradaException;
import com.financetracker.contapagar.repository.ContaPagarAmortizacaoRepository;
import com.financetracker.contapagar.repository.ContaPagarModeloRepository;
import com.financetracker.contapagar.repository.ContaPagarOcorrenciaRepository;
import com.financetracker.contapagar.util.DiaUtilUtils;
import com.financetracker.ia.domain.IaInsight;
import com.financetracker.ia.domain.TipoInsight;
import com.financetracker.ia.repository.IaInsightRepository;
import com.financetracker.transacao.dto.TransacaoCriacaoRequest;
import com.financetracker.transacao.dto.TransacaoResponse;
import com.financetracker.transacao.entity.Transacao;
import com.financetracker.transacao.enums.TipoTransacao;
import com.financetracker.transacao.exception.SaldoInsuficienteException;
import com.financetracker.transacao.repository.TransacaoRepository;
import com.financetracker.transacao.service.TransacaoService;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ContaPagarService {

    private final ContaPagarModeloRepository modeloRepository;
    private final ContaPagarOcorrenciaRepository ocorrenciaRepository;
    private final ContaPagarAmortizacaoRepository amortizacaoRepository;
    private final CategoriaRepository categoriaRepository;
    private final ContaRepository contaRepository;
    private final CartaoRepository cartaoRepository;
    private final TransacaoRepository transacaoRepository;
    private final TransacaoService transacaoService;
    private final UsuarioRepository usuarioRepository;
    private final IaInsightRepository iaInsightRepository;
    private final NotificacaoExternaService notificacaoExternaService;

    private static final DateTimeFormatter MES_ANO_FMT = DateTimeFormatter.ofPattern("MM/yyyy");

    public ContaPagarService(ContaPagarModeloRepository modeloRepository,
                             ContaPagarOcorrenciaRepository ocorrenciaRepository,
                             ContaPagarAmortizacaoRepository amortizacaoRepository,
                             CategoriaRepository categoriaRepository,
                             ContaRepository contaRepository,
                             CartaoRepository cartaoRepository,
                             TransacaoRepository transacaoRepository,
                             TransacaoService transacaoService,
                             UsuarioRepository usuarioRepository,
                             IaInsightRepository iaInsightRepository,
                             NotificacaoExternaService notificacaoExternaService) {
        this.modeloRepository = modeloRepository;
        this.ocorrenciaRepository = ocorrenciaRepository;
        this.amortizacaoRepository = amortizacaoRepository;
        this.categoriaRepository = categoriaRepository;
        this.contaRepository = contaRepository;
        this.cartaoRepository = cartaoRepository;
        this.transacaoRepository = transacaoRepository;
        this.transacaoService = transacaoService;
        this.usuarioRepository = usuarioRepository;
        this.iaInsightRepository = iaInsightRepository;
        this.notificacaoExternaService = notificacaoExternaService;
    }

    private Usuario getAuthenticatedUsuario() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autenticado"));
    }

    private Categoria findCategoriaDoUsuario(UUID id, UUID usuarioId) {
        Categoria cat = categoriaRepository.findById(id)
                .orElseThrow(CategoriaNaoEncontradaException::new);
        if (cat.getUsuario() != null && !cat.getUsuario().getId().equals(usuarioId)) {
            throw new CategoriaNaoEncontradaException();
        }
        return cat;
    }

    private Conta findContaDoUsuario(UUID id, UUID usuarioId) {
        return contaRepository.findByIdAndUsuarioIdAndAtivoTrue(id, usuarioId)
                .orElseThrow(ContaNaoEncontradaException::new);
    }

    private Cartao findCartaoDoUsuario(UUID id, UUID usuarioId) {
        return cartaoRepository.findByIdAndUsuarioIdAndAtivoTrue(id, usuarioId)
                .orElseThrow(CartaoNaoEncontradoException::new);
    }

    // ═══════════════════════════════════════════════════════════════════
    // MODELOS (CONTRATOS)
    // ═══════════════════════════════════════════════════════════════════

    @Transactional
    public ContaPagarModeloResponse criarModelo(ContaPagarModeloCriacaoRequest req) {
        Usuario usuario = getAuthenticatedUsuario();
        TipoContaPagar tipo;
        try {
            tipo = TipoContaPagar.valueOf(req.tipo().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo inválido. Use VARIAVEL, FIXO ou PARCELADO.");
        }

        if (tipo == TipoContaPagar.FIXO && (req.valorPadrao() == null || req.valorPadrao().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor padrão é obrigatório para contas FIXAS.");
        }

        int parcelaInicial = 1;
        int totalParcelas = 1;
        if (tipo == TipoContaPagar.PARCELADO) {
            if (req.totalParcelas() == null || req.totalParcelas() < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Total de parcelas é obrigatório para contas PARCELADAS.");
            }
            if (req.valorPadrao() == null || req.valorPadrao().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor estimado da parcela é obrigatório.");
            }
            totalParcelas = req.totalParcelas();
            parcelaInicial = req.parcelaInicial() != null && req.parcelaInicial() >= 1 ? req.parcelaInicial() : 1;
            if (parcelaInicial > totalParcelas) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parcela inicial não pode ser maior que o total de parcelas.");
            }
        }

        Categoria categoria = findCategoriaDoUsuario(req.categoriaId(), usuario.getId());
        Conta contaPadrao = req.contaPadraoId() != null ? findContaDoUsuario(req.contaPadraoId(), usuario.getId()) : null;

        if (Boolean.TRUE.equals(req.debitoAutomatico()) && contaPadrao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conta padrão é obrigatória quando débito automático está ativo.");
        }

        LocalDate mesInicio = req.dataInicio().withDayOfMonth(1);

        ContaPagarModelo modelo = new ContaPagarModelo();
        modelo.setUsuario(usuario);
        modelo.setNome(req.nome().trim());
        modelo.setTipo(tipo);
        modelo.setCategoria(categoria);
        modelo.setAgrupador(req.agrupador() != null && !req.agrupador().isBlank() ? req.agrupador().trim() : null);
        modelo.setDiaVencimento(req.diaVencimento());
        modelo.setValorPadrao(req.valorPadrao());
        modelo.setDataInicio(mesInicio);
        modelo.setDebitoAutomatico(Boolean.TRUE.equals(req.debitoAutomatico()));
        modelo.setContaPadrao(contaPadrao);
        modelo.setAtivo(true);

        if (tipo == TipoContaPagar.PARCELADO) {
            modelo.setTotalParcelas(totalParcelas);
            modelo.setParcelaInicial(parcelaInicial);
            int qtdRestante = totalParcelas - parcelaInicial + 1;
            modelo.setDataFim(mesInicio.plusMonths(qtdRestante - 1));
        } else {
            modelo.setDataFim(req.dataFim() != null ? req.dataFim().withDayOfMonth(1) : null);
        }

        ContaPagarModelo saved = modeloRepository.save(modelo);

        // Geração inicial de ocorrências
        if (tipo == TipoContaPagar.PARCELADO) {
            gerarTodasParcelas(saved);
        } else {
            LocalDate mesAtual = DiaUtilUtils.hoje().withDayOfMonth(1);
            LocalDate cursor = mesInicio.isBefore(mesAtual) ? mesInicio : mesAtual;
            LocalDate limite = mesAtual.plusMonths(1);
            while (!cursor.isAfter(limite)) {
                if (!cursor.isBefore(mesInicio)) {
                    garantirOcorrenciaNoMes(saved, cursor);
                }
                cursor = cursor.plusMonths(1);
            }
        }

        return new ContaPagarModeloResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ContaPagarModeloResponse> listarModelos() {
        Usuario usuario = getAuthenticatedUsuario();
        return modeloRepository.findByUsuarioIdAndAtivoTrueOrderByNomeAsc(usuario.getId())
                .stream().map(ContaPagarModeloResponse::new).toList();
    }

    @Transactional
    public ContaPagarModeloResponse editarModelo(UUID id, ContaPagarModeloEdicaoRequest req) {
        Usuario usuario = getAuthenticatedUsuario();
        ContaPagarModelo modelo = modeloRepository.findByIdAndUsuarioIdAndAtivoTrue(id, usuario.getId())
                .orElseThrow(ContaPagarNaoEncontradaException::new);

        Categoria categoria = findCategoriaDoUsuario(req.categoriaId(), usuario.getId());
        Conta contaPadrao = req.contaPadraoId() != null ? findContaDoUsuario(req.contaPadraoId(), usuario.getId()) : null;

        if (Boolean.TRUE.equals(req.debitoAutomatico()) && contaPadrao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conta padrão é obrigatória quando débito automático está ativo.");
        }

        modelo.setNome(req.nome().trim());
        modelo.setCategoria(categoria);
        modelo.setAgrupador(req.agrupador() != null && !req.agrupador().isBlank() ? req.agrupador().trim() : null);
        modelo.setDiaVencimento(req.diaVencimento());
        modelo.setValorPadrao(req.valorPadrao());
        modelo.setDebitoAutomatico(Boolean.TRUE.equals(req.debitoAutomatico()));
        modelo.setContaPadrao(contaPadrao);

        if (modelo.getTipo() != TipoContaPagar.PARCELADO) {
            LocalDate novaDataFim = req.dataFim() != null ? req.dataFim().withDayOfMonth(1) : null;
            modelo.setDataFim(novaDataFim);
            if (novaDataFim != null) {
                // Cancelar ocorrências futuras após data_fim que não foram pagas
                List<ContaPagarOcorrencia> ocorrencias = ocorrenciaRepository.findByModeloIdOrderByMesReferenciaAsc(modelo.getId());
                for (ContaPagarOcorrencia o : ocorrencias) {
                    if (o.getMesReferencia().isAfter(novaDataFim) && o.getStatus() != StatusOcorrencia.PAGA) {
                        o.setStatus(StatusOcorrencia.CANCELADA);
                        ocorrenciaRepository.save(o);
                    }
                }
            }
        }

        if (Boolean.TRUE.equals(req.aplicarValorOcorrenciasFuturas()) && req.valorPadrao() != null) {
            LocalDate mesAtual = DiaUtilUtils.hoje().withDayOfMonth(1);
            List<ContaPagarOcorrencia> ocorrencias = ocorrenciaRepository.findByModeloIdOrderByMesReferenciaAsc(modelo.getId());
            for (ContaPagarOcorrencia o : ocorrencias) {
                if (!o.getMesReferencia().isBefore(mesAtual) && o.getStatus() == StatusOcorrencia.PENDENTE) {
                    o.setValorReal(req.valorPadrao());
                    o.setValorEstimado(req.valorPadrao());
                    ocorrenciaRepository.save(o);
                }
            }
        }

        return new ContaPagarModeloResponse(modeloRepository.save(modelo));
    }

    @Transactional
    public void excluirModelo(UUID id) {
        Usuario usuario = getAuthenticatedUsuario();
        ContaPagarModelo modelo = modeloRepository.findByIdAndUsuarioIdAndAtivoTrue(id, usuario.getId())
                .orElseThrow(ContaPagarNaoEncontradaException::new);

        modelo.setAtivo(false);
        modeloRepository.save(modelo);

        List<ContaPagarOcorrencia> ocorrencias = ocorrenciaRepository.findByModeloIdOrderByMesReferenciaAsc(modelo.getId());
        for (ContaPagarOcorrencia o : ocorrencias) {
            if (o.getStatus() != StatusOcorrencia.PAGA) {
                o.setStatus(StatusOcorrencia.CANCELADA);
                ocorrenciaRepository.save(o);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GERAÇÃO DE OCORRÊNCIAS
    // ═══════════════════════════════════════════════════════════════════

    private void gerarTodasParcelas(ContaPagarModelo modelo) {
        int inicio = modelo.getParcelaInicial() != null ? modelo.getParcelaInicial() : 1;
        int total = modelo.getTotalParcelas() != null ? modelo.getTotalParcelas() : 1;
        LocalDate mesBase = modelo.getDataInicio().withDayOfMonth(1);

        for (int p = inicio; p <= total; p++) {
            int offset = p - inicio;
            LocalDate mesRef = mesBase.plusMonths(offset);
            if (ocorrenciaRepository.findByModeloIdAndMesReferencia(modelo.getId(), mesRef).isPresent()) {
                continue;
            }
            ContaPagarOcorrencia o = new ContaPagarOcorrencia();
            o.setUsuario(modelo.getUsuario());
            o.setModelo(modelo);
            o.setMesReferencia(mesRef);
            o.setNumeroParcela(p);
            o.setDataVencimento(DiaUtilUtils.calcularDataVencimento(mesRef, modelo.getDiaVencimento()));
            o.setValorEstimado(modelo.getValorPadrao());
            o.setValorReal(modelo.getValorPadrao());
            o.setStatus(StatusOcorrencia.PENDENTE);
            ocorrenciaRepository.save(o);
        }
    }

    @Transactional
    public ContaPagarOcorrencia garantirOcorrenciaNoMes(ContaPagarModelo modelo, LocalDate mesReferencia) {
        LocalDate mesRef = mesReferencia.withDayOfMonth(1);
        if (!Boolean.TRUE.equals(modelo.getAtivo())) return null;
        if (mesRef.isBefore(modelo.getDataInicio().withDayOfMonth(1))) return null;
        if (modelo.getDataFim() != null && mesRef.isAfter(modelo.getDataFim().withDayOfMonth(1))) return null;

        Optional<ContaPagarOcorrencia> existente = ocorrenciaRepository
                .findByModeloIdAndMesReferencia(modelo.getId(), mesRef);
        if (existente.isPresent()) {
            return existente.get();
        }

        ContaPagarOcorrencia o = new ContaPagarOcorrencia();
        o.setUsuario(modelo.getUsuario());
        o.setModelo(modelo);
        o.setMesReferencia(mesRef);
        o.setDataVencimento(DiaUtilUtils.calcularDataVencimento(mesRef, modelo.getDiaVencimento()));

        if (modelo.getTipo() == TipoContaPagar.VARIAVEL) {
            BigDecimal media = calcularMediaUltimos3Pagos(modelo);
            o.setValorEstimado(media);
            o.setValorReal(null);
            o.setStatus(StatusOcorrencia.AGUARDANDO_VALOR);
        } else {
            o.setValorEstimado(modelo.getValorPadrao());
            o.setValorReal(modelo.getValorPadrao());
            o.setStatus(StatusOcorrencia.PENDENTE);
        }

        return ocorrenciaRepository.save(o);
    }

    public BigDecimal calcularMediaUltimos3Pagos(ContaPagarModelo modelo) {
        List<ContaPagarOcorrencia> pagas = ocorrenciaRepository
                .findByModeloIdAndStatusOrderByMesReferenciaDesc(modelo.getId(), StatusOcorrencia.PAGA);
        List<BigDecimal> valores = pagas.stream()
                .map(ContaPagarOcorrencia::getValorPago)
                .filter(Objects::nonNull)
                .limit(3)
                .toList();
        if (valores.isEmpty()) {
            return modelo.getValorPadrao();
        }
        BigDecimal soma = valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return soma.divide(BigDecimal.valueOf(valores.size()), 2, RoundingMode.HALF_UP);
    }

    // ═══════════════════════════════════════════════════════════════════
    // GRADE MENSAL & CONSULTA DE OCORRÊNCIAS
    // ═══════════════════════════════════════════════════════════════════

    @Transactional
    public GradeMensalResponse obterGradeMensal(LocalDate mesInicio, LocalDate mesFim, String agrupadorFiltro) {
        Usuario usuario = getAuthenticatedUsuario();
        LocalDate inicio = mesInicio.withDayOfMonth(1);
        LocalDate fim = mesFim.withDayOfMonth(1);
        if (fim.isBefore(inicio)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mês final não pode ser anterior ao inicial.");
        }

        List<ContaPagarModelo> todosModelos = modeloRepository.findByUsuarioIdAndAtivoTrueOrderByNomeAsc(usuario.getId());

        List<String> agrupadores = todosModelos.stream()
                .map(ContaPagarModelo::getAgrupador)
                .filter(a -> a != null && !a.isBlank())
                .distinct()
                .sorted()
                .toList();

        List<ContaPagarModelo> modelosFiltrados = todosModelos.stream()
                .filter(m -> agrupadorFiltro == null || agrupadorFiltro.isBlank()
                        || "TODOS".equalsIgnoreCase(agrupadorFiltro)
                        || agrupadorFiltro.equalsIgnoreCase(m.getAgrupador()))
                .toList();

        // Garantir geração na janela até (hoje + 1 mês) para VARIAVEL/FIXO
        LocalDate limiteAutoGeracao = DiaUtilUtils.hoje().withDayOfMonth(1).plusMonths(1);
        List<LocalDate> listaMeses = new ArrayList<>();
        LocalDate cursor = inicio;
        while (!cursor.isAfter(fim)) {
            listaMeses.add(cursor);
            if (!cursor.isAfter(limiteAutoGeracao)) {
                for (ContaPagarModelo m : modelosFiltrados) {
                    if (m.getTipo() != TipoContaPagar.PARCELADO) {
                        garantirOcorrenciaNoMes(m, cursor);
                    }
                }
            }
            cursor = cursor.plusMonths(1);
        }

        List<ContaPagarOcorrencia> ocorrenciasPeriodo = ocorrenciaRepository
                .findByUsuarioAndMesReferenciaBetween(usuario.getId(), inicio, fim);

        Map<String, ContaPagarOcorrencia> mapa = new HashMap<>();
        for (ContaPagarOcorrencia o : ocorrenciasPeriodo) {
            String key = o.getModelo().getId() + "_" + o.getMesReferencia().toString();
            mapa.put(key, o);
        }

        List<GradeMensalResponse.LinhaGrade> linhas = new ArrayList<>();
        for (ContaPagarModelo m : modelosFiltrados) {
            List<ContaPagarOcorrenciaResponse> celulas = new ArrayList<>();
            for (LocalDate mes : listaMeses) {
                ContaPagarOcorrencia oc = mapa.get(m.getId() + "_" + mes.toString());
                celulas.add(oc != null ? new ContaPagarOcorrenciaResponse(oc) : null);
            }
            linhas.add(new GradeMensalResponse.LinhaGrade(new ContaPagarModeloResponse(m), celulas));
        }

        List<GradeMensalResponse.TotaisMes> totaisPorMes = new ArrayList<>();
        for (int i = 0; i < listaMeses.size(); i++) {
            LocalDate mes = listaMeses.get(i);
            BigDecimal pago = BigDecimal.ZERO;
            BigDecimal pendente = BigDecimal.ZERO;
            BigDecimal estimado = BigDecimal.ZERO;
            for (GradeMensalResponse.LinhaGrade linha : linhas) {
                ContaPagarOcorrenciaResponse c = linha.celulas().get(i);
                if (c == null || "CANCELADA".equals(c.status())) continue;
                if ("PAGA".equals(c.status())) {
                    pago = pago.add(c.valorPago() != null ? c.valorPago() : BigDecimal.ZERO);
                } else if ("PENDENTE".equals(c.status())) {
                    pendente = pendente.add(c.valorEfetivo());
                } else if ("AGUARDANDO_VALOR".equals(c.status())) {
                    estimado = estimado.add(c.valorEfetivo());
                }
            }
            totaisPorMes.add(new GradeMensalResponse.TotaisMes(
                    mes.toString(),
                    pago,
                    pendente,
                    estimado,
                    pago.add(pendente).add(estimado)
            ));
        }

        // Juros pagos no ano de início
        LocalDate inicioAno = LocalDate.of(DiaUtilUtils.hoje().getYear(), 1, 1);
        LocalDate fimAno = LocalDate.of(DiaUtilUtils.hoje().getYear(), 12, 1);
        BigDecimal totalJurosAno = ocorrenciaRepository
                .findByUsuarioAndMesReferenciaBetween(usuario.getId(), inicioAno, fimAno)
                .stream()
                .filter(o -> o.getStatus() == StatusOcorrencia.PAGA)
                .map(ContaPagarOcorrencia::getJurosMulta)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<String> mesesStr = listaMeses.stream().map(LocalDate::toString).toList();
        return new GradeMensalResponse(mesesStr, agrupadores, linhas, totaisPorMes, totalJurosAno);
    }

    @Transactional
    public List<ContaPagarOcorrenciaResponse> listarOcorrenciasMes(LocalDate mesReferencia, String agrupador) {
        Usuario usuario = getAuthenticatedUsuario();
        LocalDate mes = mesReferencia.withDayOfMonth(1);
        List<ContaPagarModelo> modelos = modeloRepository.findByUsuarioIdAndAtivoTrueOrderByNomeAsc(usuario.getId());
        LocalDate limiteAuto = DiaUtilUtils.hoje().withDayOfMonth(1).plusMonths(1);
        if (!mes.isAfter(limiteAuto)) {
            for (ContaPagarModelo m : modelos) {
                if (m.getTipo() != TipoContaPagar.PARCELADO) {
                    garantirOcorrenciaNoMes(m, mes);
                }
            }
        }
        return ocorrenciaRepository.findByUsuarioAndMesReferenciaBetween(usuario.getId(), mes, mes)
                .stream()
                .filter(o -> agrupador == null || agrupador.isBlank() || "TODOS".equalsIgnoreCase(agrupador)
                        || agrupador.equalsIgnoreCase(o.getModelo().getAgrupador()))
                .map(ContaPagarOcorrenciaResponse::new)
                .toList();
    }

    // ═══════════════════════════════════════════════════════════════════
    // EDIÇÃO, CONFIRMAÇÃO DE VALOR, CANCELAMENTO E PAGAMENTO
    // ═══════════════════════════════════════════════════════════════════

    @Transactional
    public ContaPagarOcorrenciaResponse atualizarOcorrencia(UUID id, AtualizarOcorrenciaRequest req) {
        Usuario usuario = getAuthenticatedUsuario();
        ContaPagarOcorrencia o = ocorrenciaRepository.findByIdAndUsuarioId(id, usuario.getId())
                .orElseThrow(ContaPagarNaoEncontradaException::new);

        if (o.getStatus() == StatusOcorrencia.PAGA) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Ocorrência já paga. Estorne a transação para editar o valor.");
        }

        if (req.dataVencimento() != null) {
            o.setDataVencimento(req.dataVencimento());
        }
        if (req.valorEstimado() != null) {
            o.setValorEstimado(req.valorEstimado());
        }
        if (req.linhaDigitavel() != null) {
            o.setLinhaDigitavel(req.linhaDigitavel().trim());
        }
        if (req.observacao() != null) {
            o.setObservacao(req.observacao().trim());
        }
        if (req.valorReal() != null) {
            if (req.valorReal().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor real deve ser positivo.");
            }
            o.setValorReal(req.valorReal());
            if (o.getStatus() == StatusOcorrencia.AGUARDANDO_VALOR) {
                o.setStatus(StatusOcorrencia.PENDENTE);
            }
            verificarContaAcimaDaMedia(o);
        }

        return new ContaPagarOcorrenciaResponse(ocorrenciaRepository.save(o));
    }

    @Transactional
    public ContaPagarOcorrenciaResponse alterarStatusCancelamento(UUID id, boolean cancelar) {
        Usuario usuario = getAuthenticatedUsuario();
        ContaPagarOcorrencia o = ocorrenciaRepository.findByIdAndUsuarioId(id, usuario.getId())
                .orElseThrow(ContaPagarNaoEncontradaException::new);

        if (o.getStatus() == StatusOcorrencia.PAGA) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Não é possível cancelar uma ocorrência já paga.");
        }

        if (cancelar) {
            o.setStatus(StatusOcorrencia.CANCELADA);
        } else {
            o.setStatus(o.getValorReal() != null ? StatusOcorrencia.PENDENTE : StatusOcorrencia.AGUARDANDO_VALOR);
        }
        return new ContaPagarOcorrenciaResponse(ocorrenciaRepository.save(o));
    }

    @Transactional
    public ContaPagarOcorrenciaResponse pagarOcorrencia(UUID id, PagarOcorrenciaRequest req) {
        Usuario usuario = getAuthenticatedUsuario();
        ContaPagarOcorrencia o = ocorrenciaRepository.findByIdAndUsuarioId(id, usuario.getId())
                .orElseThrow(ContaPagarNaoEncontradaException::new);

        return executarPagamentoInterno(o, usuario, req);
    }

    @Transactional
    public ContaPagarOcorrenciaResponse executarPagamentoInterno(
            ContaPagarOcorrencia o, Usuario usuario, PagarOcorrenciaRequest req) {

        if (o.getStatus() == StatusOcorrencia.PAGA) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Esta conta já foi paga.");
        }
        if (o.getStatus() == StatusOcorrencia.CANCELADA) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Esta ocorrência está cancelada.");
        }

        MeioPagamento meio;
        try {
            meio = MeioPagamento.valueOf(req.meio().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Meio inválido. Use CONTA ou CARTAO.");
        }

        ContaPagarModelo modelo = o.getModelo();
        if (meio == MeioPagamento.CARTAO && modelo.getTipo() == TipoContaPagar.PARCELADO) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Contas parceladas/financiamento só podem ser pagas via conta bancária (boleto).");
        }

        String sufixoMes = o.getNumeroParcela() != null && modelo.getTotalParcelas() != null
                ? String.format(" (%d/%d - %s)", o.getNumeroParcela(), modelo.getTotalParcelas(), o.getMesReferencia().format(MES_ANO_FMT))
                : " - " + o.getMesReferencia().format(MES_ANO_FMT);
        String descricao = modelo.getNome() + sufixoMes;

        Transacao transacaoSalva;

        if (meio == MeioPagamento.CONTA) {
            if (req.contaId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contaId é obrigatório para pagamento via conta.");
            }
            Conta conta = findContaDoUsuario(req.contaId(), usuario.getId());
            if (conta.getSaldo().compareTo(req.valorPago()) < 0) {
                throw new SaldoInsuficienteException("Saldo insuficiente na conta " + conta.getNome() + " para pagar este boleto.");
            }
            conta.setSaldo(conta.getSaldo().subtract(req.valorPago()));
            contaRepository.save(conta);

            Transacao t = new Transacao();
            t.setUsuario(usuario);
            t.setDescricao(descricao);
            t.setValor(req.valorPago());
            t.setTipo(TipoTransacao.PAGAMENTO_BOLETO);
            t.setContaOrigem(conta);
            t.setCategoria(modelo.getCategoria());
            t.setData(req.dataPagamento());
            t.setAtivo(true);
            t.setEstornada(false);
            transacaoSalva = transacaoRepository.save(t);
        } else {
            if (req.cartaoId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cartaoId é obrigatório para pagamento com cartão.");
            }
            findCartaoDoUsuario(req.cartaoId(), usuario.getId());
            TransacaoCriacaoRequest criacaoReq = new TransacaoCriacaoRequest(
                    descricao,
                    req.valorPago(),
                    TipoTransacao.COMPRA_CREDITO.name(),
                    null,
                    null,
                    req.cartaoId(),
                    modelo.getCategoria().getId(),
                    req.dataPagamento(),
                    1,
                    List.of(),
                    null,
                    null
            );
            TransacaoResponse trResp = transacaoService.criar(criacaoReq);
            transacaoSalva = transacaoRepository.findById(trResp.id())
                    .orElseThrow();
        }

        if (o.getValorReal() == null) {
            o.setValorReal(req.valorPago());
        }
        o.setValorPago(req.valorPago());
        o.setDataPagamento(req.dataPagamento());
        o.setStatus(StatusOcorrencia.PAGA);
        o.setTransacao(transacaoSalva);
        if (req.linhaDigitavel() != null) {
            o.setLinhaDigitavel(req.linhaDigitavel().trim());
        }
        if (req.observacao() != null) {
            o.setObservacao(req.observacao().trim());
        }

        ContaPagarOcorrencia saved = ocorrenciaRepository.save(o);

        // Atualizar estimativa de ocorrências futuras AGUARDANDO_VALOR para contas variáveis
        if (modelo.getTipo() == TipoContaPagar.VARIAVEL) {
            BigDecimal novaMedia = calcularMediaUltimos3Pagos(modelo);
            List<ContaPagarOcorrencia> futuras = ocorrenciaRepository
                    .findByModeloIdAndStatusOrderByMesReferenciaDesc(modelo.getId(), StatusOcorrencia.AGUARDANDO_VALOR);
            for (ContaPagarOcorrencia f : futuras) {
                f.setValorEstimado(novaMedia);
                ocorrenciaRepository.save(f);
            }
        }

        return new ContaPagarOcorrenciaResponse(saved);
    }

    // ═══════════════════════════════════════════════════════════════════
    // FINANCIAMENTO: DETALHE & AMORTIZAÇÃO EXTRA (FGTS / CONTA)
    // ═══════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public FinanciamentoDetalheResponse obterDetalheFinanciamento(UUID modeloId) {
        Usuario usuario = getAuthenticatedUsuario();
        ContaPagarModelo modelo = modeloRepository.findByIdAndUsuarioIdAndAtivoTrue(modeloId, usuario.getId())
                .orElseThrow(ContaPagarNaoEncontradaException::new);

        List<ContaPagarOcorrencia> ocorrencias = ocorrenciaRepository.findByModeloIdOrderByMesReferenciaAsc(modeloId);
        List<ContaPagarAmortizacao> amortizacoes = amortizacaoRepository.findByModeloIdOrderByDataDesc(modeloId);

        int pagas = 0;
        int restantes = 0;
        int canceladas = 0;
        BigDecimal totalPagoParcelas = BigDecimal.ZERO;
        BigDecimal totalJuros = BigDecimal.ZERO;
        BigDecimal totalRestanteEstimado = BigDecimal.ZERO;

        for (ContaPagarOcorrencia o : ocorrencias) {
            if (o.getStatus() == StatusOcorrencia.PAGA) {
                pagas++;
                totalPagoParcelas = totalPagoParcelas.add(o.getValorPago() != null ? o.getValorPago() : BigDecimal.ZERO);
                totalJuros = totalJuros.add(o.getJurosMulta());
            } else if (o.getStatus() == StatusOcorrencia.CANCELADA) {
                canceladas++;
            } else {
                restantes++;
                totalRestanteEstimado = totalRestanteEstimado.add(o.getValorEfetivo());
            }
        }

        // Considerar parcelas anteriores à parcela_inicial como já pagas fora do sistema
        int anterioresForaDoApp = (modelo.getParcelaInicial() != null && modelo.getParcelaInicial() > 1)
                ? modelo.getParcelaInicial() - 1 : 0;

        BigDecimal totalAmortizacoes = amortizacoes.stream()
                .map(ContaPagarAmortizacao::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new FinanciamentoDetalheResponse(
                new ContaPagarModeloResponse(modelo),
                pagas + anterioresForaDoApp,
                restantes,
                canceladas,
                totalPagoParcelas,
                totalAmortizacoes,
                totalPagoParcelas.add(totalAmortizacoes),
                totalJuros,
                totalRestanteEstimado,
                ocorrencias.stream().map(ContaPagarOcorrenciaResponse::new).toList(),
                amortizacoes.stream().map(FinanciamentoDetalheResponse.AmortizacaoItem::new).toList()
        );
    }

    @Transactional
    public FinanciamentoDetalheResponse amortizarFinanciamento(UUID modeloId, AmortizarRequest req) {
        Usuario usuario = getAuthenticatedUsuario();
        ContaPagarModelo modelo = modeloRepository.findByIdAndUsuarioIdAndAtivoTrue(modeloId, usuario.getId())
                .orElseThrow(ContaPagarNaoEncontradaException::new);

        if (modelo.getTipo() != TipoContaPagar.PARCELADO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amortização extra só é permitida para contas PARCELADAS.");
        }

        OrigemAmortizacao origem;
        ModoAmortizacao modo;
        try {
            origem = OrigemAmortizacao.valueOf(req.origem().toUpperCase());
            modo = ModoAmortizacao.valueOf(req.modo().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Origem ou modo de amortização inválido.");
        }

        Conta conta = null;
        Transacao transacao = null;
        if (origem == OrigemAmortizacao.CONTA) {
            if (req.contaId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contaId é obrigatório quando a origem é CONTA.");
            }
            conta = findContaDoUsuario(req.contaId(), usuario.getId());
            if (conta.getSaldo().compareTo(req.valor()) < 0) {
                throw new SaldoInsuficienteException("Saldo insuficiente para realizar a amortização.");
            }
            conta.setSaldo(conta.getSaldo().subtract(req.valor()));
            contaRepository.save(conta);

            Transacao t = new Transacao();
            t.setUsuario(usuario);
            t.setDescricao("Amortização Extra - " + modelo.getNome());
            t.setValor(req.valor());
            t.setTipo(TipoTransacao.PAGAMENTO_BOLETO);
            t.setContaOrigem(conta);
            t.setCategoria(modelo.getCategoria());
            t.setData(req.data());
            t.setAtivo(true);
            t.setEstornada(false);
            transacao = transacaoRepository.save(t);
        }

        List<ContaPagarOcorrencia> todas = ocorrenciaRepository.findByModeloIdOrderByMesReferenciaAsc(modeloId);
        List<ContaPagarOcorrencia> abertas = todas.stream()
                .filter(o -> o.getStatus() == StatusOcorrencia.PENDENTE || o.getStatus() == StatusOcorrencia.AGUARDANDO_VALOR)
                .toList();

        if (modo == ModoAmortizacao.REDUZIR_PRAZO) {
            int eliminar = req.parcelasEliminadas() != null ? req.parcelasEliminadas() : 0;
            if (eliminar <= 0 || eliminar > abertas.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Informe uma quantidade válida de parcelas eliminadas (máx: " + abertas.size() + ").");
            }
            // Cancelar as últimas N ocorrências futuras
            for (int i = 0; i < eliminar; i++) {
                ContaPagarOcorrencia alvo = abertas.get(abertas.size() - 1 - i);
                alvo.setStatus(StatusOcorrencia.CANCELADA);
                alvo.setObservacao("Eliminada por amortização extra em " + req.data());
                ocorrenciaRepository.save(alvo);
            }
            if (modelo.getTotalParcelas() != null) {
                modelo.setTotalParcelas(Math.max(1, modelo.getTotalParcelas() - eliminar));
            }
            if (req.novoValorParcela() != null && req.novoValorParcela().compareTo(BigDecimal.ZERO) > 0) {
                modelo.setValorPadrao(req.novoValorParcela());
                for (int i = 0; i < abertas.size() - eliminar; i++) {
                    ContaPagarOcorrencia rest = abertas.get(i);
                    rest.setValorEstimado(req.novoValorParcela());
                    rest.setValorReal(req.novoValorParcela());
                    ocorrenciaRepository.save(rest);
                }
            }
            modeloRepository.save(modelo);
        } else {
            if (req.novoValorParcela() == null || req.novoValorParcela().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o novo valor estimado da parcela.");
            }
            modelo.setValorPadrao(req.novoValorParcela());
            modeloRepository.save(modelo);
            for (ContaPagarOcorrencia o : abertas) {
                o.setValorEstimado(req.novoValorParcela());
                o.setValorReal(req.novoValorParcela());
                ocorrenciaRepository.save(o);
            }
        }

        ContaPagarAmortizacao amort = new ContaPagarAmortizacao();
        amort.setUsuario(usuario);
        amort.setModelo(modelo);
        amort.setValor(req.valor());
        amort.setData(req.data());
        amort.setOrigem(origem);
        amort.setConta(conta);
        amort.setModo(modo);
        amort.setParcelasEliminadas(req.parcelasEliminadas());
        amort.setNovoValorParcela(req.novoValorParcela());
        amort.setObservacao(req.observacao());
        amort.setTransacao(transacao);
        amortizacaoRepository.save(amort);

        return obterDetalheFinanciamento(modeloId);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SCHEDULER / DÉBITO AUTOMÁTICO / IA INSIGHTS / NOTIFICAÇÕES
    // ═══════════════════════════════════════════════════════════════════

    private void verificarContaAcimaDaMedia(ContaPagarOcorrencia o) {
        ContaPagarModelo m = o.getModelo();
        if (m.getTipo() != TipoContaPagar.VARIAVEL || o.getValorReal() == null) return;
        BigDecimal media = calcularMediaUltimos3Pagos(m);
        if (media == null || media.compareTo(BigDecimal.ZERO) <= 0) return;

        BigDecimal limite = media.multiply(BigDecimal.valueOf(1.25));
        if (o.getValorReal().compareTo(limite) > 0) {
            double pct = o.getValorReal().subtract(media)
                    .divide(media, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();
            criarInsightSeNaoExiste(
                    o.getUsuario(),
                    TipoInsight.CONTA_ACIMA_MEDIA,
                    "Conta acima da média: " + m.getNome(),
                    String.format("A conta \"%s\" (%s) veio em R$ %.2f — %.0f%% acima da sua média recente (R$ %.2f).",
                            m.getNome(), o.getMesReferencia().format(MES_ANO_FMT), o.getValorReal(), pct, media),
                    o.getId().toString()
            );
        }
    }

    private void criarInsightSeNaoExiste(Usuario usuario, TipoInsight tipo, String titulo, String msg, String chaveMeta) {
        boolean existe = iaInsightRepository.findByUsuarioIdAndLidoFalseOrderByCriadoEmDesc(usuario.getId())
                .stream()
                .anyMatch(i -> i.getTipo() == tipo && i.getMetadados() != null && i.getMetadados().contains(chaveMeta));
        if (!existe) {
            IaInsight ins = new IaInsight(usuario, tipo, titulo, msg, "{\"ocorrenciaId\":\"" + chaveMeta + "\"}");
            try {
                iaInsightRepository.save(ins);
            } catch (Exception ignored) {}
        }
    }

    @Transactional
    public void processarRotinaDiaria() {
        LocalDate hoje = DiaUtilUtils.hoje();
        LocalDate mesAtual = hoje.withDayOfMonth(1);
        LocalDate proximoMes = mesAtual.plusMonths(1);

        // 1. Garantir ocorrências do mês atual e próximo mês para todos os modelos ativos
        List<ContaPagarModelo> modelosAtivos = modeloRepository.findByAtivoTrue();
        for (ContaPagarModelo m : modelosAtivos) {
            if (m.getTipo() != TipoContaPagar.PARCELADO) {
                garantirOcorrenciaNoMes(m, mesAtual);
                garantirOcorrenciaNoMes(m, proximoMes);
            }
        }

        // 2. Processar ocorrências com vencimento até hoje + 5 dias
        List<ContaPagarOcorrencia> pendentes = ocorrenciaRepository.findAllPendentesAteData(hoje.plusDays(5));

        for (ContaPagarOcorrencia o : pendentes) {
            ContaPagarModelo m = o.getModelo();
            long diasAteVencimento = ChronoUnit.DAYS.between(hoje, o.getDataVencimento());

            // Débito automático no dia do vencimento (ou vencido)
            if (Boolean.TRUE.equals(m.getDebitoAutomatico()) && diasAteVencimento <= 0) {
                if (o.getStatus() == StatusOcorrencia.AGUARDANDO_VALOR || o.getValorReal() == null) {
                    String msg = String.format("Débito automático de \"%s\" vence em %s, mas o valor real ainda não foi confirmado.",
                            m.getNome(), o.getDataVencimento());
                    criarInsightSeNaoExiste(o.getUsuario(), TipoInsight.CONTA_SEM_VALOR,
                            "Débito automático sem valor: " + m.getNome(), msg, o.getId().toString());
                    notificacaoExternaService.notificarOcorrenciaSeNecessario(o, "DEBITO_SEM_VALOR", "⚠️ " + msg);
                    continue;
                }
                Conta contaPadrao = m.getContaPadrao();
                if (contaPadrao != null) {
                    if (contaPadrao.getSaldo().compareTo(o.getValorReal()) < 0) {
                        String msg = String.format("Saldo insuficiente na conta \"%s\" (R$ %.2f) para o débito automático de \"%s\" (R$ %.2f).",
                                contaPadrao.getNome(), contaPadrao.getSaldo(), m.getNome(), o.getValorReal());
                        criarInsightSeNaoExiste(o.getUsuario(), TipoInsight.CONTA_ATRASADA,
                                "Falha no débito automático: " + m.getNome(), msg, o.getId().toString());
                        notificacaoExternaService.notificarOcorrenciaSeNecessario(o, "DEBITO_SALDO_INSUF", "🛑 " + msg);
                        continue;
                    }
                    try {
                        PagarOcorrenciaRequest autoReq = new PagarOcorrenciaRequest(
                                MeioPagamento.CONTA.name(),
                                contaPadrao.getId(),
                                null,
                                hoje,
                                o.getValorReal(),
                                o.getLinhaDigitavel(),
                                "Pago automaticamente via Débito Automático"
                        );
                        executarPagamentoInterno(o, o.getUsuario(), autoReq);
                        continue;
                    } catch (Exception e) {
                        System.err.println("Erro no débito automático da ocorrência " + o.getId() + ": " + e.getMessage());
                    }
                }
            }

            // Gatilhos de alerta e notificação
            if (o.getStatus() == StatusOcorrencia.AGUARDANDO_VALOR && diasAteVencimento >= 0 && diasAteVencimento <= 5) {
                String msg = String.format("A conta \"%s\" vence em %s (%d dia(s)) e ainda está aguardando confirmação de valor.",
                        m.getNome(), o.getDataVencimento(), diasAteVencimento);
                criarInsightSeNaoExiste(o.getUsuario(), TipoInsight.CONTA_SEM_VALOR,
                        "Conta sem valor confirmado: " + m.getNome(), msg, o.getId().toString());
                notificacaoExternaService.notificarOcorrenciaSeNecessario(o, "D-5_SEM_VALOR", "⚪ " + msg);
            }

            if (diasAteVencimento == 3) {
                String msg = String.format("O boleto \"%s\" (R$ %.2f) vence em 3 dias (%s).",
                        m.getNome(), o.getValorEfetivo(), o.getDataVencimento());
                criarInsightSeNaoExiste(o.getUsuario(), TipoInsight.CONTA_VENCENDO,
                        "Conta vence em 3 dias: " + m.getNome(), msg, o.getId() + "_D3");
                notificacaoExternaService.notificarOcorrenciaSeNecessario(o, "D-3", "🗓️ " + msg);
            } else if (diasAteVencimento == 0) {
                String msg = String.format("O boleto \"%s\" (R$ %.2f) vence HOJE (%s).",
                        m.getNome(), o.getValorEfetivo(), o.getDataVencimento());
                criarInsightSeNaoExiste(o.getUsuario(), TipoInsight.CONTA_VENCENDO,
                        "Vence hoje: " + m.getNome(), msg, o.getId() + "_D0");
                notificacaoExternaService.notificarOcorrenciaSeNecessario(o, "D-0", "🔔 " + msg);
            } else if (diasAteVencimento < 0) {
                String msg = String.format("A conta \"%s\" (R$ %.2f) venceu em %s e consta como não paga.",
                        m.getNome(), o.getValorEfetivo(), o.getDataVencimento());
                criarInsightSeNaoExiste(o.getUsuario(), TipoInsight.CONTA_ATRASADA,
                        "Conta em atraso: " + m.getNome(), msg, o.getId() + "_ATRASO");
                notificacaoExternaService.notificarOcorrenciaSeNecessario(o, "D+1_ATRASO", "🔴 " + msg);
            }
        }
    }
}
