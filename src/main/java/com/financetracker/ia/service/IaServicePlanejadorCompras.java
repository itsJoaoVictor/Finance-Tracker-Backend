package com.financetracker.ia.service;

import com.financetracker.cartao.entity.Cartao;
import com.financetracker.cartao.repository.CartaoRepository;
import com.financetracker.ia.dto.AnaliseCartaoSimulacaoDTO;
import com.financetracker.ia.dto.MesSimulacaoDTO;
import com.financetracker.ia.dto.ProjecaoCartoesResponse;
import com.financetracker.ia.dto.SimulacaoCompraRequest;
import com.financetracker.ia.dto.SimulacaoCompraResponse;
import com.financetracker.transacao.repository.FaturaRepository;
import com.financetracker.transacao.repository.TransacaoRepository;
import com.financetracker.usuario.entity.Usuario;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class IaServicePlanejadorCompras {

    private final TransacaoRepository transacaoRepository;
    private final FaturaRepository faturaRepository;
    private final CartaoRepository cartaoRepository;
    private final IaServiceCartao iaServiceCartao;

    public IaServicePlanejadorCompras(TransacaoRepository transacaoRepository, 
                                      FaturaRepository faturaRepository, 
                                      CartaoRepository cartaoRepository,
                                      IaServiceCartao iaServiceCartao) {
        this.transacaoRepository = transacaoRepository;
        this.faturaRepository = faturaRepository;
        this.cartaoRepository = cartaoRepository;
        this.iaServiceCartao = iaServiceCartao;
    }

    public SimulacaoCompraResponse simularCompra(Usuario usuario, SimulacaoCompraRequest request) {
        LocalDate hoje = LocalDate.now();

        BigDecimal medianaReceita = calcularMedianaReceita(usuario, hoje);
        BigDecimal medianaDespesaBase = calcularMedianaDespesaBase(usuario, hoje);

        if (request.parcelas() == null || request.parcelas() <= 0) {
            return calcularMelhorParcelamento(usuario, request, medianaReceita, medianaDespesaBase, hoje);
        } else {
            return rodarSimulacao(usuario, request, request.parcelas(), medianaReceita, medianaDespesaBase, hoje, false);
        }
    }

    private SimulacaoCompraResponse calcularMelhorParcelamento(Usuario usuario, SimulacaoCompraRequest request, BigDecimal medianaReceita, BigDecimal medianaDespesaBase, LocalDate hoje) {
        SimulacaoCompraResponse melhorAmarelo = null;

        for (int p = 1; p <= 12; p++) {
            SimulacaoCompraResponse res = rodarSimulacao(usuario, request, p, medianaReceita, medianaDespesaBase, hoje, true);
            
            boolean temVermelho = res.simulacoesMesAMes().stream().anyMatch(m -> "VERMELHO".equals(m.status()));
            boolean temAmarelo = res.simulacoesMesAMes().stream().anyMatch(m -> "AMARELO".equals(m.status()));

            if (!temVermelho && !temAmarelo) {
                return new SimulacaoCompraResponse(res.viavel(), res.mesRecomendadoParaCompra(), p, 
                    "✅ A IA calculou que parcelar em " + p + "x é a opção ideal para você manter a saúde financeira no verde.", res.simulacoesMesAMes(), res.analiseCartao());
            }

            if (!temVermelho && melhorAmarelo == null) {
                melhorAmarelo = new SimulacaoCompraResponse(res.viavel(), res.mesRecomendadoParaCompra(), p, 
                    "⚠️ O mínimo sugerido para não entrar no vermelho é parcelar em " + p + "x. E mesmo assim seu orçamento ficará no limite.", res.simulacoesMesAMes(), res.analiseCartao());
            }
        }

        if (melhorAmarelo != null) return melhorAmarelo;
        
        // Se testou até 12x e tudo dá vermelho, a compra é realmente inviável
        SimulacaoCompraResponse fallback = rodarSimulacao(usuario, request, 12, medianaReceita, medianaDespesaBase, hoje, true);
        return new SimulacaoCompraResponse(false, null, null, 
            "🛑 Compra Totalmente Inviável! O valor compromete excessivamente sua renda. Mesmo simulando o máximo de 12x, seu orçamento ainda ficaria negativo.", 
            fallback.simulacoesMesAMes(), fallback.analiseCartao());
    }

    private SimulacaoCompraResponse rodarSimulacao(Usuario usuario, SimulacaoCompraRequest request, int parcelas, BigDecimal medianaReceita, BigDecimal medianaDespesaBase, LocalDate hoje, boolean modoAuto) {
        List<MesSimulacaoDTO> simulacoes = new ArrayList<>();
        boolean viavelAgora = true;
        String mesRecomendado = null;
        boolean temVermelho = false;
        boolean temAmarelo = false;

        Cartao cartao = null;
        if (request.cartaoId() != null) {
            cartao = cartaoRepository.findByIdAndUsuarioIdAndAtivoTrue(request.cartaoId(), usuario.getId()).orElse(null);
        }
        AnaliseCartaoSimulacaoDTO analiseCartao = analisarCartao(cartao, request, hoje);
        if (analiseCartao != null && !analiseCartao.limiteAprovado()) {
            viavelAgora = false;
        }

        BigDecimal valorParcela = request.valorTotal().divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.HALF_EVEN);
        BigDecimal margemSeguranca = medianaReceita.multiply(BigDecimal.valueOf(0.10)); // 10%

        int mesesParaProjetar = Math.max(6, Math.min(parcelas, 12));

        ProjecaoCartoesResponse projCartoes = null;
        try {
            projCartoes = iaServiceCartao.projetarFaturasParaUsuario(usuario);
        } catch (Exception ignored) {}

        BigDecimal somaProjecoesMesAtual = BigDecimal.ZERO;
        BigDecimal somaMediasHistoricas = BigDecimal.ZERO;
        BigDecimal projecaoFechamentoCartao = null;
        BigDecimal mediaHistoricaCartao = null;
        if (projCartoes != null && projCartoes.projecoes() != null) {
            for (var p : projCartoes.projecoes()) {
                if (p.projecaoFechamento() != null) {
                    somaProjecoesMesAtual = somaProjecoesMesAtual.add(p.projecaoFechamento());
                }
                if (p.mediaHistorica() != null) {
                    somaMediasHistoricas = somaMediasHistoricas.add(p.mediaHistorica());
                }
                if (cartao != null && p.cartaoId().equals(cartao.getId())) {
                    projecaoFechamentoCartao = p.projecaoFechamento();
                    mediaHistoricaCartao = p.mediaHistorica();
                }
            }
        }

        for (int i = 0; i < mesesParaProjetar; i++) {
            LocalDate mesAtual = hoje.plusMonths(i);
            LocalDate mesReferenciaFatura = mesAtual.withDayOfMonth(1);
            
            BigDecimal faturasFixas = faturaRepository.sumValorTotalByUsuarioAndMesReferencia(usuario.getId(), mesReferenciaFatura);
            BigDecimal faturasProjetadas;
            if (i == 0 && somaProjecoesMesAtual.compareTo(BigDecimal.ZERO) > 0) {
                faturasProjetadas = somaProjecoesMesAtual;
            } else if (i > 0 && somaMediasHistoricas.compareTo(BigDecimal.ZERO) > 0) {
                faturasProjetadas = faturasFixas.max(somaMediasHistoricas);
            } else {
                faturasProjetadas = faturasFixas;
            }

            BigDecimal faturasProjetadasCartao = null;
            if (cartao != null) {
                BigDecimal faturaFixaCartao = faturaRepository.findByCartaoIdAndUsuarioIdAndMesReferencia(cartao.getId(), usuario.getId(), mesReferenciaFatura)
                        .map(f -> f.getValorTotal().subtract(f.getValorPago()))
                        .orElse(BigDecimal.ZERO);
                if (i == 0 && projecaoFechamentoCartao != null && projecaoFechamentoCartao.compareTo(BigDecimal.ZERO) > 0) {
                    faturasProjetadasCartao = projecaoFechamentoCartao;
                } else if (i > 0 && mediaHistoricaCartao != null && mediaHistoricaCartao.compareTo(BigDecimal.ZERO) > 0) {
                    faturasProjetadasCartao = faturaFixaCartao.max(mediaHistoricaCartao);
                } else {
                    faturasProjetadasCartao = faturaFixaCartao;
                }
            }

            BigDecimal parcelaDaCompra = (i < parcelas) ? valorParcela : BigDecimal.ZERO;

            BigDecimal saldoLivre = medianaReceita.subtract(medianaDespesaBase).subtract(faturasProjetadas).subtract(parcelaDaCompra);
            
            String status = "VERDE";
            if (saldoLivre.compareTo(BigDecimal.ZERO) < 0) {
                status = "VERMELHO";
                if (i < 3) temVermelho = true;
                viavelAgora = false;
            } else if (saldoLivre.compareTo(margemSeguranca) < 0) {
                status = "AMARELO";
                if (i < 3) temAmarelo = true;
            }

            BigDecimal limiteRestanteCartao = null;
            if (cartao != null && cartao.getLimiteDisponivel() != null) {
                int parcelasPagas = Math.min(i, parcelas);
                BigDecimal limiteLiberado = valorParcela.multiply(BigDecimal.valueOf(parcelasPagas));
                limiteRestanteCartao = cartao.getLimiteDisponivel().subtract(request.valorTotal()).add(limiteLiberado);
            }

            String mesAno = mesAtual.format(DateTimeFormatter.ofPattern("MMMM/yyyy", new Locale("pt", "BR")));
            mesAno = mesAno.substring(0, 1).toUpperCase() + mesAno.substring(1);

            simulacoes.add(new MesSimulacaoDTO(
                    mesAno,
                    medianaReceita,
                    medianaDespesaBase,
                    faturasProjetadas,
                    faturasProjetadasCartao,
                    parcelaDaCompra,
                    saldoLivre,
                    status,
                    limiteRestanteCartao
            ));
        }

        String mensagem = "";
        if (!modoAuto) {
            mensagem = gerarMensagem(temVermelho, temAmarelo, request.nomeItem(), parcelas, analiseCartao);
        }
        
        if (!viavelAgora) {
            for (int i = 0; i < simulacoes.size(); i++) {
                if (!simulacoes.get(i).status().equals("VERMELHO")) {
                    mesRecomendado = simulacoes.get(i).mesAno();
                    break;
                }
            }
        } else {
            mesRecomendado = simulacoes.get(0).mesAno();
        }

        return new SimulacaoCompraResponse(viavelAgora, mesRecomendado, modoAuto ? parcelas : request.parcelas(), mensagem, simulacoes, analiseCartao);
    }

    private AnaliseCartaoSimulacaoDTO analisarCartao(Cartao cartao, SimulacaoCompraRequest request, LocalDate hoje) {
        if (cartao == null) return null;

        BigDecimal limiteDisponivelAtual = cartao.getLimiteDisponivel() != null ? cartao.getLimiteDisponivel() : BigDecimal.ZERO;
        BigDecimal limiteAposCompra = limiteDisponivelAtual.subtract(request.valorTotal());
        boolean limiteAprovado = limiteAposCompra.compareTo(BigDecimal.ZERO) >= 0;

        int diaFechamento = cartao.getDiaFechamento();
        int diaVencimento = cartao.getDiaVencimento();

        LocalDate dataCorteMesAtual = hoje.withDayOfMonth(Math.min(diaFechamento, hoje.lengthOfMonth()));
        LocalDate melhorDiaData;
        if (hoje.isAfter(dataCorteMesAtual) || hoje.isEqual(dataCorteMesAtual)) {
            melhorDiaData = hoje;
        } else {
            melhorDiaData = dataCorteMesAtual.plusDays(1);
        }

        int carencia = (diaVencimento < diaFechamento) ? (30 - diaFechamento + diaVencimento) : (diaVencimento - diaFechamento);
        int diasGanhoFolego = 30 + carencia;
        String melhorDiaStr = melhorDiaData.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        StringBuilder rec = new StringBuilder();
        if (!limiteAprovado) {
            rec.append("🛑 Compra Recusada pelo Limite! O cartão ").append(cartao.getNome())
               .append(" possui apenas ").append(formatarMoeda(limiteDisponivelAtual))
               .append(" disponíveis, o que não cobre o valor de ").append(formatarMoeda(request.valorTotal()))
               .append(". Recomendamos utilizar outro cartão ou aguardar liberação de limite.");
        } else {
            rec.append("💳 Limite Aprovado no ").append(cartao.getNome()).append("! ");
            if (!melhorDiaData.isEqual(hoje) && melhorDiaData.isAfter(hoje)) {
                long diasEspera = ChronoUnit.DAYS.between(hoje, melhorDiaData);
                rec.append("💡 Dica Smart Timing: O corte deste cartão acontece dia ").append(String.format("%02d", diaFechamento))
                   .append(". Se você aguardar ").append(diasEspera).append(" dia(s) e comprar em ")
                   .append(melhorDiaStr).append(", a primeira parcela só cairá na fatura do mês seguinte, garantindo até ")
                   .append(diasGanhoFolego).append(" dias de fôlego para o seu bolso!");
            } else {
                rec.append("🗓️ Timing Perfeito! O corte do ").append(cartao.getNome())
                   .append(" já passou este mês (dia ").append(String.format("%02d", diaFechamento))
                   .append("). Comprando hoje, você já entra no novo ciclo e ganha até ")
                   .append(diasGanhoFolego).append(" dias para começar a pagar!");
            }
        }

        return new AnaliseCartaoSimulacaoDTO(
                cartao.getId(),
                cartao.getNome(),
                limiteAprovado,
                limiteDisponivelAtual,
                limiteAposCompra,
                melhorDiaStr,
                diasGanhoFolego,
                rec.toString()
        );
    }

    private String formatarMoeda(BigDecimal valor) {
        if (valor == null) return "R$ 0,00";
        return String.format(new Locale("pt", "BR"), "R$ %.2f", valor);
    }

    private BigDecimal calcularMedianaReceita(Usuario usuario, LocalDate hoje) {
        BigDecimal[] ultimosMeses = new BigDecimal[3];
        for (int i = 1; i <= 3; i++) {
            LocalDate inicio = hoje.minusMonths(i).withDayOfMonth(1);
            LocalDate fim = hoje.minusMonths(i).withDayOfMonth(hoje.minusMonths(i).lengthOfMonth());
            ultimosMeses[i - 1] = transacaoRepository.sumReceitasValidasPorPeriodo(usuario.getId(), inicio, fim);
        }
        Arrays.sort(ultimosMeses);
        BigDecimal mediana = ultimosMeses[1];
        if (mediana.compareTo(BigDecimal.ZERO) == 0) {
            return transacaoRepository.sumReceitasValidasPorPeriodo(usuario.getId(), hoje.withDayOfMonth(1), hoje.withDayOfMonth(hoje.lengthOfMonth()));
        }
        return mediana;
    }

    private BigDecimal calcularMedianaDespesaBase(Usuario usuario, LocalDate hoje) {
        BigDecimal[] ultimosMeses = new BigDecimal[3];
        for (int i = 1; i <= 3; i++) {
            LocalDate inicio = hoje.minusMonths(i).withDayOfMonth(1);
            LocalDate fim = hoje.minusMonths(i).withDayOfMonth(hoje.minusMonths(i).lengthOfMonth());
            ultimosMeses[i - 1] = transacaoRepository.sumDespesasBasicasPorPeriodo(usuario.getId(), inicio, fim);
        }
        Arrays.sort(ultimosMeses);
        BigDecimal mediana = ultimosMeses[1];
        if (mediana.compareTo(BigDecimal.ZERO) == 0) {
            return transacaoRepository.sumDespesasBasicasPorPeriodo(usuario.getId(), hoje.withDayOfMonth(1), hoje.withDayOfMonth(hoje.lengthOfMonth()));
        }
        return mediana;
    }

    private String gerarMensagem(boolean temVermelho, boolean temAmarelo, String item, int parcelas, AnaliseCartaoSimulacaoDTO analiseCartao) {
        if (analiseCartao != null && !analiseCartao.limiteAprovado()) {
            return analiseCartao.recomendacaoIa();
        }
        String msgBase;
        if (temVermelho) {
            msgBase = "🛑 É melhor segurar essa compra!\n\nSe você comprar a " + item + " agora em " + parcelas + "x, seu orçamento em alguns meses ficará negativo devido às faturas que ainda estão ativas. Recomendamos aguardar a liberação de limite ou aumento de renda.";
        } else if (temAmarelo) {
            msgBase = "⚠️ Sinal Amarelo: O orçamento vai ficar apertado.\n\nVocê pode comprar agora, mas sua margem livre vai cair para menos de 10% da sua renda. Um imprevisto pode te deixar no vermelho.";
        } else {
            msgBase = "✅ Sinal Verde! O momento é excelente.\n\nSuas projeções mostram que você terá uma margem livre saudável. Adicionar essa parcela não estrangulará o seu orçamento.";
        }
        if (analiseCartao != null && analiseCartao.recomendacaoIa() != null && !analiseCartao.recomendacaoIa().isEmpty()) {
            return msgBase + "\n\n" + analiseCartao.recomendacaoIa();
        }
        return msgBase;
    }
}
