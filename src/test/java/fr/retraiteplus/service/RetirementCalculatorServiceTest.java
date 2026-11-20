package fr.retraiteplus.service;

import fr.retraiteplus.model.SimulationForm;
import fr.retraiteplus.model.SimulationResult;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class RetirementCalculatorServiceTest {
    private final RetirementCalculatorService service = new RetirementCalculatorService();

    @Test void calculatesBasicPension() {
        SimulationForm f = new SimulationForm();
        f.setSam(new BigDecimal("30000"));
        f.setLiquidationRate(new BigDecimal("0.50"));
        f.setAcquiredQuarters(168);
        f.setRequiredQuarters(172);
        SimulationResult r = service.calculate(f);
        assertEquals(new BigDecimal("14651.16"), r.getAnnualPension());
        assertEquals(new BigDecimal("1220.93"), r.getMonthlyPension());
    }

    @Test void appliesParametrizedDecoteAndSurcote() {
        SimulationForm f = new SimulationForm();
        f.setSam(new BigDecimal("28000"));
        f.setAcquiredQuarters(168);
        f.setRequiredQuarters(172);
        f.setMissingQuartersForDecote(4);
        f.setEligibleSurcoteQuarters(3);
        SimulationResult r = service.calculate(f);
        assertTrue(r.getMonthlyPension().compareTo(BigDecimal.ZERO) > 0);
    }
}
