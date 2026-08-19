package br.com.analisador.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WebClientConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebClientAutoConfiguration.class))
            .withUserConfiguration(WebClientConfig.class);

    @Test
    void criaWebClientQueUsaBaseUrlConfigurada() {
        runner.withPropertyValues("brapi.base-url=https://brapi.test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(WebClient.class);

                    AtomicReference<String> uriChamada = new AtomicReference<>();
                    context.getBean("brapiWebClient", WebClient.class)
                            .mutate()
                            .exchangeFunction(request -> {
                                uriChamada.set(request.url().toString());
                                return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                            })
                            .build()
                            .get()
                            .uri("/api/quote/PETR4")
                            .retrieve()
                            .toBodilessEntity()
                            .block();

                    assertThat(uriChamada.get()).isEqualTo("https://brapi.test/api/quote/PETR4");
                });
    }
}
