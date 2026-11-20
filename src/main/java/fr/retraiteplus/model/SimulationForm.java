package fr.retraiteplus.model;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class SimulationForm {
    @NotBlank private String firstName = "Alex";
    @NotBlank private String lastName = "Martin";
    @Min(1900) @Max(2026) private int birthYear = 1964;
    @Min(0) @Max(4) private int birthQuarter = 2;
    @DecimalMin("0.01") private BigDecimal sam = new BigDecimal("32000");
    @Min(1) @Max(200) private int acquiredQuarters = 160;
    @Min(1) @Max(200) private int requiredQuarters = 172;
    @DecimalMin("0.01") @DecimalMax("0.50") private BigDecimal liquidationRate = new BigDecimal("0.50");
    @Min(0) @Max(40) private int missingQuartersForDecote = 0;
    @DecimalMin("0.000") @DecimalMax("0.050") private BigDecimal decotePerQuarter = new BigDecimal("0.00625");
    @Min(0) @Max(80) private int eligibleSurcoteQuarters = 0;
    @Min(0) @Max(10) private int children = 0;
    private boolean applyTenPercentChildrenBonus = false;

    public String getFirstName(){return firstName;} public void setFirstName(String v){firstName=v;}
    public String getLastName(){return lastName;} public void setLastName(String v){lastName=v;}
    public int getBirthYear(){return birthYear;} public void setBirthYear(int v){birthYear=v;}
    public int getBirthQuarter(){return birthQuarter;} public void setBirthQuarter(int v){birthQuarter=v;}
    public BigDecimal getSam(){return sam;} public void setSam(BigDecimal v){sam=v;}
    public int getAcquiredQuarters(){return acquiredQuarters;} public void setAcquiredQuarters(int v){acquiredQuarters=v;}
    public int getRequiredQuarters(){return requiredQuarters;} public void setRequiredQuarters(int v){requiredQuarters=v;}
    public BigDecimal getLiquidationRate(){return liquidationRate;} public void setLiquidationRate(BigDecimal v){liquidationRate=v;}
    public int getMissingQuartersForDecote(){return missingQuartersForDecote;} public void setMissingQuartersForDecote(int v){missingQuartersForDecote=v;}
    public BigDecimal getDecotePerQuarter(){return decotePerQuarter;} public void setDecotePerQuarter(BigDecimal v){decotePerQuarter=v;}
    public int getEligibleSurcoteQuarters(){return eligibleSurcoteQuarters;} public void setEligibleSurcoteQuarters(int v){eligibleSurcoteQuarters=v;}
    public int getChildren(){return children;} public void setChildren(int v){children=v;}
    public boolean isApplyTenPercentChildrenBonus(){return applyTenPercentChildrenBonus;} public void setApplyTenPercentChildrenBonus(boolean v){applyTenPercentChildrenBonus=v;}
}
