package fr.retraiteplus.engine;

import fr.retraiteplus.engine.LigneCarriere.Devise;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Lit une carrière saisie au format texte (copier-coller depuis Excel ou fichier CSV).
 * <pre>
 * annee;type;revenu;devise;trimestres;points
 * 1999;SALAIRE;173640;FRF;4;231.78
 * 1983;SERVICE_NATIONAL;;;3;
 * 2025;SALAIRE;5111;EUR;;
 * </pre>
 * Séparateur « ; » ou tabulation. Les lignes vides et celles commençant par # sont ignorées,
 * ainsi qu'une éventuelle ligne d'en-tête. La virgule décimale est acceptée.
 */
public final class CarriereParser {

    private CarriereParser() {}

    public static Resultat lire(String texte) {
        List<LigneCarriere> lignes = new ArrayList<>();
        List<String> erreurs = new ArrayList<>();
        if (texte == null) return new Resultat(lignes, erreurs);
        String[] brut = texte.split("\\R");
        for (int i = 0; i < brut.length; i++) {
            String l = brut[i].trim();
            if (l.isEmpty() || l.startsWith("#")) continue;
            if (l.toLowerCase().startsWith("annee") || l.toLowerCase().startsWith("année")) continue;
            String[] c = l.split("[;\\t]", -1);
            try {
                int annee = Integer.parseInt(c[0].trim());
                if (annee < 1930 || annee > 2100) throw new IllegalArgumentException("année hors limites");
                TypePeriode type = TypePeriode.depuisTexte(champ(c, 1));
                BigDecimal revenu = nombre(champ(c, 2));
                String dev = champ(c, 3).toUpperCase();
                Devise devise = dev.isEmpty() ? (annee < 2002 ? Devise.FRF : Devise.EUR)
                        : (dev.startsWith("F") ? Devise.FRF : Devise.EUR);
                BigDecimal t = nombre(champ(c, 4));
                Integer trimestres = t == null ? null : t.intValue();
                if (trimestres != null && (trimestres < 0 || trimestres > 4))
                    throw new IllegalArgumentException("trimestres doit être entre 0 et 4");
                BigDecimal points = nombre(champ(c, 5));
                if (type != TypePeriode.SALAIRE && trimestres == null)
                    throw new IllegalArgumentException("une période assimilée doit indiquer son nombre de trimestres");
                if (type == TypePeriode.SALAIRE && revenu == null && trimestres == null)
                    throw new IllegalArgumentException("indiquez un revenu ou un nombre de trimestres");
                lignes.add(new LigneCarriere(annee, type, revenu, devise, trimestres, points));
            } catch (RuntimeException e) {
                erreurs.add("Ligne " + (i + 1) + " (« " + l + " ») : " + e.getMessage());
            }
        }
        return new Resultat(lignes, erreurs);
    }

    private static String champ(String[] c, int i) {
        return i < c.length ? c[i].trim() : "";
    }

    private static BigDecimal nombre(String s) {
        if (s == null || s.isBlank()) return null;
        String n = s.replace(" ", "").replace(" ", "").replace("€", "").replace(",", ".");
        try {
            return new BigDecimal(n);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("nombre invalide « " + s + " »");
        }
    }

    public record Resultat(List<LigneCarriere> lignes, List<String> erreurs) {}
}
