package com.financetracker.config;

import com.financetracker.transacao.entity.Fatura;
import com.financetracker.transacao.entity.Transacao;
import com.financetracker.transacao.enums.StatusFatura;
import com.financetracker.transacao.repository.FaturaRepository;
import com.financetracker.transacao.repository.TransacaoRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Executa migrações de dados na inicialização da aplicação.
 * Corrige inconsistências causadas por versões anteriores do código.
 */
@Component
public class DataMigrationComponent {

    private final FaturaRepository faturaRepository;
    private final TransacaoRepository transacaoRepository;

    public DataMigrationComponent(FaturaRepository faturaRepository,
                                   TransacaoRepository transacaoRepository) {
        this.faturaRepository = faturaRepository;
        this.transacaoRepository = transacaoRepository;
    }

    /**
     * Migração: corrige faturas ATRASADAS que foram marcadas com valorPago = valorTotal
     * artificialmente pelo bug da versão anterior.
     *
     * Antes: o rollover era feito setando valorPago = valorTotal para evitar dupla contagem.
     * Agora: usamos a flag rolladoOver para controlar o rollover sem alterar valorPago.
     *
     * Esta migration detecta faturas ATRASADAS que possuem uma transação de rollover
     * na próxima fatura e as marca como rolladoOver=true, restaurando o valorPago real.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrarFaturasAtrasadasComRolloverArtificial() {
        System.out.println("[DataMigration] Iniciando migração de faturas ATRASADAS com rollover artificial...");

        // Busca todas as faturas ATRASADAS ainda não migradas (rolladoOver = false)
        List<Fatura> todasFaturasAtrasadas = faturaRepository.findAll().stream()
                .filter(f -> f.getStatus() == StatusFatura.ATRASADA && !f.isRolladoOver())
                .toList();

        int migradas = 0;
        for (Fatura f : todasFaturasAtrasadas) {
            // Verifica se existe uma transação de rollover na próxima fatura
            String descricaoRollover = "Saldo restante não pago - Fatura "
                    + f.getMesReferencia().getMonthValue()
                    + "/" + f.getMesReferencia().getYear();

            LocalDate proximoMes = f.getMesReferencia().plusMonths(1);
            Optional<Fatura> proximaFaturaOpt = faturaRepository
                    .findByCartaoIdAndUsuarioIdAndMesReferencia(
                            f.getCartao().getId(), f.getUsuario().getId(), proximoMes);

            if (proximaFaturaOpt.isEmpty()) continue;

            Fatura proximaFatura = proximaFaturaOpt.get();
            List<Transacao> transacoesProxima = transacaoRepository.findByFaturaIdAndAtivoTrue(proximaFatura.getId());
            boolean temRollover = transacoesProxima.stream()
                    .anyMatch(t -> descricaoRollover.equals(t.getDescricao()));

            if (temRollover && f.getValorPago().compareTo(f.getValorTotal()) == 0) {
                // Esta fatura foi afetada pelo bug: valorPago foi setado = valorTotal artificialmente.
                // Restaura o valorPago real (0, pois nunca foi realmente paga) e marca rolladoOver=true.
                f.setValorPago(BigDecimal.ZERO);
                f.setRolladoOver(true);
                faturaRepository.save(f);
                migradas++;
                System.out.println("[DataMigration] Corrigida fatura ATRASADA: " + f.getId()
                        + " (cartão: " + f.getCartao().getNome()
                        + ", mês: " + f.getMesReferencia() + ")");
            }
        }

        System.out.println("[DataMigration] Migração concluída. Faturas corrigidas: " + migradas);
    }
}
