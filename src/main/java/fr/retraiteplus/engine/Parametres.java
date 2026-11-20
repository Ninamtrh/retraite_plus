package fr.retraiteplus.engine;

import fr.retraiteplus.engine.LigneCarriere.Devise;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/**
 * Paramètres réglementaires datés, chargés depuis src/main/resources/parametres.
 * <p>
 * Toutes les valeurs qui changent chaque année sont dans des fichiers CSV : pour mettre
 * le simulateur à jour, il suffit d'ajouter une ligne, sans toucher au code.
 */
public final class Parametres {

    public record Montant(BigDecimal valeur, Devise devise) {}

    private final TreeMap<Integer, Montant> pass = new TreeMap<>();
    private final TreeMap<Integer, Montant> seuilTrimestre = new TreeMap<>();
    /** année d'effet de la pension → (année du salaire → coefficient) */
    private final TreeMap<Integer, TreeMap<Integer, BigDecimal>> coefficientsSalaires = new TreeMap<>();
    private final TreeMap<LocalDate, BigDecimal> revalorisationPensions = new TreeMap<>();
    private final TreeMap<LocalDate, BigDecimal> valeurServicePoint = new TreeMap<>();
    private final TreeMap<Integer, BigDecimal> valeurAchatPoint = new TreeMap<>();
    private final Properties constantes = new Properties();

    public static Parametres chargerDepuisClasspath() {
        Parametres p = new Parametres();
        for (String[] l : csv("pass.csv")) p.pass.put(Integer.parseInt(l[0]), new Montant(new BigDecimal(l[1]), Devise.valueOf(l[2])));
        for (String[] l : csv("seuil-trimestre.csv")) p.seuilTrimestre.put(Integer.parseInt(l[0]), new Montant(new BigDecimal(l[1]), Devise.valueOf(l[2])));
        for (int annee : new int[]{2025, 2026}) {
            TreeMap<Integer, BigDecimal> t = new TreeMap<>();
            for (String[] l : csv("coefficients-salaires-" + annee + ".csv")) t.put(Integer.parseInt(l[0]), new BigDecimal(l[1]));
            p.coefficientsSalaires.put(annee, t);
        }
        for (String[] l : csv("revalorisation-pensions.csv")) p.revalorisationPensions.put(LocalDate.parse(l[0]), new BigDecimal(l[1]));
        for (String[] l : csv("agirc-valeur-service.csv")) p.valeurServicePoint.put(LocalDate.parse(l[0]), new BigDecimal(l[1]));
        for (String[] l : csv("agirc-valeur-achat.csv")) p.valeurAchatPoint.put(Integer.parseInt(l[0]), new BigDecimal(l[1]));
        try (InputStream in = ouvrir("constantes.properties")) {
            p.constantes.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return p;
    }

    // ---------- accès ----------

    /** PASS de l'année (si l'année est hors table, on prend la plus proche et on le signale via estConnu). */
    public Montant pass(int annee) { return proche(pass, annee); }
    public boolean passConnu(int annee) { return pass.containsKey(annee); }

    public Montant seuilTrimestre(int annee) { return proche(seuilTrimestre, annee); }
    public boolean seuilConnu(int annee) { return seuilTrimestre.containsKey(annee); }

    /** SMIC horaire au 1er janvier, déduit du seuil de validation (150 h depuis 2014). */
    public BigDecimal smicHoraire(int annee) {
        Montant s = seuilTrimestre(Math.max(annee, 2014));
        return s.valeur().divide(BigDecimal.valueOf(150), 4, java.math.RoundingMode.HALF_UP);
    }

    /** Année de la table de coefficients utilisée pour une pension prenant effet l'année donnée. */
    public int anneeTableCoefficients(int anneeEffet) {
        Integer k = coefficientsSalaires.floorKey(anneeEffet);
        return k != null ? k : coefficientsSalaires.firstKey();
    }

    public boolean tableCoefficientsExacte(int anneeEffet) { return coefficientsSalaires.containsKey(anneeEffet); }

    /** Coefficient de revalorisation du salaire d'une année pour une pension prenant effet en anneeEffet ; 1 si absent. */
    public BigDecimal coefficientSalaire(int anneeEffet, int anneeSalaire) {
        TreeMap<Integer, BigDecimal> t = coefficientsSalaires.get(anneeTableCoefficients(anneeEffet));
        BigDecimal c = t.get(anneeSalaire);
        return c != null ? c : BigDecimal.ONE;
    }

    public boolean coefficientSalaireConnu(int anneeEffet, int anneeSalaire) {
        return coefficientsSalaires.get(anneeTableCoefficients(anneeEffet)).containsKey(anneeSalaire);
    }

    /** Produit des revalorisations de pension intervenues après la date d'effet et jusqu'à la date donnée. */
    public BigDecimal revalorisationPensionEntre(LocalDate effet, LocalDate jusquA) {
        BigDecimal r = BigDecimal.ONE;
        for (Map.Entry<LocalDate, BigDecimal> e : revalorisationPensions.entrySet()) {
            if (e.getKey().isAfter(effet) && !e.getKey().isAfter(jusquA)) r = r.multiply(e.getValue());
        }
        return r;
    }

    public BigDecimal valeurServicePoint(LocalDate date) {
        Map.Entry<LocalDate, BigDecimal> e = valeurServicePoint.floorEntry(date);
        return e != null ? e.getValue() : valeurServicePoint.firstEntry().getValue();
    }

    public LocalDate dateValeurServicePoint(LocalDate date) {
        LocalDate k = valeurServicePoint.floorKey(date);
        return k != null ? k : valeurServicePoint.firstKey();
    }

    public BigDecimal valeurAchatPoint(int annee) { return valeurAchatPoint.get(annee); }
    public BigDecimal derniereValeurAchatPoint() { return valeurAchatPoint.lastEntry().getValue(); }

    public BigDecimal constante(String cle) {
        String v = constantes.getProperty(cle);
        if (v == null) throw new IllegalStateException("constante manquante : " + cle);
        return new BigDecimal(v.trim());
    }

    public int constanteEntiere(String cle) { return constante(cle).intValue(); }

    // ---------- outils ----------

    private static <V> V proche(TreeMap<Integer, V> m, int annee) {
        if (m.containsKey(annee)) return m.get(annee);
        if (annee > m.lastKey()) return m.lastEntry().getValue();
        if (annee < m.firstKey()) return m.firstEntry().getValue();
        return m.floorEntry(annee).getValue();
    }

    private static InputStream ouvrir(String nom) {
        InputStream in = Parametres.class.getResourceAsStream("/parametres/" + nom);
        if (in == null) throw new IllegalStateException("Fichier de paramètres introuvable : " + nom);
        return in;
    }

    private static List<String[]> csv(String nom) {
        List<String[]> res = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(ouvrir(nom), StandardCharsets.UTF_8))) {
            String l;
            boolean entete = true;
            while ((l = r.readLine()) != null) {
                l = l.trim();
                if (l.isEmpty() || l.startsWith("#")) continue;
                if (entete) { entete = false; continue; }
                res.add(l.split(";"));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return res;
    }
}
