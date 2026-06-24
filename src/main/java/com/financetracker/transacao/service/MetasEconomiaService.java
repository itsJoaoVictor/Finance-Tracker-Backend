package com.financetracker.transacao.service;

import com.financetracker.conta.entity.Conta;
import com.financetracker.conta.exception.ContaNaoEncontradaException;
import com.financetracker.conta.repository.ContaRepository;
import com.financetracker.transacao.dto.MetaEconomiaCriacaoRequest;
import com.financetracker.transacao.dto.MetaEconomiaDepositoRequest;
import com.financetracker.transacao.dto.MetaEconomiaResgateRequest;
import com.financetracker.transacao.dto.MetaEconomiaResponse;
import com.financetracker.transacao.entity.MetasEconomia;
import com.financetracker.transacao.entity.Transacao;
import com.financetracker.transacao.enums.TipoTransacao;
import com.financetracker.transacao.exception.MetaNaoEncontradaException;
import com.financetracker.transacao.repository.MetasEconomiaRepository;
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
public class MetasEconomiaService {

    private final MetasEconomiaRepository metasRepository;
    private final ContaRepository contaRepository;
    private final TransacaoRepository transacaoRepository;
    private final UsuarioRepository usuarioRepository;

    public MetasEconomiaService(MetasEconomiaRepository metasRepository,
                                ContaRepository contaRepository,
                                TransacaoRepository transacaoRepository,
                                UsuarioRepository usuarioRepository) {
        this.metasRepository = metasRepository;
        this.contaRepository = contaRepository;
        this.transacaoRepository = transacaoRepository;
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
    public MetaEconomiaResponse depositar(UUID id, MetaEconomiaDepositoRequest request) {
        Usuario usuario = getAuthenticatedUsuario();
        MetasEconomia meta = metasRepository.findByIdAndUsuarioIdAndAtivoTrue(id, usuario.getId())
                .orElseThrow(MetaNaoEncontradaException::new);

        Conta contaOrigem = contaRepository.findByIdAndUsuarioIdAndAtivoTrue(request.contaOrigemId(), usuario.getId())
                .orElseThrow(ContaNaoEncontradaException::new);

        if (contaOrigem.getSaldo().compareTo(request.valor()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo insuficiente na conta selecionada");
        }

        // Debitar da conta de origem
        contaOrigem.setSaldo(contaOrigem.getSaldo().subtract(request.valor()));
        contaRepository.save(contaOrigem);

        // Creditar no cofrinho
        meta.setValorAcumulado(meta.getValorAcumulado().add(request.valor()));
        metasRepository.save(meta);

        // Registrar transação (SAQUE com destino no cofrinho)
        Transacao transacao = new Transacao();
        transacao.setUsuario(usuario);
        transacao.setDescricao("Depósito no Cofrinho - " + meta.getNome());
        transacao.setValor(request.valor());
        transacao.setTipo(TipoTransacao.SAQUE);
        transacao.setContaOrigem(contaOrigem);
        transacao.setMetaDestino(meta);
        transacao.setData(LocalDate.now());
        transacao.setAtivo(true);
        transacao.setEstornada(false);
        transacaoRepository.save(transacao);

        return new MetaEconomiaResponse(meta);
    }

    @Transactional
    public MetaEconomiaResponse resgatar(UUID id, MetaEconomiaResgateRequest request) {
        Usuario usuario = getAuthenticatedUsuario();
        MetasEconomia meta = metasRepository.findByIdAndUsuarioIdAndAtivoTrue(id, usuario.getId())
                .orElseThrow(MetaNaoEncontradaException::new);

        if (meta.getValorAcumulado().compareTo(request.valor()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo insuficiente no cofrinho");
        }

        Conta contaDestino = contaRepository.findByIdAndUsuarioIdAndAtivoTrue(request.contaDestinoId(), usuario.getId())
                .orElseThrow(ContaNaoEncontradaException::new);

        // Debitar do cofrinho
        meta.setValorAcumulado(meta.getValorAcumulado().subtract(request.valor()));
        metasRepository.save(meta);

        // Creditar na conta de destino
        contaDestino.setSaldo(contaDestino.getSaldo().add(request.valor()));
        contaRepository.save(contaDestino);

        // Registrar transação (DEPOSITO com origem no cofrinho)
        Transacao transacao = new Transacao();
        transacao.setUsuario(usuario);
        transacao.setDescricao("Resgate do Cofrinho - " + meta.getNome());
        transacao.setValor(request.valor());
        transacao.setTipo(TipoTransacao.DEPOSITO);
        transacao.setContaDestino(contaDestino);
        transacao.setMetaOrigem(meta);
        transacao.setData(LocalDate.now());
        transacao.setAtivo(true);
        transacao.setEstornada(false);
        transacaoRepository.save(transacao);

        return new MetaEconomiaResponse(meta);
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