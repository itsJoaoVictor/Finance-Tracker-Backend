package com.financetracker.transacao.service;

import com.financetracker.transacao.dto.AgendamentoCriacaoRequest;
import com.financetracker.transacao.dto.AgendamentoResponse;
import com.financetracker.transacao.entity.AgendamentoTransacao;
import com.financetracker.transacao.exception.AgendamentoNaoEncontradoException;
import com.financetracker.transacao.repository.AgendamentoTransacaoRepository;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import com.financetracker.categoria.entity.Categoria;
import com.financetracker.categoria.exception.CategoriaNaoEncontradaException;
import com.financetracker.categoria.repository.CategoriaRepository;
import com.financetracker.conta.entity.Conta;
import com.financetracker.conta.exception.ContaNaoEncontradaException;
import com.financetracker.conta.repository.ContaRepository;
import com.financetracker.transacao.enums.Recorrencia;
import com.financetracker.transacao.enums.TipoTransacao;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class AgendamentoService {

    private final AgendamentoTransacaoRepository agendamentoRepository;
    private final ContaRepository contaRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;

    public AgendamentoService(AgendamentoTransacaoRepository agendamentoRepository,
                              ContaRepository contaRepository,
                              CategoriaRepository categoriaRepository,
                              UsuarioRepository usuarioRepository) {
        this.agendamentoRepository = agendamentoRepository;
        this.contaRepository = contaRepository;
        this.categoriaRepository = categoriaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    private Usuario getAuthenticatedUsuario() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autenticado"));
    }

    @Transactional
    public AgendamentoResponse criar(AgendamentoCriacaoRequest request) {
        Usuario usuario = getAuthenticatedUsuario();

        TipoTransacao tipo = TipoTransacao.valueOf(request.tipo());
        if (tipo != TipoTransacao.DEPOSITO && tipo != TipoTransacao.SAQUE && tipo != TipoTransacao.PIX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo inválido para agendamento. Use DEPOSITO, SAQUE ou PIX.");
        }

        Conta contaOrigem = null;
        if (request.contaOrigemId() != null) {
            contaOrigem = contaRepository.findByIdAndUsuarioIdAndAtivoTrue(request.contaOrigemId(), usuario.getId())
                    .orElseThrow(() -> new ContaNaoEncontradaException());
        }
        Conta contaDestino = null;
        if (request.contaDestinoId() != null) {
            contaDestino = contaRepository.findByIdAndUsuarioIdAndAtivoTrue(request.contaDestinoId(), usuario.getId())
                    .orElseThrow(() -> new ContaNaoEncontradaException());
        }

        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(CategoriaNaoEncontradaException::new);
        if (categoria.getUsuario() != null && !categoria.getUsuario().getId().equals(usuario.getId())) {
            throw new CategoriaNaoEncontradaException();
        }

        AgendamentoTransacao agendamento = new AgendamentoTransacao();
        agendamento.setUsuario(usuario);
        agendamento.setDescricao(request.descricao());
        agendamento.setValor(request.valor());
        agendamento.setTipo(tipo);
        agendamento.setContaOrigem(contaOrigem);
        agendamento.setContaDestino(contaDestino);
        agendamento.setCategoria(categoria);
        agendamento.setRecorrencia(Recorrencia.valueOf(request.recorrencia()));
        agendamento.setDiaExecucao(request.diaExecucao());
        agendamento.setDataInicio(request.dataInicio());
        agendamento.setDataProximaExecucao(calcularProximaData(request.dataInicio(), request.diaExecucao()));
        agendamento.setAtivo(true);

        return new AgendamentoResponse(agendamentoRepository.save(agendamento));
    }

    @Transactional(readOnly = true)
    public List<AgendamentoResponse> listar() {
        Usuario usuario = getAuthenticatedUsuario();
        return agendamentoRepository.findByUsuarioIdAndAtivoTrue(usuario.getId())
                .stream().map(AgendamentoResponse::new).toList();
    }

    private LocalDate calcularProximaData(LocalDate dataInicio, int diaExecucao) {
        int maxDia = dataInicio.lengthOfMonth();
        int dia = Math.min(diaExecucao, maxDia);
        LocalDate data = dataInicio.withDayOfMonth(dia);
        if (data.isBefore(dataInicio) || data.isEqual(dataInicio)) {
            // If the configured day is the same as or before today, start from next month
            data = data.plusMonths(1);
            int maxDiaProx = data.lengthOfMonth();
            data = data.withDayOfMonth(Math.min(diaExecucao, maxDiaProx));
        }
        return data;
    }

    @Transactional
    public void excluir(UUID id) {
        Usuario usuario = getAuthenticatedUsuario();
        AgendamentoTransacao agendamento = agendamentoRepository
                .findByIdAndUsuarioIdAndAtivoTrue(id, usuario.getId())
                .orElseThrow(AgendamentoNaoEncontradoException::new);
        agendamento.setAtivo(false);
        agendamentoRepository.save(agendamento);
    }
}