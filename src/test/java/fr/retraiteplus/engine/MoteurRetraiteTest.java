package fr.retraiteplus.engine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Tests du moteur : cas pratique du TD et cas génériques (décote, suspension, minimum contributif…). */
class MoteurRetraiteTest {

    private static final Parametres P = Parametres.chargerDepuisClasspath();
    private final MoteurRetraite moteur = new MoteurRetraite(P);

    private static String ressource(String chemin) {
        try (InputStream in = MoteurRetraiteTest.class.getResourceAsStream(chemin)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    private DonneesSimulation ousmane() {
        DonneesSimulation d = new DonneesSimulation();
        d.nom = "OUSMANE";
        d.dateNaissance = LocalDate.of(1963, 1, 6);
        d.dateDepart = LocalDate.of(2025, 2, 1);
        d.dateCalcul = LocalDate.of(2026, 11, 20);
        d.nombreEnfants = 1;
        CarriereParser.Resultat c = CarriereParser.lire(ressource("/exemples/ousmane.csv"));
        assertTrue(c.erreurs().isEmpty(), c.erreurs().toString());
        d.carriere = c.lignes();
        return d;
    }

    // ------------------------------------------------------------------ cas Ousmane
    @Test void ousmane_reglesDeGeneration() {
        ResultatSimulation r = moteur.simuler(ousmane());
        assertEquals(170, r.dureeRequise);
        assertEquals("62 ans et 9 mois", r.ageLegal);
        assertEquals(LocalDate.of(2025, 10, 6), r.dateAgeLegal);
    }

    @Test void ousmane_partEnCarriereLongueAuTauxPlein() {
        ResultatSimulation r = moteur.simuler(ousmane());
        assertTrue(r.carriereLongueEligible, r.carriereLongueDetail);
        assertTrue(r.departPossible);
        assertTrue(r.tauxPlein);
        assertEquals(171, r.trimestresTousRegimes);
        assertEquals(0, r.trimestresDecote);
        assertEquals(0, new BigDecimal("50").compareTo(r.tauxPct));
    }

    @Test void ousmane_trimestres2025CalculesAvecLeSeuil() {
        ResultatSimulation r = moteur.simuler(ousmane());
        ResultatSimulation.AnneeDetail a2025 = r.annees.stream().filter(a -> a.annee == 2025).findFirst().orElseThrow();
        assertEquals(2, a2025.trimestresCotises);   // 5 111 € / 1 782 € = 2 trimestres
        assertTrue(a2025.exclueSam);                 // l'année de départ ne compte pas dans le SAM
    }

    @Test void ousmane_detecteAnomalie2022() {
        ResultatSimulation r = moteur.simuler(ousmane());
        ResultatSimulation.AnneeDetail a2022 = r.annees.stream().filter(a -> a.annee == 2022).findFirst().orElseThrow();
        assertEquals(1, a2022.trimestresCotises);
        assertEquals(Integer.valueOf(4), a2022.trimestresCalcules);   // 7 920 € / 1 585,50 € → 4 trimestres
        assertNotNull(a2022.alerte);
    }

    @Test void ousmane_samEtPensionDeBase() {
        ResultatSimulation r = moteur.simuler(ousmane());
        assertEquals(25, r.anneesRetenues);
        assertEquals(new BigDecimal("42487.77"), r.sam);
        assertEquals(new BigDecimal("21243.89"), r.baseAnnuelle);
        assertEquals(new BigDecimal("1770.32"), r.baseMensuelle);
        assertFalse(r.basePlafonnee);
    }

    @Test void ousmane_complementaireAgircArrco() {
        ResultatSimulation r = moteur.simuler(ousmane());
        assertEquals(new BigDecimal("11203.02"), r.pointsTotal);
        assertEquals(new BigDecimal("1.4386"), r.valeurPoint);
        assertEquals(new BigDecimal("1343.06"), r.complementaireMensuelle);
    }

    @Test void ousmane_montantRevaloriseEn2026() {
        ResultatSimulation r = moteur.simuler(ousmane());
        assertTrue(r.departPasse);
        assertTrue(r.totalBrutMensuelActuel.compareTo(r.totalBrutMensuel) > 0);
    }

    @Test void ousmane_pasDeMajorationPourUnSeulEnfant() {
        ResultatSimulation r = moteur.simuler(ousmane());
        assertEquals(0, r.majorationEnfantsBase.signum());
        assertEquals(0, r.trimestresMajorationEnfants);
    }

    @Test void ousmane_departTropTotImpossible() {
        DonneesSimulation d = ousmane();
        d.dateDepart = LocalDate.of(2024, 2, 1);     // 167 trimestres seulement à cette date
        ResultatSimulation r = moteur.simuler(d);
        assertFalse(r.departPossible);
    }

    // ------------------------------------------------------------------ cas génériques
    private DonneesSimulation femme1966() {
        StringBuilder sb = new StringBuilder();
        for (int y = 1989; y <= 2025; y++) {
            if (y >= 1995 && y <= 1997) continue;
            if (y < 2002) sb.append(y).append(";SALAIRE;18000;FRF;;150\n");
            else sb.append(y).append(";SALAIRE;").append(20000 + (y - 2002) * 500).append(";EUR;;").append(y < 2021 ? "150" : "").append('\n');
        }
        sb.append("1996;MATERNITE;;;2;\n");
        DonneesSimulation d = new DonneesSimulation();
        d.sexe = DonneesSimulation.Sexe.FEMME;
        d.dateNaissance = LocalDate.of(1966, 5, 15);
        d.dateDepart = LocalDate.of(2029, 9, 1);
        d.dateCalcul = LocalDate.of(2026, 11, 20);
        d.nombreEnfants = 3;
        d.salaireProjete = new BigDecimal("33000");
        d.carriere = CarriereParser.lire(sb.toString()).lignes();
        return d;
    }

    @Test void femme1966_suspensionDeLaReforme() {
        ResultatSimulation r = moteur.simuler(femme1966());
        assertTrue(r.suspensionAppliquee);
        assertEquals("63 ans et 3 mois", r.ageLegal);
        assertEquals(172, r.dureeRequise);
    }

    @Test void femme1966_decoteEtCoefficientAnticipation() {
        ResultatSimulation r = moteur.simuler(femme1966());
        assertEquals(24, r.trimestresMajorationEnfants);   // 8 trimestres par enfant pour la mère
        assertEquals(160, r.trimestresTousRegimes);
        assertFalse(r.tauxPlein);
        assertEquals(12, r.trimestresDecote);
        assertEquals(0, new BigDecimal("42.5").compareTo(r.tauxPct));
        assertEquals(0, new BigDecimal("0.88").compareTo(r.coefficientAnticipation));
        assertTrue(r.majorationEnfantsBase.signum() > 0);  // 3 enfants : +10 %
    }

    @Test void femme1966_scenariosComparesEtSurcoteA67Ans() {
        ResultatSimulation r = moteur.simuler(femme1966());
        assertTrue(r.scenarios.size() >= 3);
        ResultatSimulation.Scenario a67 = r.scenarios.get(r.scenarios.size() - 1);
        assertEquals(0, new BigDecimal("50").compareTo(a67.tauxPct));
        assertTrue(a67.totalMensuel.compareTo(r.totalBrutMensuel) > 0);
    }

    @Test void carriereCourte_minimumContributifEtReversion() {
        StringBuilder sb = new StringBuilder();
        for (int y = 2002; y <= 2023; y++) sb.append(y).append(";SALAIRE;9000;EUR;;").append(y < 2021 ? "60" : "").append('\n');
        DonneesSimulation d = new DonneesSimulation();
        d.dateNaissance = LocalDate.of(1959, 3, 2);
        d.dateDepart = LocalDate.of(2026, 4, 1);
        d.dateCalcul = LocalDate.of(2026, 11, 20);
        d.carriere = CarriereParser.lire(sb.toString()).lignes();
        d.conjointDecede = true;
        d.pensionBaseConjoint = new BigDecimal("15000");
        d.pointsConjoint = new BigDecimal("6000");
        ResultatSimulation r = moteur.simuler(d);
        assertTrue(r.tauxPlein);                         // 67 ans : taux plein automatique
        assertTrue(r.complementMico.signum() > 0);       // petite pension : minimum contributif
        assertEquals(new BigDecimal("8100.00"), r.reversionBaseAnnuelle);
        assertTrue(r.reversionAgircAnnuelle.signum() > 0);
    }

    @Test void reversionAgircPerdueSiRemariage() {
        DonneesSimulation d = ousmane();
        d.conjointDecede = true;
        d.pointsConjoint = new BigDecimal("5000");
        d.remarie = true;
        ResultatSimulation r = moteur.simuler(d);
        assertEquals(0, r.reversionAgircAnnuelle.signum());
    }

    // ------------------------------------------------------------------ règles unitaires
    @Test void reglesGenerations() {
        assertEquals(168, RegleGeneration.pour(LocalDate.of(1961, 3, 1), LocalDate.of(2023, 6, 1)).getDureeRequise());
        assertEquals(171, RegleGeneration.pour(LocalDate.of(1964, 5, 1), LocalDate.of(2026, 6, 1)).getDureeRequise());
        assertEquals(170, RegleGeneration.pour(LocalDate.of(1964, 5, 1), LocalDate.of(2027, 3, 1)).getDureeRequise());
        assertEquals(new RegleGeneration.Age(64, 0), RegleGeneration.pour(LocalDate.of(1970, 1, 1), LocalDate.of(2034, 2, 1)).getAgeLegal());
    }

    @Test void coefficientsAnticipationAgirc() {
        assertEquals(0, new BigDecimal("0.96").compareTo(MoteurRetraite.coefficientAnticipation(4)));
        assertEquals(0, new BigDecimal("0.88").compareTo(MoteurRetraite.coefficientAnticipation(12)));
        assertEquals(0, new BigDecimal("0.78").compareTo(MoteurRetraite.coefficientAnticipation(20)));
    }

    @Test void parserSignaleLesErreurs() {
        CarriereParser.Resultat r = CarriereParser.lire("2020;SALAIRE;abc;EUR;;\n2021;CHOMAGE;;;;\n2022;SALAIRE;30000;EUR;4;250,5");
        assertEquals(2, r.erreurs().size());
        assertEquals(1, r.lignes().size());
        assertEquals(new BigDecimal("250.5"), r.lignes().get(0).points());
    }
}
