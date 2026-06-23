package com.financetracker.transacao.service;

import com.financetracker.categoria.entity.Categoria;
import com.financetracker.categoria.exception.CategoriaNaoEncontradaException;
import com.financetracker.categoria.repository.CategoriaRepository;
import com.financetracker.transacao.dto.*;
import com.financetracker.transacao.entity.OrcamentoCategoria;
import com.financetracker.transacao.enums.TipoTransacao;
import com.financetracker.transacao.exception.TransacaoNaoEncontradaException;
import com.financetracker.transacao.repository.OrcamentoCategoriaRepository;
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
import java.util.UUID;

@Service
public class OrcamentoService {

    private final OrcamentoCategoriaRepository orcamentoRepository;
    private final TransacaoRepository transacaoRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;

    public OrcamentoService(OrcamentoCategoriaRepository orcamentoRepository,
                            TransacaoRepository transacaoRepository,
                            CategoriaRepository categoriaRepository,
                            UsuarioRepository usuarioRepository) {
        this.orcamentoRepository = orcamentoRepository;
        this.transacaoRepository = transacaoRepository;
        this.categoriaRepository = categoriaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    private Usuario getAuthenticatedUsuario() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autenticado"));
    }

    private Categoria findCategoriaDoUsuario(UUID categoriaId, UUID usuarioId) {
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(CategoriaNaoEncontradaException::new);
        if (categoria.getUsuario() != null && !categoria.getUsuario().getId().equals(usuarioId)) {
            throw new CategoriaNaoEncontradaException();
        }
        return categoria;
    }

    @Transactional
    public OrcamentoResponse criarOuAtualizar(OrcamentoCriacaoRequest request) {
        Usuario usuario = getAuthenticatedUsuario();
        Categoria categoria = findCategoriaDoUsuario(request.categoriaId(), usuario.getId());

        OrcamentoCategoria orcamento = orcamentoRepository
                .findByUsuarioIdAndCategoriaIdAndMesReferencia(
                        usuario.getId(), categoria.getId(), request.mesReferencia())
                .orElseGet(() -> {
                    OrcamentoCategoria novo = new OrcamentoCategoria();
                    novo.setUsuario(usuario);
                    novo.setCategoria(categoria);
                    novo.setMesReferencia(request.mesReferencia());
                    return novo;
                });

        orcamento.setLimiteMensal(request.limiteMensal());
        return new OrcamentoResponse(orcamentoRepository.save(orcamento));
    }

    @Transactional(readOnly = true)
    public List<OrcamentoResumoResponse> resumo() {
        Usuario usuario = getAuthenticatedUsuario();
        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        LocalDate fimMes = inicioMes.plusMonths(1).minusDays(1);

        List<OrcamentoCategoria> orcamentos = orcamentoRepository
                .findByUsuarioIdAndMesReferencia(usuario.getId(), inicioMes);

        return orcamentos.stream().map(o -> {
            BigDecimal totalGasto = transacaoRepository.sumValorByCategoriaAndPeriodo(
                    usuario.getId(), o.getCategoria().getId(), inicioMes, fimMes,
                    List.of(TipoTransacao.SAQUE, TipoTransacao.PIX, TipoTransacao.COMPRA_CREDITO));
            return new OrcamentoResumoResponse(
                    o.getCategoria().getId(),
                    o.getCategoria().getNome(),
                    o.getLimiteMensal(),
                    totalGasto != null ? totalGasto : BigDecimal.ZERO);
        }).toList();
    }
}