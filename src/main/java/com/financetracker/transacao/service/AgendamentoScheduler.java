package com.financetracker.transacao.service;

import com.financetracker.conta.entity.Conta;
import com.financetracker.conta.repository.ContaRepository;
import com.financetracker.transacao.entity.AgendamentoTransacao;
import com.financetracker.transacao.entity.Transacao;
import com.financetracker.transacao.enums.Recorrencia;
import com.financetracker.transacao.enums.TipoTransacao;
import com.financetracker.transacao.exception.SaldoInsuficienteException;
import com.financetracker.transacao.repository.AgendamentoTransacaoRepository;
import com.financetracker.transacao.repository.TransacaoRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
public class AgendamentoScheduler {

    private final AgendamentoTransacaoRepository agendamentoRepository;
    private final TransacaoRepository transacaoRepository;
    private final ContaRepository contaRepository;

    public AgendamentoScheduler(AgendamentoTransacaoRepository agendamentoRepository,
                                TransacaoRepository transacaoRepository,
                                ContaRepository contaRepository) {
        this.agendamentoRepository = agendamentoRepository;
        this.transacaoRepository = transacaoRepository;
        this.contaRepository = contaRepository;
    }

    @Scheduled(cron = "0 30 6 * * ?") // Runs daily at 6:30 AM (after assinaturas)
    @Transactional
    public void processarAgendamentos() {
        LocalDate hoje = LocalDate.now();
        List<AgendamentoTransacao> agendamentos = agendamentoRepository
                .findByAtivoTrueAndDataProximaExecucaoLessThanEqual(hoje);

        for (AgendamentoTransacao a : agendamentos) {
            try {
                // Prevenir duplicidade: verificar se já foi gerada hoje
                boolean jaProcessado = transacaoRepository
                        .findByUsuarioIdAndAtivoTrueOrderByDataDesc(a.getUsuario().getId())
                        .stream()
                        .anyMatch(t -> t.getAgendamento() != null
                                && t.getAgendamento().getId().equals(a.getId())
                                && t.getData().equals(hoje));
                if (jaProcessado) continue;

                // Executar a transação agendada
                switch (a.getTipo()) {
                    case SAQUE, PIX -> {
                        if (a.getContaOrigem() != null) {
                            Conta origem = a.getContaOrigem();
                            if (origem.getSaldo().compareTo(a.getValor()) < 0) {
                                throw new SaldoInsuficienteException("Saldo insuficiente para execução de agendamento.");
                            }
                            origem.setSaldo(origem.getSaldo().subtract(a.getValor()));
                            contaRepository.save(origem);
                        }
                    }
                    case DEPOSITO -> {
                        if (a.getContaDestino() != null) {
                            Conta destino = a.getContaDestino();
                            destino.setSaldo(destino.getSaldo().add(a.getValor()));
                            contaRepository.save(destino);
                        }
                    }
                }

                Transacao transacao = new Transacao();
                transacao.setUsuario(a.getUsuario());
                transacao.setDescricao(a.getDescricao());
                transacao.setValor(a.getValor());
                transacao.setTipo(a.getTipo());
                transacao.setContaOrigem(a.getContaOrigem());
                transacao.setContaDestino(a.getContaDestino());
                transacao.setCategoria(a.getCategoria());
                transacao.setData(hoje);
                transacao.setAgendamento(a);
                transacao.setAtivo(true);

                transacaoRepository.save(transacao);

                // Avançar próxima execução
                a.setDataProximaExecucao(calcularProximaData(a));
                agendamentoRepository.save(a);

            } catch (Exception e) {
                System.err.println("Erro ao processar agendamento " + a.getId() + ": " + e.getMessage());
            }
        }
    }

    private LocalDate calcularProximaData(AgendamentoTransacao a) {
        LocalDate atual = a.getDataProximaExecucao();
        LocalDate proxima = switch (a.getRecorrencia()) {
            case SEMANAL -> atual.plusWeeks(1);
            case QUINZENAL -> atual.plusDays(15);
            case MENSAL -> atual.plusMonths(1);
        };
        int maxDia = proxima.lengthOfMonth();
        int dia = Math.min(a.getDiaExecucao(), maxDia);
        return proxima.withDayOfMonth(dia);
    }
}