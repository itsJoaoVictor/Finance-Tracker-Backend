package com.financetracker.assinatura.service;

import com.financetracker.assinatura.dto.*;
import com.financetracker.assinatura.entity.Assinatura;
import com.financetracker.assinatura.enums.TipoRecorrencia;
import com.financetracker.assinatura.enums.UnidadeFrequencia;
import com.financetracker.assinatura.exception.AssinaturaNaoEncontradaException;
import com.financetracker.assinatura.exception.FrequenciaInvalidaException;
import com.financetracker.assinatura.repository.AssinaturaRepository;
import com.financetracker.cartao.entity.Cartao;
import com.financetracker.cartao.repository.CartaoRepository;
import com.financetracker.categoria.entity.Categoria;
import com.financetracker.categoria.exception.CategoriaNaoEncontradaException;
import com.financetracker.categoria.repository.CategoriaRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AssinaturaService {

    private final AssinaturaRepository assinaturaRepository;
    private final CartaoRepository cartaoRepository;
    private final CategoriaRepository categoriaRepository;
    private final FaturaRepository faturaRepository;
    private final TransacaoRepository transacaoRepository;
    private final UsuarioRepository usuarioRepository;

    public AssinaturaService(AssinaturaRepository assinaturaRepository,
                             CartaoRepository cartaoRepository,
                             CategoriaRepository categoriaRepository,
                             FaturaRepository faturaRepository,
                             TransacaoRepository transacaoRepository,
                             UsuarioRepository usuarioRepository) {
        this.assinaturaRepository = assinaturaRepository;
        this.cartaoRepository = cartaoRepository;
        this.categoriaRepository = categoriaRepository;
        this.faturaRepository = faturaRepository;
        this.transacaoRepository = transacaoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    private Usuario getAuthenticatedUsuario() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autenticado"));
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

    // RN-04 — Cálculo da data da próxima cobrança
    private LocalDate calcularProximaCobranca(Assinatura a) {
        LocalDate base = a.getDataProximaCobranca() != null
                ? a.getDataProximaCobranca()
                : a.getDataInicio();
        LocalDate proxima;

        switch (a.getTipoRecorrencia()) {
            case MENSAL -> proxima = base.plusMonths(1);
            case TRIMESTRAL -> proxima = base.plusMonths(3);
            case ANUAL -> proxima = base.plusYears(1);
            case PERSONALIZADO -> {
                if (a.getFrequencia() == null || a.getUnidadeFrequencia() == null) {
                    throw new FrequenciaInvalidaException("Frequência e unidade são obrigatórias para recorrência personalizada.");
                }
                proxima = switch (a.getUnidadeFrequencia()) {
                    case SEMANAS -> base.plusWeeks(a.getFrequencia());
                    case MESES -> base.plusMonths(a.getFrequencia());
                    case ANOS -> base.plusYears(a.getFrequencia());
                };
            }
            default -> throw new FrequenciaInvalidaException("Tipo de recorrência inválido.");
        }

        // Ajuste de fim de mês (ex: dia 31 em fevereiro)
        int diaCobranca = a.getDiaCobranca();
        int maxDia = proxima.lengthOfMonth();
        if (diaCobranca > maxDia) {
            proxima = proxima.withDayOfMonth(maxDia);
        }

        return proxima;
    }

    // RN-01 — Anti-IDOR: buscar assinatura do usuário
    private Assinatura findAssinaturaDoUsuario(UUID id, UUID usuarioId) {
        return assinaturaRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(AssinaturaNaoEncontradaException::new);
    }

    @Transactional
    public AssinaturaResponse criar(AssinaturaCriacaoRequest request) {
        Usuario usuario = getAuthenticatedUsuario();

        // RN-01 — Validar cartão e categoria pertencem ao usuário
        Cartao cartao = findCartaoDoUsuario(request.cartaoId(), usuario.getId());
        Categoria categoria = findCategoriaDoUsuario(request.categoriaId(), usuario.getId());

        // Validar PERSONALIZADO
        if (request.tipoRecorrencia() == TipoRecorrencia.PERSONALIZADO) {
            if (request.frequencia() == null || request.unidadeFrequencia() == null) {
                throw new FrequenciaInvalidaException("Frequência e unidade são obrigatórias para recorrência personalizada.");
            }
        }

        Assinatura assinatura = new Assinatura();
        assinatura.setUsuario(usuario);
        assinatura.setCartao(cartao);
        assinatura.setCategoria(categoria);
        assinatura.setNome(request.nome());
        assinatura.setValor(request.valor());
        assinatura.setTipoRecorrencia(request.tipoRecorrencia());
        assinatura.setFrequencia(request.frequencia());
        assinatura.setUnidadeFrequencia(request.unidadeFrequencia());
        assinatura.setDiaCobranca(request.diaCobranca());
        assinatura.setDataInicio(request.dataInicio());

        // Calcular data_proxima_cobranca
        int maxDia = request.dataInicio().lengthOfMonth();
        int dia = Math.min(request.diaCobranca(), maxDia);
        LocalDate primeiraCobranca = request.dataInicio().withDayOfMonth(dia);
        if (primeiraCobranca.isBefore(request.dataInicio())) {
            primeiraCobranca = primeiraCobranca.plusMonths(1);
            int maxDiaProx = primeiraCobranca.lengthOfMonth();
            primeiraCobranca = primeiraCobranca.withDayOfMonth(Math.min(request.diaCobranca(), maxDiaProx));
        }
        assinatura.setDataProximaCobranca(primeiraCobranca);

        assinatura.setAtivo(request.ativo() != null ? request.ativo() : true);

        return new AssinaturaResponse(assinaturaRepository.save(assinatura));
    }

    @Transactional(readOnly = true)
    public List<AssinaturaResponse> listar() {
        Usuario usuario = getAuthenticatedUsuario();
        return assinaturaRepository.findByUsuarioId(usuario.getId())
                .stream().map(AssinaturaResponse::new).toList();
    }

    @Transactional(readOnly = true)
    public AssinaturaResponse buscarPorId(UUID id) {
        Usuario usuario = getAuthenticatedUsuario();
        return new AssinaturaResponse(findAssinaturaDoUsuario(id, usuario.getId()));
    }

    @Transactional
    public AssinaturaResponse editar(UUID id, AssinaturaEdicaoRequest request) {
        Usuario usuario = getAuthenticatedUsuario();
        Assinatura assinatura = findAssinaturaDoUsuario(id, usuario.getId());

        // RN-01 — Validar recursos
        Cartao cartao = findCartaoDoUsuario(request.cartaoId(), usuario.getId());
        Categoria categoria = findCategoriaDoUsuario(request.categoriaId(), usuario.getId());

        if (request.tipoRecorrencia() == TipoRecorrencia.PERSONALIZADO) {
            if (request.frequencia() == null || request.unidadeFrequencia() == null) {
                throw new FrequenciaInvalidaException("Frequência e unidade são obrigatórias para recorrência personalizada.");
            }
        }

        assinatura.setNome(request.nome());
        assinatura.setValor(request.valor());
        assinatura.setCartao(cartao);
        assinatura.setCategoria(categoria);
        assinatura.setTipoRecorrencia(request.tipoRecorrencia());
        assinatura.setFrequencia(request.frequencia());
        assinatura.setUnidadeFrequencia(request.unidadeFrequencia());
        assinatura.setDiaCobranca(request.diaCobranca());
        assinatura.setDataInicio(request.dataInicio());
        assinatura.setAtivo(request.ativo() != null ? request.ativo() : assinatura.getAtivo());

        // RN-07 — Edição afeta apenas lançamentos futuros
        // Recalcular data_proxima_cobranca se alterou parâmetros
        assinatura.setDataProximaCobranca(calcularNovaProximaCobranca(assinatura));

        return new AssinaturaResponse(assinaturaRepository.save(assinatura));
    }

    private LocalDate calcularNovaProximaCobranca(Assinatura a) {
        int maxDia = a.getDataInicio().lengthOfMonth();
        int dia = Math.min(a.getDiaCobranca(), maxDia);
        LocalDate proxima = a.getDataInicio().withDayOfMonth(dia);
        while (!proxima.isAfter(LocalDate.now())) {
            proxima = proxima.plusMonths(1);
            int maxDiaProx = proxima.lengthOfMonth();
            proxima = proxima.withDayOfMonth(Math.min(a.getDiaCobranca(), maxDiaProx));
        }
        return proxima;
    }

    @Transactional
    public void excluir(UUID id) {
        Usuario usuario = getAuthenticatedUsuario();
        Assinatura assinatura = assinaturaRepository.findByIdAndUsuarioId(id, usuario.getId())
                .orElseThrow(AssinaturaNaoEncontradaException::new);

        // RN-09 — Verificar se existem transações históricas
        long transacoesAssociadas = transacaoRepository.findByUsuarioIdAndAtivoTrueOrderByDataDesc(usuario.getId())
                .stream().filter(t -> t.getDescricao() != null
                        && t.getDescricao().contains(assinatura.getNome()))
                .count();

        if (transacoesAssociadas > 0) {
            // Soft delete — inativa definitivamente
            assinatura.setAtivo(false);
            assinaturaRepository.save(assinatura);
        } else {
            // Exclusão física
            assinaturaRepository.delete(assinatura);
        }
    }

    @Transactional
    public void pausar(UUID id) {
        Usuario usuario = getAuthenticatedUsuario();
        Assinatura assinatura = findAssinaturaDoUsuario(id, usuario.getId());
        assinatura.setAtivo(false);
        assinaturaRepository.save(assinatura);
    }

    @Transactional
    public void reativar(UUID id) {
        Usuario usuario = getAuthenticatedUsuario();
        Assinatura assinatura = findAssinaturaDoUsuario(id, usuario.getId());
        assinatura.setAtivo(true);
        // Recalcular próxima cobrança
        assinatura.setDataProximaCobranca(calcularNovaProximaCobranca(assinatura));
        assinaturaRepository.save(assinatura);
    }

    @Transactional(readOnly = true)
    public List<AssinaturaProximaResponse> proximas(int dias) {
        Usuario usuario = getAuthenticatedUsuario();
        LocalDate hoje = LocalDate.now();
        LocalDate limite = hoje.plusDays(dias);

        return assinaturaRepository.findByUsuarioId(usuario.getId())
                .stream()
                .filter(a -> a.getAtivo()
                        && !a.getDataProximaCobranca().isAfter(limite)
                        && !a.getDataProximaCobranca().isBefore(hoje))
                .map(a -> new AssinaturaProximaResponse(
                        a.getId(), a.getNome(), a.getValor(), a.getCartao().getId(),
                        a.getDataProximaCobranca(),
                        ChronoUnit.DAYS.between(hoje, a.getDataProximaCobranca())))
                .toList();
    }

    // ── Scheduler: processar cobranças pendentes (RN-03) ────────

    @Transactional
    public void processarCobrancasPendentes() {
        LocalDate hoje = LocalDate.now();
        List<Assinatura> assinaturas = assinaturaRepository
                .findByAtivoTrueAndDataProximaCobrancaLessThanEqual(hoje);

        for (Assinatura a : assinaturas) {
            try {
                Usuario usuario = a.getUsuario();
                Cartao cartao = a.getCartao();

                // RN-03.2 — Verificar se fatura atual já foi paga
                Optional<Fatura> faturaAberta = faturaRepository
                        .findByCartaoIdAndStatusAndUsuarioId(cartao.getId(), StatusFatura.ABERTA, usuario.getId());

                // RN-03.5 — Prevenir duplicidade
                List<Transacao> transacoes = transacaoRepository
                        .findByFaturaIdAndAtivoTrue(
                                faturaAberta.map(Fatura::getId).orElse(null));
                boolean jaCobrada = transacoes.stream()
                        .anyMatch(t -> t.getDescricao() != null
                                && t.getDescricao().contains(a.getNome())
                                && t.getData().equals(a.getDataProximaCobranca()));
                if (jaCobrada) continue;

                // Criar a transação da assinatura
                Transacao transacao = new Transacao();
                transacao.setUsuario(usuario);
                transacao.setDescricao("Assinatura: " + a.getNome());
                transacao.setValor(a.getValor());
                transacao.setTipo(TipoTransacao.COMPRA_CREDITO);
                transacao.setCartao(cartao);
                transacao.setCategoria(a.getCategoria());
                transacao.setData(a.getDataProximaCobranca());
                transacao.setAtivo(true);
                transacao.setEstornada(false);

                if (faturaAberta.isPresent()) {
                    Fatura fatura = faturaAberta.get();
                    transacao.setFatura(fatura);
                    fatura.setValorTotal(fatura.getValorTotal().add(a.getValor()));
                    faturaRepository.save(fatura);
                }

                // RN-05 — Consumir limite do cartão (mesmo que fique negativo, gerar alerta)
                cartao.setLimiteDisponivel(cartao.getLimiteDisponivel().subtract(a.getValor()));
                cartaoRepository.save(cartao);

                transacaoRepository.save(transacao);

                // Avançar data_proxima_cobranca
                a.setDataProximaCobranca(calcularProximaCobranca(a));
                assinaturaRepository.save(a);

            } catch (Exception e) {
                // Log error but continue processing other subscriptions
                System.err.println("Erro ao processar assinatura " + a.getId() + ": " + e.getMessage());
            }
        }
    }
}