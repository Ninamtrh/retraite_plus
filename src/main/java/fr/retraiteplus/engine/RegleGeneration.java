package fr.retraiteplus.engine;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

/**
 * Règles d'âge et de durée d'assurance selon la date de naissance.
 * <p>
 * Sources : réformes 2010 et 2023 (loi n°2023-270), suspension de la réforme par la LFSS 2026
 * (pensions prenant effet à compter du 1er septembre 2026 : générations 1964 à 1968),
 * tableaux carrière longue (lassuranceretraite.fr, retraitesdeletat.gouv.fr).
 */
public final class RegleGeneration {

    public static final LocalDate DEBUT_SUSPENSION = LocalDate.of(2026, 9, 1);

    /** Âge exprimé en années et mois. */
    public record Age(int annees, int mois) {
        public LocalDate atteintLe(LocalDate naissance) { return naissance.plusYears(annees).plusMonths(mois); }
        public String libelle() { return annees + " ans" + (mois > 0 ? " et " + mois + " mois" : ""); }
        public static Age entre(LocalDate debut, LocalDate fin) {
            Period p = Period.between(debut, fin);
            return new Age(p.getYears(), p.getMonths());
        }
    }

    /** Une borne de départ anticipé carrière longue. */
    public record BorneCarriereLongue(int ageDebutAvant, Age ageDepart, int trimestresCotisesRequis) {}

    private final Age ageLegal;
    private final int dureeRequise;
    private final Age ageTauxPlein;
    private final List<BorneCarriereLongue> bornes;
    private final boolean suspensionAppliquee;
    private final boolean bornesAVerifier;

    private RegleGeneration(Age ageLegal, int dureeRequise, Age ageTauxPlein, List<BorneCarriereLongue> bornes,
                            boolean suspensionAppliquee, boolean bornesAVerifier) {
        this.ageLegal = ageLegal;
        this.dureeRequise = dureeRequise;
        this.ageTauxPlein = ageTauxPlein;
        this.bornes = bornes;
        this.suspensionAppliquee = suspensionAppliquee;
        this.bornesAVerifier = bornesAVerifier;
    }

    public Age getAgeLegal() { return ageLegal; }
    public int getDureeRequise() { return dureeRequise; }
    public Age getAgeTauxPlein() { return ageTauxPlein; }
    public List<BorneCarriereLongue> getBornes() { return bornes; }
    public boolean isSuspensionAppliquee() { return suspensionAppliquee; }
    public boolean isBornesAVerifier() { return bornesAVerifier; }

    /**
     * @param naissance date de naissance
     * @param effet     date d'effet de la pension (la suspension ne joue que pour les effets ≥ 01/09/2026)
     */
    public static RegleGeneration pour(LocalDate naissance, LocalDate effet) {
        int y = naissance.getYear();
        int m = naissance.getMonthValue();
        boolean susp = !effet.isBefore(DEBUT_SUSPENSION);
        boolean suspJoue = false;

        Age legal;
        int duree;
        Age tauxPlein = new Age(67, 0);

        if (y < 1951 || (y == 1951 && m < 7)) {
            legal = new Age(60, 0);
            duree = y <= 1948 ? 160 : (y == 1949 ? 161 : (y == 1950 ? 162 : 163));
            tauxPlein = new Age(65, 0);
        } else if (y == 1951) { legal = new Age(60, 4); duree = 163; tauxPlein = new Age(65, 4); }
        else if (y == 1952) { legal = new Age(60, 9); duree = 164; tauxPlein = new Age(65, 9); }
        else if (y == 1953) { legal = new Age(61, 2); duree = 165; tauxPlein = new Age(66, 2); }
        else if (y == 1954) { legal = new Age(61, 7); duree = 165; tauxPlein = new Age(66, 7); }
        else if (y <= 1957) { legal = new Age(62, 0); duree = 166; }
        else if (y <= 1960) { legal = new Age(62, 0); duree = 167; }
        else if (y == 1961 && m < 9) { legal = new Age(62, 0); duree = 168; }
        else if (y == 1961) { legal = new Age(62, 3); duree = 169; }
        else if (y == 1962) { legal = new Age(62, 6); duree = 169; }
        else if (y == 1963) { legal = new Age(62, 9); duree = 170; }
        else if (y == 1964) {
            if (susp) { legal = new Age(62, 9); duree = 170; suspJoue = true; } else { legal = new Age(63, 0); duree = 171; }
        } else if (y == 1965) {
            if (susp) { suspJoue = true; if (m <= 3) { legal = new Age(62, 9); duree = 170; } else { legal = new Age(63, 0); duree = 171; } }
            else { legal = new Age(63, 3); duree = 172; }
        } else if (y == 1966) { legal = susp ? new Age(63, 3) : new Age(63, 6); duree = 172; suspJoue = susp; }
        else if (y == 1967) { legal = susp ? new Age(63, 6) : new Age(63, 9); duree = 172; suspJoue = susp; }
        else if (y == 1968) { legal = susp ? new Age(63, 9) : new Age(64, 0); duree = 172; suspJoue = susp; }
        else { legal = new Age(64, 0); duree = 172; }

        // ----- Carrière longue -----
        List<BorneCarriereLongue> bornes = new ArrayList<>();
        boolean aVerifier = false;
        boolean avantReforme2023 = y < 1961 || (y == 1961 && m < 9);
        if (avantReforme2023) {
            // Règles antérieures (décret 2012) : départ à 60 ans si début avant 20 ans,
            // 58/59 ans si début avant 16/17 ans avec durée majorée de 8 trimestres (simplification).
            if (y >= 1956) bornes.add(new BorneCarriereLongue(16, new Age(58, 0), duree + 8));
            bornes.add(new BorneCarriereLongue(20, new Age(60, 0), duree));
        } else {
            bornes.add(new BorneCarriereLongue(16, new Age(58, 0), duree));
            if (y > 1963 || (y == 1963 && m >= 9)) bornes.add(new BorneCarriereLongue(18, new Age(60, 0), duree));
            Age borne20 = borne20(y, m, susp);
            if (susp && y >= 1965 && y <= 1971) aVerifier = true;
            bornes.add(new BorneCarriereLongue(20, borne20, duree));
            if (legal.annees() * 12 + legal.mois() > 63 * 12)
                bornes.add(new BorneCarriereLongue(21, new Age(63, 0), duree));
        }
        return new RegleGeneration(legal, duree, tauxPlein, bornes, suspJoue, aVerifier);
    }

    private static Age borne20(int y, int m, boolean susp) {
        if (y < 1963 || (y == 1963 && m < 9)) return new Age(60, 0);
        if (y == 1963) return new Age(60, 3);
        if (y == 1964) return new Age(60, 6);
        // Décalage d'un cran (3 mois) pour les départs pendant la suspension (nés à partir de déc. 1965)
        int cran = (susp && (y > 1965 || (y == 1965 && m == 12))) ? 1 : 0;
        int moisSupp = Math.min((y - 1964) - cran, 8) * 3; // 1965 → +3 mois ... plafonné à 62 ans
        int total = 60 * 12 + 6 + Math.max(moisSupp, 0);
        total = Math.min(total, 62 * 12);
        return new Age(total / 12, total % 12);
    }
}
