package com.boulangerie.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class FormatUtil {
    private FormatUtil() {}

    private static final DecimalFormat MONTANT_FMT;
    private static final DateTimeFormatter DATE_FMT    = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    static {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.FRENCH);
        sym.setGroupingSeparator(' ');
        sym.setDecimalSeparator(',');
        MONTANT_FMT = new DecimalFormat("#,##0.00", sym);
    }

    public static String montant(BigDecimal v) {
        if (v == null) return "0,00";
        return MONTANT_FMT.format(v);
    }

    public static String montant(double v) {
        return MONTANT_FMT.format(v);
    }

    public static String date(LocalDate d) {
        return d == null ? "" : d.format(DATE_FMT);
    }

    public static String dateHeure(LocalDateTime dt) {
        return dt == null ? "" : dt.format(DATETIME_FMT);
    }

    public static LocalDate parseDate(String s) {
        try { return LocalDate.parse(s.trim(), DATE_FMT); }
        catch (Exception e) { return null; }
    }
}
