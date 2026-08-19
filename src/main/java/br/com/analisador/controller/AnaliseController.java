package br.com.analisador.controller;

import br.com.analisador.model.RelatorioAnalise;
import br.com.analisador.service.AnaliseService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;
import java.util.regex.Pattern;

@Controller
public class AnaliseController {

    private static final Pattern TICKER_PATTERN = Pattern.compile("^[A-Za-z0-9]{1,10}$");

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

        String tickerLimpo = ticker.trim().toUpperCase();
        if (!TICKER_PATTERN.matcher(tickerLimpo).matches()) {
            model.addAttribute("erro", "Ticker inválido — use apenas letras e números (ex: PETR4, VALE3)");
            return "index";
        }

        Optional<RelatorioAnalise> relatorio = analiseService.analisar(tickerLimpo);

        if (relatorio.isEmpty()) {
            model.addAttribute("erro", "Ticker '" + tickerLimpo + "' não encontrado ou sem dados");
            return "index";
        }

        model.addAttribute("relatorio", relatorio.get());
        return "resultado";
    }
}
