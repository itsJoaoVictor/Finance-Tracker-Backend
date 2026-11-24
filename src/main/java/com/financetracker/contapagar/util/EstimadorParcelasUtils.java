package com.financetracker.contapagar.util;

import com.financetracker.contapagar.enums.ModoParcelamento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Modelo matemático determinístico para recalibração de parcelas futuras estimadas
 * a partir do histórico real de boletos confirmados/pagos.
 */
public final class EstimadorParcelasUtils {

    private static final BigDecimal EWMA_ALPHA = new BigDecimal("0.65");
    private static final BigDecimal EWMA_ONE_MINUS_ALPHA = new BigDecimal("0.35");
    private static final BigDecimal PESO_RITMO_HISTORICO = new BigDecimal("0.70");
    private static final BigDecimal PESO_RITMO_PLANEJADO = new BigDecimal("0.30");
    private static final int JANELA_EWMA = 6;
    private static final int JANELA_TENDENCIA = 4;

    private EstimadorParcelasUtils() {}

    /**
     * Projeta os valores estimados para as próximas {@code quantidadeFuturas} parcelas
     * ainda em aberto ({@code AGUARDANDO_VALOR}), com base no modo do contrato e no
     * histórico cronológico de valores reais já confirmados ou pagos.
     */
    public static List<BigDecimal> projetarParcelasFuturas(
            ModoParcelamento modo,
            BigDecimal valorInicialModelo,
            BigDecimal valorUltimaModelo,
            List<BigDecimal> historicoCronologico,
            int quantidadeFuturas) {

        List<BigDecimal> resultado = new ArrayList<>(Math.max(0, quantidadeFuturas));
        if (quantidadeFuturas <= 0 || historicoCronologico == null || historicoCronologico.isEmpty()) {
            return resultado;
        }

        if (modo == ModoParcelamento.PROGRESSIVO && valorUltimaModelo != null && valorUltimaModelo.compareTo(BigDecimal.ZERO) > 0) {
            boolean crescente = valorInicialModelo != null && valorUltimaModelo.compareTo(valorInicialModelo) > 0;
            boolean decrescente = valorInicialModelo != null && valorUltimaModelo.compareTo(valorInicialModelo) < 0;

            if (crescente) {
                return projetarCrescenteComTeto(valorUltimaModelo, historicoCronologico, quantidadeFuturas);
            }
            if (decrescente) {
                return projetarDecrescenteReancorado(valorUltimaModelo, historicoCronologico, quantidadeFuturas);
            }
        }

        // FIXO (ou PROGRESSIVO plano): suavização exponencial (EWMA)
        BigDecimal ewma = calcularEwma(valorInicialModelo, historicoCronologico);
        for (int i = 0; i < quantidadeFuturas; i++) {
            resultado.add(ewma);
        }
        return resultado;
    }

    /**
     * Suavização Exponencial (EWMA, alpha = 0.65) sobre os últimos valores reais.
     * Ideal para contratos Price / MCMV com oscilação leve de TR e seguro MIP.
     */
    public static BigDecimal calcularEwma(BigDecimal valorBaseInicial, List<BigDecimal> historicoCronologico) {
        int inicio = Math.max(0, historicoCronologico.size() - JANELA_EWMA);
        List<BigDecimal> janela = historicoCronologico.subList(inicio, historicoCronologico.size());

        BigDecimal estimativa = (valorBaseInicial != null && valorBaseInicial.compareTo(BigDecimal.ZERO) > 0)
                ? valorBaseInicial
                : janela.get(0);

        for (BigDecimal observado : janela) {
            estimativa = observado.multiply(EWMA_ALPHA)
                    .add(estimativa.multiply(EWMA_ONE_MINUS_ALPHA));
        }
        return estimativa.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * SAC / Decrescente: re-ancora a curva linear no último valor real confirmado/pago
     * até o alvo final (sem jamais inverter para cima caso o valor real já tenha ficado abaixo).
     */
    private static List<BigDecimal> projetarDecrescenteReancorado(
            BigDecimal valorUltimaModelo,
            List<BigDecimal> historicoCronologico,
            int quantidadeFuturas) {

        BigDecimal ultimoReal = historicoCronologico.get(historicoCronologico.size() - 1);
        BigDecimal alvoFinal = valorUltimaModelo.min(ultimoReal);
        BigDecimal diferenca = alvoFinal.subtract(ultimoReal); // <= 0

        List<BigDecimal> resultado = new ArrayList<>(quantidadeFuturas);
        for (int j = 0; j < quantidadeFuturas; j++) {
            BigDecimal proporcao = BigDecimal.valueOf(j + 1)
                    .divide(BigDecimal.valueOf(quantidadeFuturas), 10, RoundingMode.HALF_UP);
            BigDecimal projetado = ultimoReal.add(diferenca.multiply(proporcao))
                    .setScale(2, RoundingMode.HALF_UP);
            resultado.add(projetado);
        }
        return resultado;
    }

    /**
     * Taxa de Obra / Crescente: combina o ritmo real ponderado dos últimos boletos (70%)
     * com o ritmo planejado restante (30%), limitado ao teto efetivo para não extrapolar.
     */
    private static List<BigDecimal> projetarCrescenteComTeto(
            BigDecimal valorTetoModelo,
            List<BigDecimal> historicoCronologico,
            int quantidadeFuturas) {

        BigDecimal ultimoReal = historicoCronologico.get(historicoCronologico.size() - 1);
        BigDecimal tetoEfetivo = valorTetoModelo.max(ultimoReal);
        BigDecimal restanteAteTeto = tetoEfetivo.subtract(ultimoReal).max(BigDecimal.ZERO);

        BigDecimal passoEfetivo;
        if (historicoCronologico.size() == 1) {
            passoEfetivo = restanteAteTeto.divide(BigDecimal.valueOf(quantidadeFuturas), 10, RoundingMode.HALF_UP);
        } else {
            int inicio = Math.max(0, historicoCronologico.size() - JANELA_TENDENCIA);
            List<BigDecimal> janela = historicoCronologico.subList(inicio, historicoCronologico.size());

            BigDecimal somaPonderada = BigDecimal.ZERO;
            int somaPesos = 0;
            for (int i = 1; i < janela.size(); i++) {
                BigDecimal delta = janela.get(i).subtract(janela.get(i - 1));
                int peso = i; // passos mais recentes recebem peso maior (1, 2, 3...)
                somaPonderada = somaPonderada.add(delta.multiply(BigDecimal.valueOf(peso)));
                somaPesos += peso;
            }

            BigDecimal passoHistorico = somaPesos > 0
                    ? somaPonderada.divide(BigDecimal.valueOf(somaPesos), 10, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal passoHistoricoPositivo = passoHistorico.max(BigDecimal.ZERO);
            BigDecimal passoPlanejado = restanteAteTeto.divide(BigDecimal.valueOf(quantidadeFuturas), 10, RoundingMode.HALF_UP);

            passoEfetivo = passoHistoricoPositivo.multiply(PESO_RITMO_HISTORICO)
                    .add(passoPlanejado.multiply(PESO_RITMO_PLANEJADO));
        }

        List<BigDecimal> resultado = new ArrayList<>(quantidadeFuturas);
        for (int j = 0; j < quantidadeFuturas; j++) {
            BigDecimal projetado = ultimoReal.add(passoEfetivo.multiply(BigDecimal.valueOf(j + 1)));
            if (projetado.compareTo(tetoEfetivo) > 0) {
                projetado = tetoEfetivo;
            }
            resultado.add(projetado.setScale(2, RoundingMode.HALF_UP));
        }
        return resultado;
    }
}
