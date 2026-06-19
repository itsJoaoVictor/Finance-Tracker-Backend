package com.financetracker.cartao.service;

import com.financetracker.cartao.dto.*;
import com.financetracker.cartao.entity.Cartao;
import com.financetracker.cartao.exception.*;
import com.financetracker.cartao.repository.CartaoRepository;
import com.financetracker.conta.entity.Conta;
import com.financetracker.conta.exception.ContaNaoEncontradaException;
import com.financetracker.conta.repository.ContaRepository;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class CartaoService {

    private final CartaoRepository cartaoRepository;
    private final ContaRepository contaRepository;
    private final UsuarioRepository usuarioRepository;

    public CartaoService(CartaoRepository cartaoRepository, ContaRepository contaRepository, UsuarioRepository usuarioRepository) {
        this.cartaoRepository = cartaoRepository;
        this.contaRepository = contaRepository;
        this.usuarioRepository = usuarioRepository;
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

    @Transactional(readOnly = true)
    public List<CartaoResponse> listar() {
        Usuario usuario = getAuthenticatedUsuario();
        return cartaoRepository.findByUsuarioIdAndAtivoTrue(usuario.getId())
                .stream()
                .map(CartaoResponse::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public CartaoResponse buscarPorId(UUID cartaoId) {
        Usuario usuario = getAuthenticatedUsuario();
        Cartao cartao = findCartaoDoUsuario(cartaoId, usuario.getId());
        return new CartaoResponse(cartao);
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

        // RN-04 — Soft Delete
        cartao.setAtivo(false);
        cartaoRepository.save(cartao);
    }

    @Transactional(readOnly = true)
    public CartaoResumoResponse resumo() {
        Usuario usuario = getAuthenticatedUsuario();
        List<Cartao> cartoes = cartaoRepository.findByUsuarioIdAndAtivoTrue(usuario.getId());

        BigDecimal totalLimite = BigDecimal.ZERO;
        BigDecimal totalLimiteDisponivel = BigDecimal.ZERO;
        BigDecimal totalFaturaEstimada = BigDecimal.ZERO;

        for (Cartao c : cartoes) {
            totalLimite = totalLimite.add(c.getLimite());
            totalLimiteDisponivel = totalLimiteDisponivel.add(c.getLimiteDisponivel());
            // Fatura estimada = Limite total - Limite disponível
            BigDecimal faturaCartao = c.getLimite().subtract(c.getLimiteDisponivel());
            if (faturaCartao.compareTo(BigDecimal.ZERO) > 0) {
                totalFaturaEstimada = totalFaturaEstimada.add(faturaCartao);
            }
        }

        return new CartaoResumoResponse(
                totalLimite,
                totalLimiteDisponivel,
                totalFaturaEstimada,
                cartoes.size()
        );
    }
}
