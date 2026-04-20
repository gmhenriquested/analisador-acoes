package br.com.analisador.client;

import br.com.analisador.model.BrapiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Optional;

@Component
public class BrapiClient {

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
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> reactor.core.publisher.Mono.error(
                                            new RuntimeException("Erro BRAPI: " + clientResponse.statusCode() + " - " + body))))
                    .bodyToMono(BrapiResponse.class)
                    .block();

            if (response == null || response.results() == null || response.results().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(response.results().get(0));

        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        }
    }
}
