package br.com.analisador.controller;

import br.com.analisador.exception.BrapiException;
import br.com.analisador.exception.TickerNaoEncontradoException;
import br.com.analisador.model.RelatorioAnalise;
import br.com.analisador.service.AnaliseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * API JSON da analise. Mesma logica da UI web, mas resposta em JSON, pensada para
 * ser consumida por outros programas e por agentes de IA (um servidor MCP pode
 * expor esta analise como ferramenta de agente).
 *
 * Erros sao tratados aqui mesmo e devolvidos como JSON, para nao cair no
 * {@link GlobalExceptionHandler}, que renderiza paginas HTML da UI.
 */
@RestController
@RequestMapping("/api")
public class ApiAnaliseController {

    private static final Logger log = LoggerFactory.getLogger(ApiAnaliseController.class);

    /** Tickers da B3: 4 letras + 1 ou 2 digitos, com sufixo opcional F (fracionario). */
    private static final Pattern TICKER_VALIDO = Pattern.compile("^[A-Z]{4}\\d{1,2}F?$");

    private final AnaliseService analiseService;

    public ApiAnaliseController(AnaliseService analiseService) {
        this.analiseService = analiseService;
    }

    @GetMapping("/analise/{ticker}")
    public ResponseEntity<?> analisar(@PathVariable("ticker") String ticker) {
        String simbolo = ticker == null ? "" : ticker.trim().toUpperCase();
        if (!TICKER_VALIDO.matcher(simbolo).matches()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Ticker '" + simbolo + "' invalido. Use o formato da B3, ex: PETR4, VALE3, ITUB4."));
        }

        log.info("API analisando ticker {}", simbolo);
        try {
            RelatorioAnalise relatorio = analiseService.analisar(simbolo);
            return ResponseEntity.ok(relatorio);
        } catch (TickerNaoEncontradoException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", e.getMensagemUsuario()));
        } catch (BrapiException e) {
            log.error("Falha ao consultar a BRAPI: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("erro", e.getMensagemUsuario()));
        }
    }
}
