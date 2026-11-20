package fr.retraiteplus.engine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Résultat complet d'une simulation. Champs publics : lus directement par les vues Thymeleaf.
 * Montants en euros bruts annuels sauf mention « Mensuel ».
 */
public class ResultatSimulation {

    /** Détail d'une année de carrière. */
    public static class AnneeDetail {
        public int annee;
        public BigDecimal revenu;            // revenu brut déclaré, dans la devise d'origine
        public String devise;
        public BigDecimal plafond;           // PASS de l'année, devise d'origine
        public boolean plafonne;             // le revenu dépassait le PASS
        public BigDecimal revenuRetenuEur;   // min(revenu, PASS) converti en euros
        public BigDecimal coefficient;
        public BigDecimal revenuRevalorise;
        public boolean retenuPourSam;
        public boolean exclueSam;            // année de départ ou postérieure
        public int trimestresCotises;
        public int trimestresAssimiles;
        public int trimestresValides;
        public Integer trimestresCalcules;   // calcul théorique à partir du revenu (contrôle)
        public String detailAssimiles = "";
        public BigDecimal points;
        public boolean pointsCalcules;
        public boolean projetee;
        public String alerte;
    }

    /** Une date de départ comparée. */
    public static class Scenario {
        public String libelle;
        public LocalDate date;
        public String age;
        public boolean possible;
        public String motif;
        public int trimestres;
        public BigDecimal tauxPct;
        public BigDecimal baseMensuel;
        public BigDecimal complementaireMensuel;
        public BigDecimal totalMensuel;
        public BigDecimal netMensuel;
        public boolean choisi;
    }

    // ----- identité / règles -----
    public String nomComplet;
    public LocalDate dateNaissance;
    public LocalDate dateDepart;
    public String ageAuDepart;
    public String ageLegal;
    public LocalDate dateAgeLegal;
    public String ageTauxPlein;
    public LocalDate dateAgeTauxPlein;
    public int dureeRequise;
    public boolean suspensionAppliquee;

    // ----- conditions de départ -----
    public boolean departPossible;
    public String motifDepart;
    public boolean carriereLongueEligible;
    public String carriereLongueDetail;
    public LocalDate dateCarriereLongue;
    public List<String> carriereLongueVerifications = new ArrayList<>();

    // ----- trimestres -----
    public int trimestresCotises;
    public int trimestresAssimiles;
    public int trimestresRegimeGeneral;     // cotisés + assimilés au RG
    public int trimestresAutresRegimes;
    public int trimestresMajorationEnfants;
    public int trimestresTousRegimes;       // RG + autres + majorations
    public int trimestresReputesCotises;    // pour la carrière longue
    public int trimestresManquants;

    // ----- SAM -----
    public int nombreAnneesSam;
    public int anneesRetenues;
    public BigDecimal sam;
    public int anneeTableCoefficients;

    // ----- taux et pension de base -----
    public boolean tauxPlein;
    public String motifTauxPlein;
    public int trimestresDecote;
    public BigDecimal decotePct;
    public int trimestresSurcote;
    public BigDecimal surcotePct;
    public BigDecimal tauxPct;
    public BigDecimal prorata;              // 0..1
    public BigDecimal prorataPct;
    public BigDecimal baseAvantPlafond;
    public BigDecimal plafondPensionBase;
    public boolean basePlafonnee;
    public BigDecimal baseApresSurcote;
    public BigDecimal majorationEnfantsBase;
    public BigDecimal complementMico;
    public String micoDetail;
    public BigDecimal baseAnnuelle;
    public BigDecimal baseMensuelle;

    // ----- Agirc-Arrco -----
    public BigDecimal pointsDeclares;
    public BigDecimal pointsCalcules;
    public BigDecimal pointsTotal;
    public BigDecimal valeurPoint;
    public LocalDate dateValeurPoint;
    public BigDecimal coefficientAnticipation;
    public BigDecimal majorationEnfantsAgirc;
    public BigDecimal complementaireAnnuelle;
    public BigDecimal complementaireMensuelle;

    // ----- totaux -----
    public BigDecimal totalBrutAnnuel;
    public BigDecimal totalBrutMensuel;
    public BigDecimal tauxPrelevementsBasePct;
    public BigDecimal tauxPrelevementsComplPct;
    public BigDecimal totalNetMensuel;
    public BigDecimal dernierSalaireAnnuel;
    public BigDecimal tauxRemplacementPct;

    // ----- montant revalorisé aujourd'hui (si départ passé) -----
    public boolean departPasse;
    public LocalDate dateCalcul;
    public BigDecimal baseMensuelleActuelle;
    public BigDecimal complementaireMensuelleActuelle;
    public BigDecimal totalBrutMensuelActuel;
    public BigDecimal valeurPointActuelle;

    // ----- réversion -----
    public boolean reversionCalculee;
    public BigDecimal reversionBaseAnnuelle;
    public BigDecimal reversionAgircAnnuelle;
    public BigDecimal reversionMensuelle;
    public BigDecimal plafondRessourcesReversion;
    public BigDecimal ressourcesRetenues;
    public List<String> reversionDetail = new ArrayList<>();

    // ----- détails / pédagogie -----
    public List<AnneeDetail> annees = new ArrayList<>();
    public List<Scenario> scenarios = new ArrayList<>();
    public List<String> alertes = new ArrayList<>();
    public List<String> etapes = new ArrayList<>();
    public List<String> limites = new ArrayList<>();
}
