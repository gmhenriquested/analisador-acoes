package br.com.analisador.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${brapi.base-url}")
    private String brapiBaseUrl;

    @Bean
    public WebClient brapiWebClient() {
        return WebClient.builder()
                .baseUrl(brapiBaseUrl)
                .build();
    }
}
