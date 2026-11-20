package fr.retraiteplus.engine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Données d'entrée d'une simulation (indépendantes de l'interface web). */
public class DonneesSimulation {

    public enum Sexe { HOMME, FEMME }

    /** Taux de CSG applicable selon le revenu fiscal de référence. */
    public enum TauxCsg {
        NORMAL("Taux normal (8,3 %)"), MEDIAN("Taux médian (6,6 %)"),
        REDUIT("Taux réduit (3,8 %)"), EXONERE("Exonéré");
        private final String libelle;
        TauxCsg(String l) { libelle = l; }
        public String getLibelle() { return libelle; }
    }

    public String nom = "";
    public String prenom = "";
    public LocalDate dateNaissance;
    public Sexe sexe = Sexe.HOMME;
    public LocalDate dateDepart;
    /** Date à laquelle on veut connaître le montant revalorisé (par défaut : aujourd'hui). */
    public LocalDate dateCalcul = LocalDate.now();

    public List<LigneCarriere> carriere = new ArrayList<>();
    /** Trimestres validés dans d'autres régimes de base (fonction publique, indépendants…). */
    public int trimestresAutresRegimes = 0;
    /** Salaire annuel brut projeté jusqu'au départ (null = pas de projection). */
    public BigDecimal salaireProjete;

    public int nombreEnfants = 0;
    /**
     * Trimestres de majoration de durée d'assurance pour enfants. null = calcul automatique
     * (8 par enfant pour une mère, 0 pour un père sauf partage ou attribution).
     */
    public Integer trimestresMajorationEnfants;

    /** Taux plein automatique : inaptitude au travail, invalidité, handicap, ancien combattant… */
    public boolean tauxPleinParStatut = false;

    public TauxCsg tauxCsg = TauxCsg.NORMAL;

    // ----- Réversion (conjoint décédé) -----
    public boolean conjointDecede = false;
    /** Retraite de base annuelle que percevait (ou aurait perçue) le conjoint décédé. */
    public BigDecimal pensionBaseConjoint;
    /** Points Agirc-Arrco du conjoint décédé. */
    public BigDecimal pointsConjoint;
    public boolean remarie = false;
    /** Autres ressources annuelles brutes du survivant (revenus fonciers, salaire…), hors pensions calculées ici. */
    public BigDecimal autresRessources;

    public DonneesSimulation copieAvecDepart(LocalDate depart) {
        DonneesSimulation d = new DonneesSimulation();
        d.nom = nom; d.prenom = prenom; d.dateNaissance = dateNaissance; d.sexe = sexe;
        d.dateDepart = depart; d.dateCalcul = dateCalcul; d.carriere = carriere;
        d.trimestresAutresRegimes = trimestresAutresRegimes; d.salaireProjete = salaireProjete;
        d.nombreEnfants = nombreEnfants; d.trimestresMajorationEnfants = trimestresMajorationEnfants;
        d.tauxPleinParStatut = tauxPleinParStatut; d.tauxCsg = tauxCsg;
        d.conjointDecede = conjointDecede; d.pensionBaseConjoint = pensionBaseConjoint;
        d.pointsConjoint = pointsConjoint; d.remarie = remarie; d.autresRessources = autresRessources;
        return d;
    }
}
