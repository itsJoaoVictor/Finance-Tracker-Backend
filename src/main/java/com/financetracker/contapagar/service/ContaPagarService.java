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
import com.financetracker.contapagar.entity.Financiamento;
import com.financetracker.contapagar.enums.*;
import com.financetracker.contapagar.exception.ContaPagarNaoEncontradaException;
import com.financetracker.contapagar.repository.ContaPagarAmortizacaoRepository;
import com.financetracker.contapagar.repository.ContaPagarModeloRepository;
import com.financetracker.contapagar.repository.ContaPagarOcorrenciaRepository;
import com.financetracker.contapagar.repository.FinanciamentoRepository;
import com.financetracker.contapagar.util.DiaUtilUtils;
import com.financetracker.contapagar.util.EstimadorParcelasUtils;
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
    private final FinanciamentoRepository financiamentoRepository;
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
                             FinanciamentoRepository financiamentoRepository,
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
        this.financiamentoRepository = financiamentoRepository;
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo inválido. Use VARIAVEL, FIXO, PARCELADO ou AVULSO.");
        }

        if ((tipo == TipoContaPagar.FIXO || tipo == TipoContaPagar.AVULSO)
                && (req.valorPadrao() == null || req.valorPadrao().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor é obrigatório para contas FIXAS e boletos AVULSOS.");
        }

        int parcelaInicial = 1;
        int totalParcelas = 1;
        int intervaloMeses = req.intervaloMeses() != null ? req.intervaloMeses() : 1;
        ModoParcelamento modoParcelamento = ModoParcelamento.FIXO;
        BigDecimal valorInicialModelo = req.valorPadrao();
        BigDecimal valorUltimaModelo = req.valorUltimaParcela();
        boolean valoresEstimados = Boolean.TRUE.equals(req.valoresEstimados());

        if (intervaloMeses < 1 || intervaloMeses > 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Intervalo entre parcelas deve ser entre 1 e 60 meses.");
        }

        if (tipo == TipoContaPagar.PARCELADO) {
            if (req.totalParcelas() == null || req.totalParcelas() < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Total de parcelas é obrigatório para contas PARCELADAS.");
            }
            totalParcelas = req.totalParcelas();
            parcelaInicial = req.parcelaInicial() != null && req.parcelaInicial() >= 1 ? req.parcelaInicial() : 1;
            if (parcelaInicial > totalParcelas) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parcela inicial não pode ser maior que o total de parcelas.");
            }
            int qtdRestante = totalParcelas - parcelaInicial + 1;

            if (req.modoParcelamento() != null && !req.modoParcelamento().isBlank()) {
                try {
                    modoParcelamento = ModoParcelamento.valueOf(req.modoParcelamento().toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modo de parcelamento inválido. Use FIXO, PROGRESSIVO ou PERSONALIZADO.");
                }
            }

            if (modoParcelamento == ModoParcelamento.PERSONALIZADO) {
                if (req.valoresParcelas() == null || req.valoresParcelas().size() != qtdRestante) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "No modo PERSONALIZADO, informe exatamente " + qtdRestante + " valor(es) de parcela.");
                }
                for (BigDecimal v : req.valoresParcelas()) {
                    if (v == null || v.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Todos os valores das parcelas devem ser maiores que zero.");
                    }
                }
                valorInicialModelo = req.valoresParcelas().get(0).setScale(2, RoundingMode.HALF_UP);
                valorUltimaModelo = req.valoresParcelas().get(qtdRestante - 1).setScale(2, RoundingMode.HALF_UP);
            } else if (modoParcelamento == ModoParcelamento.PROGRESSIVO) {
                if (valorInicialModelo == null || valorInicialModelo.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor da parcela inicial/atual é obrigatório.");
                }
                if (qtdRestante == 1) {
                    if (valorUltimaModelo == null) {
                        valorUltimaModelo = valorInicialModelo;
                    }
                } else if (valorUltimaModelo == null || valorUltimaModelo.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor estimado da última parcela é obrigatório no modo PROGRESSIVO.");
                }
                validarOverridesParcelas(req.valoresParcelas(), qtdRestante);
            } else {
                if (valorInicialModelo == null || valorInicialModelo.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor estimado da parcela é obrigatório.");
                }
                valorUltimaModelo = null;
                validarOverridesParcelas(req.valoresParcelas(), qtdRestante);
            }
        } else {
            intervaloMeses = 1;
            modoParcelamento = ModoParcelamento.FIXO;
            valorUltimaModelo = null;
            valoresEstimados = false;
        }

        Categoria categoria = findCategoriaDoUsuario(req.categoriaId(), usuario.getId());
        Conta contaPadrao = req.contaPadraoId() != null ? findContaDoUsuario(req.contaPadraoId(), usuario.getId()) : null;
        Financiamento financiamento = req.financiamentoId() != null
                ? financiamentoRepository.findByIdAndUsuarioIdAndAtivoTrue(req.financiamentoId(), usuario.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Financiamento não encontrado."))
                : null;

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
        modelo.setFinanciamento(financiamento);
        modelo.setDiaVencimento(req.diaVencimento());
        modelo.setValorPadrao(valorInicialModelo);
        modelo.setModoParcelamento(modoParcelamento);
        modelo.setValorUltimaParcela(valorUltimaModelo);
        modelo.setIntervaloMeses(intervaloMeses);
        modelo.setValoresEstimados(valoresEstimados);
        modelo.setDataInicio(mesInicio);
        modelo.setDebitoAutomatico(Boolean.TRUE.equals(req.debitoAutomatico()));
        modelo.setContaPadrao(contaPadrao);
        modelo.setAtivo(true);

        if (tipo == TipoContaPagar.PARCELADO) {
            modelo.setTotalParcelas(totalParcelas);
            modelo.setParcelaInicial(parcelaInicial);
            int qtdRestante = totalParcelas - parcelaInicial + 1;
            modelo.setDataFim(mesInicio.plusMonths((long) (qtdRestante - 1) * intervaloMeses));
        } else if (tipo == TipoContaPagar.AVULSO) {
            modelo.setTotalParcelas(1);
            modelo.setParcelaInicial(1);
            modelo.setDataFim(mesInicio);
        } else {
            modelo.setDataFim(req.dataFim() != null ? req.dataFim().withDayOfMonth(1) : null);
        }

        ContaPagarModelo saved = modeloRepository.save(modelo);

        // Geração inicial de ocorrências
        if (tipo == TipoContaPagar.PARCELADO) {
            gerarTodasParcelas(saved, req.valoresParcelas());
        } else if (tipo == TipoContaPagar.AVULSO) {
            ContaPagarOcorrencia unica = garantirOcorrenciaNoMes(saved, mesInicio);
            if (unica != null) {
                // Preserva a data de vencimento exata informada no boleto avulso e eventual linha digitável/observação
                unica.setDataVencimento(req.dataInicio());
                if (req.linhaDigitavel() != null && !req.linhaDigitavel().isBlank()) {
                    unica.setLinhaDigitavel(req.linhaDigitavel().trim());
                }
                if (req.observacao() != null && !req.observacao().isBlank()) {
                    unica.setObservacao(req.observacao().trim());
                }
                ocorrenciaRepository.save(unica);
            }
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
        Financiamento financiamento = req.financiamentoId() != null
                ? financiamentoRepository.findByIdAndUsuarioIdAndAtivoTrue(req.financiamentoId(), usuario.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Financiamento não encontrado."))
                : null;

        if (Boolean.TRUE.equals(req.debitoAutomatico()) && contaPadrao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conta padrão é obrigatória quando débito automático está ativo.");
        }

        modelo.setNome(req.nome().trim());
        modelo.setCategoria(categoria);
        modelo.setAgrupador(req.agrupador() != null && !req.agrupador().isBlank() ? req.agrupador().trim() : null);
        modelo.setFinanciamento(financiamento);
        modelo.setDiaVencimento(req.diaVencimento());
        modelo.setValorPadrao(req.valorPadrao());
        modelo.setDebitoAutomatico(Boolean.TRUE.equals(req.debitoAutomatico()));
        modelo.setContaPadrao(contaPadrao);

        if (modelo.getTipo() == TipoContaPagar.PARCELADO) {
            if (req.modoParcelamento() != null && !req.modoParcelamento().isBlank()) {
                try {
                    modelo.setModoParcelamento(ModoParcelamento.valueOf(req.modoParcelamento().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modo de parcelamento inválido. Use FIXO, PROGRESSIVO ou PERSONALIZADO.");
                }
            }
            if (req.valorUltimaParcela() != null) {
                if (req.valorUltimaParcela().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor da última parcela deve ser maior que zero.");
                }
                modelo.setValorUltimaParcela(req.valorUltimaParcela());
            } else if (modelo.getModoParcelamento() == ModoParcelamento.FIXO) {
                modelo.setValorUltimaParcela(null);
            }
            if (req.valoresEstimados() != null) {
                modelo.setValoresEstimados(req.valoresEstimados());
            }
        } else {
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

        if (Boolean.TRUE.equals(req.aplicarValorOcorrenciasFuturas())) {
            LocalDate mesAtual = DiaUtilUtils.hoje().withDayOfMonth(1);
            List<ContaPagarOcorrencia> ocorrencias = ocorrenciaRepository.findByModeloIdOrderByMesReferenciaAsc(modelo.getId());

            if (modelo.getTipo() == TipoContaPagar.PARCELADO) {
                List<ContaPagarOcorrencia> abertasFuturas = ocorrencias.stream()
                        .filter(o -> !o.getMesReferencia().isBefore(mesAtual)
                                && (o.getStatus() == StatusOcorrencia.PENDENTE || o.getStatus() == StatusOcorrencia.AGUARDANDO_VALOR))
                        .toList();

                boolean somenteEstimado = Boolean.TRUE.equals(modelo.getValoresEstimados());
                int qtdAbertas = abertasFuturas.size();
                ModoParcelamento modoAtual = modelo.getModoParcelamento() != null ? modelo.getModoParcelamento() : ModoParcelamento.FIXO;

                if (modoAtual == ModoParcelamento.PROGRESSIVO) {
                    if (modelo.getValorPadrao() == null || modelo.getValorPadrao().compareTo(BigDecimal.ZERO) <= 0
                            || (qtdAbertas > 1 && (modelo.getValorUltimaParcela() == null || modelo.getValorUltimaParcela().compareTo(BigDecimal.ZERO) <= 0))) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Informe o valor da parcela atual e da última parcela para recalcular a curva progressiva.");
                    }
                    for (int i = 0; i < qtdAbertas; i++) {
                        ContaPagarOcorrencia o = abertasFuturas.get(i);
                        BigDecimal valorCalculado = calcularValorParcelaProgressiva(
                                modelo.getValorPadrao(), modelo.getValorUltimaParcela(), i, qtdAbertas);
                        aplicarValorOcorrenciaParcelada(o, valorCalculado, somenteEstimado);
                        ocorrenciaRepository.save(o);
                    }
                } else if (req.valorPadrao() != null && req.valorPadrao().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal valorFixo = req.valorPadrao().setScale(2, RoundingMode.HALF_UP);
                    for (ContaPagarOcorrencia o : abertasFuturas) {
                        aplicarValorOcorrenciaParcelada(o, valorFixo, somenteEstimado);
                        ocorrenciaRepository.save(o);
                    }
                }
            } else if (req.valorPadrao() != null) {
                for (ContaPagarOcorrencia o : ocorrencias) {
                    if (!o.getMesReferencia().isBefore(mesAtual) && o.getStatus() == StatusOcorrencia.PENDENTE) {
                        o.setValorReal(req.valorPadrao());
                        o.setValorEstimado(req.valorPadrao());
                        ocorrenciaRepository.save(o);
                    }
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

    private void validarOverridesParcelas(List<BigDecimal> valoresParcelas, int qtdRestante) {
        if (valoresParcelas == null || valoresParcelas.isEmpty()) {
            return;
        }
        if (valoresParcelas.size() > qtdRestante) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A lista de valores customizados possui mais itens que o número de parcelas restantes (" + qtdRestante + ").");
        }
        for (BigDecimal v : valoresParcelas) {
            if (v != null && v.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Todos os valores customizados das parcelas devem ser maiores que zero.");
            }
        }
    }

    public static BigDecimal calcularValorParcelaProgressiva(
            BigDecimal valorInicial, BigDecimal valorFinal, int indice, int quantidadeTotal) {
        if (quantidadeTotal <= 1 || valorFinal == null) {
            return valorInicial.setScale(2, RoundingMode.HALF_UP);
        }
        if (indice <= 0) {
            return valorInicial.setScale(2, RoundingMode.HALF_UP);
        }
        if (indice >= quantidadeTotal - 1) {
            return valorFinal.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal diferenca = valorFinal.subtract(valorInicial);
        BigDecimal proporcao = BigDecimal.valueOf(indice)
                .divide(BigDecimal.valueOf(quantidadeTotal - 1), 10, RoundingMode.HALF_UP);
        return valorInicial.add(diferenca.multiply(proporcao)).setScale(2, RoundingMode.HALF_UP);
    }

    private void aplicarValorOcorrenciaParcelada(ContaPagarOcorrencia o, BigDecimal valor, boolean somenteEstimado) {
        o.setValorEstimado(valor);
        if (somenteEstimado) {
            o.setValorReal(null);
            o.setStatus(StatusOcorrencia.AGUARDANDO_VALOR);
        } else {
            o.setValorReal(valor);
            o.setStatus(StatusOcorrencia.PENDENTE);
        }
    }

    private void gerarTodasParcelas(ContaPagarModelo modelo, List<BigDecimal> valoresCustomizados) {
        int inicio = modelo.getParcelaInicial() != null ? modelo.getParcelaInicial() : 1;
        int total = modelo.getTotalParcelas() != null ? modelo.getTotalParcelas() : 1;
        int qtdRestante = total - inicio + 1;
        int intervalo = modelo.getIntervaloMeses() != null && modelo.getIntervaloMeses() >= 1 ? modelo.getIntervaloMeses() : 1;
        ModoParcelamento modo = modelo.getModoParcelamento() != null ? modelo.getModoParcelamento() : ModoParcelamento.FIXO;
        boolean somenteEstimado = Boolean.TRUE.equals(modelo.getValoresEstimados());
        LocalDate mesBase = modelo.getDataInicio().withDayOfMonth(1);

        for (int p = inicio; p <= total; p++) {
            int offset = p - inicio;
            LocalDate mesRef = mesBase.plusMonths((long) offset * intervalo);
            if (ocorrenciaRepository.findByModeloIdAndMesReferencia(modelo.getId(), mesRef).isPresent()) {
                continue;
            }

            BigDecimal valorParcela;
            if (valoresCustomizados != null && offset < valoresCustomizados.size() && valoresCustomizados.get(offset) != null) {
                valorParcela = valoresCustomizados.get(offset).setScale(2, RoundingMode.HALF_UP);
            } else if (modo == ModoParcelamento.PROGRESSIVO) {
                valorParcela = calcularValorParcelaProgressiva(
                        modelo.getValorPadrao(), modelo.getValorUltimaParcela(), offset, qtdRestante);
            } else {
                valorParcela = modelo.getValorPadrao().setScale(2, RoundingMode.HALF_UP);
            }

            ContaPagarOcorrencia o = new ContaPagarOcorrencia();
            o.setUsuario(modelo.getUsuario());
            o.setModelo(modelo);
            o.setMesReferencia(mesRef);
            o.setNumeroParcela(p);
            o.setDataVencimento(DiaUtilUtils.calcularDataVencimento(mesRef, modelo.getDiaVencimento()));
            aplicarValorOcorrenciaParcelada(o, valorParcela, somenteEstimado);
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

        ContaPagarOcorrencia saved = ocorrenciaRepository.save(o);
        if (req.valorReal() != null) {
            recalibrarParcelasFuturasPorHistorico(saved.getModelo());
        }

        return new ContaPagarOcorrenciaResponse(saved);
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

        // Atualizar estimativa de ocorrências futuras AGUARDANDO_VALOR
        if (modelo.getTipo() == TipoContaPagar.VARIAVEL) {
            BigDecimal novaMedia = calcularMediaUltimos3Pagos(modelo);
            List<ContaPagarOcorrencia> futuras = ocorrenciaRepository
                    .findByModeloIdAndStatusOrderByMesReferenciaDesc(modelo.getId(), StatusOcorrencia.AGUARDANDO_VALOR);
            for (ContaPagarOcorrencia f : futuras) {
                f.setValorEstimado(novaMedia);
                ocorrenciaRepository.save(f);
            }
        } else if (modelo.getTipo() == TipoContaPagar.PARCELADO) {
            recalibrarParcelasFuturasPorHistorico(modelo);
        }

        return new ContaPagarOcorrenciaResponse(saved);
    }

    /**
     * Recalibra matematicamente o valorEstimado das parcelas futuras ainda em AGUARDANDO_VALOR
     * usando o histórico real de parcelas já pagas ou com valorReal confirmado.
     * Atua apenas em modelos PARCELADO com valoresEstimados = true e modo FIXO ou PROGRESSIVO.
     */
    public void recalibrarParcelasFuturasPorHistorico(ContaPagarModelo modelo) {
        if (modelo == null
                || modelo.getTipo() != TipoContaPagar.PARCELADO
                || !Boolean.TRUE.equals(modelo.getValoresEstimados())) {
            return;
        }
        ModoParcelamento modo = modelo.getModoParcelamento() != null
                ? modelo.getModoParcelamento()
                : ModoParcelamento.FIXO;
        if (modo == ModoParcelamento.PERSONALIZADO) {
            return;
        }

        List<ContaPagarOcorrencia> todas = ocorrenciaRepository.findByModeloIdOrderByMesReferenciaAsc(modelo.getId());
        if (todas == null || todas.isEmpty()) {
            return;
        }

        List<BigDecimal> historicoValores = new ArrayList<>();
        LocalDate ultimoMesHistorico = null;
        for (ContaPagarOcorrencia oc : todas) {
            BigDecimal valorObservado = null;
            if (oc.getStatus() == StatusOcorrencia.PAGA) {
                // Prioriza valorReal para evitar que eventual multa por atraso distorça parcelas futuras
                valorObservado = oc.getValorReal() != null ? oc.getValorReal() : oc.getValorPago();
            } else if (oc.getStatus() == StatusOcorrencia.PENDENTE && oc.getValorReal() != null) {
                valorObservado = oc.getValorReal();
            }
            if (valorObservado != null && valorObservado.compareTo(BigDecimal.ZERO) > 0) {
                historicoValores.add(valorObservado);
                ultimoMesHistorico = oc.getMesReferencia();
            }
        }

        if (historicoValores.isEmpty() || ultimoMesHistorico == null) {
            return;
        }

        final LocalDate corte = ultimoMesHistorico;
        List<ContaPagarOcorrencia> futurasAguardando = todas.stream()
                .filter(oc -> oc.getStatus() == StatusOcorrencia.AGUARDANDO_VALOR
                        && oc.getMesReferencia() != null
                        && oc.getMesReferencia().isAfter(corte))
                .toList();

        if (futurasAguardando.isEmpty()) {
            return;
        }

        List<BigDecimal> novasEstimativas = EstimadorParcelasUtils.projetarParcelasFuturas(
                modo,
                modelo.getValorPadrao(),
                modelo.getValorUltimaParcela(),
                historicoValores,
                futurasAguardando.size()
        );

        for (int i = 0; i < futurasAguardando.size(); i++) {
            ContaPagarOcorrencia futura = futurasAguardando.get(i);
            BigDecimal nova = novasEstimativas.get(i);
            if (futura.getValorEstimado() == null || futura.getValorEstimado().compareTo(nova) != 0) {
                futura.setValorEstimado(nova);
                ocorrenciaRepository.save(futura);
            }
        }
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

        BigDecimal totalDescontoObtido = amortizacoes.stream()
                .map(ContaPagarAmortizacao::getDescontoObtido)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean concluido = restantes == 0;

        return new FinanciamentoDetalheResponse(
                new ContaPagarModeloResponse(modelo),
                pagas + anterioresForaDoApp,
                restantes,
                canceladas,
                totalPagoParcelas,
                totalAmortizacoes,
                totalPagoParcelas.add(totalAmortizacoes),
                totalJuros,
                totalDescontoObtido,
                totalRestanteEstimado,
                concluido,
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
        DirecaoAbatimento direcao = DirecaoAbatimento.ULTIMAS;
        try {
            origem = OrigemAmortizacao.valueOf(req.origem().toUpperCase());
            modo = ModoAmortizacao.valueOf(req.modo().toUpperCase());
            if (req.direcaoAbatimento() != null && !req.direcaoAbatimento().isBlank()) {
                direcao = DirecaoAbatimento.valueOf(req.direcaoAbatimento().toUpperCase());
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Origem, modo ou direção de amortização inválido.");
        }

        if (req.descontoObtido() != null && req.descontoObtido().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Desconto obtido não pode ser negativo.");
        }

        List<ContaPagarOcorrencia> todas = ocorrenciaRepository.findByModeloIdOrderByMesReferenciaAsc(modeloId);
        List<ContaPagarOcorrencia> abertas = todas.stream()
                .filter(o -> o.getStatus() == StatusOcorrencia.PENDENTE || o.getStatus() == StatusOcorrencia.AGUARDANDO_VALOR)
                .toList();

        if (abertas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este financiamento não possui parcelas em aberto para amortizar.");
        }

        // Validar parâmetros antes de debitar conta
        int eliminar = 0;
        if (modo == ModoAmortizacao.REDUZIR_PRAZO) {
            eliminar = req.parcelasEliminadas() != null ? req.parcelasEliminadas() : 0;
            if (eliminar <= 0 || eliminar > abertas.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Informe uma quantidade válida de parcelas eliminadas (máx: " + abertas.size() + ").");
            }
        } else {
            if (req.novoValorParcela() == null || req.novoValorParcela().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o novo valor estimado da parcela.");
            }
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

        boolean somenteEstimado = Boolean.TRUE.equals(modelo.getValoresEstimados());
        BigDecimal valorOriginalAbatido;

        if (modo == ModoAmortizacao.REDUZIR_PRAZO) {
            List<ContaPagarOcorrencia> alvos;
            List<ContaPagarOcorrencia> restantesApos;
            if (direcao == DirecaoAbatimento.PROXIMAS) {
                alvos = abertas.subList(0, eliminar);
                restantesApos = abertas.subList(eliminar, abertas.size());
            } else {
                alvos = abertas.subList(abertas.size() - eliminar, abertas.size());
                restantesApos = abertas.subList(0, abertas.size() - eliminar);
            }

            valorOriginalAbatido = BigDecimal.ZERO;
            String sufixoDirecao = direcao == DirecaoAbatimento.PROXIMAS ? " (antecipação)" : "";
            for (ContaPagarOcorrencia alvo : alvos) {
                valorOriginalAbatido = valorOriginalAbatido.add(alvo.getValorEfetivo());
                alvo.setStatus(StatusOcorrencia.CANCELADA);
                alvo.setObservacao("Abatida por amortização extra em " + req.data() + sufixoDirecao);
                ocorrenciaRepository.save(alvo);
            }

            if (modelo.getTotalParcelas() != null) {
                modelo.setTotalParcelas(Math.max(0, modelo.getTotalParcelas() - eliminar));
            }
            atualizarDataFimModelo(modelo, todas, restantesApos);

            if (req.novoValorParcela() != null && req.novoValorParcela().compareTo(BigDecimal.ZERO) > 0) {
                atualizarValoresParcelasRestantes(modelo, restantesApos, req.novoValorParcela(), req.novoValorUltimaParcela(), somenteEstimado);
            } else if (!restantesApos.isEmpty()
                    && modelo.getModoParcelamento() == ModoParcelamento.PROGRESSIVO
                    && direcao == DirecaoAbatimento.ULTIMAS) {
                // Nova última parcela ativa passa a ser a referência final do contrato
                modelo.setValorUltimaParcela(restantesApos.get(restantesApos.size() - 1).getValorEfetivo());
            }
            modeloRepository.save(modelo);
        } else {
            BigDecimal restanteAntes = abertas.stream()
                    .map(ContaPagarOcorrencia::getValorEfetivo)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            atualizarValoresParcelasRestantes(modelo, abertas, req.novoValorParcela(), req.novoValorUltimaParcela(), somenteEstimado);
            modeloRepository.save(modelo);

            BigDecimal restanteDepois = abertas.stream()
                    .map(ContaPagarOcorrencia::getValorEfetivo)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            valorOriginalAbatido = restanteAntes.subtract(restanteDepois).max(BigDecimal.ZERO);
        }

        BigDecimal descontoObtido = req.descontoObtido() != null
                ? req.descontoObtido().setScale(2, RoundingMode.HALF_UP)
                : valorOriginalAbatido.subtract(req.valor()).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        ContaPagarAmortizacao amort = new ContaPagarAmortizacao();
        amort.setUsuario(usuario);
        amort.setModelo(modelo);
        amort.setValor(req.valor());
        amort.setData(req.data());
        amort.setOrigem(origem);
        amort.setConta(conta);
        amort.setModo(modo);
        amort.setDirecaoAbatimento(direcao);
        amort.setParcelasEliminadas(modo == ModoAmortizacao.REDUZIR_PRAZO ? eliminar : null);
        amort.setNovoValorParcela(req.novoValorParcela());
        amort.setValorOriginalAbatido(valorOriginalAbatido.setScale(2, RoundingMode.HALF_UP));
        amort.setDescontoObtido(descontoObtido);
        amort.setObservacao(req.observacao());
        amort.setTransacao(transacao);
        amortizacaoRepository.save(amort);

        return obterDetalheFinanciamento(modeloId);
    }

    @Transactional
    public FinanciamentoDetalheResponse ajustarPrazoParcelamento(UUID modeloId, AjustarPrazoRequest req) {
        Usuario usuario = getAuthenticatedUsuario();
        ContaPagarModelo modelo = modeloRepository.findByIdAndUsuarioIdAndAtivoTrue(modeloId, usuario.getId())
                .orElseThrow(ContaPagarNaoEncontradaException::new);

        if (modelo.getTipo() != TipoContaPagar.PARCELADO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ajuste de prazo só é permitido para contas PARCELADAS.");
        }

        String acao = req.acao().trim().toUpperCase();
        String motivo = (req.motivo() != null && !req.motivo().isBlank()) ? req.motivo().trim() : null;

        List<ContaPagarOcorrencia> todas = ocorrenciaRepository.findByModeloIdOrderByMesReferenciaAsc(modeloId);
        List<ContaPagarOcorrencia> abertas = todas.stream()
                .filter(o -> o.getStatus() == StatusOcorrencia.PENDENTE || o.getStatus() == StatusOcorrencia.AGUARDANDO_VALOR)
                .toList();

        if ("ENCERRAR".equals(acao)) {
            if (abertas.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este contrato já não possui parcelas em aberto.");
            }
            String obs = motivo != null ? "Encerrado antecipadamente: " + motivo : "Encerrado antecipadamente";
            for (ContaPagarOcorrencia o : abertas) {
                o.setStatus(StatusOcorrencia.CANCELADA);
                o.setObservacao(obs);
                ocorrenciaRepository.save(o);
            }
            if (modelo.getTotalParcelas() != null) {
                modelo.setTotalParcelas(Math.max(0, modelo.getTotalParcelas() - abertas.size()));
            }
            atualizarDataFimModelo(modelo, todas, List.of());
            modeloRepository.save(modelo);
        } else if ("REDUZIR".equals(acao)) {
            int qtd = req.quantidadeParcelas() != null ? req.quantidadeParcelas() : 0;
            if (qtd <= 0 || qtd > abertas.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Informe uma quantidade válida de parcelas para reduzir (máx: " + abertas.size() + ").");
            }
            String obs = motivo != null ? "Prazo reduzido: " + motivo : "Prazo reduzido antecipadamente";
            List<ContaPagarOcorrencia> alvos = abertas.subList(abertas.size() - qtd, abertas.size());
            List<ContaPagarOcorrencia> restantes = abertas.subList(0, abertas.size() - qtd);
            for (ContaPagarOcorrencia alvo : alvos) {
                alvo.setStatus(StatusOcorrencia.CANCELADA);
                alvo.setObservacao(obs);
                ocorrenciaRepository.save(alvo);
            }
            if (modelo.getTotalParcelas() != null) {
                modelo.setTotalParcelas(Math.max(0, modelo.getTotalParcelas() - qtd));
            }
            atualizarDataFimModelo(modelo, todas, restantes);
            modeloRepository.save(modelo);

            // Se for PROGRESSIVO estimado (ex.: Taxa de Obra adiantou), recalibrar as parcelas restantes
            if (!restantes.isEmpty()
                    && Boolean.TRUE.equals(modelo.getValoresEstimados())
                    && modelo.getModoParcelamento() == ModoParcelamento.PROGRESSIVO) {
                boolean temHistorico = todas.stream().anyMatch(oc ->
                        oc.getStatus() == StatusOcorrencia.PAGA
                                || (oc.getStatus() == StatusOcorrencia.PENDENTE && oc.getValorReal() != null));
                if (temHistorico) {
                    recalibrarParcelasFuturasPorHistorico(modelo);
                } else if (modelo.getValorPadrao() != null && modelo.getValorUltimaParcela() != null) {
                    for (int i = 0; i < restantes.size(); i++) {
                        ContaPagarOcorrencia r = restantes.get(i);
                        if (r.getStatus() == StatusOcorrencia.AGUARDANDO_VALOR) {
                            BigDecimal v = calcularValorParcelaProgressiva(
                                    modelo.getValorPadrao(), modelo.getValorUltimaParcela(), i, restantes.size());
                            r.setValorEstimado(v);
                            ocorrenciaRepository.save(r);
                        }
                    }
                }
            }
        } else if ("ESTENDER".equals(acao)) {
            int qtd = req.quantidadeParcelas() != null ? req.quantidadeParcelas() : 0;
            if (qtd <= 0 || qtd > 120) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Informe entre 1 e 120 parcelas para estender o prazo.");
            }
            int intervalo = modelo.getIntervaloMeses() != null && modelo.getIntervaloMeses() >= 1
                    ? modelo.getIntervaloMeses() : 1;
            boolean somenteEstimado = Boolean.TRUE.equals(modelo.getValoresEstimados());

            // Determinar valor base das novas parcelas
            BigDecimal valorAdicional = req.valorParcelasAdicionadas();
            if (valorAdicional == null || valorAdicional.compareTo(BigDecimal.ZERO) <= 0) {
                if (!abertas.isEmpty()) {
                    valorAdicional = abertas.get(abertas.size() - 1).getValorEfetivo();
                } else if (modelo.getValorUltimaParcela() != null && modelo.getValorUltimaParcela().compareTo(BigDecimal.ZERO) > 0) {
                    valorAdicional = modelo.getValorUltimaParcela();
                } else if (modelo.getValorPadrao() != null && modelo.getValorPadrao().compareTo(BigDecimal.ZERO) > 0) {
                    valorAdicional = modelo.getValorPadrao();
                } else {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Informe o valor estimado das parcelas adicionadas.");
                }
            }
            valorAdicional = valorAdicional.setScale(2, RoundingMode.HALF_UP);

            // Último mês ativo (PAGA, PENDENTE ou AGUARDANDO_VALOR)
            LocalDate ultimoMesAtivo = todas.stream()
                    .filter(o -> o.getStatus() != StatusOcorrencia.CANCELADA)
                    .map(ContaPagarOcorrencia::getMesReferencia)
                    .max(Comparator.naturalOrder())
                    .orElse(null);

            int ultimaParcelaAtivaNum = todas.stream()
                    .filter(o -> o.getStatus() != StatusOcorrencia.CANCELADA && o.getNumeroParcela() != null)
                    .mapToInt(ContaPagarOcorrencia::getNumeroParcela)
                    .max()
                    .orElse(modelo.getTotalParcelas() != null ? modelo.getTotalParcelas() : 0);

            // 1. Reativar eventuais parcelas CANCELADAS posteriores ao último mês ativo (evita conflito de UNIQUE modelo_id+mes_referencia)
            List<ContaPagarOcorrencia> canceladasPosteriores = todas.stream()
                    .filter(o -> o.getStatus() == StatusOcorrencia.CANCELADA
                            && (ultimoMesAtivo == null || o.getMesReferencia().isAfter(ultimoMesAtivo)))
                    .toList();

            int adicionadas = 0;
            LocalDate novoDataFim = ultimoMesAtivo != null ? ultimoMesAtivo : modelo.getDataInicio().withDayOfMonth(1);
            String obsExtensao = motivo != null ? "Prazo estendido: " + motivo : "Prazo estendido";

            for (ContaPagarOcorrencia canc : canceladasPosteriores) {
                if (adicionadas >= qtd) break;
                adicionadas++;
                ultimaParcelaAtivaNum++;
                canc.setNumeroParcela(ultimaParcelaAtivaNum);
                aplicarValorOcorrenciaParcelada(canc, valorAdicional, somenteEstimado);
                canc.setObservacao(obsExtensao);
                ocorrenciaRepository.save(canc);
                if (canc.getMesReferencia().isAfter(novoDataFim)) {
                    novoDataFim = canc.getMesReferencia();
                }
            }

            // 2. Criar novas ocorrências além da maior data existente
            LocalDate maiorMesExistente = todas.stream()
                    .map(ContaPagarOcorrencia::getMesReferencia)
                    .max(Comparator.naturalOrder())
                    .orElse(modelo.getDataInicio().withDayOfMonth(1).minusMonths(intervalo));

            while (adicionadas < qtd) {
                adicionadas++;
                ultimaParcelaAtivaNum++;
                maiorMesExistente = maiorMesExistente.plusMonths(intervalo);

                ContaPagarOcorrencia nova = new ContaPagarOcorrencia();
                nova.setUsuario(modelo.getUsuario());
                nova.setModelo(modelo);
                nova.setMesReferencia(maiorMesExistente);
                nova.setNumeroParcela(ultimaParcelaAtivaNum);
                nova.setDataVencimento(DiaUtilUtils.calcularDataVencimento(maiorMesExistente, modelo.getDiaVencimento()));
                aplicarValorOcorrenciaParcelada(nova, valorAdicional, somenteEstimado);
                nova.setObservacao(obsExtensao);
                ocorrenciaRepository.save(nova);
                novoDataFim = maiorMesExistente;
            }

            int totalAnterior = modelo.getTotalParcelas() != null ? modelo.getTotalParcelas() : 0;
            modelo.setTotalParcelas(totalAnterior + qtd);
            modelo.setDataFim(novoDataFim);
            modeloRepository.save(modelo);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ação inválida. Use ENCERRAR, REDUZIR ou ESTENDER.");
        }

        return obterDetalheFinanciamento(modeloId);
    }

    private void atualizarDataFimModelo(
            ContaPagarModelo modelo,
            List<ContaPagarOcorrencia> todas,
            List<ContaPagarOcorrencia> restantesApos) {
        if (!restantesApos.isEmpty()) {
            modelo.setDataFim(restantesApos.get(restantesApos.size() - 1).getMesReferencia());
        } else {
            LocalDate ultimoMesPago = todas.stream()
                    .filter(o -> o.getStatus() == StatusOcorrencia.PAGA)
                    .map(ContaPagarOcorrencia::getMesReferencia)
                    .max(Comparator.naturalOrder())
                    .orElse(modelo.getDataInicio());
            modelo.setDataFim(ultimoMesPago);
        }
    }

    private void atualizarValoresParcelasRestantes(
            ContaPagarModelo modelo,
            List<ContaPagarOcorrencia> restantes,
            BigDecimal novoValorInicial,
            BigDecimal novoValorUltima,
            boolean somenteEstimado) {
        modelo.setValorPadrao(novoValorInicial.setScale(2, RoundingMode.HALF_UP));
        if (restantes.isEmpty()) {
            return;
        }
        boolean usarCurva = modelo.getModoParcelamento() == ModoParcelamento.PROGRESSIVO
                && (novoValorUltima != null && novoValorUltima.compareTo(BigDecimal.ZERO) > 0);
        if (usarCurva) {
            modelo.setValorUltimaParcela(novoValorUltima.setScale(2, RoundingMode.HALF_UP));
            for (int i = 0; i < restantes.size(); i++) {
                BigDecimal v = calcularValorParcelaProgressiva(novoValorInicial, novoValorUltima, i, restantes.size());
                aplicarValorOcorrenciaParcelada(restantes.get(i), v, somenteEstimado);
                ocorrenciaRepository.save(restantes.get(i));
            }
        } else {
            BigDecimal valorFixo = novoValorInicial.setScale(2, RoundingMode.HALF_UP);
            for (ContaPagarOcorrencia o : restantes) {
                aplicarValorOcorrenciaParcelada(o, valorFixo, somenteEstimado);
                ocorrenciaRepository.save(o);
            }
        }
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
