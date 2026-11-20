package fr.retraiteplus.controller;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Mise en forme française utilisée dans les vues : ${@fmt.eur(x)}, ${@fmt.date(d)}… */
@Component("fmt")
public class Formats {
    private static final DecimalFormatSymbols FR = DecimalFormatSymbols.getInstance(Locale.FRANCE);
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public String eur(BigDecimal v) { return v == null ? "—" : nombre(v, "#,##0.00") + " €"; }
    public String eur0(BigDecimal v) { return v == null ? "—" : nombre(v, "#,##0") + " €"; }
    public String num(BigDecimal v) { return v == null ? "—" : nombre(v, "#,##0.##"); }
    public String pct(BigDecimal v) { return v == null ? "—" : nombre(v, "0.###") + " %"; }
    public String date(LocalDate d) { return d == null ? "—" : d.format(D); }
    /** Valeur brute pour les graphiques (point décimal). */
    public String js(BigDecimal v) { return v == null ? "0" : v.toPlainString(); }

    private String nombre(BigDecimal v, String motif) {
        return new DecimalFormat(motif, FR).format(v).replace(' ', ' ').replace(' ', ' ');
    }
}
