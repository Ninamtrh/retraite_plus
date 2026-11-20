package fr.retraiteplus.service;

import fr.retraiteplus.model.*;
import org.springframework.stereotype.Service;
import java.math.*;
import java.util.*;

@Service
public class RetirementCalculatorService {
    private static final int SCALE = 2;
    private static BigDecimal pct(BigDecimal value) { return value.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP); }

    public SimulationResult calculate(SimulationForm f) {
        List<String> notes = new ArrayList<>();
        BigDecimal decote = f.getDecotePerQuarter().multiply(BigDecimal.valueOf(f.getMissingQuartersForDecote()));
        BigDecimal adjustedRate = f.getLiquidationRate().subtract(decote).max(BigDecimal.ZERO);
        BigDecimal prorata = BigDecimal.valueOf(f.getAcquiredQuarters())
                .divide(BigDecimal.valueOf(f.getRequiredQuarters()), 8, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE);
        BigDecimal annualBase = f.getSam().multiply(adjustedRate).multiply(prorata);
        BigDecimal surcote = new BigDecimal("0.0125").multiply(BigDecimal.valueOf(f.getEligibleSurcoteQuarters()));
        BigDecimal afterSurcote = annualBase.multiply(BigDecimal.ONE.add(surcote));
        BigDecimal childBonus = (f.isApplyTenPercentChildrenBonus() && f.getChildren() >= 3)
                ? new BigDecimal("0.10") : BigDecimal.ZERO;
        BigDecimal annual = afterSurcote.multiply(BigDecimal.ONE.add(childBonus)).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal monthly = annual.divide(BigDecimal.valueOf(12), SCALE, RoundingMode.HALF_UP);

        notes.add("SAM renseigné : " + money(f.getSam()) + " € par an.");
        notes.add("Taux après décote paramétrée : " + pct(adjustedRate) + " %.");
        notes.add("Proratisation : " + f.getAcquiredQuarters() + " / " + f.getRequiredQuarters()
                + " = " + pct(prorata) + " %.");
        if (f.getMissingQuartersForDecote() > 0)
            notes.add("Décote pédagogique : " + f.getMissingQuartersForDecote() + " trimestre(s) × "
                    + pct(f.getDecotePerQuarter()) + " point(s) de taux.");
        if (f.getEligibleSurcoteQuarters() > 0)
            notes.add("Surcote paramétrée : " + f.getEligibleSurcoteQuarters() + " trimestre(s) × 1,25 %.");
        if (childBonus.signum() > 0)
            notes.add("Majoration de 10 % activée pour 3 enfants ou plus (à vérifier selon les conditions applicables).");
        notes.add("Montant mensuel = montant annuel estimé divisé par 12, avant prélèvements sociaux.");
        notes.add("Estimation pédagogique : vérifier l'âge légal, la durée requise et les conditions d'éligibilité avec les règles officielles de la génération.");
        return new SimulationResult(adjustedRate, prorata, annualBase, annual, monthly,
                pct(decote), pct(surcote), pct(childBonus), notes);
    }
    private String money(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
}
