package fr.retraiteplus.model;

import fr.retraiteplus.engine.DonneesSimulation.Sexe;
import fr.retraiteplus.engine.DonneesSimulation.TauxCsg;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Formulaire web : données brutes du salarié, telles qu'elles figurent sur son relevé de carrière. */
public class SimulationForm {
    private String prenom = "";
    private String nom = "";
    @NotNull(message = "La date de naissance est obligatoire")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateNaissance;
    private Sexe sexe = Sexe.HOMME;
    @NotNull(message = "La date de départ est obligatoire")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateDepart;

    /** Carrière au format annee;type;revenu;devise;trimestres;points */
    private String carriereCsv = "";
    @Min(0) @Max(200) private int trimestresAutresRegimes = 0;
    @DecimalMin("0") private BigDecimal salaireProjete;

    @Min(0) @Max(20) private int nombreEnfants = 0;
    @Min(0) @Max(100) private Integer trimestresMajorationEnfants;
    private boolean tauxPleinParStatut = false;
    private TauxCsg tauxCsg = TauxCsg.NORMAL;

    private boolean conjointDecede = false;
    @DecimalMin("0") private BigDecimal pensionBaseConjoint;
    @DecimalMin("0") private BigDecimal pointsConjoint;
    private boolean remarie = false;
    @DecimalMin("0") private BigDecimal autresRessources;

    public String getPrenom() { return prenom; } public void setPrenom(String v) { prenom = v; }
    public String getNom() { return nom; } public void setNom(String v) { nom = v; }
    public LocalDate getDateNaissance() { return dateNaissance; } public void setDateNaissance(LocalDate v) { dateNaissance = v; }
    public Sexe getSexe() { return sexe; } public void setSexe(Sexe v) { sexe = v; }
    public LocalDate getDateDepart() { return dateDepart; } public void setDateDepart(LocalDate v) { dateDepart = v; }
    public String getCarriereCsv() { return carriereCsv; } public void setCarriereCsv(String v) { carriereCsv = v; }
    public int getTrimestresAutresRegimes() { return trimestresAutresRegimes; } public void setTrimestresAutresRegimes(int v) { trimestresAutresRegimes = v; }
    public BigDecimal getSalaireProjete() { return salaireProjete; } public void setSalaireProjete(BigDecimal v) { salaireProjete = v; }
    public int getNombreEnfants() { return nombreEnfants; } public void setNombreEnfants(int v) { nombreEnfants = v; }
    public Integer getTrimestresMajorationEnfants() { return trimestresMajorationEnfants; } public void setTrimestresMajorationEnfants(Integer v) { trimestresMajorationEnfants = v; }
    public boolean isTauxPleinParStatut() { return tauxPleinParStatut; } public void setTauxPleinParStatut(boolean v) { tauxPleinParStatut = v; }
    public TauxCsg getTauxCsg() { return tauxCsg; } public void setTauxCsg(TauxCsg v) { tauxCsg = v; }
    public boolean isConjointDecede() { return conjointDecede; } public void setConjointDecede(boolean v) { conjointDecede = v; }
    public BigDecimal getPensionBaseConjoint() { return pensionBaseConjoint; } public void setPensionBaseConjoint(BigDecimal v) { pensionBaseConjoint = v; }
    public BigDecimal getPointsConjoint() { return pointsConjoint; } public void setPointsConjoint(BigDecimal v) { pointsConjoint = v; }
    public boolean isRemarie() { return remarie; } public void setRemarie(boolean v) { remarie = v; }
    public BigDecimal getAutresRessources() { return autresRessources; } public void setAutresRessources(BigDecimal v) { autresRessources = v; }
}
