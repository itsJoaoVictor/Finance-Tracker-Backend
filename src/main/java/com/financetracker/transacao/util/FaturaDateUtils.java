package com.financetracker.transacao.util;

import com.financetracker.contapagar.util.DiaUtilUtils;

import java.time.LocalDate;

public final class FaturaDateUtils {

    private FaturaDateUtils() {}

    public static LocalDate calcularDataFechamento(LocalDate mesReferencia, int diaFechamento) {
        int dia = Math.min(Math.max(1, diaFechamento), mesReferencia.lengthOfMonth());
        return mesReferencia.withDayOfMonth(dia);
    }

    /**
     * Calcula a data de vencimento original baseada apenas no dia configurado no cartão
     * (sem prorrogação de dia útil).
     */
    public static LocalDate calcularDataVencimentoPadrao(LocalDate mesReferencia, LocalDate dataFechamento, int diaVencimento) {
        int diaNoMes = Math.min(Math.max(1, diaVencimento), mesReferencia.lengthOfMonth());
        LocalDate dataVencimento = mesReferencia.withDayOfMonth(diaNoMes);
        if (dataVencimento.isBefore(dataFechamento)) {
            LocalDate proximoMes = mesReferencia.plusMonths(1);
            int diaProximoMes = Math.min(Math.max(1, diaVencimento), proximoMes.lengthOfMonth());
            dataVencimento = proximoMes.withDayOfMonth(diaProximoMes);
        }
        return dataVencimento;
    }

    /**
     * Calcula a data de vencimento inicial de uma nova fatura, prorrogando automaticamente
     * para o próximo dia útil quando cair em fim de semana ou feriado nacional.
     */
    public static LocalDate calcularDataVencimentoInicial(LocalDate mesReferencia, LocalDate dataFechamento, int diaVencimento) {
        LocalDate padrao = calcularDataVencimentoPadrao(mesReferencia, dataFechamento, diaVencimento);
        return DiaUtilUtils.proximoDiaUtil(padrao);
    }
}
