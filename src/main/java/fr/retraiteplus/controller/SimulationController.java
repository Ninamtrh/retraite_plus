package fr.retraiteplus.controller;

import fr.retraiteplus.engine.CarriereParser;
import fr.retraiteplus.engine.DonneesSimulation;
import fr.retraiteplus.engine.ResultatSimulation;
import fr.retraiteplus.model.SimulationForm;
import fr.retraiteplus.service.RetirementCalculatorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Controller
public class SimulationController {
    private final RetirementCalculatorService service;

    public SimulationController(RetirementCalculatorService service) { this.service = service; }

    @ModelAttribute
    public void listes(Model model) {
        model.addAttribute("sexes", DonneesSimulation.Sexe.values());
        model.addAttribute("tauxCsgListe", DonneesSimulation.TauxCsg.values());
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("form", new SimulationForm());
        return "index";
    }

    @GetMapping("/exemple/ousmane")
    public String exemple(Model model) {
        model.addAttribute("form", service.exempleOusmane());
        model.addAttribute("infoMessage", "Cas pratique M. Ousmane chargé : vérifiez les données puis lancez le calcul.");
        return "index";
    }

    @GetMapping(value = "/modele-carriere.csv", produces = "text/csv")
    public ResponseEntity<byte[]> modele() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modele-carriere.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(service.exempleCsv().getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/simuler")
    public String simulate(@Valid @ModelAttribute("form") SimulationForm form, BindingResult errors,
                           @RequestParam(value = "fichierCarriere", required = false) MultipartFile fichier,
                           Model model) {
        List<String> messages = new ArrayList<>();
        if (fichier != null && !fichier.isEmpty()) {
            try {
                form.setCarriereCsv(new String(fichier.getBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                messages.add("Impossible de lire le fichier importé.");
            }
        }
        if (errors.hasErrors()) {
            errors.getFieldErrors().forEach(e -> messages.add(e.getField() + " : " + e.getDefaultMessage()));
        }
        CarriereParser.Resultat carriere = service.lireCarriere(form.getCarriereCsv());
        messages.addAll(carriere.erreurs());
        if (form.getDateNaissance() != null && form.getDateDepart() != null
                && !form.getDateDepart().isAfter(form.getDateNaissance().plusYears(50)))
            messages.add("La date de départ doit être postérieure aux 50 ans de l'assuré.");
        if (!messages.isEmpty()) {
            model.addAttribute("errorMessages", messages);
            return "index";
        }
        try {
            ResultatSimulation r = service.simuler(form, carriere.lignes());
            model.addAttribute("r", r);
            model.addAttribute("form", form);
            return "resultat";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessages", List.of(e.getMessage()));
            return "index";
        }
    }
}
