package fr.retraiteplus.service;

import fr.retraiteplus.engine.*;
import fr.retraiteplus.model.SimulationForm;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** Fait le lien entre le formulaire web et le moteur de calcul (indépendant de Spring). */
@Service
public class RetirementCalculatorService {

    private final MoteurRetraite moteur = new MoteurRetraite(Parametres.chargerDepuisClasspath());

    /** Lit la carrière ; renvoie la liste des erreurs de saisie (vide si tout est correct). */
    public CarriereParser.Resultat lireCarriere(String csv) {
        return CarriereParser.lire(csv);
    }

    public ResultatSimulation simuler(SimulationForm f, List<LigneCarriere> carriere) {
        DonneesSimulation d = new DonneesSimulation();
        d.prenom = f.getPrenom() == null ? "" : f.getPrenom().trim();
        d.nom = f.getNom() == null ? "" : f.getNom().trim();
        d.dateNaissance = f.getDateNaissance();
        d.sexe = f.getSexe();
        d.dateDepart = f.getDateDepart();
        d.dateCalcul = LocalDate.now();
        d.carriere = carriere;
        d.trimestresAutresRegimes = f.getTrimestresAutresRegimes();
        d.salaireProjete = f.getSalaireProjete();
        d.nombreEnfants = f.getNombreEnfants();
        d.trimestresMajorationEnfants = f.getTrimestresMajorationEnfants();
        d.tauxPleinParStatut = f.isTauxPleinParStatut();
        d.tauxCsg = f.getTauxCsg();
        d.conjointDecede = f.isConjointDecede();
        d.pensionBaseConjoint = f.getPensionBaseConjoint();
        d.pointsConjoint = f.getPointsConjoint();
        d.remarie = f.isRemarie();
        d.autresRessources = f.getAutresRessources();
        return moteur.simuler(d);
    }

    /** Formulaire pré-rempli avec le cas pratique du TD (M. Ousmane). */
    public SimulationForm exempleOusmane() {
        SimulationForm f = new SimulationForm();
        f.setPrenom("");
        f.setNom("OUSMANE");
        f.setDateNaissance(LocalDate.of(1963, 1, 6));
        f.setDateDepart(LocalDate.of(2025, 2, 1));
        f.setNombreEnfants(1);
        f.setConjointDecede(true);
        f.setCarriereCsv(exempleCsv());
        return f;
    }

    public String exempleCsv() {
        try (InputStream in = getClass().getResourceAsStream("/exemples/ousmane.csv")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | NullPointerException e) {
            return "";
        }
    }
}
