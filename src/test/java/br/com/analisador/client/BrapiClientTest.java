package br.com.analisador.client;

import br.com.analisador.exception.BrapiAutenticacaoException;
import br.com.analisador.exception.BrapiIndisponivelException;
import br.com.analisador.exception.BrapiLimiteExcedidoException;
import br.com.analisador.exception.TickerNaoEncontradoException;
import br.com.analisador.model.BrapiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;

class BrapiClientTest {

    private BrapiClient clientQueResponde(Function<Void, ClientResponse> resposta) {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://brapi.dev")
                .exchangeFunction(request -> Mono.just(resposta.apply(null)))
                .build();
        return new BrapiClient(webClient, "", 5);
    }

    private BrapiClient clientQueFalha(Throwable erro) {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://brapi.dev")
                .exchangeFunction(request -> Mono.error(erro))
                .build();
        return new BrapiClient(webClient, "", 5);
    }

    private ClientResponse resposta(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    @Test
    void retornaPrimeiroResultadoQuandoBrapiRespondeComDados() {
        BrapiClient client = clientQueResponde(v -> resposta(HttpStatus.OK,
                "{\"results\":[{\"symbol\":\"PETR4\",\"regularMarketPrice\":38.5}]}"));

        BrapiResponse.QuoteResult result = client.buscarCotacao("petr4");

        assertThat(result.symbol()).isEqualTo("PETR4");
        assertThat(result.regularMarketPrice()).isEqualTo(38.5);
    }

    @Test
    void lancaTickerNaoEncontradoEm404() {
        BrapiClient client = clientQueResponde(v -> resposta(HttpStatus.NOT_FOUND, "{\"error\":true}"));

        assertThatThrownBy(() -> client.buscarCotacao("XXXX9"))
                .isInstanceOf(TickerNaoEncontradoException.class);
    }

    @Test
    void lancaTickerNaoEncontradoQuandoListaDeResultadosVemVazia() {
        BrapiClient client = clientQueResponde(v -> resposta(HttpStatus.OK, "{\"results\":[]}"));

        assertThatThrownBy(() -> client.buscarCotacao("PETR4"))
                .isInstanceOf(TickerNaoEncontradoException.class);
    }

    @Test
    void lancaAutenticacaoEm401() {
        BrapiClient client = clientQueResponde(v -> resposta(HttpStatus.UNAUTHORIZED, "no token"));

        assertThatThrownBy(() -> client.buscarCotacao("WEGE3"))
                .isInstanceOf(BrapiAutenticacaoException.class);
    }

    @Test
    void lancaLimiteExcedidoEm429() {
        BrapiClient client = clientQueResponde(v -> resposta(HttpStatus.TOO_MANY_REQUESTS, "slow down"));

        assertThatThrownBy(() -> client.buscarCotacao("PETR4"))
                .isInstanceOf(BrapiLimiteExcedidoException.class);
    }

    @Test
    void lancaIndisponivelEm500() {
        BrapiClient client = clientQueResponde(v -> resposta(HttpStatus.INTERNAL_SERVER_ERROR, "boom"));

        assertThatThrownBy(() -> client.buscarCotacao("PETR4"))
                .isInstanceOf(BrapiIndisponivelException.class);
    }

    @Test
    void lancaIndisponivelQuandoARedeFalha() {
        BrapiClient client = clientQueFalha(new WebClientRequestException(
                new IOException("connection refused"), GET, URI.create("https://brapi.dev"), new HttpHeaders()));

        assertThatThrownBy(() -> client.buscarCotacao("PETR4"))
                .isInstanceOf(BrapiIndisponivelException.class)
                .hasCauseInstanceOf(WebClientRequestException.class);
    }

    @Test
    void lancaIndisponivelQuandoCorpoNaoPodeSerLido() {
        BrapiClient client = clientQueResponde(v -> resposta(HttpStatus.OK, "isto nao e json"));

        assertThatThrownBy(() -> client.buscarCotacao("PETR4"))
                .isInstanceOf(BrapiIndisponivelException.class);
    }
}
