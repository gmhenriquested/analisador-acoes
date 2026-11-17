package br.com.analisador.controller;

import br.com.analisador.exception.BrapiException;
import br.com.analisador.exception.TickerNaoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Converte falhas em mensagens amigaveis na propria pagina inicial,
 * garantindo que nada chegue ao usuario como pagina de erro generica
 * e que toda falha inesperada fique registrada no log com stack trace.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TickerNaoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String tickerNaoEncontrado(TickerNaoEncontradoException e, Model model) {
        log.info("Ticker nao encontrado: {}", e.getTicker());
        model.addAttribute("erro", e.getMensagemUsuario());
        return "index";
    }

    @ExceptionHandler(BrapiException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public String falhaBrapi(BrapiException e, Model model) {
        log.error("Falha ao consultar a BRAPI: {}", e.getMessage(), e);
        model.addAttribute("erro", e.getMensagemUsuario());
        return "index";
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String parametroAusente(MissingServletRequestParameterException e, Model model) {
        model.addAttribute("erro", "Informe um ticker (ex: PETR4, VALE3)");
        return "index";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String erroInesperado(Exception e, Model model) {
        log.error("Erro inesperado ao processar a requisicao", e);
        model.addAttribute("erro", "Ocorreu um erro inesperado ao gerar a análise. Tente novamente.");
        return "index";
    }
}
