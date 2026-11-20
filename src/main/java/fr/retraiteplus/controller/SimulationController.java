package fr.retraiteplus.controller;

import fr.retraiteplus.model.*;
import fr.retraiteplus.service.RetirementCalculatorService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class SimulationController {
    private final RetirementCalculatorService calculator;
    public SimulationController(RetirementCalculatorService calculator){this.calculator=calculator;}

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("form", new SimulationForm());
        return "index";
    }

    @PostMapping("/simuler")
    public String simulate(@Valid @ModelAttribute("form") SimulationForm form,
                           BindingResult errors, Model model) {
        if (errors.hasErrors()) {
            model.addAttribute("errorMessage", "Vérifiez les champs : certaines valeurs sont invalides.");
            return "index";
        }
        model.addAttribute("result", calculator.calculate(form));
        model.addAttribute("personName", form.getFirstName() + " " + form.getLastName());
        model.addAttribute("form", form);
        return "resultat";
    }
}
