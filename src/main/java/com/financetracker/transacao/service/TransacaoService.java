package com.financetracker.transacao.service;

import com.financetracker.cartao.entity.Cartao;
import com.financetracker.cartao.repository.CartaoRepository;
import com.financetracker.categoria.entity.Categoria;
import com.financetracker.categoria.exception.CategoriaNaoEncontradaException;
import com.financetracker.categoria.repository.CategoriaRepository;
import com.financetracker.conta.entity.Conta;
import com.financetracker.conta.exception.ContaNaoEncontradaException;
import com.financetracker.conta.repository.ContaRepository;
import com.financetracker.transacao.dto.*;
import com.financetracker.transacao.entity.*;
import com.financetracker.transacao.enums.*;
import com.financetracker.transacao.exception.*;
import com.financetracker.transacao.repository.*;
import com.financetracker.transacao.entity.AgendamentoTransacao;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TransacaoService {

    private final TransacaoRepository transacaoRepository;
    private final FaturaRepository faturaRepository;
    private final MetasEconomiaRepository metasRepository;
    private final OrcamentoCategoriaRepository orcamentoRepository;
    private final TagRepository tagRepository;
    private final AgendamentoTransacaoRepository agendamentoRepository;
    private final ContaRepository contaRepository;
    private final CartaoRepository cartaoRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final com.financetracker.ia.service.IaService iaService;

    public TransacaoService(TransacaoRepository transacaoRepository,
                            FaturaRepository faturaRepository,
                            MetasEconomiaRepository metasRepository,
                            OrcamentoCategoriaRepository orcamentoRepository,
                            TagRepository tagRepository,
                            AgendamentoTransacaoRepository agendamentoRepository,
                            ContaRepository contaRepository,
                            CartaoRepository cartaoRepository,
                            CategoriaRepository categoriaRepository,
                            UsuarioRepository usuarioRepository,
                            com.financetracker.ia.service.IaService iaService) {
        this.transacaoRepository = transacaoRepository;
        this.faturaRepository = faturaRepository;
        this.metasRepository = metasRepository;
        this.orcamentoRepository = orcamentoRepository;
        this.tagRepository = tagRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.contaRepository = contaRepository;
        this.cartaoRepository = cartaoRepository;
        this.categoriaRepository = categoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.iaService = iaService;
    }

    private Usuario getAuthenticatedUsuario() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autenticado"));
    }

    // ── helpers de verificação de posse (Anti-IDOR) ──────────────

    private Conta findContaDoUsuario(UUID contaId, UUID usuarioId) {
        return contaRepository.findByIdAndUsuarioIdAndAtivoTrue(contaId, usuarioId)
                .orElseThrow(ContaNaoEncontradaException::new);
    }

    private Cartao findCartaoDoUsuario(UUID cartaoId, UUID usuarioId) {
        return cartaoRepository.findByIdAndUsuarioIdAndAtivoTrue(cartaoId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cartão não encontrado."));
    }

    private Categoria findCategoriaDoUsuario(UUID categoriaId, UUID usuarioId) {
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(CategoriaNaoEncontradaException::new);
        if (categoria.getUsuario() != null && !categoria.getUsuario().getId().equals(usuarioId)) {
            throw new CategoriaNaoEncontradaException();
        }
        return categoria;
    }

    private Fatura findFaturaDoUsuario(UUID faturaId, UUID usuarioId) {
        return faturaRepository.findByIdAndUsuarioId(faturaId, usuarioId)
                .orElseThrow(FaturaNaoEncontradaException::new);
    }

    private MetasEconomia findMetaDoUsuario(UUID metaId, UUID usuarioId) {
        return metasRepository.findByIdAndUsuarioIdAndAtivoTrue(metaId, usuarioId)
                .orElseThrow(MetaNaoEncontradaException::new);
    }

    private void validarTags(List<UUID> tagIds, UUID usuarioId) {
        if (tagIds == null || tagIds.isEmpty()) return;
        for (UUID tagId : tagIds) {
            tagRepository.findByIdAndUsuarioIdAndAtivoTrue(tagId, usuarioId)
                    .orElseThrow(TagNaoEncontradaException::new);
        }
    }

    // ── Alerta de Orçamento (RN-13) ─────────────────────────────

    private TransacaoResponse.AlertaOrcamento calcularAlertaOrcamento(
            UUID usuarioId, UUID categoriaId, BigDecimal novoValor, TipoTransacao tipo) {

        if (categoriaId == null) return null;
        if (tipo != TipoTransacao.SAQUE && tipo != TipoTransacao.PIX
                && tipo != TipoTransacao.COMPRA_CREDITO) return null;

        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        OrcamentoCategoria orcamento = orcamentoRepository
                .findByUsuarioIdAndCategoriaIdAndMesReferencia(usuarioId, categoriaId, inicioMes)
                .orElse(null);
        if (orcamento == null || orcamento.getLimiteMensal() == null) return null;

        BigDecimal limite = orcamento.getLimiteMensal();
        BigDecimal jaGasto = transacaoRepository.sumValorByCategoriaAndPeriodo(
                usuarioId, categoriaId, inicioMes, inicioMes.plusMonths(1).minusDays(1),
                List.of(TipoTransacao.SAQUE, TipoTransacao.PIX, TipoTransacao.COMPRA_CREDITO));
        BigDecimal total = (jaGasto != null ? jaGasto : BigDecimal.ZERO).add(novoValor);

        double percentual = total.divide(limite, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue();

        if (percentual >= 80.0) {
            return new TransacaoResponse.AlertaOrcamento(true, percentual, limite, total);
        }
        return null;
    }

    // ── Cálculo da fatura aberta ────────────────────────────────

    private LocalDate calcularMesReferenciaFatura(Cartao cartao, LocalDate data) {
        int diaFechamento = cartao.getDiaFechamento();
        if (data.getDayOfMonth() < diaFechamento) {
            return data.withDayOfMonth(1);
        } else {
            return data.plusMonths(1).withDayOfMonth(1);
        }
    }

    private Fatura getOrCreateFatura(Cartao cartao, Usuario usuario, LocalDate mesReferencia) {
        Optional<Fatura> existente = faturaRepository
                .findByCartaoIdAndUsuarioIdAndMesReferencia(cartao.getId(), usuario.getId(), mesReferencia);
        if (existente.isPresent()) {
            return existente.get();
        }

        int diaFechamento = cartao.getDiaFechamento();
        int diaVencimento = cartao.getDiaVencimento();

        LocalDate dataFechamento = mesReferencia.withDayOfMonth(diaFechamento);
        LocalDate dataVencimento = mesReferencia.withDayOfMonth(diaVencimento);
        if (dataVencimento.isBefore(dataFechamento)) {
            dataVencimento = dataVencimento.plusMonths(1);
        }

        Fatura fatura = new Fatura();
        fatura.setUsuario(usuario);
        fatura.setCartao(cartao);
        fatura.setMesReferencia(mesReferencia);
        fatura.setDataFechamento(dataFechamento);
        fatura.setDataVencimento(dataVencimento);
        fatura.setValorTotal(BigDecimal.ZERO);
        fatura.setValorPago(BigDecimal.ZERO);

        LocalDate dataCriacaoUsuario = usuario.getCriadoEm().toLocalDate();
        if (dataVencimento.isBefore(dataCriacaoUsuario)) {
            fatura.setStatus(StatusFatura.PAGA);
        } else {
            LocalDate hoje = LocalDate.now();
            if (hoje.isAfter(dataFechamento) || hoje.isEqual(dataFechamento)) {
                fatura.setStatus(StatusFatura.FECHADA);
            } else {
                fatura.setStatus(StatusFatura.ABERTA);
            }
        }

        return faturaRepository.save(fatura);
    }

    private Fatura getOrCreateFaturaAberta(Cartao cartao, Usuario usuario, LocalDate data) {
        LocalDate mesReferencia = calcularMesReferenciaFatura(cartao, data);
        return getOrCreateFatura(cartao, usuario, mesReferencia);
    }

    // ── Criação de Transação Comum (POST /api/transacoes) ──────

    @Transactional
    public TransacaoResponse criar(TransacaoCriacaoRequest request) {
        Usuario usuario = getAuthenticatedUsuario();
        TipoTransacao tipo = TipoTransacao.valueOf(request.tipo());

        if (tipo == TipoTransacao.COMPRA_CREDITO && (request.descricao() == null || request.descricao().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Descrição é obrigatória.");
        }

        String descricao = request.descricao();
        if (descricao == null || descricao.isBlank()) {
            descricao = switch (tipo) {
                case DEPOSITO -> "Depósito";
                case SAQUE -> "Saque";
                case PIX -> "Pix";
                default -> tipo.name();
            };
        }

        // RN-01 — Anti-IDOR: validar posse de todos os recursos
        Categoria categoria = null;
        if (request.categoriaId() != null) {
            categoria = findCategoriaDoUsuario(request.categoriaId(), usuario.getId());
        } else if (tipo != TipoTransacao.DEPOSITO && tipo != TipoTransacao.SAQUE && tipo != TipoTransacao.PIX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria é obrigatória.");
        }
        validarTags(request.tagIds(), usuario.getId());

        Conta contaOrigem = null;
        Conta contaDestino = null;
        Cartao cartao = null;
        Fatura fatura = null;
        MetasEconomia metaOrigem = null;
        MetasEconomia metaDestino = null;

        switch (tipo) {
            case DEPOSITO -> {
                if (request.contaDestinoId() == null)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contaDestinoId é obrigatório para DEPOSITO.");
                contaDestino = findContaDoUsuario(request.contaDestinoId(), usuario.getId());
                // RN-02 — Soma ao saldo
                contaDestino.setSaldo(contaDestino.getSaldo().add(request.valor()));
                contaRepository.save(contaDestino);
            }
            case SAQUE -> {
                if (request.contaOrigemId() == null)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contaOrigemId é obrigatório para SAQUE.");
                contaOrigem = findContaDoUsuario(request.contaOrigemId(), usuario.getId());
                // RN-03 — Validar saldo
                if (contaOrigem.getSaldo().compareTo(request.valor()) < 0) {
                    throw new SaldoInsuficienteException("Saldo insuficiente para realizar o saque.");
                }
                contaOrigem.setSaldo(contaOrigem.getSaldo().subtract(request.valor()));
                contaRepository.save(contaOrigem);

                // RN-17.1 — Aporte em Cofrinho (SAQUE/PIX com meta_destino_id)
                if (request.metaDestinoId() != null) {
                    metaDestino = findMetaDoUsuario(request.metaDestinoId(), usuario.getId());
                    metaDestino.setValorAcumulado(metaDestino.getValorAcumulado().add(request.valor()));
                    metasRepository.save(metaDestino);
                }
            }
            case PIX -> {
                if (request.contaOrigemId() == null)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contaOrigemId é obrigatório para PIX.");
                contaOrigem = findContaDoUsuario(request.contaOrigemId(), usuario.getId());
                // RN-05
                if (contaOrigem.getSaldo().compareTo(request.valor()) < 0) {
                    throw new SaldoInsuficienteException("Saldo insuficiente para realizar o PIX.");
                }
                contaOrigem.setSaldo(contaOrigem.getSaldo().subtract(request.valor()));
                contaRepository.save(contaOrigem);

                if (request.metaDestinoId() != null) {
                    metaDestino = findMetaDoUsuario(request.metaDestinoId(), usuario.getId());
                    metaDestino.setValorAcumulado(metaDestino.getValorAcumulado().add(request.valor()));
                    metasRepository.save(metaDestino);
                }
            }
            case COMPRA_CREDITO -> {
                if (request.cartaoId() == null)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cartaoId é obrigatório para COMPRA_CREDITO.");
                cartao = findCartaoDoUsuario(request.cartaoId(), usuario.getId());
                contaOrigem = cartao.getConta();

                // RN-07.1 — Validação de Limite
                BigDecimal valorTotalCompra = request.valor();
                if (cartao.getLimiteDisponivel().compareTo(valorTotalCompra) < 0) {
                    throw new LimiteInsuficienteException("Limite de crédito insuficiente.");
                }
                cartao.setLimiteDisponivel(cartao.getLimiteDisponivel().subtract(valorTotalCompra));
                cartaoRepository.save(cartao);

                // Determinar o mês de referência inicial com base na data da compra
                LocalDate mesReferenciaInicial = calcularMesReferenciaFatura(cartao, request.data());

                int totalParcelas = request.totalParcelas() != null ? request.totalParcelas() : 1;
                BigDecimal valorParcela = valorTotalCompra.divide(BigDecimal.valueOf(totalParcelas), 2, RoundingMode.HALF_EVEN);
                BigDecimal ajusteCentavos = valorTotalCompra.subtract(valorParcela.multiply(BigDecimal.valueOf(totalParcelas)));
                Transacao primeiraTransacao = null;

                for (int i = 1; i <= totalParcelas; i++) {
                    BigDecimal valorAtual = valorParcela;
                    // Ajuste de centavos na primeira parcela
                    if (i == 1) {
                        valorAtual = valorAtual.add(ajusteCentavos);
                    }

                    // Determina a fatura correspondente a cada parcela
                    LocalDate mesReferenciaParcela = mesReferenciaInicial.plusMonths(i - 1);
                    Fatura faturaAlvo = getOrCreateFatura(cartao, usuario, mesReferenciaParcela);

                    Transacao t = new Transacao();
                    t.setUsuario(usuario);
                    t.setDescricao(descricao);
                    t.setValor(valorAtual);
                    t.setTipo(TipoTransacao.COMPRA_CREDITO);
                    t.setContaOrigem(contaOrigem);
                    t.setCartao(cartao);
                    t.setFatura(faturaAlvo);
                    t.setCategoria(categoria);
                    t.setData(request.data());
                    t.setNumeroParcela(i);
                    t.setTotalParcelas(totalParcelas);
                    t.setAtivo(true);
                    t.setEstornada(false);

                    Transacao saved = transacaoRepository.save(t);
                    if (i == 1) primeiraTransacao = saved;

                    // Soma o valor à fatura
                    faturaAlvo.setValorTotal(faturaAlvo.getValorTotal().add(valorAtual));
                    if (faturaAlvo.getStatus() == StatusFatura.PAGA) {
                        faturaAlvo.setValorPago(faturaAlvo.getValorTotal());
                        // Liberar limite do cartão imediatamente se for uma fatura histórica já paga
                        cartao.setLimiteDisponivel(cartao.getLimiteDisponivel().add(valorAtual));
                    }
                    faturaRepository.save(faturaAlvo);
                }
                cartaoRepository.save(cartao);

                TransacaoResponse response = new TransacaoResponse(primeiraTransacao);
                try {
                    iaService.analisarNovaTransacao(primeiraTransacao);
                } catch (Exception ignored) {}
                TransacaoResponse.AlertaOrcamento alerta = calcularAlertaOrcamento(
                        usuario.getId(), categoria.getId(), request.valor(), tipo);
                return alerta != null ? response.withAlerta(alerta) : response;
            }
        }

        // Criação da transação para DEPOSITO, SAQUE, PIX
        Transacao transacao = new Transacao();
        transacao.setUsuario(usuario);
        transacao.setDescricao(descricao);
        transacao.setValor(request.valor());
        transacao.setTipo(tipo);
        transacao.setContaOrigem(contaOrigem);
        transacao.setContaDestino(contaDestino);
        transacao.setCartao(cartao);
        transacao.setFatura(fatura);
        transacao.setMetaOrigem(metaOrigem);
        transacao.setMetaDestino(metaDestino);
        transacao.setCategoria(categoria);
        transacao.setData(request.data());

        int totalParcelas = request.totalParcelas() != null ? request.totalParcelas() : 1;
        if (totalParcelas > 1) {
            transacao.setNumeroParcela(1);
            transacao.setTotalParcelas(totalParcelas);
        }
        transacao.setAtivo(true);
        transacao.setEstornada(false);

        Transacao saved = transacaoRepository.save(transacao);
        try {
            iaService.analisarNovaTransacao(saved);
        } catch (Exception ignored) {}
        TransacaoResponse response = new TransacaoResponse(saved);

        // RN-13 — Alerta de Orçamento
        TransacaoResponse.AlertaOrcamento alerta = calcularAlertaOrcamento(
                usuario.getId(), categoria != null ? categoria.getId() : null, request.valor(), tipo);

        return alerta != null ? response.withAlerta(alerta) : response;
    }

    // ── Transferência (POST /api/transacoes/transferir) ─────────

    @Transactional
    public TransacaoResponse transferir(TransferenciaRequest request) {
        Usuario usuario = getAuthenticatedUsuario();

        Conta origem = findContaDoUsuario(request.contaOrigemId(), usuario.getId());
        Conta destino = findContaDoUsuario(request.contaDestinoId(), usuario.getId());

        // RN-04 — Validar saldo
        if (origem.getSaldo().compareTo(request.valor()) < 0) {
            throw new SaldoInsuficienteException("Saldo insuficiente para realizar a transferência.");
        }

        // RN-04 — Mesmo usuário (já garantido pelo findContaDoUsuario)

        origem.setSaldo(origem.getSaldo().subtract(request.valor()));
        destino.setSaldo(destino.getSaldo().add(request.valor()));
        contaRepository.save(origem);
        contaRepository.save(destino);

        Categoria categoria = null;
        if (request.categoriaId() != null) {
            categoria = findCategoriaDoUsuario(request.categoriaId(), usuario.getId());
        }

        Transacao transacao = new Transacao();
        transacao.setUsuario(usuario);
        transacao.setDescricao(request.descricao() != null ? request.descricao() : "Transferência entre contas");
        transacao.setValor(request.valor());
        transacao.setTipo(TipoTransacao.TRANSFERENCIA);
        transacao.setContaOrigem(origem);
        transacao.setContaDestino(destino);
        transacao.setCategoria(categoria);
        transacao.setData(LocalDate.now());
        transacao.setAtivo(true);
        transacao.setEstornada(false);

        Transacao saved = transacaoRepository.save(transacao);
        return new TransacaoResponse(saved);
    }

    // ── Pagamento de Fatura (POST /api/transacoes/pagar-fatura) ─

    @Transactional
    public TransacaoResponse pagarFatura(PagamentoFaturaRequest request) {
        Usuario usuario = getAuthenticatedUsuario();

        Fatura fatura = findFaturaDoUsuario(request.faturaId(), usuario.getId());
        Conta conta = findContaDoUsuario(request.contaOrigemId(), usuario.getId());
        BigDecimal valor = request.valor();

        // RN-01 — Verificar se o cartão da fatura pertence ao usuário
        Cartao cartao = fatura.getCartao();
        if (!cartao.getUsuario().getId().equals(usuario.getId())) {
            throw new FaturaNaoEncontradaException();
        }

        TipoPagamentoFatura tipoPagamento;
        try {
            tipoPagamento = TipoPagamentoFatura.valueOf(request.tipoPagamento());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de pagamento inválido.");
        }

        switch (tipoPagamento) {
            case TOTAL -> {
                // RN-08.1 — Pagamento Total: fatura deve estar FECHADA ou ATRASADA
                if (fatura.getStatus() != StatusFatura.FECHADA && fatura.getStatus() != StatusFatura.ATRASADA) {
                    throw new PagamentoFaturaInvalidoException("Pagamento total só pode ser realizado em faturas fechadas ou atrasadas.");
                }

                // Se a fatura estiver ATRASADA e já foi feito o rollover, não é permitido pagar diretamente
                if (fatura.getStatus() == StatusFatura.ATRASADA && fatura.isRolladoOver()) {
                    throw new PagamentoFaturaInvalidoException("Esta fatura foi transferida para a próxima. Pague a fatura do mês seguinte.");
                }

                BigDecimal valorReal = fatura.getValorTotal().subtract(fatura.getValorPago());
                if (valorReal.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new PagamentoFaturaInvalidoException("Esta fatura já foi totalmente paga.");
                }
                if (conta.getSaldo().compareTo(valorReal) < 0) {
                    throw new SaldoInsuficienteException("Saldo insuficiente para pagar a fatura.");
                }

                conta.setSaldo(conta.getSaldo().subtract(valorReal));
                contaRepository.save(conta);

                // Restabelece limite do cartão: devolve o valor real sendo pago
                cartao.setLimiteDisponivel(cartao.getLimiteDisponivel().add(valorReal));
                cartaoRepository.save(cartao);

                fatura.setValorPago(fatura.getValorTotal());
                fatura.setStatus(StatusFatura.PAGA);
                faturaRepository.save(fatura);

                // Usa o valor real pago para criar a transação
                valor = valorReal;
            }
            case PARCIAL -> {
                // RN-08.3 — Pagamento Parcial
                if (fatura.getStatus() != StatusFatura.FECHADA && fatura.getStatus() != StatusFatura.ATRASADA) {
                    throw new PagamentoFaturaInvalidoException("Pagamento parcial só pode ser realizado em faturas fechadas ou atrasadas.");
                }

                // Se a fatura estiver ATRASADA e já foi feito o rollover, não é permitido pagar diretamente
                if (fatura.getStatus() == StatusFatura.ATRASADA && fatura.isRolladoOver()) {
                    throw new PagamentoFaturaInvalidoException("Esta fatura foi transferida para a próxima. Pague a fatura do mês seguinte.");
                }

                BigDecimal restanteReal = fatura.getValorTotal().subtract(fatura.getValorPago());
                if (valor.compareTo(restanteReal) > 0) {
                    throw new PagamentoFaturaInvalidoException("Valor do pagamento excede o saldo devedor da fatura.");
                }
                if (conta.getSaldo().compareTo(valor) < 0) {
                    throw new SaldoInsuficienteException("Saldo insuficiente.");
                }

                conta.setSaldo(conta.getSaldo().subtract(valor));
                contaRepository.save(conta);

                // Libera limite proporcional ao valor pago
                cartao.setLimiteDisponivel(cartao.getLimiteDisponivel().add(valor));
                cartaoRepository.save(cartao);

                fatura.setValorPago(fatura.getValorPago().add(valor));
                if (fatura.getValorPago().compareTo(fatura.getValorTotal()) >= 0) {
                    fatura.setStatus(StatusFatura.PAGA);
                } else {
                    fatura.setStatus(StatusFatura.PAGA_PARCIAL);
                }
                faturaRepository.save(fatura);
            }
            case ANTECIPADO -> {
                // User feedback: Pagamento antecipado mantém fatura ABERTA
                // Apenas aumenta limite disponível e diminui o valor da fatura
                if (fatura.getStatus() != StatusFatura.ABERTA) {
                    throw new PagamentoFaturaInvalidoException("Pagamento antecipado só pode ser realizado em faturas abertas.");
                }
                if (conta.getSaldo().compareTo(valor) < 0) {
                    throw new SaldoInsuficienteException("Saldo insuficiente.");
                }

                conta.setSaldo(conta.getSaldo().subtract(valor));
                contaRepository.save(conta);

                // Libera limite disponível imediatamente
                cartao.setLimiteDisponivel(cartao.getLimiteDisponivel().add(valor));
                cartaoRepository.save(cartao);

                // Diminui o valor total da fatura (não altera status - continua ABERTA)
                fatura.setValorTotal(fatura.getValorTotal().subtract(valor));
                if (fatura.getValorTotal().compareTo(BigDecimal.ZERO) < 0) {
                    fatura.setValorTotal(BigDecimal.ZERO);
                }
                faturaRepository.save(fatura);
            }
        }

        // Criar transação de pagamento
        Transacao transacao = new Transacao();
        transacao.setUsuario(usuario);
        
        String prefixo = switch (tipoPagamento) {
            case TOTAL -> "Pagamento Total de Fatura - ";
            case PARCIAL -> "Pagamento Parcial de Fatura - ";
            case ANTECIPADO -> "Pagamento Antecipado Fatura - ";
        };
        transacao.setDescricao(prefixo + fatura.getMesReferencia());
        transacao.setValor(valor);
        transacao.setTipo(TipoTransacao.PAGAMENTO_CREDITO);
        transacao.setContaOrigem(conta);
        transacao.setCartao(cartao);
        transacao.setFatura(fatura);
        transacao.setTipoPagamentoFatura(tipoPagamento);
        transacao.setData(LocalDate.now());
        transacao.setAtivo(true);

        return new TransacaoResponse(transacaoRepository.save(transacao));
    }
    // ── Estorno (POST /api/transacoes/{id}/estornar) ────────────

    @Transactional
    public TransacaoResponse estornar(UUID transacaoId, EstornoRequest request) {
        Usuario usuario = getAuthenticatedUsuario();
        Transacao transacao = transacaoRepository.findByIdAndUsuarioIdAndAtivoTrue(transacaoId, usuario.getId())
                .orElseThrow(TransacaoNaoEncontradaException::new);

        if (transacao.getEstornada()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Transação já estornada.");
        }

        BigDecimal valorEstorno = request.valor() != null ? request.valor() : transacao.getValor();

        switch (transacao.getTipo()) {
            case DEPOSITO -> {
                Conta conta = transacao.getContaDestino();
                conta.setSaldo(conta.getSaldo().subtract(valorEstorno));
                contaRepository.save(conta);
            }
            case SAQUE, PIX -> {
                if (transacao.getMetaDestino() != null) {
                    // Reverter aporte do cofrinho
                    MetasEconomia meta = transacao.getMetaDestino();
                    meta.setValorAcumulado(meta.getValorAcumulado().subtract(valorEstorno));
                    if (meta.getValorAcumulado().compareTo(BigDecimal.ZERO) < 0)
                        meta.setValorAcumulado(BigDecimal.ZERO);
                    metasRepository.save(meta);
                }
                Conta conta = transacao.getContaOrigem();
                conta.setSaldo(conta.getSaldo().add(valorEstorno));
                contaRepository.save(conta);
            }
            case TRANSFERENCIA -> {
                Conta origem = transacao.getContaOrigem();
                Conta destino = transacao.getContaDestino();
                origem.setSaldo(origem.getSaldo().add(valorEstorno));
                destino.setSaldo(destino.getSaldo().subtract(valorEstorno));
                contaRepository.save(origem);
                contaRepository.save(destino);
            }
            case COMPRA_CREDITO -> {
                // RN-14 — Estorno de Cartão
                Cartao cartao = transacao.getCartao();
                if (cartao == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cartão não associado.");

                if (request.valor() == null || request.valor().compareTo(transacao.getValor()) >= 0) {
                    // Estorno total
                    cartao.setLimiteDisponivel(cartao.getLimiteDisponivel().add(transacao.getValor()));
                    cartaoRepository.save(cartao);

                    Fatura fatura = transacao.getFatura();
                    if (fatura != null) {
                        if (fatura.getStatus() == StatusFatura.ABERTA) {
                            fatura.setValorTotal(fatura.getValorTotal().subtract(transacao.getValor()));
                            if (fatura.getValorTotal().compareTo(BigDecimal.ZERO) < 0)
                                fatura.setValorTotal(BigDecimal.ZERO);
                            faturaRepository.save(fatura);
                        } else {
                            // Fatura já fechada/paga: gerar lançamento de crédito na fatura aberta atual
                            Fatura faturaAtual = getOrCreateFaturaAberta(cartao, usuario, LocalDate.now());
                            // Registrar crédito na fatura atual
                            faturaAtual.setValorTotal(faturaAtual.getValorTotal().subtract(transacao.getValor()));
                            if (faturaAtual.getValorTotal().compareTo(BigDecimal.ZERO) < 0)
                                faturaAtual.setValorTotal(BigDecimal.ZERO);
                            faturaRepository.save(faturaAtual);

                            Transacao credito = new Transacao();
                            credito.setUsuario(usuario);
                            credito.setDescricao("Estorno: " + transacao.getDescricao());
                            credito.setValor(transacao.getValor());
                            credito.setTipo(TipoTransacao.COMPRA_CREDITO);
                            credito.setCartao(cartao);
                            credito.setFatura(faturaAtual);
                            credito.setCategoria(transacao.getCategoria());
                            credito.setData(LocalDate.now());
                            credito.setAtivo(true);
                            transacaoRepository.save(credito);
                        }
                    }
                } else {
                    // Estorno parcial (RN-14.2)
                    cartao.setLimiteDisponivel(cartao.getLimiteDisponivel().add(valorEstorno));
                    cartaoRepository.save(cartao);

                    Fatura fatura = transacao.getFatura();
                    if (fatura != null && fatura.getStatus() == StatusFatura.ABERTA) {
                        fatura.setValorTotal(fatura.getValorTotal().subtract(valorEstorno));
                        if (fatura.getValorTotal().compareTo(BigDecimal.ZERO) < 0)
                            fatura.setValorTotal(BigDecimal.ZERO);
                        faturaRepository.save(fatura);
                    } else if (fatura != null) {
                        // Lançamento de crédito na fatura aberta atual
                        Fatura faturaAtual = getOrCreateFaturaAberta(cartao, usuario, LocalDate.now());
                        faturaAtual.setValorTotal(faturaAtual.getValorTotal().subtract(valorEstorno));
                        if (faturaAtual.getValorTotal().compareTo(BigDecimal.ZERO) < 0)
                            faturaAtual.setValorTotal(BigDecimal.ZERO);
                        faturaRepository.save(faturaAtual);
                    }
                }

                // Se for parcelada, inativar parcelas futuras
                if (transacao.getTotalParcelas() != null && transacao.getTotalParcelas() > 1) {
                    List<Transacao> parcelas = transacaoRepository.findByFaturaIdAndAtivoTrue(transacao.getFatura().getId());
                    for (Transacao p : parcelas) {
                        if (!p.getId().equals(transacaoId) && p.getAtivo()) {
                            p.setAtivo(false);
                            transacaoRepository.save(p);
                        }
                    }
                }
            }
        }

        transacao.setEstornada(true);
        return new TransacaoResponse(transacaoRepository.save(transacao));
    }

    // ── Listar (GET /api/transacoes) ────────────────────────────

    @Transactional(readOnly = true)
    public List<TransacaoResponse> listar() {
        Usuario usuario = getAuthenticatedUsuario();
        return transacaoRepository.findByUsuarioIdAndAtivoTrueOrderByDataDesc(usuario.getId())
                .stream().map(TransacaoResponse::new).toList();
    }

    @Transactional(readOnly = true)
    public Page<TransacaoResponse> listarPaginado(
            TipoTransacao tipo, String descricao, LocalDate dataInicio, LocalDate dataFim, Pageable pageable) {
        Usuario usuario = getAuthenticatedUsuario();
        List<TipoTransacao> tipos = (tipo != null) ? List.of(tipo) : Arrays.asList(TipoTransacao.values());
        String descPattern = (descricao != null && !descricao.isBlank()) ? "%" + descricao.trim() + "%" : "%";
        LocalDate inicio = (dataInicio != null) ? dataInicio : LocalDate.of(1970, 1, 1);
        LocalDate fim = (dataFim != null) ? dataFim : LocalDate.of(2100, 12, 31);
        return transacaoRepository.findFiltered(usuario.getId(), tipos, descPattern, inicio, fim, pageable)
                .map(TransacaoResponse::new);
    }

    // ── Soft Delete (DELETE /api/transacoes/{id}) ───────────────

    @Transactional
    public void excluir(UUID id) {
        Usuario usuario = getAuthenticatedUsuario();
        Transacao transacao = transacaoRepository.findByIdAndUsuarioIdAndAtivoTrue(id, usuario.getId())
                .orElseThrow(TransacaoNaoEncontradaException::new);

        // RN-10 — Reverter impacto no saldo/limite
        reverterImpacto(transacao, usuario);

        transacao.setAtivo(false);
        transacao.setEstornada(true);
        transacaoRepository.save(transacao);
    }

    private void reverterImpacto(Transacao t, Usuario usuario) {
        switch (t.getTipo()) {
            case DEPOSITO -> {
                Conta c = t.getContaDestino();
                c.setSaldo(c.getSaldo().subtract(t.getValor()));
                contaRepository.save(c);
            }
            case SAQUE, PIX -> {
                if (t.getMetaDestino() != null) {
                    MetasEconomia meta = t.getMetaDestino();
                    meta.setValorAcumulado(meta.getValorAcumulado().subtract(t.getValor()));
                    if (meta.getValorAcumulado().compareTo(BigDecimal.ZERO) < 0)
                        meta.setValorAcumulado(BigDecimal.ZERO);
                    metasRepository.save(meta);
                }
                Conta c = t.getContaOrigem();
                c.setSaldo(c.getSaldo().add(t.getValor()));
                contaRepository.save(c);
            }
            case TRANSFERENCIA -> {
                Conta origem = t.getContaOrigem();
                Conta destino = t.getContaDestino();
                origem.setSaldo(origem.getSaldo().add(t.getValor()));
                destino.setSaldo(destino.getSaldo().subtract(t.getValor()));
                contaRepository.save(origem);
                contaRepository.save(destino);
            }
            case COMPRA_CREDITO -> {
                Cartao cartao = t.getCartao();
                if (cartao != null) {
                    cartao.setLimiteDisponivel(cartao.getLimiteDisponivel().add(t.getValor()));
                    cartaoRepository.save(cartao);
                }
                Fatura fatura = t.getFatura();
                if (fatura != null) {
                    fatura.setValorTotal(fatura.getValorTotal().subtract(t.getValor()));
                    if (fatura.getValorTotal().compareTo(BigDecimal.ZERO) < 0)
                        fatura.setValorTotal(BigDecimal.ZERO);
                    faturaRepository.save(fatura);
                }
            }
        }
    }

    // ── Projeção de Fluxo de Caixa (RN-16) ─────────────────────

    @Transactional(readOnly = true)
    public List<ProjecaoResponse> projetar(int dias) {
        Usuario usuario = getAuthenticatedUsuario();

        LocalDate hoje = LocalDate.now();
        LocalDate dataLimite = hoje.plusDays(dias);

        // Saldo atual consolidado
        List<Conta> contas = contaRepository.findByUsuarioIdAndAtivoTrue(usuario.getId());
        BigDecimal saldoAtual = contas.stream()
                .map(Conta::getSaldo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Transações agendadas futuras
        BigDecimal receitas = BigDecimal.ZERO;
        BigDecimal despesas = BigDecimal.ZERO;

        List<Transacao> transacoesFuturas = transacaoRepository
                .findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(usuario.getId(), hoje, dataLimite);
        for (Transacao t : transacoesFuturas) {
            switch (t.getTipo()) {
                case DEPOSITO -> receitas = receitas.add(t.getValor());
                case SAQUE, PIX -> despesas = despesas.add(t.getValor());
            }
        }

        // Agendamentos
        List<AgendamentoTransacao> agendamentos = agendamentoRepository
                .findByAtivoTrueAndDataProximaExecucaoLessThanEqual(dataLimite);
        for (AgendamentoTransacao a : agendamentos) {
            LocalDate prox = a.getDataProximaExecucao();
            while (!prox.isAfter(dataLimite)) {
                switch (a.getTipo()) {
                    case DEPOSITO -> receitas = receitas.add(a.getValor());
                    case SAQUE, PIX -> despesas = despesas.add(a.getValor());
                }
                prox = switch (a.getRecorrencia()) {
                    case SEMANAL -> prox.plusWeeks(1);
                    case QUINZENAL -> prox.plusDays(15);
                    case MENSAL -> prox.plusMonths(1);
                };
            }
        }

        // Faturas a vencer
        BigDecimal faturasPendentes = BigDecimal.ZERO;
        List<Fatura> faturas = faturaRepository.findByUsuarioIdAndStatus(usuario.getId(), StatusFatura.FECHADA);
        for (Fatura f : faturas) {
            if (!f.getDataVencimento().isAfter(dataLimite) && !f.getDataVencimento().isBefore(hoje)) {
                BigDecimal restante = f.getValorTotal().subtract(f.getValorPago());
                if (restante.compareTo(BigDecimal.ZERO) > 0) {
                    faturasPendentes = faturasPendentes.add(restante);
                }
            }
        }

        BigDecimal saldoProjetado = saldoAtual.add(receitas).subtract(despesas).subtract(faturasPendentes);

        return List.of(
                new ProjecaoResponse(hoje, saldoAtual),
                new ProjecaoResponse(dataLimite, saldoProjetado)
        );
    }

    @Transactional(readOnly = true)
    public List<TransacaoResponse> buscarPorFatura(UUID faturaId) {
        Usuario usuario = getAuthenticatedUsuario();
        Fatura fatura = findFaturaDoUsuario(faturaId, usuario.getId());

        return transacaoRepository.findByFaturaIdAndAtivoTrue(fatura.getId())
                .stream()
                .map(TransacaoResponse::new)
                .toList();
    }
}