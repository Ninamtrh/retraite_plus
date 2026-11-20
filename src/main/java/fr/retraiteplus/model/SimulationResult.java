package fr.retraiteplus.model;

import java.math.BigDecimal;
import java.util.List;

public class SimulationResult {
    private final BigDecimal adjustedRate, prorata, annualBeforeBonuses, annualPension, monthlyPension;
    private final BigDecimal decotePercent, surcotePercent, childrenBonusPercent;
    private final List<String> explanations;
    public SimulationResult(BigDecimal adjustedRate, BigDecimal prorata, BigDecimal annualBeforeBonuses,
        BigDecimal annualPension, BigDecimal monthlyPension, BigDecimal decotePercent,
        BigDecimal surcotePercent, BigDecimal childrenBonusPercent, List<String> explanations) {
        this.adjustedRate=adjustedRate; this.prorata=prorata; this.annualBeforeBonuses=annualBeforeBonuses;
        this.annualPension=annualPension; this.monthlyPension=monthlyPension;
        this.decotePercent=decotePercent; this.surcotePercent=surcotePercent;
        this.childrenBonusPercent=childrenBonusPercent; this.explanations=explanations;
    }
    public BigDecimal getAdjustedRate(){return adjustedRate;} public BigDecimal getProrata(){return prorata;}
    public BigDecimal getAnnualBeforeBonuses(){return annualBeforeBonuses;}
    public BigDecimal getAnnualPension(){return annualPension;} public BigDecimal getMonthlyPension(){return monthlyPension;}
    public BigDecimal getDecotePercent(){return decotePercent;} public BigDecimal getSurcotePercent(){return surcotePercent;}
    public BigDecimal getChildrenBonusPercent(){return childrenBonusPercent;} public List<String> getExplanations(){return explanations;}
}
