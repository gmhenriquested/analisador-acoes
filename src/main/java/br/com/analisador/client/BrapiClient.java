package br.com.analisador.client;

import br.com.analisador.model.BrapiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Optional;

@Component
public class BrapiClient {

    private static final Logger log = LoggerFactory.getLogger(BrapiClient.class);

    private final WebClient webClient;
    private final String token;

    public BrapiClient(WebClient brapiWebClient,
                       @Value("${brapi.token}") String token) {
        this.webClient = brapiWebClient;
        this.token = token;
    }

    /**
     * Busca cotacao + fundamentos + modulos avancados (financialData, defaultKeyStatistics).
     * Retorna Optional vazio se o ticker nao existir (404) ou der erro.
     */
    public Optional<BrapiResponse.QuoteResult> buscarCotacao(String ticker) {
        try {
            BrapiResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/quote/{ticker}")
                            .queryParam("fundamental", "true")
                            .queryParam("modules", "defaultKeyStatistics,financialData")
                            .build(ticker.toUpperCase()))
                    .headers(headers -> {
                        if (token != null && !token.isBlank()) {
                            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                        }
                    })
                    .retrieve()
                    .bodyToMono(BrapiResponse.class)
                    .block();

            if (response == null || response.results() == null || response.results().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(response.results().get(0));

        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (WebClientResponseException e) {
            log.warn("Erro BRAPI ao buscar {}: status {}", ticker, e.getStatusCode());
            return Optional.empty();
        }
    }
}
