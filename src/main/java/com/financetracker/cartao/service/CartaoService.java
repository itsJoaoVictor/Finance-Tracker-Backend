package com.financetracker.cartao.service;

import com.financetracker.cartao.dto.*;
import com.financetracker.cartao.entity.Cartao;
import com.financetracker.cartao.exception.*;
import com.financetracker.cartao.repository.CartaoRepository;
import com.financetracker.conta.entity.Conta;
import com.financetracker.conta.exception.ContaNaoEncontradaException;
import com.financetracker.conta.repository.ContaRepository;
import com.financetracker.transacao.dto.FaturaResponse;
import com.financetracker.assinatura.entity.Assinatura;
import com.financetracker.assinatura.repository.AssinaturaRepository;
import com.financetracker.ia.repository.IaClassificacaoAssinaturaRepository;
import com.financetracker.transacao.entity.Fatura;
import com.financetracker.transacao.entity.Transacao;
import com.financetracker.transacao.enums.StatusFatura;
import com.financetracker.transacao.enums.TipoTransacao;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CartaoService {

    private final CartaoRepository cartaoRepository;
    private final ContaRepository contaRepository;
    private final UsuarioRepository usuarioRepository;
    private final FaturaRepository faturaRepository;
    private final TransacaoRepository transacaoRepository;
    private final AssinaturaRepository assinaturaRepository;
    private final com.financetracker.ia.repository.IaClassificacaoAssinaturaRepository iaClassificacaoAssinaturaRepository;

    public CartaoService(CartaoRepository cartaoRepository,
                         ContaRepository contaRepository,
                         UsuarioRepository usuarioRepository,
                         FaturaRepository faturaRepository,
                         TransacaoRepository transacaoRepository,
                         AssinaturaRepository assinaturaRepository,
                         com.financetracker.ia.repository.IaClassificacaoAssinaturaRepository iaClassificacaoAssinaturaRepository) {
        this.cartaoRepository = cartaoRepository;
        this.contaRepository = contaRepository;
        this.usuarioRepository = usuarioRepository;
        this.faturaRepository = faturaRepository;
        this.transacaoRepository = transacaoRepository;
        this.assinaturaRepository = assinaturaRepository;
        this.iaClassificacaoAssinaturaRepository = iaClassificacaoAssinaturaRepository;
    }

    private Usuario getAuthenticatedUsuario() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autenticado"));
    }

    private Conta findContaDoUsuario(UUID contaId, UUID usuarioId) {
        return contaRepository.findByIdAndUsuarioIdAndAtivoTrue(contaId, usuarioId)
                .orElseThrow(ContaNaoEncontradaException::new);
    }

    private Cartao findCartaoDoUsuario(UUID cartaoId, UUID usuarioId) {
        return cartaoRepository.findByIdAndUsuarioIdAndAtivoTrue(cartaoId, usuarioId)
                .orElseThrow(CartaoNaoEncontradoException::new);
    }

    @Transactional
    public CartaoResponse criar(CartaoCriacaoRequest request) {
        Usuario usuario = getAuthenticatedUsuario();

        // RN-05 — limite de 10 cartões
        long totalAtivos = cartaoRepository.countByUsuarioIdAndAtivoTrue(usuario.getId());
        if (totalAtivos >= 10) {
            throw new LimiteCartoesException("Limite máximo de 10 cartões atingido.");
        }

        // RN-01 — conta associada deve pertencer ao usuário logado
        Conta conta = findContaDoUsuario(request.contaId(), usuario.getId());

        // RN-06 — limite não negativo (validado também por Bean Validation, mas double check)
        if (request.limite().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O limite não pode ser negativo.");
        }

        Cartao cartao = new Cartao();
        cartao.setUsuario(usuario);
        cartao.setNome(request.nome());
        cartao.setLimite(request.limite());
        // RN-02 — Inicialização do limite disponível
        cartao.setLimiteDisponivel(request.limite());
        cartao.setDiaFechamento(request.diaFechamento());
        cartao.setDiaVencimento(request.diaVencimento());
        cartao.setConta(conta);
        cartao.setCorHexadecimal(request.corHexadecimal());
        cartao.setAtivo(true);

        return new CartaoResponse(cartaoRepository.save(cartao));
    }

    @Transactional
    public List<CartaoResponse> listar() {
        Usuario usuario = getAuthenticatedUsuario();
        atualizarStatusERolloverFaturas(usuario);
        List<Cartao> cartoes = cartaoRepository.findByUsuarioIdAndAtivoTrue(usuario.getId());
        LocalDate hoje = LocalDate.now();

        return cartoes.stream().map(c -> {
            LocalDate mesReferenciaAtual = calcularMesReferenciaFatura(c, hoje);
            List<Fatura> faturas = faturaRepository.findByCartaoIdAndUsuarioIdOrderByMesReferenciaDesc(c.getId(), usuario.getId());
            BigDecimal faturaCartao = BigDecimal.ZERO;
            String status = "ABERTA";

            LocalDate faturaRef = null;
            if (faturas.isEmpty()) {
                faturaCartao = c.getLimite().subtract(c.getLimiteDisponivel());
            } else {
                BigDecimal fechadasPendentes = BigDecimal.ZERO;
                boolean temFechadaOuAtrasada = false;
                for (Fatura f : faturas) {
                    // Faturas ATRASADAS com rolladoOver=true já tiveram seu saldo transferido
                    // para a próxima fatura - não somar aqui para evitar dupla contagem
                    if (f.getStatus() == StatusFatura.ATRASADA && f.isRolladoOver()) continue;
                    if ((f.getStatus() == StatusFatura.FECHADA || f.getStatus() == StatusFatura.ATRASADA || f.getStatus() == StatusFatura.PAGA_PARCIAL)
                            && f.getValorTotal().compareTo(f.getValorPago()) > 0) {
                        fechadasPendentes = fechadasPendentes.add(f.getValorTotal());
                        temFechadaOuAtrasada = true;
                        if (faturaRef == null) {
                            faturaRef = f.getMesReferencia();
                        }
                    }
                }

                if (temFechadaOuAtrasada) {
                    faturaCartao = fechadasPendentes;
                    status = "FECHADA";
                } else {
                // Encontra a fatura aberta mais antiga (para servir como a fatura atual exibida no card)
                Fatura fAtual = faturas.stream()
                        .filter(f -> f.getStatus() == StatusFatura.ABERTA)
                        .min((a, b) -> a.getMesReferencia().compareTo(b.getMesReferencia()))
                        .orElse(null);

                if (fAtual != null) {
                    BigDecimal restante = fAtual.getValorTotal();
                    faturaCartao = restante.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : restante;
                    status = "ABERTA";
                    faturaRef = fAtual.getMesReferencia();
                } else {
                    // Se não houver faturas abertas com saldo, usa a fatura do mês atual de referência mesmo que zerada
                    for (Fatura f : faturas) {
                        if (f.getMesReferencia().equals(mesReferenciaAtual)) {
                            BigDecimal restante = f.getValorTotal();
                            faturaCartao = restante.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : restante;
                            faturaRef = f.getMesReferencia();
                            break;
                        }
                    }
                    status = "ABERTA";
                }
                }
            }
            // Fallback: sempre exibir o mês de referência atual quando não há fatura específica
            if (faturaRef == null) {
                faturaRef = mesReferenciaAtual;
            }

            return new CartaoResponse(c, faturaCartao, status, faturaRef.toString());
        }).toList();
    }

    @Transactional
    public CartaoResponse buscarPorId(UUID cartaoId) {
        Usuario usuario = getAuthenticatedUsuario();
        atualizarStatusERolloverFaturas(usuario);
        Cartao cartao = findCartaoDoUsuario(cartaoId, usuario.getId());
        
        LocalDate hoje = LocalDate.now();
        LocalDate mesReferenciaAtual = calcularMesReferenciaFatura(cartao, hoje);
        List<Fatura> faturas = faturaRepository.findByCartaoIdAndUsuarioIdOrderByMesReferenciaDesc(cartao.getId(), usuario.getId());
        BigDecimal faturaCartao = BigDecimal.ZERO;
        String status = "ABERTA";
        LocalDate faturaRef = null;

        if (faturas.isEmpty()) {
            faturaCartao = cartao.getLimite().subtract(cartao.getLimiteDisponivel());
        } else {
            BigDecimal fechadasPendentes = BigDecimal.ZERO;
            boolean temFechadaOuAtrasada = false;
            for (Fatura f : faturas) {
                // Faturas ATRASADAS com rolladoOver=true já tiveram seu saldo transferido
                // para a próxima fatura - não somar aqui para evitar dupla contagem
                if (f.getStatus() == StatusFatura.ATRASADA && f.isRolladoOver()) continue;
                if ((f.getStatus() == StatusFatura.FECHADA || f.getStatus() == StatusFatura.ATRASADA || f.getStatus() == StatusFatura.PAGA_PARCIAL)
                        && f.getValorTotal().compareTo(f.getValorPago()) > 0) {
                    fechadasPendentes = fechadasPendentes.add(f.getValorTotal());
                    temFechadaOuAtrasada = true;
                    if (faturaRef == null) {
                        faturaRef = f.getMesReferencia();
                    }
                }
            }

            if (temFechadaOuAtrasada) {
                faturaCartao = fechadasPendentes;
                status = "FECHADA";
            } else {
                // Encontra a fatura aberta mais antiga (para servir como a fatura atual exibida no card)
                Fatura fAtual = faturas.stream()
                        .filter(f -> f.getStatus() == StatusFatura.ABERTA)
                        .min((a, b) -> a.getMesReferencia().compareTo(b.getMesReferencia()))
                        .orElse(null);

                if (fAtual != null) {
                    faturaCartao = fAtual.getValorTotal();
                    status = "ABERTA";
                    faturaRef = fAtual.getMesReferencia();
                } else {
                    // Se não houver faturas abertas com saldo, usa a fatura do mês atual de referência mesmo que zerada
                    for (Fatura f : faturas) {
                        if (f.getMesReferencia().equals(mesReferenciaAtual)) {
                            faturaCartao = f.getValorTotal();
                            if (faturaCartao.compareTo(BigDecimal.ZERO) < 0) {
                                faturaCartao = BigDecimal.ZERO;
                            }
                            faturaRef = f.getMesReferencia();
                            break;
                        }
                    }
                    status = "ABERTA";
                }
            }
        }
        // Fallback: sempre exibir o mês de referência atual quando não há fatura específica
        if (faturaRef == null) {
            faturaRef = mesReferenciaAtual;
        }
        return new CartaoResponse(cartao, faturaCartao, status, faturaRef.toString());
    }

    @Transactional
    public CartaoResponse editar(UUID cartaoId, CartaoEdicaoRequest request) {
        Usuario usuario = getAuthenticatedUsuario();
        Cartao cartao = findCartaoDoUsuario(cartaoId, usuario.getId());

        // RN-01 — conta associada deve pertencer ao usuário logado
        Conta conta = findContaDoUsuario(request.contaId(), usuario.getId());

        // RN-06 — limite não negativo
        if (request.limite().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O limite não pode ser negativo.");
        }

        // RN-03 — Ajuste Proporcional do Limite Disponível
        BigDecimal limiteAntigo = cartao.getLimite();
        BigDecimal novoLimite = request.limite();
        BigDecimal diferenca = novoLimite.subtract(limiteAntigo);
        BigDecimal novoLimiteDisponivel = cartao.getLimiteDisponivel().add(diferenca);

        if (novoLimiteDisponivel.compareTo(BigDecimal.ZERO) < 0) {
            throw new LimiteDisponivelInvalidoException("O novo limite não pode ser menor do que o limite já utilizado.");
        }

        cartao.setNome(request.nome());
        cartao.setLimite(novoLimite);
        cartao.setLimiteDisponivel(novoLimiteDisponivel);
        cartao.setDiaFechamento(request.diaFechamento());
        cartao.setDiaVencimento(request.diaVencimento());
        cartao.setConta(conta);
        cartao.setCorHexadecimal(request.corHexadecimal());

        return new CartaoResponse(cartaoRepository.save(cartao));
    }

    @Transactional
    public void excluir(UUID cartaoId) {
        Usuario usuario = getAuthenticatedUsuario();
        Cartao cartao = findCartaoDoUsuario(cartaoId, usuario.getId());

        // 1. IaClassificacaoAssinatura → FK assinatura_id NOT NULL
        List<com.financetracker.assinatura.entity.Assinatura> assinaturas =
                assinaturaRepository.findByCartaoId(cartaoId);
        if (!assinaturas.isEmpty()) {
            List<UUID> assinaturaIds = assinaturas.stream()
                    .map(com.financetracker.assinatura.entity.Assinatura::getId).toList();
            iaClassificacaoAssinaturaRepository.deleteByAssinaturaIds(assinaturaIds);
        }

        // 2. Transações → FK fatura_id + cartao_id
        List<Transacao> transacoes = transacaoRepository.findByCartaoId(cartaoId);
        transacaoRepository.deleteAll(transacoes);

        // 3. Faturas → FK cartao_id
        List<Fatura> faturas = faturaRepository.findByCartaoId(cartaoId);
        faturaRepository.deleteAll(faturas);

        // 4. Assinaturas → FK cartao_id
        assinaturaRepository.deleteAll(assinaturas);

        // 5. Cartão
        cartaoRepository.delete(cartao);
    }

    private LocalDate calcularMesReferenciaFatura(Cartao cartao, LocalDate data) {
        int diaFechamento = cartao.getDiaFechamento();
        if (data.getDayOfMonth() < diaFechamento) {
            return data.withDayOfMonth(1);
        } else {
            return data.plusMonths(1).withDayOfMonth(1);
        }
    }

    @Transactional
    public CartaoResumoResponse resumo() {
        Usuario usuario = getAuthenticatedUsuario();
        atualizarStatusERolloverFaturas(usuario);
        List<Cartao> cartoes = cartaoRepository.findByUsuarioIdAndAtivoTrue(usuario.getId());

        BigDecimal totalLimite = BigDecimal.ZERO;
        BigDecimal totalLimiteDisponivel = BigDecimal.ZERO;
        BigDecimal totalFaturaEstimada = BigDecimal.ZERO;

        LocalDate hoje = LocalDate.now();

        for (Cartao c : cartoes) {
            totalLimite = totalLimite.add(c.getLimite());
            totalLimiteDisponivel = totalLimiteDisponivel.add(c.getLimiteDisponivel());

            LocalDate mesReferenciaAtual = calcularMesReferenciaFatura(c, hoje);
            List<Fatura> faturas = faturaRepository.findByCartaoIdAndUsuarioIdOrderByMesReferenciaDesc(c.getId(), usuario.getId());
            BigDecimal faturaCartao = BigDecimal.ZERO;

            if (faturas.isEmpty()) {
                faturaCartao = c.getLimite().subtract(c.getLimiteDisponivel());
            } else {
                BigDecimal fechadasPendentes = BigDecimal.ZERO;
                boolean temFechadaOuAtrasada = false;
                for (Fatura f : faturas) {
                    // Faturas ATRASADAS com rolladoOver=true já tiveram seu saldo transferido
                    // para a próxima fatura - não somar aqui para evitar dupla contagem
                    if (f.getStatus() == StatusFatura.ATRASADA && f.isRolladoOver()) continue;
                    if ((f.getStatus() == StatusFatura.FECHADA || f.getStatus() == StatusFatura.ATRASADA || f.getStatus() == StatusFatura.PAGA_PARCIAL)
                            && f.getValorTotal().compareTo(f.getValorPago()) > 0) {
                        fechadasPendentes = fechadasPendentes.add(f.getValorTotal());
                        temFechadaOuAtrasada = true;
                    }
                }

                if (temFechadaOuAtrasada) {
                    faturaCartao = fechadasPendentes;
                } else {
                    // Encontra a fatura aberta mais antiga (para servir como a fatura atual exibida no card)
                    Fatura fAtual = faturas.stream()
                            .filter(f -> f.getStatus() == StatusFatura.ABERTA)
                            .min((a, b) -> a.getMesReferencia().compareTo(b.getMesReferencia()))
                            .orElse(null);

                    if (fAtual != null) {
                        faturaCartao = fAtual.getValorTotal();
                    } else {
                        // Se não houver faturas abertas com saldo, usa a fatura do mês atual de referência mesmo que zerada
                        for (Fatura f : faturas) {
                            if (f.getMesReferencia().equals(mesReferenciaAtual)) {
                                faturaCartao = f.getValorTotal();
                                if (faturaCartao.compareTo(BigDecimal.ZERO) < 0) {
                                    faturaCartao = BigDecimal.ZERO;
                                }
                                break;
                            }
                        }
                    }
                }
            }

            totalFaturaEstimada = totalFaturaEstimada.add(faturaCartao);
        }

        return new CartaoResumoResponse(
                totalLimite,
                totalLimiteDisponivel,
                totalFaturaEstimada,
                cartoes.size()
        );
    }

    @Transactional
    public List<FaturaResponse> listarFaturas(UUID cartaoId) {
        Usuario usuario = getAuthenticatedUsuario();
        atualizarStatusERolloverFaturas(usuario);
        Cartao cartao = findCartaoDoUsuario(cartaoId, usuario.getId());

        return faturaRepository.findByCartaoIdAndUsuarioIdOrderByMesReferenciaDesc(cartao.getId(), usuario.getId())
                .stream()
                .map(FaturaResponse::new)
                .toList();
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

    @Transactional
    public void atualizarStatusERolloverFaturas(Usuario usuario) {
        LocalDate hoje = LocalDate.now();
        List<Fatura> faturas = faturaRepository.findByUsuarioId(usuario.getId());

        for (Fatura f : faturas) {
            BigDecimal restante = f.getValorTotal().subtract(f.getValorPago());

            // Se passou do vencimento
            if (hoje.isAfter(f.getDataVencimento())) {
                if (restante.compareTo(BigDecimal.ZERO) > 0) {
                    // Apenas crédito rotativo (non-parcela) gera rollover.
                    // Parcelas já estão alocadas nas faturas corretas pelo criar().
                    BigDecimal rotativo = transacaoRepository.sumRotativoByFaturaId(f.getId());

                    if (!f.isRolladoOver()) {
                        f.setStatus(StatusFatura.ATRASADA);
                        f.setRolladoOver(true);
                        faturaRepository.save(f);

                        // Rollover apenas o saldo rotativo não pago (mínimo entre rotativo e restante pra não exagerar)
                        BigDecimal valorRollover = rotativo.min(restante);
                        if (valorRollover.compareTo(BigDecimal.ZERO) > 0) {
                            LocalDate proximoMes = f.getMesReferencia().plusMonths(1);
                            Fatura proximaFatura = getOrCreateFatura(f.getCartao(), usuario, proximoMes);

                            Transacao rollover = new Transacao();
                            rollover.setUsuario(usuario);
                            rollover.setDescricao("Saldo restante não pago - Fatura " + f.getMesReferencia().getMonthValue() + "/" + f.getMesReferencia().getYear());
                            rollover.setValor(valorRollover);
                            rollover.setTipo(TipoTransacao.COMPRA_CREDITO);
                            rollover.setCartao(f.getCartao());
                            rollover.setFatura(proximaFatura);
                            rollover.setData(hoje);
                            rollover.setAtivo(true);
                            rollover.setEstornada(false);
                            transacaoRepository.save(rollover);

                            proximaFatura.setValorTotal(proximaFatura.getValorTotal().add(valorRollover));
                            faturaRepository.save(proximaFatura);
                        }
                    } else if (f.getStatus() != StatusFatura.ATRASADA) {
                        f.setStatus(StatusFatura.ATRASADA);
                        faturaRepository.save(f);
                    }
                } else {
                    if (f.getStatus() != StatusFatura.PAGA) {
                        f.setStatus(StatusFatura.PAGA);
                        faturaRepository.save(f);
                    }
                }
            }
            // Se passou do fechamento mas ainda não do vencimento
            else if (hoje.isAfter(f.getDataFechamento()) || hoje.isEqual(f.getDataFechamento())) {
                if (f.getStatus() == StatusFatura.ABERTA) {
                    if (restante.compareTo(BigDecimal.ZERO) <= 0) {
                        f.setStatus(StatusFatura.PAGA);
                    } else {
                        f.setStatus(StatusFatura.FECHADA);
                    }
                    faturaRepository.save(f);
                }
            }
        }
    }
}
