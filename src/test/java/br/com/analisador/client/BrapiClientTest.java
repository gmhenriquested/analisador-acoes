package br.com.analisador.client;

import br.com.analisador.model.BrapiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrapiClientTest {

    private final List<ClientRequest> requisicoes = new ArrayList<>();

    @Test
    void montaUriComTickerMaiusculoEQueryParams() {
        BrapiClient client = clientQueResponde(HttpStatus.OK, corpoComResultado("PETR4"), "");

        client.buscarCotacao("petr4");

        assertThat(requisicoes).hasSize(1);
        assertThat(requisicoes.get(0).url().toString())
                .contains("/api/quote/PETR4")
                .contains("fundamental=true")
                .contains("modules=defaultKeyStatistics,financialData");
    }

    @Test
    void enviaAuthorizationQuandoTokenConfigurado() {
        BrapiClient client = clientQueResponde(HttpStatus.OK, corpoComResultado("PETR4"), "meu-token");

        client.buscarCotacao("PETR4");

        assertThat(requisicoes.get(0).headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer meu-token");
    }

    @Test
    void naoEnviaAuthorizationQuandoTokenEmBranco() {
        BrapiClient client = clientQueResponde(HttpStatus.OK, corpoComResultado("PETR4"), "   ");

        client.buscarCotacao("PETR4");

        assertThat(requisicoes.get(0).headers().containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    void naoEnviaAuthorizationQuandoTokenNulo() {
        BrapiClient client = clientQueResponde(HttpStatus.OK, corpoComResultado("PETR4"), null);

        client.buscarCotacao("PETR4");

        assertThat(requisicoes.get(0).headers().containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    void deserializaPrimeiroResultadoIgnorandoCamposDesconhecidos() {
        String body = """
                {"results":[
                  {
                    "symbol":"PETR4",
                    "shortName":"PETROBRAS PN",
                    "longName":"Petroleo Brasileiro SA",
                    "regularMarketPrice":38.5,
                    "priceEarnings":8.1,
                    "campoDesconhecido":"ignorado",
                    "defaultKeyStatistics":{"priceToBook":0.9,"campoNovo":1},
                    "financialData":{"returnOnEquity":0.2,"currentRatio":1.8}
                  },
                  {"symbol":"VALE3"}
                ]}
                """;
        BrapiClient client = clientQueResponde(HttpStatus.OK, body, "");

        BrapiResponse.QuoteResult r = client.buscarCotacao("PETR4").orElseThrow();

        assertThat(r.symbol()).isEqualTo("PETR4");
        assertThat(r.longName()).isEqualTo("Petroleo Brasileiro SA");
        assertThat(r.priceEarnings()).isEqualTo(8.1);
        assertThat(r.defaultKeyStatistics().priceToBook()).isEqualTo(0.9);
        assertThat(r.financialData().returnOnEquity()).isEqualTo(0.2);
        assertThat(r.financialData().currentRatio()).isEqualTo(1.8);
    }

    @Test
    void retornaVazioQuandoResultsVazio() {
        BrapiClient client = clientQueResponde(HttpStatus.OK, "{\"results\":[]}", "");

        assertThat(client.buscarCotacao("PETR4")).isEmpty();
    }

    @Test
    void retornaVazioQuandoResultsNulo() {
        BrapiClient client = clientQueResponde(HttpStatus.OK, "{}", "");

        assertThat(client.buscarCotacao("PETR4")).isEmpty();
    }

    @Test
    void propagaErroQuandoBrapiRetorna404() {
        BrapiClient client = clientQueResponde(HttpStatus.NOT_FOUND, "{\"error\":\"not found\"}", "");

        assertThatThrownBy(() -> client.buscarCotacao("ZZZZ9"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Erro BRAPI")
                .hasMessageContaining("404");
    }

    @Test
    void propagaErroQuandoBrapiFalha() {
        BrapiClient client = clientQueResponde(HttpStatus.INTERNAL_SERVER_ERROR, "boom", "");

        assertThatThrownBy(() -> client.buscarCotacao("PETR4"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Erro BRAPI")
                .hasMessageContaining("boom");
    }

    private BrapiClient clientQueResponde(HttpStatus status, String body, String token) {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://brapi.test")
                .exchangeFunction(request -> {
                    requisicoes.add(request);
                    return Mono.just(ClientResponse.create(status)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(body)
                            .build());
                })
                .build();
        return new BrapiClient(webClient, token);
    }

    private static String corpoComResultado(String ticker) {
        return "{\"results\":[{\"symbol\":\"" + ticker + "\"}]}";
    }
}
