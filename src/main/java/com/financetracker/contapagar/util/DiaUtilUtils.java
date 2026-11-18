package com.financetracker.contapagar.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

public final class DiaUtilUtils {

    public static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");

    private DiaUtilUtils() {}

    public static LocalDate hoje() {
        return LocalDate.now(ZONE_BR);
    }

    public static LocalDate calcularDataVencimento(LocalDate mesReferencia, int diaVencimento) {
        int maxDia = mesReferencia.lengthOfMonth();
        int dia = Math.min(Math.max(1, diaVencimento), maxDia);
        return proximoDiaUtil(mesReferencia.withDayOfMonth(dia));
    }

    public static LocalDate proximoDiaUtil(LocalDate data) {
        LocalDate d = data;
        while (!isDiaUtil(d)) {
            d = d.plusDays(1);
        }
        return d;
    }

    public static boolean isDiaUtil(LocalDate data) {
        DayOfWeek dow = data.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        return !isFeriadoNacional(data);
    }

    public static boolean isFeriadoNacional(LocalDate data) {
        int ano = data.getYear();
        Set<LocalDate> fixos = Set.of(
                LocalDate.of(ano, 1, 1),   // Confraternização Universal
                LocalDate.of(ano, 4, 21),  // Tiradentes
                LocalDate.of(ano, 5, 1),   // Dia do Trabalho
                LocalDate.of(ano, 9, 7),   // Independência
                LocalDate.of(ano, 10, 12), // Nossa Sra. Aparecida
                LocalDate.of(ano, 11, 2),  // Finados
                LocalDate.of(ano, 11, 15), // Proclamação da República
                LocalDate.of(ano, 11, 20), // Consciência Negra
                LocalDate.of(ano, 12, 25)  // Natal
        );
        if (fixos.contains(data)) return true;

        LocalDate pascoa = calcularPascoa(ano);
        LocalDate carnavalSeg = pascoa.minusDays(48);
        LocalDate carnavalTer = pascoa.minusDays(47);
        LocalDate sextaSanta = pascoa.minusDays(2);
        LocalDate corpusChristi = pascoa.plusDays(60);

        return data.equals(carnavalSeg)
                || data.equals(carnavalTer)
                || data.equals(sextaSanta)
                || data.equals(corpusChristi);
    }

    private static LocalDate calcularPascoa(int ano) {
        int a = ano % 19;
        int b = ano / 100;
        int c = ano % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(ano, mes, dia);
    }
}
