package fr.retraiteplus.engine;

import java.math.BigDecimal;

/**
 * Une ligne du relevé de carrière.
 *
 * @param annee       année civile
 * @param type        nature de la période
 * @param revenu      revenu soumis à cotisation (peut être null pour une période assimilée)
 * @param devise      EUR ou FRF (les revenus avant 2002 sont en francs sur les relevés)
 * @param trimestres  trimestres déclarés sur le relevé ; null = calcul automatique à partir du revenu
 * @param points      points Agirc-Arrco de la ligne ; null = calcul automatique si possible
 * @param projetee    true si la ligne a été ajoutée par la projection de fin de carrière
 */
public record LigneCarriere(int annee, TypePeriode type, BigDecimal revenu, Devise devise,
                            Integer trimestres, BigDecimal points, boolean projetee) {

    public LigneCarriere(int annee, TypePeriode type, BigDecimal revenu, Devise devise,
                         Integer trimestres, BigDecimal points) {
        this(annee, type, revenu, devise, trimestres, points, false);
    }

    public enum Devise { EUR, FRF }
}
