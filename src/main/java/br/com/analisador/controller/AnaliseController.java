package br.com.analisador.controller;

import br.com.analisador.model.RelatorioAnalise;
import br.com.analisador.service.AnaliseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.regex.Pattern;

@Controller
public class AnaliseController {

    private static final Logger log = LoggerFactory.getLogger(AnaliseController.class);

    /** Tickers da B3: 4 letras + 1 ou 2 digitos, com sufixo opcional F (fracionario). */
    private static final Pattern TICKER_VALIDO = Pattern.compile("^[A-Z]{4}\\d{1,2}F?$");

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

        String simbolo = ticker.trim().toUpperCase();
        if (!TICKER_VALIDO.matcher(simbolo).matches()) {
            model.addAttribute("erro",
                    "Ticker '" + simbolo + "' inválido. Use o formato da B3, ex: PETR4, VALE3, ITUB4.");
            return "index";
        }

        log.info("Analisando ticker {}", simbolo);
        RelatorioAnalise relatorio = analiseService.analisar(simbolo);
        model.addAttribute("relatorio", relatorio);
        return "resultado";
    }
}
