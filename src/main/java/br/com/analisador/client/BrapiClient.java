package br.com.analisador.client;

import br.com.analisador.exception.BrapiAutenticacaoException;
import br.com.analisador.exception.BrapiException;
import br.com.analisador.exception.BrapiIndisponivelException;
import br.com.analisador.exception.BrapiLimiteExcedidoException;
import br.com.analisador.exception.TickerNaoEncontradoException;
import br.com.analisador.model.BrapiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

@Component
public class BrapiClient {

    private static final Logger log = LoggerFactory.getLogger(BrapiClient.class);

    private final WebClient webClient;
    private final String token;
    private final Duration timeout;

    public BrapiClient(WebClient brapiWebClient,
                       @Value("${brapi.token}") String token,
                       @Value("${brapi.timeout-seconds:10}") long timeoutSeconds) {
        this.webClient = brapiWebClient;
        this.token = token;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    /**
     * Busca cotacao + fundamentos + modulos avancados (financialData, defaultKeyStatistics).
     *
     * @throws TickerNaoEncontradoException  ticker inexistente ou sem dados (404 / lista vazia)
     * @throws BrapiAutenticacaoException    token ausente ou sem permissao (401 / 403)
     * @throws BrapiLimiteExcedidoException  limite de requisicoes atingido (429)
     * @throws BrapiIndisponivelException    timeout, falha de rede ou erro inesperado da BRAPI
     */
    public BrapiResponse.QuoteResult buscarCotacao(String ticker) {
        String simbolo = ticker.toUpperCase();
        BrapiResponse response;
        try {
            response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/quote/{ticker}")
                            .queryParam("fundamental", "true")
                            .queryParam("modules", "defaultKeyStatistics,financialData")
                            .build(simbolo))
                    .headers(headers -> {
                        if (token != null && !token.isBlank()) {
                            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                        }
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(body -> traduzirErroHttp(simbolo, clientResponse.statusCode(), body)))
                    .bodyToMono(BrapiResponse.class)
                    .timeout(timeout)
                    .block();

        } catch (BrapiException e) {
            throw e;
        } catch (WebClientRequestException e) {
            throw new BrapiIndisponivelException(
                    "Falha de rede ao consultar a BRAPI para " + simbolo, e);
        } catch (WebClientResponseException e) {
            throw traduzirErroHttp(simbolo, e.getStatusCode(), e.getResponseBodyAsString());
        } catch (RuntimeException e) {
            // inclui TimeoutException encapsulada pelo block() e falhas de desserializacao
            throw new BrapiIndisponivelException(
                    "Erro inesperado ao consultar a BRAPI para " + simbolo, e);
        }

        if (response == null || response.results() == null || response.results().isEmpty()) {
            throw new TickerNaoEncontradoException(simbolo);
        }
        return response.results().get(0);
    }

    private BrapiException traduzirErroHttp(String simbolo, HttpStatusCode status, String body) {
        String detalhe = "BRAPI respondeu " + status + " para " + simbolo + ": " + resumir(body);
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            log.info(detalhe);
            return new TickerNaoEncontradoException(simbolo);
        }
        if (status.value() == HttpStatus.UNAUTHORIZED.value() || status.value() == HttpStatus.FORBIDDEN.value()) {
            return new BrapiAutenticacaoException(detalhe);
        }
        if (status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return new BrapiLimiteExcedidoException(detalhe);
        }
        return new BrapiIndisponivelException(detalhe, null);
    }

    private String resumir(String body) {
        if (body == null || body.isBlank()) {
            return "<corpo vazio>";
        }
        return body.length() > 500 ? body.substring(0, 500) + "..." : body;
    }
}
