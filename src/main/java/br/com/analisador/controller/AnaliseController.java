package br.com.analisador.controller;

import br.com.analisador.model.RelatorioAnalise;
import br.com.analisador.service.AnaliseService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
public class AnaliseController {

    private final AnaliseService analiseService;

    public AnaliseController(AnaliseService analiseService) {
        this.analiseService = analiseService;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/analisar")
    public String analisar(@RequestParam("ticker") String ticker, Model model) {
        if (ticker == null || ticker.isBlank()) {
            model.addAttribute("erro", "Informe um ticker (ex: PETR4, VALE3)");
            return "index";
        }

        Optional<RelatorioAnalise> relatorio = analiseService.analisar(ticker.trim());

        if (relatorio.isEmpty()) {
            model.addAttribute("erro", "Ticker '" + ticker.toUpperCase() + "' não encontrado ou sem dados");
            return "index";
        }

        model.addAttribute("relatorio", relatorio.get());
        return "resultado";
    }
}
