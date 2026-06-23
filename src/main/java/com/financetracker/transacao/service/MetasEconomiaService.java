package com.financetracker.transacao.service;

import com.financetracker.conta.entity.Conta;
import com.financetracker.conta.exception.ContaNaoEncontradaException;
import com.financetracker.conta.repository.ContaRepository;
import com.financetracker.transacao.dto.MetaEconomiaCriacaoRequest;
import com.financetracker.transacao.dto.MetaEconomiaResponse;
import com.financetracker.transacao.entity.MetasEconomia;
import com.financetracker.transacao.exception.MetaNaoEncontradaException;
import com.financetracker.transacao.repository.MetasEconomiaRepository;
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
public class MetasEconomiaService {

    private final MetasEconomiaRepository metasRepository;
    private final ContaRepository contaRepository;
    private final UsuarioRepository usuarioRepository;

    public MetasEconomiaService(MetasEconomiaRepository metasRepository,
                                ContaRepository contaRepository,
                                UsuarioRepository usuarioRepository) {
        this.metasRepository = metasRepository;
        this.contaRepository = contaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    private Usuario getAuthenticatedUsuario() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autenticado"));
    }

    @Transactional
    public MetaEconomiaResponse criar(MetaEconomiaCriacaoRequest request) {
        Usuario usuario = getAuthenticatedUsuario();

        Conta conta = contaRepository.findByIdAndUsuarioIdAndAtivoTrue(request.contaVinculadaId(), usuario.getId())
                .orElseThrow(ContaNaoEncontradaException::new);

        MetasEconomia meta = new MetasEconomia();
        meta.setUsuario(usuario);
        meta.setNome(request.nome());
        meta.setValorAlvo(request.valorAlvo());
        meta.setValorAcumulado(BigDecimal.ZERO);
        meta.setContaVinculada(conta);
        meta.setAtivo(true);

        return new MetaEconomiaResponse(metasRepository.save(meta));
    }

    @Transactional(readOnly = true)
    public List<MetaEconomiaResponse> listar() {
        Usuario usuario = getAuthenticatedUsuario();
        return metasRepository.findByUsuarioIdAndAtivoTrue(usuario.getId())
                .stream().map(MetaEconomiaResponse::new).toList();
    }

    @Transactional
    public void excluir(UUID id) {
        Usuario usuario = getAuthenticatedUsuario();
        MetasEconomia meta = metasRepository.findByIdAndUsuarioIdAndAtivoTrue(id, usuario.getId())
                .orElseThrow(MetaNaoEncontradaException::new);

        // RN-17.3 — Estornar saldo acumulado de volta à conta vinculada
        Conta conta = meta.getContaVinculada();
        conta.setSaldo(conta.getSaldo().add(meta.getValorAcumulado()));
        contaRepository.save(conta);

        meta.setAtivo(false);
        metasRepository.save(meta);
    }
}