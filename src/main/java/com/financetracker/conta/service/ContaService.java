package com.financetracker.conta.service;

import com.financetracker.conta.dto.*;
import com.financetracker.conta.entity.Conta;
import com.financetracker.conta.exception.ContaNaoEncontradaException;
import com.financetracker.conta.exception.LimitaContasException;
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
public class ContaService {

    private final ContaRepository contaRepository;
    private final UsuarioRepository usuarioRepository;

    public ContaService(ContaRepository contaRepository, UsuarioRepository usuarioRepository) {
        this.contaRepository = contaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // ─── helpers ───────────────────────────────────────────────

    private Usuario getAuthenticatedUsuario() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autenticado"));
    }

    private Conta findContaDoUsuario(UUID contaId, UUID usuarioId) {
        return contaRepository.findByIdAndUsuarioIdAndAtivoTrue(contaId, usuarioId)
                .orElseThrow(ContaNaoEncontradaException::new);
    }

    // ─── casos de uso ───────────────────────────────────────────

    @Transactional
    public ContaResponse criar(ContaCriacaoRequest request) {
        Usuario usuario = getAuthenticatedUsuario();

        // RN-05 — limite de 10 contas
        long totalAtivas = contaRepository.countByUsuarioIdAndAtivoTrue(usuario.getId());
        if (totalAtivas >= 10) {
            throw new LimitaContasException("Limite máximo de 10 contas atingido.");
        }

        // RN-04 — exclusividade conta padrão
        boolean contaPadrao = Boolean.TRUE.equals(request.contaPadrao());
        if (contaPadrao) {
            contaRepository.clearContaPadraoByUsuarioId(usuario.getId());
        }

        Conta conta = new Conta();
        conta.setUsuario(usuario);
        conta.setNome(request.nome());
        conta.setTipo(request.tipo());
        conta.setSaldo(request.saldo());
        conta.setCorHexadecimal(request.corHexadecimal());
        conta.setContaPadrao(contaPadrao);
        conta.setAtivo(true);

        return new ContaResponse(contaRepository.save(conta));
    }

    @Transactional(readOnly = true)
    public List<ContaResponse> listar() {
        Usuario usuario = getAuthenticatedUsuario();
        return contaRepository.findByUsuarioIdAndAtivoTrue(usuario.getId())
                .stream()
                .map(ContaResponse::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public ContaResponse buscarPorId(UUID contaId) {
        Usuario usuario = getAuthenticatedUsuario();
        Conta conta = findContaDoUsuario(contaId, usuario.getId());
        return new ContaResponse(conta);
    }

    @Transactional
    public ContaResponse editar(UUID contaId, ContaEdicaoRequest request) {
        Usuario usuario = getAuthenticatedUsuario();
        Conta conta = findContaDoUsuario(contaId, usuario.getId());

        // RN-04 — exclusividade conta padrão
        boolean contaPadrao = Boolean.TRUE.equals(request.contaPadrao());
        if (contaPadrao) {
            contaRepository.clearContaPadraoByUsuarioId(usuario.getId());
        }

        conta.setNome(request.nome());
        conta.setTipo(request.tipo());
        conta.setCorHexadecimal(request.corHexadecimal());
        conta.setContaPadrao(contaPadrao);
        // RN-02 — saldo NÃO é atualizado aqui

        return new ContaResponse(contaRepository.save(conta));
    }

    @Transactional
    public void excluir(UUID contaId) {
        Usuario usuario = getAuthenticatedUsuario();
        Conta conta = findContaDoUsuario(contaId, usuario.getId());

        // RN-06 — proteger última conta ativa
        long totalAtivas = contaRepository.countByUsuarioIdAndAtivoTrue(usuario.getId());
        if (totalAtivas <= 1) {
            throw new LimitaContasException("Não é possível excluir a única conta ativa.");
        }

        // RN-03 — soft delete
        conta.setAtivo(false);
        contaRepository.save(conta);
    }

    @Transactional(readOnly = true)
    public ContaResumoResponse resumo() {
        Usuario usuario = getAuthenticatedUsuario();
        List<Conta> contas = contaRepository.findByUsuarioIdAndAtivoTrue(usuario.getId());
        BigDecimal total = contas.stream()
                .map(Conta::getSaldo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ContaResumoResponse(total, contas.size());
    }
}
