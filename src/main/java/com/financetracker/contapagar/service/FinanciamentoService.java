package com.financetracker.contapagar.service;

import com.financetracker.contapagar.dto.*;
import com.financetracker.contapagar.entity.*;
import com.financetracker.contapagar.enums.ModoParcelamento;
import com.financetracker.contapagar.enums.StatusOcorrencia;
import com.financetracker.contapagar.repository.*;
import com.financetracker.transacao.entity.Transacao;
import com.financetracker.transacao.enums.TipoTransacao;
import com.financetracker.transacao.repository.TransacaoRepository;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FinanciamentoService {

    private static final Set<TipoTransacao> TIPOS_DESPESA_VINCULAVEIS = EnumSet.of(
            TipoTransacao.PAGAMENTO_BOLETO,
            TipoTransacao.PIX,
            TipoTransacao.SAQUE,
            TipoTransacao.COMPRA_CREDITO
    );

    private final FinanciamentoRepository financiamentoRepository;
    private final FinanciamentoTransacaoRepository financiamentoTransacaoRepository;
    private final ContaPagarModeloRepository modeloRepository;
    private final ContaPagarOcorrenciaRepository ocorrenciaRepository;
    private final ContaPagarAmortizacaoRepository amortizacaoRepository;
    private final TransacaoRepository transacaoRepository;
    private final UsuarioRepository usuarioRepository;

    public FinanciamentoService(FinanciamentoRepository financiamentoRepository,
                                FinanciamentoTransacaoRepository financiamentoTransacaoRepository,
                                ContaPagarModeloRepository modeloRepository,
                                ContaPagarOcorrenciaRepository ocorrenciaRepository,
                                ContaPagarAmortizacaoRepository amortizacaoRepository,
                                TransacaoRepository transacaoRepository,
                                UsuarioRepository usuarioRepository) {
        this.financiamentoRepository = financiamentoRepository;
        this.financiamentoTransacaoRepository = financiamentoTransacaoRepository;
        this.modeloRepository = modeloRepository;
        this.ocorrenciaRepository = ocorrenciaRepository;
        this.amortizacaoRepository = amortizacaoRepository;
        this.transacaoRepository = transacaoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    private Usuario getAuthenticatedUsuario() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autenticado"));
    }

    private Financiamento findFinanciamentoDoUsuario(UUID id, UUID usuarioId) {
        return financiamentoRepository.findByIdAndUsuarioIdAndAtivoTrue(id, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Financiamento não encontrado."));
    }

    // ═══════════════════════════════════════════════════════════════════
    // CRUD DO FINANCIAMENTO (ENTIDADE PAI — ESTILO CAIXINHA / META)
    // ═══════════════════════════════════════════════════════════════════

    @Transactional
    public FinanciamentoResumoResponse criar(FinanciamentoRequest req) {
        Usuario usuario = getAuthenticatedUsuario();
        BigDecimal pagoAnterior = req.valorPagoAnteriormente() != null
                ? req.valorPagoAnteriormente().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        if (pagoAnterior.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor pago anteriormente não pode ser negativo.");
        }
        BigDecimal valorContratado = normalizarValorContratado(req.valorContratado());

        Financiamento fin = new Financiamento();
        fin.setUsuario(usuario);
        fin.setNome(req.nome().trim());
        fin.setDescricao(req.descricao() != null && !req.descricao().isBlank() ? req.descricao().trim() : null);
        fin.setCorHexadecimal(req.corHexadecimal() != null && !req.corHexadecimal().isBlank()
                ? req.corHexadecimal().trim() : "#7c5cfc");
        fin.setValorPagoAnteriormente(pagoAnterior);
        fin.setValorContratado(valorContratado);
        fin.setAtivo(true);

        Financiamento saved = financiamentoRepository.save(fin);
        return montarResumo(saved, usuario.getId());
    }

    @Transactional(readOnly = true)
    public List<FinanciamentoResumoResponse> listar() {
        Usuario usuario = getAuthenticatedUsuario();
        return financiamentoRepository.findByUsuarioIdAndAtivoTrueOrderByNomeAsc(usuario.getId())
                .stream()
                .map(f -> montarResumo(f, usuario.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public FinanciamentoResumoResponse obterPorId(UUID id) {
        Usuario usuario = getAuthenticatedUsuario();
        Financiamento fin = findFinanciamentoDoUsuario(id, usuario.getId());
        return montarResumo(fin, usuario.getId());
    }

    @Transactional
    public FinanciamentoResumoResponse editar(UUID id, FinanciamentoRequest req) {
        Usuario usuario = getAuthenticatedUsuario();
        Financiamento fin = findFinanciamentoDoUsuario(id, usuario.getId());

        BigDecimal pagoAnterior = req.valorPagoAnteriormente() != null
                ? req.valorPagoAnteriormente().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        if (pagoAnterior.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor pago anteriormente não pode ser negativo.");
        }
        BigDecimal valorContratado = normalizarValorContratado(req.valorContratado());

        fin.setNome(req.nome().trim());
        fin.setDescricao(req.descricao() != null && !req.descricao().isBlank() ? req.descricao().trim() : null);
        if (req.corHexadecimal() != null && !req.corHexadecimal().isBlank()) {
            fin.setCorHexadecimal(req.corHexadecimal().trim());
        }
        fin.setValorPagoAnteriormente(pagoAnterior);
        fin.setValorContratado(valorContratado);

        Financiamento saved = financiamentoRepository.save(fin);
        return montarResumo(saved, usuario.getId());
    }

    private BigDecimal normalizarValorContratado(BigDecimal valor) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        if (valor.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor do bem / financiado não pode ser negativo.");
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public void excluir(UUID id) {
        Usuario usuario = getAuthenticatedUsuario();
        Financiamento fin = findFinanciamentoDoUsuario(id, usuario.getId());

        // Desvincular modelos sem apagar nenhuma conta/boleto
        List<ContaPagarModelo> modelos = modeloRepository
                .findByUsuarioIdAndFinanciamentoIdAndAtivoTrueOrderByNomeAsc(usuario.getId(), fin.getId());
        for (ContaPagarModelo m : modelos) {
            m.setFinanciamento(null);
            modeloRepository.save(m);
        }

        // Remover vínculos de transações soltas (sem apagar as transações!)
        List<FinanciamentoTransacao> vinculos = financiamentoTransacaoRepository
                .findByFinanciamentoIdWithTransacao(fin.getId());
        if (!vinculos.isEmpty()) {
            financiamentoTransacaoRepository.deleteAll(vinculos);
        }

        fin.setAtivo(false);
        financiamentoRepository.save(fin);
    }

    // ═══════════════════════════════════════════════════════════════════
    // VINCULAÇÃO DE BOLETOS / MODELOS E TRANSAÇÕES SOLTAS JÁ PAGAS
    // ═══════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public ItensDisponiveisVinculoResponse listarItensDisponiveis() {
        Usuario usuario = getAuthenticatedUsuario();
        UUID uid = usuario.getId();

        // 1. Modelos (parcelados, boletos avulsos já pagos, fixos, etc.) ainda sem financiamento pai
        List<ContaPagarModelo> modelosSoltos = modeloRepository
                .findByUsuarioIdAndFinanciamentoIsNullAndAtivoTrueOrderByNomeAsc(uid);

        Map<UUID, List<ContaPagarOcorrencia>> ocPorModelo = new HashMap<>();
        if (!modelosSoltos.isEmpty()) {
            List<UUID> ids = modelosSoltos.stream().map(ContaPagarModelo::getId).toList();
            for (ContaPagarOcorrencia o : ocorrenciaRepository.findByModeloIdIn(ids)) {
                ocPorModelo.computeIfAbsent(o.getModelo().getId(), k -> new ArrayList<>()).add(o);
            }
        }

        List<ItensDisponiveisVinculoResponse.ModeloDisponivelItem> modelosDisponiveis = modelosSoltos.stream()
                .map(m -> {
                    List<ContaPagarOcorrencia> ocs = ocPorModelo.getOrDefault(m.getId(), List.of());
                    int pagas = 0;
                    int restantes = 0;
                    BigDecimal pago = BigDecimal.ZERO;
                    BigDecimal restante = BigDecimal.ZERO;
                    for (ContaPagarOcorrencia o : ocs) {
                        if (o.getStatus() == StatusOcorrencia.PAGA) {
                            pagas++;
                            pago = pago.add(o.getValorPago() != null ? o.getValorPago() : BigDecimal.ZERO);
                        } else if (o.getStatus() == StatusOcorrencia.PENDENTE || o.getStatus() == StatusOcorrencia.AGUARDANDO_VALOR) {
                            restantes++;
                            restante = restante.add(o.getValorEfetivo());
                        }
                    }
                    return new ItensDisponiveisVinculoResponse.ModeloDisponivelItem(
                            m.getId(),
                            m.getNome(),
                            m.getTipo().name(),
                            m.getAgrupador(),
                            m.getTotalParcelas(),
                            pagas,
                            restantes,
                            pago,
                            restante
                    );
                })
                .toList();

        // 2. Transações soltas de despesa (excluindo as que já pertencem a alguma ocorrência, amortização ou financiamento)
        Set<UUID> jaVinculadas = new HashSet<>();
        jaVinculadas.addAll(ocorrenciaRepository.findTransacaoIdsVinculadasByUsuarioId(uid));
        jaVinculadas.addAll(amortizacaoRepository.findTransacaoIdsVinculadasByUsuarioId(uid));
        jaVinculadas.addAll(financiamentoTransacaoRepository.findTransacaoIdsVinculadasByUsuarioId(uid));

        List<ItensDisponiveisVinculoResponse.TransacaoDisponivelItem> transacoesDisponiveis = transacaoRepository
                .findByUsuarioIdAndAtivoTrueOrderByDataDesc(uid)
                .stream()
                .filter(t -> !Boolean.TRUE.equals(t.getEstornada()))
                .filter(t -> t.getTipo() != null && TIPOS_DESPESA_VINCULAVEIS.contains(t.getTipo()))
                .filter(t -> t.getMetaDestino() == null && t.getMetaOrigem() == null)
                .filter(t -> !jaVinculadas.contains(t.getId()))
                .limit(150)
                .map(t -> new ItensDisponiveisVinculoResponse.TransacaoDisponivelItem(
                        t.getId(),
                        t.getDescricao(),
                        t.getValor(),
                        t.getData(),
                        t.getTipo().name(),
                        t.getCategoria() != null ? t.getCategoria().getNome() : null
                ))
                .toList();

        return new ItensDisponiveisVinculoResponse(modelosDisponiveis, transacoesDisponiveis);
    }

    @Transactional
    public FinanciamentoResumoResponse vincularItens(UUID financiamentoId, VincularItensFinanciamentoRequest req) {
        Usuario usuario = getAuthenticatedUsuario();
        UUID uid = usuario.getId();
        Financiamento fin = findFinanciamentoDoUsuario(financiamentoId, uid);

        if (req.modeloIds() != null) {
            for (UUID modeloId : req.modeloIds()) {
                if (modeloId == null) continue;
                ContaPagarModelo modelo = modeloRepository.findByIdAndUsuarioIdAndAtivoTrue(modeloId, uid)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta/modelo não encontrado."));
                modelo.setFinanciamento(fin);
                modeloRepository.save(modelo);
            }
        }

        if (req.transacaoIds() != null && !req.transacaoIds().isEmpty()) {
            Set<UUID> jaEmOcorrenciaOuAmort = new HashSet<>();
            jaEmOcorrenciaOuAmort.addAll(ocorrenciaRepository.findTransacaoIdsVinculadasByUsuarioId(uid));
            jaEmOcorrenciaOuAmort.addAll(amortizacaoRepository.findTransacaoIdsVinculadasByUsuarioId(uid));

            for (UUID transacaoId : req.transacaoIds()) {
                if (transacaoId == null) continue;
                if (financiamentoTransacaoRepository.existsByTransacaoId(transacaoId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Uma das transações selecionadas já está vinculada a um financiamento.");
                }
                if (jaEmOcorrenciaOuAmort.contains(transacaoId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Esta transação já pertence a um boleto/parcela de Contas a Pagar. Vincule o boleto/conta diretamente para evitar duplicidade.");
                }
                Transacao tx = transacaoRepository.findByIdAndUsuarioIdAndAtivoTrue(transacaoId, uid)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transação não encontrada."));
                if (Boolean.TRUE.equals(tx.getEstornada())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é possível vincular uma transação estornada.");
                }
                FinanciamentoTransacao ft = new FinanciamentoTransacao();
                ft.setUsuario(usuario);
                ft.setFinanciamento(fin);
                ft.setTransacao(tx);
                financiamentoTransacaoRepository.save(ft);
            }
        }

        return montarResumo(fin, uid);
    }

    @Transactional
    public FinanciamentoResumoResponse desvincularModelo(UUID financiamentoId, UUID modeloId) {
        Usuario usuario = getAuthenticatedUsuario();
        Financiamento fin = findFinanciamentoDoUsuario(financiamentoId, usuario.getId());
        ContaPagarModelo modelo = modeloRepository.findByIdAndUsuarioIdAndAtivoTrue(modeloId, usuario.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta/modelo não encontrado."));
        if (modelo.getFinanciamento() != null && modelo.getFinanciamento().getId().equals(fin.getId())) {
            modelo.setFinanciamento(null);
            modeloRepository.save(modelo);
        }
        return montarResumo(fin, usuario.getId());
    }

    @Transactional
    public FinanciamentoResumoResponse desvincularTransacao(UUID financiamentoId, UUID transacaoId) {
        Usuario usuario = getAuthenticatedUsuario();
        Financiamento fin = findFinanciamentoDoUsuario(financiamentoId, usuario.getId());
        financiamentoTransacaoRepository.findByFinanciamentoIdAndTransacaoId(fin.getId(), transacaoId)
                .ifPresent(financiamentoTransacaoRepository::delete);
        return montarResumo(fin, usuario.getId());
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONSOLIDAÇÃO DE TOTAIS
    // ═══════════════════════════════════════════════════════════════════

    private FinanciamentoResumoResponse montarResumo(Financiamento fin, UUID usuarioId) {
        List<ContaPagarModelo> modelos = modeloRepository
                .findByUsuarioIdAndFinanciamentoIdAndAtivoTrueOrderByNomeAsc(usuarioId, fin.getId());

        Map<UUID, List<ContaPagarOcorrencia>> ocPorModelo = new HashMap<>();
        Map<UUID, List<ContaPagarAmortizacao>> amortPorModelo = new HashMap<>();

        if (!modelos.isEmpty()) {
            List<UUID> modeloIds = modelos.stream().map(ContaPagarModelo::getId).toList();
            for (ContaPagarOcorrencia o : ocorrenciaRepository.findByModeloIdIn(modeloIds)) {
                ocPorModelo.computeIfAbsent(o.getModelo().getId(), k -> new ArrayList<>()).add(o);
            }
            for (ContaPagarAmortizacao a : amortizacaoRepository.findByModeloIdIn(modeloIds)) {
                amortPorModelo.computeIfAbsent(a.getModelo().getId(), k -> new ArrayList<>()).add(a);
            }
        }

        BigDecimal totalPagoModelos = BigDecimal.ZERO;
        BigDecimal totalPagoAmortizacoes = BigDecimal.ZERO;
        BigDecimal totalRestante = BigDecimal.ZERO;
        BigDecimal totalDescontoObtido = BigDecimal.ZERO;

        List<FinanciamentoResumoResponse.ModeloVinculadoResumo> modelosResumo = new ArrayList<>();
        for (ContaPagarModelo m : modelos) {
            List<ContaPagarOcorrencia> ocs = ocPorModelo.getOrDefault(m.getId(), List.of());
            List<ContaPagarAmortizacao> amorts = amortPorModelo.getOrDefault(m.getId(), List.of());

            int pagas = 0;
            int restantes = 0;
            BigDecimal mPago = BigDecimal.ZERO;
            BigDecimal mRestante = BigDecimal.ZERO;

            for (ContaPagarOcorrencia o : ocs) {
                if (o.getStatus() == StatusOcorrencia.PAGA) {
                    pagas++;
                    mPago = mPago.add(o.getValorPago() != null ? o.getValorPago() : BigDecimal.ZERO);
                } else if (o.getStatus() == StatusOcorrencia.PENDENTE || o.getStatus() == StatusOcorrencia.AGUARDANDO_VALOR) {
                    restantes++;
                    mRestante = mRestante.add(o.getValorEfetivo());
                }
            }

            BigDecimal mAmort = amorts.stream()
                    .map(ContaPagarAmortizacao::getValor)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal mDesconto = amorts.stream()
                    .map(ContaPagarAmortizacao::getDescontoObtido)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal mPagoTotal = mPago.add(mAmort);
            BigDecimal mValorTotal = mPagoTotal.add(mRestante);

            totalPagoModelos = totalPagoModelos.add(mPago);
            totalPagoAmortizacoes = totalPagoAmortizacoes.add(mAmort);
            totalRestante = totalRestante.add(mRestante);
            totalDescontoObtido = totalDescontoObtido.add(mDesconto);

            modelosResumo.add(new FinanciamentoResumoResponse.ModeloVinculadoResumo(
                    m.getId(),
                    m.getNome(),
                    m.getTipo().name(),
                    m.getModoParcelamento() != null ? m.getModoParcelamento().name() : ModoParcelamento.FIXO.name(),
                    m.getIntervaloMeses() != null ? m.getIntervaloMeses() : 1,
                    Boolean.TRUE.equals(m.getValoresEstimados()),
                    m.getDiaVencimento(),
                    m.getValorPadrao(),
                    m.getValorUltimaParcela(),
                    m.getTotalParcelas(),
                    pagas,
                    restantes,
                    mPagoTotal,
                    mAmort,
                    mDesconto,
                    mRestante,
                    mValorTotal,
                    restantes == 0
            ));
        }

        List<FinanciamentoTransacao> vinculosTx = financiamentoTransacaoRepository
                .findByFinanciamentoIdWithTransacao(fin.getId())
                .stream()
                .filter(ft -> ft.getTransacao() != null
                        && Boolean.TRUE.equals(ft.getTransacao().getAtivo())
                        && !Boolean.TRUE.equals(ft.getTransacao().getEstornada()))
                .toList();

        BigDecimal totalPagoTransacoesSoltas = vinculosTx.stream()
                .map(ft -> ft.getTransacao().getValor())
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<FinanciamentoResumoResponse.TransacaoVinculadaItem> txItens = vinculosTx.stream()
                .map(ft -> {
                    Transacao t = ft.getTransacao();
                    return new FinanciamentoResumoResponse.TransacaoVinculadaItem(
                            ft.getId(),
                            t.getId(),
                            t.getDescricao(),
                            t.getValor(),
                            t.getData(),
                            t.getTipo() != null ? t.getTipo().name() : "",
                            t.getCategoria() != null ? t.getCategoria().getNome() : null,
                            ft.getObservacao()
                    );
                })
                .collect(Collectors.toList());

        BigDecimal pagoAnterior = fin.getValorPagoAnteriormente() != null ? fin.getValorPagoAnteriormente() : BigDecimal.ZERO;
        BigDecimal totalPago = pagoAnterior
                .add(totalPagoModelos)
                .add(totalPagoAmortizacoes)
                .add(totalPagoTransacoesSoltas);
        BigDecimal valorTotal = totalPago.add(totalRestante);
        BigDecimal valorContratado = fin.getValorContratado();
        BigDecimal jurosEncargosProjetados = null;
        Double percentualJurosProjetados = null;
        if (valorContratado != null && valorContratado.compareTo(BigDecimal.ZERO) > 0) {
            jurosEncargosProjetados = valorTotal.subtract(valorContratado)
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            percentualJurosProjetados = jurosEncargosProjetados.multiply(BigDecimal.valueOf(100))
                    .divide(valorContratado, 2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        double percentualPago = valorTotal.compareTo(BigDecimal.ZERO) > 0
                ? totalPago.multiply(BigDecimal.valueOf(100))
                        .divide(valorTotal, 2, RoundingMode.HALF_UP)
                        .doubleValue()
                : 0.0;

        boolean concluido = totalRestante.compareTo(BigDecimal.ZERO) == 0
                && (!modelos.isEmpty() || totalPago.compareTo(BigDecimal.ZERO) > 0);

        return new FinanciamentoResumoResponse(
                fin.getId(),
                fin.getNome(),
                fin.getDescricao(),
                fin.getCorHexadecimal(),
                pagoAnterior,
                totalPagoModelos,
                totalPagoAmortizacoes,
                totalPagoTransacoesSoltas,
                totalPago,
                totalRestante,
                valorTotal,
                valorContratado,
                jurosEncargosProjetados,
                percentualJurosProjetados,
                totalDescontoObtido,
                percentualPago,
                concluido,
                modelosResumo.size(),
                txItens.size(),
                modelosResumo,
                txItens
        );
    }
}
