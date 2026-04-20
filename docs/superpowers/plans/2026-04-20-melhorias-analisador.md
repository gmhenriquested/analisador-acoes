# Melhorias do Analisador de Ações - Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Elevar o analisador de ações de protótipo funcional a aplicação Spring Boot de qualidade portfolio, demonstrando testes, cache, limiares por setor e comparação multi-stock.

**Architecture:** Quatro fases em ordem de dependência — Phase 1 corrige o uso indevido de WebFlux e estabelece cobertura de testes; Phase 2 adiciona Caffeine cache e limiares fundamentalistas por setor; Phase 3 entrega comparação multi-stock com Chart.js; Phase 4 polishes UX com exceções customizadas, validação B3 e navegação aprimorada.

**Tech Stack:** Spring Boot 3.3.4, Java 17, `RestClient` (incluído em `spring-boot-starter-web`), Caffeine (`com.github.ben-manes.caffeine:caffeine:3.1.8`), `spring-boot-starter-cache`, Thymeleaf, Chart.js CDN, Mockito (via `spring-boot-starter-test`)

---

## File Map

**Deletar:**
- `src/main/java/br/com/analisador/config/WebClientConfig.java`

**Criar:**
- `src/main/java/br/com/analisador/config/RestClientConfig.java`
- `src/main/java/br/com/analisador/config/CacheConfig.java`
- `src/main/java/br/com/analisador/model/SectorThresholds.java`
- `src/main/java/br/com/analisador/model/SectorConfig.java`
- `src/main/java/br/com/analisador/exception/TickerNotFoundException.java`
- `src/main/java/br/com/analisador/exception/ApiUnavailableException.java`
- `src/main/java/br/com/analisador/controller/GlobalExceptionHandler.java`
- `src/main/resources/templates/comparar.html`
- `src/main/resources/templates/error.html`
- `src/test/java/br/com/analisador/service/AnaliseServiceTest.java`

**Modificar:**
- `pom.xml` — remover webflux, adicionar caffeine + cache
- `src/main/java/br/com/analisador/AnalisadorAcoesApplication.java` — adicionar `@EnableCaching`
- `src/main/java/br/com/analisador/client/BrapiClient.java` — substituir WebClient por RestClient
- `src/main/java/br/com/analisador/service/AnaliseService.java` — cache, limiares por setor, `analisarMultiplos`, exceções
- `src/main/java/br/com/analisador/controller/AnaliseController.java` — `/comparar`, validação, exceções
- `src/main/java/br/com/analisador/model/BrapiResponse.java` — adicionar campo `sector`
- `src/main/resources/application.properties` — config cache
- `src/main/resources/templates/index.html` — link para comparação
- `src/main/resources/templates/resultado.html` — mini form, badge setor, loading
- `src/main/resources/static/css/style.css` — estilos loading, badge setor, comparação

---

## PHASE 1: RestClient Migration + Unit Tests

### Task 1: Substituir WebFlux por RestClient

**Files:**
- Modify: `pom.xml`
- Delete: `src/main/java/br/com/analisador/config/WebClientConfig.java`
- Create: `src/main/java/br/com/analisador/config/RestClientConfig.java`
- Modify: `src/main/java/br/com/analisador/model/BrapiResponse.java`
- Modify: `src/main/java/br/com/analisador/client/BrapiClient.java`

- [ ] **Step 1: Atualizar pom.xml**

Substituir o bloco `spring-boot-starter-webflux` pelo trecho de comentário apenas — `spring-boot-starter-web` já inclui `RestClient` desde Spring Boot 3.2. O `pom.xml` completo das `<dependencies>` fica:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <!-- REMOVIDO: spring-boot-starter-webflux (usava .block() anulando reatividade) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-thymeleaf</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

- [ ] **Step 2: Criar RestClientConfig.java**

Criar `src/main/java/br/com/analisador/config/RestClientConfig.java`:

```java
package br.com.analisador.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Value("${brapi.base-url}")
    private String brapiBaseUrl;

    @Bean
    public RestClient brapiRestClient() {
        return RestClient.builder()
                .baseUrl(brapiBaseUrl)
                .build();
    }
}
```

- [ ] **Step 3: Adicionar campo `sector` ao BrapiResponse.QuoteResult**

Adicionar `String sector` como último campo em `src/main/java/br/com/analisador/model/BrapiResponse.java`:

```java
package br.com.analisador.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BrapiResponse(List<QuoteResult> results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuoteResult(
            String symbol,
            String shortName,
            String longName,
            String currency,
            Double regularMarketPrice,
            Double regularMarketChangePercent,
            Long marketCap,
            Double priceEarnings,
            Double earningsPerShare,
            String logourl,
            DefaultKeyStatistics defaultKeyStatistics,
            FinancialData financialData,
            String sector                          // setor para limiares específicos (Phase 2)
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DefaultKeyStatistics(
            Double forwardPE,
            Double priceToBook,
            Double enterpriseToEbitda,
            Double bookValue,
            Double trailingEps,
            Double profitMargins
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FinancialData(
            Double currentPrice,
            Double returnOnEquity,
            Double returnOnAssets,
            Double debtToEquity,
            Double currentRatio,
            Double grossMargins,
            Double operatingMargins,
            Double profitMargins,
            Double revenueGrowth,
            Double earningsGrowth
    ) {}
}
```

- [ ] **Step 4: Reescrever BrapiClient com RestClient**

Substituir todo o conteúdo de `src/main/java/br/com/analisador/client/BrapiClient.java`:

```java
package br.com.analisador.client;

import br.com.analisador.model.BrapiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class BrapiClient {

    private final RestClient restClient;
    private final String token;

    public BrapiClient(RestClient brapiRestClient,
                       @Value("${brapi.token}") String token) {
        this.restClient = brapiRestClient;
        this.token = token;
    }

    public Optional<BrapiResponse.QuoteResult> buscarCotacao(String ticker) {
        try {
            BrapiResponse response = restClient.get()
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
                    .body(BrapiResponse.class);

            if (response == null || response.results() == null || response.results().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(response.results().get(0));

        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
```

- [ ] **Step 5: Deletar WebClientConfig.java**

```bash
rm src/main/java/br/com/analisador/config/WebClientConfig.java
```

- [ ] **Step 6: Verificar que o projeto compila e sobe**

```bash
./mvnw spring-boot:run -q &
sleep 8
curl -s http://localhost:8080/ | grep -c "Analisador"
kill %1
```

Saída esperada: `1` (encontrou a palavra no HTML)

- [ ] **Step 7: Commit**

```bash
git add pom.xml \
    src/main/java/br/com/analisador/config/RestClientConfig.java \
    src/main/java/br/com/analisador/client/BrapiClient.java \
    src/main/java/br/com/analisador/model/BrapiResponse.java
git rm src/main/java/br/com/analisador/config/WebClientConfig.java
git commit -m "refactor: replace WebFlux/WebClient with synchronous RestClient

Remove spring-boot-starter-webflux which was being misused (.block()
on Mono defeats reactive I/O). RestClient is included in spring-boot-starter-web.
Add sector field to BrapiResponse.QuoteResult for Phase 2 sector thresholds."
```

---

### Task 2: Testes unitários — setup + P/L + P/VP (11 testes)

**Files:**
- Create: `src/test/java/br/com/analisador/service/AnaliseServiceTest.java`

- [ ] **Step 1: Criar AnaliseServiceTest com setup e testes de P/L**

Criar `src/test/java/br/com/analisador/service/AnaliseServiceTest.java`:

```java
package br.com.analisador.service;

import br.com.analisador.client.BrapiClient;
import br.com.analisador.model.BrapiResponse;
import br.com.analisador.model.MetricaAvaliada;
import br.com.analisador.model.MetricaAvaliada.Status;
import br.com.analisador.model.RelatorioAnalise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnaliseServiceTest {

    @Mock
    BrapiClient brapiClient;

    @InjectMocks
    AnaliseService service;

    // -------- HELPER --------

    /**
     * Cria um QuoteResult com apenas os campos relevantes para cada métrica.
     * Passar null para campos não relevantes ao teste.
     * Ordem: pl, pvp, roe, dp (em %, ex: 30.0 = 0.3x), lc, ml (decimal), cr (decimal)
     */
    private BrapiResponse.QuoteResult makeQuote(Double pl, Double pvp, Double roe,
                                                Double dp, Double lc, Double ml, Double cr) {
        BrapiResponse.DefaultKeyStatistics stats =
                new BrapiResponse.DefaultKeyStatistics(null, pvp, null, null, null, null);
        BrapiResponse.FinancialData fin =
                new BrapiResponse.FinancialData(null, roe, null, dp, lc, null, null, ml, cr, null);
        return new BrapiResponse.QuoteResult(
                "TEST3", "Test Corp", "Test Corporation SA", "BRL",
                10.0, 0.5, null, pl, null, null, stats, fin, null);
    }

    private MetricaAvaliada getMetrica(RelatorioAnalise r, int index) {
        return r.metricas().get(index);
    }

    // -------- P/L TESTES (índice 0) --------

    @Test
    void analisar_PLNegativo_retornaRuim() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(-5.0, null, null, null, null, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 0).status()).isEqualTo(Status.RUIM);
    }

    @Test
    void analisar_PLAbaixo10_retornaBom() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(5.0, null, null, null, null, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 0).status()).isEqualTo(Status.BOM);
    }

    @Test
    void analisar_PLEntre10e20_retornaNeutro() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(15.0, null, null, null, null, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 0).status()).isEqualTo(Status.NEUTRO);
    }

    @Test
    void analisar_PLAcima20_retornaRuim() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(25.0, null, null, null, null, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 0).status()).isEqualTo(Status.RUIM);
    }

    @Test
    void analisar_PLNulo_retornaIndisponivel() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, null, null, null, null, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 0).status()).isEqualTo(Status.INDISPONIVEL);
    }

    // -------- P/VP TESTES (índice 1) --------

    @Test
    void analisar_PVPAbaixo1_retornaBom() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, 0.8, null, null, null, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 1).status()).isEqualTo(Status.BOM);
    }

    @Test
    void analisar_PVPEntre1e2_retornaNeutro() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, 1.5, null, null, null, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 1).status()).isEqualTo(Status.NEUTRO);
    }

    @Test
    void analisar_PVPAcima2_retornaRuim() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, 3.0, null, null, null, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 1).status()).isEqualTo(Status.RUIM);
    }

    @Test
    void analisar_PVPNulo_retornaIndisponivel() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, null, null, null, null, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 1).status()).isEqualTo(Status.INDISPONIVEL);
    }
}
```

- [ ] **Step 2: Rodar os testes e verificar que passam**

```bash
./mvnw test -Dtest=AnaliseServiceTest -pl . -q
```

Saída esperada:
```
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
```

- [ ] **Step 3: Commit**

```bash
git add src/test/java/br/com/analisador/service/AnaliseServiceTest.java
git commit -m "test: add AnaliseServiceTest with P/L and P/VP coverage (9 tests)"
```

---

### Task 3: Testes unitários — ROE, Dívida/Patrimônio, Liquidez (12 testes)

**Files:**
- Modify: `src/test/java/br/com/analisador/service/AnaliseServiceTest.java`

- [ ] **Step 1: Adicionar testes de ROE (índice 2) ao final da classe**

Adicionar dentro da classe `AnaliseServiceTest`, antes do `}` final:

```java
    // -------- ROE TESTES (índice 2) --------

    @Test
    void analisar_ROEAcima15pct_retornaBom() {
        // 0.20 = 20% ROE
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, null, 0.20, null, null, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 2).status()).isEqualTo(Status.BOM);
    }

    @Test
    void analisar_ROEEntre10e15pct_retornaNeutro() {
        // 0.12 = 12% ROE
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, null, 0.12, null, null, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 2).status()).isEqualTo(Status.NEUTRO);
    }

    @Test
    void analisar_ROEAbaixo10pct_retornaRuim() {
        // 0.05 = 5% ROE
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, null, 0.05, null, null, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 2).status()).isEqualTo(Status.RUIM);
    }

    @Test
    void analisar_ROENulo_retornaIndisponivel() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, null, null, null, null, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 2).status()).isEqualTo(Status.INDISPONIVEL);
    }

    // -------- DÍVIDA/PATRIMÔNIO TESTES (índice 3) --------
    // BRAPI retorna em %, ex: 30.0 = 0.30x (a classe divide por 100)

    @Test
    void analisar_DividaAbaixo50pct_retornaBom() {
        // 30.0 → ratio 0.3x = BOM (< 0.5x)
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, null, null, 30.0, null, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 3).status()).isEqualTo(Status.BOM);
    }

    @Test
    void analisar_DividaEntre50e100pct_retornaNeutro() {
        // 80.0 → ratio 0.8x = NEUTRO (0.5x a 1.0x)
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, null, null, 80.0, null, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 3).status()).isEqualTo(Status.NEUTRO);
    }

    @Test
    void analisar_DividaAcima100pct_retornaRuim() {
        // 150.0 → ratio 1.5x = RUIM (> 1.0x)
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, null, null, 150.0, null, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 3).status()).isEqualTo(Status.RUIM);
    }

    @Test
    void analisar_DividaNula_retornaIndisponivel() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, null, null, null, null, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 3).status()).isEqualTo(Status.INDISPONIVEL);
    }

    // -------- LIQUIDEZ CORRENTE TESTES (índice 4) --------

    @Test
    void analisar_LiquidezAcima15_retornaBom() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, null, null, null, 2.0, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 4).status()).isEqualTo(Status.BOM);
    }

    @Test
    void analisar_LiquidezEntre10e15_retornaNeutro() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, null, null, null, 1.2, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 4).status()).isEqualTo(Status.NEUTRO);
    }

    @Test
    void analisar_LiquidezAbaixo10_retornaRuim() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, null, null, null, 0.8, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 4).status()).isEqualTo(Status.RUIM);
    }

    @Test
    void analisar_LiquidezNula_retornaIndisponivel() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, null, null, null, null, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 4).status()).isEqualTo(Status.INDISPONIVEL);
    }
```

- [ ] **Step 2: Rodar os testes e verificar que passam**

```bash
./mvnw test -Dtest=AnaliseServiceTest -pl . -q
```

Saída esperada:
```
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
```

- [ ] **Step 3: Commit**

```bash
git add src/test/java/br/com/analisador/service/AnaliseServiceTest.java
git commit -m "test: add ROE, Dívida/Patrimônio and Liquidez tests (12 more, 21 total)"
```

---

### Task 4: Testes unitários — Margem, Crescimento, Score e Integração (13 testes)

**Files:**
- Modify: `src/test/java/br/com/analisador/service/AnaliseServiceTest.java`

- [ ] **Step 1: Adicionar testes de Margem Líquida (índice 5) ao final da classe**

Adicionar dentro da classe `AnaliseServiceTest`, antes do `}` final:

```java
    // -------- MARGEM LÍQUIDA TESTES (índice 5) --------
    // ml é decimal: 0.20 = 20%, 0.08 = 8%, 0.02 = 2%

    @Test
    void analisar_MargemAcima15pct_retornaBom() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, null, null, null, null, 0.20, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 5).status()).isEqualTo(Status.BOM);
    }

    @Test
    void analisar_MargemEntre5e15pct_retornaNeutro() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, null, null, null, null, 0.08, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 5).status()).isEqualTo(Status.NEUTRO);
    }

    @Test
    void analisar_MargemAbaixo5pct_retornaRuim() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, null, null, null, null, 0.02, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 5).status()).isEqualTo(Status.RUIM);
    }

    @Test
    void analisar_MargemNula_retornaIndisponivel() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, null, null, null, null, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 5).status()).isEqualTo(Status.INDISPONIVEL);
    }

    // -------- CRESCIMENTO RECEITA TESTES (índice 6) --------
    // cr é decimal: 0.15 = 15%, -0.05 = -5%

    @Test
    void analisar_CrescimentoAcima10pct_retornaBom() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, null, null, null, null, null, 0.15)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 6).status()).isEqualTo(Status.BOM);
    }

    @Test
    void analisar_CrescimentoEntre0e10pct_retornaNeutro() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, null, null, null, null, null, 0.05)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 6).status()).isEqualTo(Status.NEUTRO);
    }

    @Test
    void analisar_CrescimentoNegativo_retornaRuim() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, null, null, null, null, null, -0.05)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 6).status()).isEqualTo(Status.RUIM);
    }

    @Test
    void analisar_CrescimentoNulo_retornaIndisponivel() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, null, null, null, null, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(getMetrica(result.get(), 6).status()).isEqualTo(Status.INDISPONIVEL);
    }

    // -------- SCORE TESTES --------

    @Test
    void analisar_TodasMetricasBom_retornaPontuacao100() {
        // pl=5(BOM), pvp=0.8(BOM), roe=0.20(BOM), dp=30(BOM), lc=2(BOM), ml=0.20(BOM), cr=0.15(BOM)
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(5.0, 0.8, 0.20, 30.0, 2.0, 0.20, 0.15)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(result.get().pontuacao()).isEqualTo(100);
        assertThat(result.get().classificacao()).isEqualTo("Potencialmente atrativa");
    }

    @Test
    void analisar_TodasMetricasRuim_retornaPontuacao0() {
        // pl=25(RUIM), pvp=3(RUIM), roe=0.05(RUIM), dp=150(RUIM), lc=0.8(RUIM), ml=0.02(RUIM), cr=-0.05(RUIM)
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(25.0, 3.0, 0.05, 150.0, 0.8, 0.02, -0.05)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(result.get().pontuacao()).isEqualTo(0);
        assertThat(result.get().classificacao()).isEqualTo("Requer cautela");
    }

    @Test
    void analisar_TodasMetricasNulas_retornaPontuacao0() {
        when(brapiClient.buscarCotacao("TEST3"))
                .thenReturn(Optional.of(makeQuote(null, null, null, null, null, null, null)));
        Optional<RelatorioAnalise> result = service.analisar("TEST3");
        assertThat(result).isPresent();
        assertThat(result.get().pontuacao()).isEqualTo(0);
    }

    // -------- INTEGRAÇÃO --------

    @Test
    void analisar_TickerNaoEncontrado_retornaEmpty() {
        when(brapiClient.buscarCotacao("XXXXX")).thenReturn(Optional.empty());
        Optional<RelatorioAnalise> result = service.analisar("XXXXX");
        assertThat(result).isEmpty();
    }

    @Test
    void analisar_TickerValido_retornaRelatorioCompleto() {
        when(brapiClient.buscarCotacao("PETR4"))
                .thenReturn(Optional.of(makeQuote(8.0, 0.9, 0.18, 40.0, 1.8, 0.17, 0.12)));
        Optional<RelatorioAnalise> result = service.analisar("PETR4");
        assertThat(result).isPresent();
        RelatorioAnalise r = result.get();
        assertThat(r.ticker()).isEqualTo("TEST3"); // symbol do makeQuote
        assertThat(r.metricas()).hasSize(7);
        assertThat(r.pontuacao()).isBetween(0, 100);
        assertThat(r.resumo()).contains("Classificação:");
    }
```

- [ ] **Step 2: Rodar todos os testes**

```bash
./mvnw test -Dtest=AnaliseServiceTest -pl . -q
```

Saída esperada:
```
[INFO] Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
```

- [ ] **Step 3: Verificar cobertura total da suite**

```bash
./mvnw test -pl . -q
```

Saída esperada: `BUILD SUCCESS` com 34 testes passando.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/br/com/analisador/service/AnaliseServiceTest.java
git commit -m "test: complete AnaliseServiceTest with 34 unit tests covering all 7 metrics, score calculation and integration paths"
```

---

## PHASE 2: Caffeine Cache + Sector-Specific Thresholds

### Task 5: Adicionar Caffeine cache

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/java/br/com/analisador/AnalisadorAcoesApplication.java`
- Create: `src/main/java/br/com/analisador/config/CacheConfig.java`
- Modify: `src/main/java/br/com/analisador/service/AnaliseService.java`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Adicionar dependências de cache ao pom.xml**

Adicionar antes de `</dependencies>` em `pom.xml`:

```xml
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-cache</artifactId>
    </dependency>
    <dependency>
        <groupId>com.github.ben-manes.caffeine</groupId>
        <artifactId>caffeine</artifactId>
        <version>3.1.8</version>
    </dependency>
```

- [ ] **Step 2: Adicionar @EnableCaching ao AnalisadorAcoesApplication**

Arquivo `src/main/java/br/com/analisador/AnalisadorAcoesApplication.java` completo:

```java
package br.com.analisador;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class AnalisadorAcoesApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalisadorAcoesApplication.class, args);
    }
}
```

- [ ] **Step 3: Criar CacheConfig.java**

Criar `src/main/java/br/com/analisador/config/CacheConfig.java`:

```java
package br.com.analisador.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    public CaffeineCacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("cotacoes");
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(200));
        return manager;
    }
}
```

- [ ] **Step 4: Adicionar @Cacheable ao AnaliseService.analisar()**

Adicionar import e anotação em `src/main/java/br/com/analisador/service/AnaliseService.java`:

Adicionar import:
```java
import org.springframework.cache.annotation.Cacheable;
```

Anotar o método `analisar`:
```java
@Cacheable(value = "cotacoes", key = "#ticker.toUpperCase()")
public Optional<RelatorioAnalise> analisar(String ticker) {
```

- [ ] **Step 5: Rodar testes — garantir que cache não quebra testes unitários**

```bash
./mvnw test -Dtest=AnaliseServiceTest -pl . -q
```

Saída esperada: `Tests run: 34, Failures: 0, Errors: 0, Skipped: 0`

> Nota: `@Cacheable` não interfere em testes com `@InjectMocks` pois o Spring AOP proxy não é ativado — Mockito injeta direto.

- [ ] **Step 6: Commit**

```bash
git add pom.xml \
    src/main/java/br/com/analisador/AnalisadorAcoesApplication.java \
    src/main/java/br/com/analisador/config/CacheConfig.java \
    src/main/java/br/com/analisador/service/AnaliseService.java
git commit -m "feat: add Caffeine cache with 5-minute TTL for stock analysis results

Caches results by ticker (uppercase) to avoid repeated BRAPI calls
for the same stock within a 5-minute window. Cache holds up to 200 entries."
```

---

### Task 6: Limiares por setor — modelos

**Files:**
- Create: `src/main/java/br/com/analisador/model/SectorThresholds.java`
- Create: `src/main/java/br/com/analisador/model/SectorConfig.java`

- [ ] **Step 1: Criar SectorThresholds.java**

Criar `src/main/java/br/com/analisador/model/SectorThresholds.java`:

```java
package br.com.analisador.model;

/**
 * Limiares fundamentalistas ajustados por setor.
 * Campos "bom" e "neutro" marcam os thresholds de cada métrica.
 *
 * P/L, P/VP: menor é melhor → BOM se < plBom, NEUTRO se < plNeutro
 * ROE, LiquidezCorrente, MargemLiquida, CrescimentoReceita: maior é melhor
 *   → BOM se > xyzBom, NEUTRO se >= xyzNeutro
 * DividaPatrimonio (ratio após /100): menor é melhor
 *   → BOM se < dpBom, NEUTRO se <= dpNeutro
 */
public record SectorThresholds(
        // P/L
        double plBom,
        double plNeutro,
        // P/VP
        double pvpBom,
        double pvpNeutro,
        // ROE (em %, após *100)
        double roeBom,
        double roeNeutro,
        // Dívida/Patrimônio (ratio, após dividir por 100)
        double dpBom,
        double dpNeutro,
        // Liquidez Corrente
        double lcBom,
        double lcNeutro,
        // Margem Líquida (em %, após *100)
        double mlBom,
        double mlNeutro,
        // Crescimento Receita (em %, após *100)
        double crBom,
        double crNeutro
) {}
```

- [ ] **Step 2: Criar SectorConfig.java**

Criar `src/main/java/br/com/analisador/model/SectorConfig.java`:

```java
package br.com.analisador.model;

import java.util.Map;

/**
 * Mapeia nomes de setor (substrings, case-insensitive) para SectorThresholds.
 * Ordem: plBom, plNeutro, pvpBom, pvpNeutro, roeBom, roeNeutro,
 *        dpBom, dpNeutro, lcBom, lcNeutro, mlBom, mlNeutro, crBom, crNeutro
 */
public class SectorConfig {

    public static final SectorThresholds DEFAULT = new SectorThresholds(
            10, 20,    // P/L: < 10 BOM, < 20 NEUTRO
            1.0, 2.0,  // P/VP: < 1 BOM, < 2 NEUTRO
            15, 10,    // ROE: > 15% BOM, > 10% NEUTRO
            0.5, 1.0,  // D/P: < 0.5x BOM, < 1.0x NEUTRO
            1.5, 1.0,  // Liquidez: > 1.5 BOM, > 1.0 NEUTRO
            15, 5,     // Margem: > 15% BOM, > 5% NEUTRO
            10, 0      // Crescimento: > 10% BOM, >= 0% NEUTRO
    );

    private static final Map<String, SectorThresholds> SECTOR_MAP = Map.of(
            "financ", new SectorThresholds(
                    10, 20, 1.0, 2.0,
                    18, 12,             // bancos precisam de ROE alto
                    8.0, 15.0,          // bancos têm D/P naturalmente alto
                    1.0, 0.8,           // liquidez diferente em bancos
                    20, 10,
                    10, 0
            ),
            "banc", new SectorThresholds(
                    10, 20, 1.0, 2.0,
                    18, 12,
                    8.0, 15.0,
                    1.0, 0.8,
                    20, 10,
                    10, 0
            ),
            "segur", new SectorThresholds(
                    10, 20, 1.0, 2.0,
                    18, 12,
                    8.0, 15.0,
                    1.0, 0.8,
                    20, 10,
                    10, 0
            ),
            "util", new SectorThresholds(
                    15, 25, 1.5, 3.0,
                    10, 6,              // utilidades têm ROE mais baixo mas estável
                    1.5, 3.0,           // aceita mais dívida por fluxo estável
                    1.2, 1.0,
                    10, 5,
                    5, 0
            ),
            "petrol", new SectorThresholds(
                    12, 25, 1.0, 2.0,
                    12, 8,              // commodities têm margens cíclicas
                    1.0, 2.0,
                    1.2, 1.0,
                    10, 5,
                    5, 0
            ),
            "miner", new SectorThresholds(
                    12, 25, 1.0, 2.0,
                    12, 8,
                    1.0, 2.0,
                    1.2, 1.0,
                    10, 5,
                    5, 0
            ),
            "varejo", new SectorThresholds(
                    15, 30, 2.0, 5.0,
                    15, 10,
                    1.5, 3.0,
                    1.2, 1.0,
                    5, 2,               // varejo tem margem estruturalmente baixa
                    10, 0
            ),
            "comerci", new SectorThresholds(
                    15, 30, 2.0, 5.0,
                    15, 10,
                    1.5, 3.0,
                    1.2, 1.0,
                    5, 2,
                    10, 0
            )
    );

    /**
     * Retorna os limiares adequados para o setor informado.
     * Faz substring match case-insensitive. Retorna DEFAULT se não encontrar.
     */
    public static SectorThresholds getThresholds(String sector) {
        if (sector == null || sector.isBlank()) return DEFAULT;
        String lower = sector.toLowerCase();
        for (Map.Entry<String, SectorThresholds> entry : SECTOR_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return DEFAULT;
    }
}
```

- [ ] **Step 3: Verificar que o projeto compila**

```bash
./mvnw compile -q
```

Saída esperada: `BUILD SUCCESS` sem erros.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/br/com/analisador/model/SectorThresholds.java \
        src/main/java/br/com/analisador/model/SectorConfig.java
git commit -m "feat: add SectorThresholds and SectorConfig for sector-aware metric evaluation

Five sector categories: FINANCEIRO/BANCOS, UTILIDADES, PETRÓLEO/MINERAÇÃO,
VAREJO/COMÉRCIO, DEFAULT. Each category has 14 threshold values covering
all 7 fundamental metrics."
```

---

### Task 7: Integrar limiares por setor no AnaliseService

**Files:**
- Modify: `src/main/java/br/com/analisador/service/AnaliseService.java`
- Modify: `src/main/resources/templates/resultado.html`
- Modify: `src/main/resources/static/css/style.css`

- [ ] **Step 1: Refatorar AnaliseService para usar SectorThresholds**

Substituir todo o conteúdo de `src/main/java/br/com/analisador/service/AnaliseService.java`:

```java
package br.com.analisador.service;

import br.com.analisador.client.BrapiClient;
import br.com.analisador.model.BrapiResponse;
import br.com.analisador.model.MetricaAvaliada;
import br.com.analisador.model.MetricaAvaliada.Status;
import br.com.analisador.model.RelatorioAnalise;
import br.com.analisador.model.SectorConfig;
import br.com.analisador.model.SectorThresholds;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AnaliseService {

    private final BrapiClient brapiClient;

    public AnaliseService(BrapiClient brapiClient) {
        this.brapiClient = brapiClient;
    }

    @Cacheable(value = "cotacoes", key = "#ticker.toUpperCase()")
    public Optional<RelatorioAnalise> analisar(String ticker) {
        Optional<BrapiResponse.QuoteResult> dadosOpt = brapiClient.buscarCotacao(ticker);
        if (dadosOpt.isEmpty()) return Optional.empty();

        BrapiResponse.QuoteResult d = dadosOpt.get();
        SectorThresholds t = SectorConfig.getThresholds(d.sector());

        List<MetricaAvaliada> metricas = new ArrayList<>();
        metricas.add(avaliarPL(d.priceEarnings(), t));

        Double pvp = d.defaultKeyStatistics() != null ? d.defaultKeyStatistics().priceToBook() : null;
        metricas.add(avaliarPVP(pvp, t));

        Double roe = d.financialData() != null ? d.financialData().returnOnEquity() : null;
        metricas.add(avaliarROE(roe, t));

        Double dp = d.financialData() != null ? d.financialData().debtToEquity() : null;
        metricas.add(avaliarDividaPatrimonio(dp, t));

        Double lc = d.financialData() != null ? d.financialData().currentRatio() : null;
        metricas.add(avaliarLiquidez(lc, t));

        Double ml = d.financialData() != null ? d.financialData().profitMargins() : null;
        metricas.add(avaliarMargemLiquida(ml, t));

        Double cr = d.financialData() != null ? d.financialData().revenueGrowth() : null;
        metricas.add(avaliarCrescimentoReceita(cr, t));

        int pontuacao = calcularPontuacao(metricas);
        String classificacao = classificar(pontuacao);
        String resumo = gerarResumo(metricas, classificacao);
        String setor = d.sector() != null ? d.sector() : "Não informado";

        return Optional.of(new RelatorioAnalise(
                d.symbol(),
                d.longName() != null ? d.longName() : d.shortName(),
                d.regularMarketPrice(),
                d.regularMarketChangePercent(),
                d.logourl(),
                metricas,
                pontuacao,
                classificacao,
                resumo,
                setor
        ));
    }

    // ========= AVALIACOES INDIVIDUAIS =========

    private MetricaAvaliada avaliarPL(Double pl, SectorThresholds t) {
        if (pl == null) return indisponivel("P/L", "Preço sobre Lucro não disponível");
        if (pl < 0) return new MetricaAvaliada("P/L", fmt(pl), Status.RUIM,
                "P/L negativo indica que a empresa está dando prejuízo");
        if (pl < t.plBom()) return new MetricaAvaliada("P/L", fmt(pl), Status.BOM,
                "P/L abaixo de " + (int) t.plBom() + " sugere ação potencialmente barata");
        if (pl <= t.plNeutro()) return new MetricaAvaliada("P/L", fmt(pl), Status.NEUTRO,
                "P/L entre " + (int) t.plBom() + " e " + (int) t.plNeutro() + " é considerado justo");
        return new MetricaAvaliada("P/L", fmt(pl), Status.RUIM,
                "P/L acima de " + (int) t.plNeutro() + " pode indicar ação cara");
    }

    private MetricaAvaliada avaliarPVP(Double pvp, SectorThresholds t) {
        if (pvp == null) return indisponivel("P/VP", "Preço sobre Valor Patrimonial não disponível");
        if (pvp < t.pvpBom()) return new MetricaAvaliada("P/VP", fmt(pvp), Status.BOM,
                "Cotada abaixo do valor patrimonial — pode estar descontada");
        if (pvp <= t.pvpNeutro()) return new MetricaAvaliada("P/VP", fmt(pvp), Status.NEUTRO,
                "P/VP entre " + t.pvpBom() + " e " + t.pvpNeutro() + " é considerado normal");
        return new MetricaAvaliada("P/VP", fmt(pvp), Status.RUIM,
                "P/VP alto pode indicar ação sobrevalorizada");
    }

    private MetricaAvaliada avaliarROE(Double roe, SectorThresholds t) {
        if (roe == null) return indisponivel("ROE", "Retorno sobre Patrimônio não disponível");
        double pct = roe * 100;
        if (pct > t.roeBom()) return new MetricaAvaliada("ROE", fmtPct(pct), Status.BOM,
                "ROE acima de " + (int) t.roeBom() + "% indica empresa muito rentável");
        if (pct >= t.roeNeutro()) return new MetricaAvaliada("ROE", fmtPct(pct), Status.NEUTRO,
                "ROE entre " + (int) t.roeNeutro() + "% e " + (int) t.roeBom() + "% é razoável");
        return new MetricaAvaliada("ROE", fmtPct(pct), Status.RUIM,
                "ROE abaixo de " + (int) t.roeNeutro() + "% indica baixa rentabilidade");
    }

    private MetricaAvaliada avaliarDividaPatrimonio(Double dp, SectorThresholds t) {
        if (dp == null) return indisponivel("Dívida/Patrimônio", "Endividamento não disponível");
        double ratio = dp / 100.0;
        if (ratio < t.dpBom()) return new MetricaAvaliada("Dívida/Patrimônio", fmt(ratio) + "x", Status.BOM,
                "Endividamento baixo — empresa financeiramente saudável");
        if (ratio <= t.dpNeutro()) return new MetricaAvaliada("Dívida/Patrimônio", fmt(ratio) + "x", Status.NEUTRO,
                "Endividamento moderado");
        return new MetricaAvaliada("Dívida/Patrimônio", fmt(ratio) + "x", Status.RUIM,
                "Endividamento alto — risco financeiro elevado");
    }

    private MetricaAvaliada avaliarLiquidez(Double lc, SectorThresholds t) {
        if (lc == null) return indisponivel("Liquidez Corrente", "Liquidez não disponível");
        if (lc > t.lcBom()) return new MetricaAvaliada("Liquidez Corrente", fmt(lc), Status.BOM,
                "Empresa tem boa capacidade de pagar obrigações de curto prazo");
        if (lc >= t.lcNeutro()) return new MetricaAvaliada("Liquidez Corrente", fmt(lc), Status.NEUTRO,
                "Liquidez adequada mas sem folga");
        return new MetricaAvaliada("Liquidez Corrente", fmt(lc), Status.RUIM,
                "Liquidez abaixo de " + t.lcNeutro() + " — pode ter dificuldade de honrar dívidas");
    }

    private MetricaAvaliada avaliarMargemLiquida(Double ml, SectorThresholds t) {
        if (ml == null) return indisponivel("Margem Líquida", "Margem líquida não disponível");
        double pct = ml * 100;
        if (pct > t.mlBom()) return new MetricaAvaliada("Margem Líquida", fmtPct(pct), Status.BOM,
                "Margem alta — empresa converte bem receita em lucro");
        if (pct >= t.mlNeutro()) return new MetricaAvaliada("Margem Líquida", fmtPct(pct), Status.NEUTRO,
                "Margem razoável");
        return new MetricaAvaliada("Margem Líquida", fmtPct(pct), Status.RUIM,
                "Margem baixa ou negativa");
    }

    private MetricaAvaliada avaliarCrescimentoReceita(Double cr, SectorThresholds t) {
        if (cr == null) return indisponivel("Crescimento Receita", "Crescimento não disponível");
        double pct = cr * 100;
        if (pct > t.crBom()) return new MetricaAvaliada("Crescimento Receita", fmtPct(pct), Status.BOM,
                "Empresa crescendo fortemente");
        if (pct >= t.crNeutro()) return new MetricaAvaliada("Crescimento Receita", fmtPct(pct), Status.NEUTRO,
                "Crescimento modesto");
        return new MetricaAvaliada("Crescimento Receita", fmtPct(pct), Status.RUIM,
                "Receita em queda");
    }

    // ========= CONSOLIDACAO =========

    private int calcularPontuacao(List<MetricaAvaliada> metricas) {
        int total = 0, pontos = 0;
        for (MetricaAvaliada m : metricas) {
            if (m.status() == Status.INDISPONIVEL) continue;
            total++;
            switch (m.status()) {
                case BOM -> pontos += 2;
                case NEUTRO -> pontos += 1;
                default -> {}
            }
        }
        if (total == 0) return 0;
        return (int) ((pontos * 100.0) / (total * 2));
    }

    private String classificar(int pontuacao) {
        if (pontuacao >= 70) return "Potencialmente atrativa";
        if (pontuacao >= 40) return "Neutra";
        return "Requer cautela";
    }

    private String gerarResumo(List<MetricaAvaliada> metricas, String classificacao) {
        long bons = metricas.stream().filter(m -> m.status() == Status.BOM).count();
        long ruins = metricas.stream().filter(m -> m.status() == Status.RUIM).count();
        return String.format("Classificação: %s. %d métrica(s) positiva(s), %d negativa(s). "
                        + "Esta é uma análise automatizada educacional — sempre combine com análise qualitativa, "
                        + "do setor e consulte um profissional antes de investir.",
                classificacao, bons, ruins);
    }

    // ========= HELPERS =========

    private MetricaAvaliada indisponivel(String nome, String msg) {
        return new MetricaAvaliada(nome, "-", Status.INDISPONIVEL, msg);
    }

    private String fmt(double v) { return String.format("%.2f", v); }
    private String fmtPct(double v) { return String.format("%.2f%%", v); }
}
```

- [ ] **Step 2: Adicionar campo `setor` ao RelatorioAnalise**

Substituir todo o conteúdo de `src/main/java/br/com/analisador/model/RelatorioAnalise.java`:

```java
package br.com.analisador.model;

import java.util.List;

public record RelatorioAnalise(
        String ticker,
        String nomeEmpresa,
        Double precoAtual,
        Double variacaoPercentual,
        String logoUrl,
        List<MetricaAvaliada> metricas,
        int pontuacao,
        String classificacao,
        String resumo,
        String setor           // setor detectado da BRAPI
) {}
```

- [ ] **Step 3: Adicionar badge de setor no resultado.html**

No arquivo `src/main/resources/templates/resultado.html`, adicionar após a `<div>` do preço (após `</div>` que fecha `.preco-atual`) e antes do `</div>` que fecha o bloco de info da empresa:

```html
                <div class="setor-badge" th:text="'Setor: ' + ${relatorio.setor()}">Setor: —</div>
```

O bloco da empresa ficará:
```html
        <section class="empresa-header">
            <img th:if="${relatorio.logoUrl()}" th:src="${relatorio.logoUrl()}" alt="Logo" class="logo-empresa">
            <div>
                <h1 th:text="${relatorio.ticker()}">TICKER</h1>
                <h2 th:text="${relatorio.nomeEmpresa()}">Nome da empresa</h2>
                <div class="preco-atual">
                    <span class="preco" th:text="'R$ ' + ${#numbers.formatDecimal(relatorio.precoAtual(), 1, 'POINT', 2, 'COMMA')}">R$ 0,00</span>
                    <span class="variacao"
                          th:classappend="${relatorio.variacaoPercentual() != null and relatorio.variacaoPercentual() >= 0} ? 'positivo' : 'negativo'"
                          th:text="${relatorio.variacaoPercentual() != null} ? (${#numbers.formatDecimal(relatorio.variacaoPercentual(), 1, 'POINT', 2, 'COMMA')} + '%') : '-'">0%</span>
                </div>
                <div class="setor-badge" th:text="'Setor: ' + ${relatorio.setor()}">Setor: —</div>
            </div>
        </section>
```

- [ ] **Step 4: Adicionar estilo do badge no style.css**

Adicionar ao final de `src/main/resources/static/css/style.css`:

```css
/* ========= SETOR BADGE ========= */
.setor-badge {
    display: inline-block;
    margin-top: 0.4rem;
    padding: 0.2rem 0.7rem;
    background: #ebf8ff;
    color: #2b6cb0;
    border: 1px solid #bee3f8;
    border-radius: 999px;
    font-size: 0.8rem;
    font-weight: 500;
}
```

- [ ] **Step 5: Rodar todos os testes**

```bash
./mvnw test -pl . -q
```

Saída esperada: `Tests run: 34, Failures: 0, Errors: 0, Skipped: 0`

> Os testes passam porque `makeQuote` passa `null` para sector, e `SectorConfig.getThresholds(null)` retorna `DEFAULT` com valores idênticos aos limiares hardcoded anteriores.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/br/com/analisador/service/AnaliseService.java \
        src/main/java/br/com/analisador/model/RelatorioAnalise.java \
        src/main/resources/templates/resultado.html \
        src/main/resources/static/css/style.css
git commit -m "feat: sector-aware metric thresholds with BRAPI sector field

Metrics are now evaluated against thresholds appropriate for each sector
(FINANCEIRO, UTILIDADES, PETRÓLEO/MINERAÇÃO, VAREJO, DEFAULT).
RelatorioAnalise gains setor field displayed as badge in resultado.html."
```

---

## PHASE 3: Multi-Stock Comparison

### Task 8: Método analisarMultiplos no AnaliseService

**Files:**
- Modify: `src/main/java/br/com/analisador/service/AnaliseService.java`
- Modify: `src/test/java/br/com/analisador/service/AnaliseServiceTest.java`

- [ ] **Step 1: Adicionar analisarMultiplos ao AnaliseService**

Adicionar imports ao `AnaliseService.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
```

Adicionar método `analisarMultiplos` logo após o método `analisar`:

```java
    /**
     * Analisa múltiplos tickers em paralelo.
     * Máximo 5 tickers. Timeout global de 10 segundos.
     * Tickers não encontrados são omitidos silenciosamente.
     */
    public List<RelatorioAnalise> analisarMultiplos(List<String> tickers) {
        List<CompletableFuture<Optional<RelatorioAnalise>>> futures = tickers.stream()
                .limit(5)
                .map(ticker -> CompletableFuture.supplyAsync(() -> analisarSemCache(ticker)))
                .collect(Collectors.toList());

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(10, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // timeout: coleta o que tiver pronto
        }

        return futures.stream()
                .map(f -> {
                    try { return f.getNow(Optional.empty()); }
                    catch (Exception e) { return Optional.<RelatorioAnalise>empty(); }
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    /**
     * Versão interna sem @Cacheable (para uso em paralelo sem proxy AOP).
     * Reutiliza a mesma lógica de analisar().
     */
    private Optional<RelatorioAnalise> analisarSemCache(String ticker) {
        Optional<BrapiResponse.QuoteResult> dadosOpt = brapiClient.buscarCotacao(ticker);
        if (dadosOpt.isEmpty()) return Optional.empty();

        BrapiResponse.QuoteResult d = dadosOpt.get();
        SectorThresholds t = SectorConfig.getThresholds(d.sector());

        List<MetricaAvaliada> metricas = new ArrayList<>();
        metricas.add(avaliarPL(d.priceEarnings(), t));

        Double pvp = d.defaultKeyStatistics() != null ? d.defaultKeyStatistics().priceToBook() : null;
        metricas.add(avaliarPVP(pvp, t));

        Double roe = d.financialData() != null ? d.financialData().returnOnEquity() : null;
        metricas.add(avaliarROE(roe, t));

        Double dp = d.financialData() != null ? d.financialData().debtToEquity() : null;
        metricas.add(avaliarDividaPatrimonio(dp, t));

        Double lc = d.financialData() != null ? d.financialData().currentRatio() : null;
        metricas.add(avaliarLiquidez(lc, t));

        Double ml = d.financialData() != null ? d.financialData().profitMargins() : null;
        metricas.add(avaliarMargemLiquida(ml, t));

        Double cr = d.financialData() != null ? d.financialData().revenueGrowth() : null;
        metricas.add(avaliarCrescimentoReceita(cr, t));

        int pontuacao = calcularPontuacao(metricas);
        String classificacao = classificar(pontuacao);
        String resumo = gerarResumo(metricas, classificacao);
        String setor = d.sector() != null ? d.sector() : "Não informado";

        return Optional.of(new RelatorioAnalise(
                d.symbol(), d.longName() != null ? d.longName() : d.shortName(),
                d.regularMarketPrice(), d.regularMarketChangePercent(), d.logourl(),
                metricas, pontuacao, classificacao, resumo, setor));
    }
```

- [ ] **Step 2: Adicionar testes de analisarMultiplos ao AnaliseServiceTest**

Adicionar ao final da classe `AnaliseServiceTest` (antes do `}` final):

```java
    // -------- ANALISE MULTIPLOS TESTES --------

    @Test
    void analisarMultiplos_DoisTickers_retornaListaOrdenada() {
        when(brapiClient.buscarCotacao("PETR4"))
                .thenReturn(Optional.of(makeQuote(8.0, 0.9, 0.18, 40.0, 1.8, 0.17, 0.12)));
        when(brapiClient.buscarCotacao("VALE3"))
                .thenReturn(Optional.of(makeQuote(12.0, 1.5, 0.22, 60.0, 2.1, 0.25, 0.05)));

        List<RelatorioAnalise> result = service.analisarMultiplos(List.of("PETR4", "VALE3"));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(RelatorioAnalise::ticker)
                .containsExactlyInAnyOrder("TEST3", "TEST3"); // ambos usam o makeQuote
    }

    @Test
    void analisarMultiplos_TickerInvalido_omiteDoResultado() {
        when(brapiClient.buscarCotacao("PETR4"))
                .thenReturn(Optional.of(makeQuote(8.0, 0.9, 0.18, 40.0, 1.8, 0.17, 0.12)));
        when(brapiClient.buscarCotacao("XXXXX")).thenReturn(Optional.empty());

        List<RelatorioAnalise> result = service.analisarMultiplos(List.of("PETR4", "XXXXX"));

        assertThat(result).hasSize(1);
    }

    @Test
    void analisarMultiplos_MaisDe5Tickers_limitaA5() {
        List<String> tickers = List.of("PETR4", "VALE3", "ITUB4", "BBAS3", "MGLU3", "WEGE3");
        tickers.forEach(t -> when(brapiClient.buscarCotacao(t))
                .thenReturn(Optional.of(makeQuote(10.0, 1.0, 0.15, 50.0, 1.5, 0.10, 0.08))));

        List<RelatorioAnalise> result = service.analisarMultiplos(tickers);

        assertThat(result).hasSizeLessThanOrEqualTo(5);
    }
```

- [ ] **Step 3: Rodar todos os testes**

```bash
./mvnw test -pl . -q
```

Saída esperada: `Tests run: 37, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/br/com/analisador/service/AnaliseService.java \
        src/test/java/br/com/analisador/service/AnaliseServiceTest.java
git commit -m "feat: add analisarMultiplos with parallel CompletableFuture and 10s timeout

Analyzes up to 5 tickers in parallel using CompletableFuture.allOf().
Invalid tickers are silently omitted from results."
```

---

### Task 9: Endpoint /comparar e atualização do index.html

**Files:**
- Modify: `src/main/java/br/com/analisador/controller/AnaliseController.java`
- Modify: `src/main/resources/templates/index.html`

- [ ] **Step 1: Adicionar endpoint /comparar ao AnaliseController**

Substituir todo o conteúdo de `src/main/java/br/com/analisador/controller/AnaliseController.java`:

```java
package br.com.analisador.controller;

import br.com.analisador.model.RelatorioAnalise;
import br.com.analisador.service.AnaliseService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class AnaliseController {

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

        Optional<RelatorioAnalise> relatorio = analiseService.analisar(ticker.trim().toUpperCase());

        if (relatorio.isEmpty()) {
            model.addAttribute("erro", "Ticker '" + ticker.toUpperCase() + "' não encontrado ou sem dados");
            return "index";
        }

        model.addAttribute("relatorio", relatorio.get());
        return "resultado";
    }

    @GetMapping("/comparar")
    public String comparar(@RequestParam("tickers") String tickersParam, Model model) {
        if (tickersParam == null || tickersParam.isBlank()) {
            model.addAttribute("erro", "Informe ao menos dois tickers separados por vírgula");
            return "index";
        }

        List<String> tickers = Arrays.stream(tickersParam.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(t -> !t.isBlank())
                .limit(5)
                .collect(Collectors.toList());

        if (tickers.size() < 2) {
            model.addAttribute("erro", "Informe ao menos dois tickers para comparar");
            return "index";
        }

        List<RelatorioAnalise> relatorios = analiseService.analisarMultiplos(tickers);

        if (relatorios.isEmpty()) {
            model.addAttribute("erro", "Nenhum ticker encontrado: " + tickersParam);
            return "index";
        }

        model.addAttribute("relatorios", relatorios);
        return "comparar";
    }
}
```

- [ ] **Step 2: Adicionar formulário de comparação ao index.html**

Substituir todo o conteúdo de `src/main/resources/templates/index.html`:

```html
<!DOCTYPE html>
<html lang="pt-BR" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Analisador Fundamentalista de Ações</title>
    <link rel="stylesheet" th:href="@{/css/style.css}">
</head>
<body>
<div class="container">
    <header>
        <h1>📊 Analisador Fundamentalista</h1>
        <p class="subtitle">Análise automatizada de ações da B3 com base em indicadores fundamentalistas</p>
    </header>

    <main>
        <form action="/analisar" method="get" class="search-form" id="form-analisar">
            <input type="text"
                   name="ticker"
                   placeholder="Digite o ticker (ex: PETR4, VALE3, ITUB4)"
                   required
                   autofocus
                   maxlength="10">
            <button type="submit">Analisar</button>
        </form>

        <div class="comparar-section">
            <p class="comparar-label">Ou compare múltiplas ações:</p>
            <form action="/comparar" method="get" class="search-form" id="form-comparar">
                <input type="text"
                       name="tickers"
                       placeholder="Ex: PETR4,VALE3,ITUB4 (até 5 tickers)"
                       maxlength="50">
                <button type="submit" class="btn-secundario">Comparar</button>
            </form>
        </div>

        <div th:if="${erro}" class="alerta-erro">
            <p th:text="${erro}"></p>
        </div>

        <section class="info-box">
            <h3>Sobre a análise</h3>
            <p>Este sistema consulta a API da <strong>BRAPI</strong> e avalia 7 indicadores fundamentalistas:</p>
            <ul>
                <li><strong>P/L</strong> — Preço sobre Lucro</li>
                <li><strong>P/VP</strong> — Preço sobre Valor Patrimonial</li>
                <li><strong>ROE</strong> — Retorno sobre Patrimônio Líquido</li>
                <li><strong>Dívida/Patrimônio</strong> — Nível de endividamento</li>
                <li><strong>Liquidez Corrente</strong> — Capacidade de pagar dívidas de curto prazo</li>
                <li><strong>Margem Líquida</strong> — % da receita que vira lucro</li>
                <li><strong>Crescimento da Receita</strong> — Evolução do faturamento</li>
            </ul>
            <p class="aviso">
                ⚠️ <strong>Importante:</strong> esta é uma ferramenta educacional. Não constitui recomendação de
                investimento. Os critérios são heurísticas gerais ajustadas por setor.
            </p>
        </section>
    </main>

    <footer>
        <p>Dados fornecidos por <a href="https://brapi.dev" target="_blank" rel="noopener">brapi.dev</a></p>
    </footer>
</div>
</body>
</html>
```

- [ ] **Step 3: Adicionar estilos de comparação ao style.css**

Adicionar ao final de `src/main/resources/static/css/style.css`:

```css
/* ========= COMPARAR SECTION ========= */
.comparar-section {
    margin-top: 1rem;
    padding-top: 1rem;
    border-top: 1px solid #e2e8f0;
}

.comparar-label {
    color: #718096;
    font-size: 0.9rem;
    margin-bottom: 0.5rem;
}

.btn-secundario {
    background: #fff;
    color: #4299e1;
    border: 2px solid #4299e1;
    padding: 0.6rem 1.4rem;
    border-radius: 6px;
    font-size: 1rem;
    font-weight: 600;
    cursor: pointer;
    transition: background 0.2s, color 0.2s;
}

.btn-secundario:hover {
    background: #ebf8ff;
}
```

- [ ] **Step 4: Verificar que o projeto compila**

```bash
./mvnw compile -q
```

Saída esperada: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/analisador/controller/AnaliseController.java \
        src/main/resources/templates/index.html \
        src/main/resources/static/css/style.css
git commit -m "feat: add /comparar endpoint and compare form on index page

GET /comparar accepts comma-separated tickers (max 5), delegates to
analisarMultiplos(), renders comparar.html. index.html gains a second
form for multi-stock comparison."
```

---

### Task 10: Template comparar.html com tabela e Chart.js

**Files:**
- Create: `src/main/resources/templates/comparar.html`
- Modify: `src/main/resources/static/css/style.css`

- [ ] **Step 1: Criar comparar.html**

Criar `src/main/resources/templates/comparar.html`:

```html
<!DOCTYPE html>
<html lang="pt-BR" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Comparação de Ações</title>
    <link rel="stylesheet" th:href="@{/css/style.css}">
</head>
<body>
<div class="container">
    <header class="header-resultado">
        <a href="/" class="btn-voltar">← Nova análise</a>
    </header>

    <main>
        <h1 class="comparar-titulo">Comparação de Ações</h1>

        <!-- TABELA DE COMPARAÇÃO -->
        <div class="comparar-table-wrapper">
            <table class="comparar-table">
                <thead>
                    <tr>
                        <th>Indicador</th>
                        <th th:each="r : ${relatorios}" th:text="${r.ticker()}">TICKER</th>
                    </tr>
                </thead>
                <tbody>
                    <!-- Pontuação -->
                    <tr class="row-destaque">
                        <td>Pontuação</td>
                        <td th:each="r : ${relatorios}"
                            th:classappend="${r.pontuacao()} >= 70 ? 'score-bom' : (${r.pontuacao()} >= 40 ? 'score-neutro' : 'score-ruim')"
                            th:text="${r.pontuacao()} + '/100'">0/100</td>
                    </tr>
                    <!-- Classificação -->
                    <tr>
                        <td>Classificação</td>
                        <td th:each="r : ${relatorios}" th:text="${r.classificacao()}">-</td>
                    </tr>
                    <!-- Setor -->
                    <tr>
                        <td>Setor</td>
                        <td th:each="r : ${relatorios}" th:text="${r.setor()}">-</td>
                    </tr>
                    <!-- Preço -->
                    <tr>
                        <td>Preço (R$)</td>
                        <td th:each="r : ${relatorios}"
                            th:text="${r.precoAtual() != null} ? ${#numbers.formatDecimal(r.precoAtual(), 1, 'POINT', 2, 'COMMA')} : '-'">-</td>
                    </tr>
                    <!-- Métricas individuais (índices 0-6) -->
                    <tr th:each="i : ${#numbers.sequence(0, 6)}">
                        <td th:text="${relatorios[0].metricas()[__${i}__].nome()}">Métrica</td>
                        <td th:each="r : ${relatorios}"
                            th:classappend="${r.metricas()[__${i}__].status().name().toLowerCase()}"
                            th:text="${r.metricas()[__${i}__].valor()}">-</td>
                    </tr>
                </tbody>
            </table>
        </div>

        <!-- RADAR CHART -->
        <div class="chart-wrapper">
            <h2>Visão Radar</h2>
            <p class="chart-legenda">Pontuação por métrica: BOM=2, NEUTRO=1, RUIM/INDISPONÍVEL=0</p>
            <canvas id="radarChart" width="500" height="400"></canvas>
        </div>

        <section class="disclaimer">
            <p>⚠️ <strong>Disclaimer:</strong> análise educacional, não constitui recomendação de investimento.</p>
        </section>
    </main>
</div>

<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
<script th:inline="javascript">
    /*<![CDATA[*/
    const labels = ['P/L', 'P/VP', 'ROE', 'Dívida/Pat.', 'Liquidez', 'Margem Líq.', 'Cresc. Rec.'];
    const relatorios = /*[[${relatorios}]]*/ [];

    function statusToValue(status) {
        if (status === 'BOM') return 2;
        if (status === 'NEUTRO') return 1;
        return 0;
    }

    const colors = [
        'rgba(66, 153, 225, 0.7)',
        'rgba(72, 187, 120, 0.7)',
        'rgba(237, 137, 54, 0.7)',
        'rgba(159, 122, 234, 0.7)',
        'rgba(245, 101, 101, 0.7)'
    ];

    const datasets = relatorios.map((r, idx) => ({
        label: r.ticker,
        data: r.metricas.map(m => statusToValue(m.status)),
        backgroundColor: colors[idx % colors.length].replace('0.7', '0.2'),
        borderColor: colors[idx % colors.length],
        borderWidth: 2,
        pointBackgroundColor: colors[idx % colors.length]
    }));

    new Chart(document.getElementById('radarChart'), {
        type: 'radar',
        data: { labels, datasets },
        options: {
            responsive: false,
            scales: {
                r: {
                    min: 0,
                    max: 2,
                    ticks: { stepSize: 1, display: false },
                    pointLabels: { font: { size: 13 } }
                }
            },
            plugins: {
                legend: { position: 'top' }
            }
        }
    });
    /*]]>*/
</script>
</body>
</html>
```

- [ ] **Step 2: Adicionar estilos da tabela de comparação ao style.css**

Adicionar ao final de `src/main/resources/static/css/style.css`:

```css
/* ========= COMPARAR TEMPLATE ========= */
.comparar-titulo {
    font-size: 1.8rem;
    color: #2d3748;
    margin-bottom: 1.5rem;
}

.comparar-table-wrapper {
    overflow-x: auto;
    margin-bottom: 2rem;
}

.comparar-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.95rem;
}

.comparar-table th,
.comparar-table td {
    padding: 0.7rem 1rem;
    border: 1px solid #e2e8f0;
    text-align: center;
}

.comparar-table th {
    background: #f7fafc;
    font-weight: 700;
    color: #2d3748;
}

.comparar-table td:first-child {
    text-align: left;
    font-weight: 500;
    background: #f7fafc;
}

.comparar-table .row-destaque td {
    font-weight: 700;
    font-size: 1.05rem;
}

.comparar-table td.bom { background: #f0fff4; color: #276749; }
.comparar-table td.neutro { background: #fffbeb; color: #92400e; }
.comparar-table td.ruim { background: #fff5f5; color: #9b2c2c; }
.comparar-table td.indisponivel { background: #f7fafc; color: #a0aec0; }

.comparar-table .score-bom { background: #c6f6d5; color: #22543d; }
.comparar-table .score-neutro { background: #fefcbf; color: #744210; }
.comparar-table .score-ruim { background: #fed7d7; color: #742a2a; }

.chart-wrapper {
    margin: 2rem 0;
    text-align: center;
}

.chart-wrapper h2 {
    font-size: 1.3rem;
    color: #2d3748;
    margin-bottom: 0.3rem;
}

.chart-legenda {
    color: #718096;
    font-size: 0.85rem;
    margin-bottom: 1rem;
}
```

- [ ] **Step 3: Verificar que o projeto compila e sobe**

```bash
./mvnw spring-boot:run -q &
sleep 8
curl -s "http://localhost:8080/comparar?tickers=PETR4,VALE3" | grep -c "Comparação"
kill %1
```

Saída esperada: `1` (encontrou o título no HTML)

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/comparar.html \
        src/main/resources/static/css/style.css
git commit -m "feat: add comparar.html with comparison table and Chart.js radar chart

Color-coded comparison table with per-metric status highlighting.
Radar chart (Chart.js 4.4 CDN) with 7-axis polygon per stock,
values 0/1/2 for RUIM/NEUTRO/BOM."
```

---

## PHASE 4: UX Enhancements

### Task 11: Exceções customizadas + atualização do BrapiClient

**Files:**
- Create: `src/main/java/br/com/analisador/exception/TickerNotFoundException.java`
- Create: `src/main/java/br/com/analisador/exception/ApiUnavailableException.java`
- Modify: `src/main/java/br/com/analisador/client/BrapiClient.java`

- [ ] **Step 1: Criar TickerNotFoundException.java**

Criar `src/main/java/br/com/analisador/exception/TickerNotFoundException.java`:

```java
package br.com.analisador.exception;

public class TickerNotFoundException extends RuntimeException {

    private final String ticker;

    public TickerNotFoundException(String ticker) {
        super("Ticker não encontrado: " + ticker);
        this.ticker = ticker;
    }

    public String getTicker() {
        return ticker;
    }
}
```

- [ ] **Step 2: Criar ApiUnavailableException.java**

Criar `src/main/java/br/com/analisador/exception/ApiUnavailableException.java`:

```java
package br.com.analisador.exception;

public class ApiUnavailableException extends RuntimeException {

    public ApiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 3: Atualizar BrapiClient para lançar ApiUnavailableException**

Substituir todo o conteúdo de `src/main/java/br/com/analisador/client/BrapiClient.java`:

```java
package br.com.analisador.client;

import br.com.analisador.exception.ApiUnavailableException;
import br.com.analisador.model.BrapiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class BrapiClient {

    private final RestClient restClient;
    private final String token;

    public BrapiClient(RestClient brapiRestClient,
                       @Value("${brapi.token}") String token) {
        this.restClient = brapiRestClient;
        this.token = token;
    }

    /**
     * Busca dados de um ticker na BRAPI.
     * Retorna Optional.empty() se o ticker não existir (404 ou resultado vazio).
     * Lança ApiUnavailableException em caso de erros 5xx ou timeout.
     */
    public Optional<BrapiResponse.QuoteResult> buscarCotacao(String ticker) {
        try {
            BrapiResponse response = restClient.get()
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
                    .body(BrapiResponse.class);

            if (response == null || response.results() == null || response.results().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(response.results().get(0));

        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new ApiUnavailableException("API BRAPI indisponível: " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 4: Rodar os testes para confirmar que nada quebrou**

```bash
./mvnw test -pl . -q
```

Saída esperada: `Tests run: 37, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/analisador/exception/TickerNotFoundException.java \
        src/main/java/br/com/analisador/exception/ApiUnavailableException.java \
        src/main/java/br/com/analisador/client/BrapiClient.java
git commit -m "feat: add custom exceptions TickerNotFoundException and ApiUnavailableException

BrapiClient now throws ApiUnavailableException on 5xx/timeout errors
(distinguishing them from 404 which returns Optional.empty())."
```

---

### Task 12: GlobalExceptionHandler + error.html

**Files:**
- Create: `src/main/java/br/com/analisador/controller/GlobalExceptionHandler.java`
- Create: `src/main/resources/templates/error.html`

- [ ] **Step 1: Criar GlobalExceptionHandler.java**

Criar `src/main/java/br/com/analisador/controller/GlobalExceptionHandler.java`:

```java
package br.com.analisador.controller;

import br.com.analisador.exception.ApiUnavailableException;
import br.com.analisador.exception.TickerNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TickerNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleTickerNotFound(TickerNotFoundException ex, Model model) {
        model.addAttribute("titulo", "Ticker não encontrado");
        model.addAttribute("mensagem",
                "O ticker '" + ex.getTicker() + "' não foi encontrado na BRAPI. "
                + "Verifique se o código está correto (ex: PETR4, VALE3, ITUB4).");
        model.addAttribute("sugestao", "Retornar ao início e tentar outro ticker.");
        return "error";
    }

    @ExceptionHandler(ApiUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String handleApiUnavailable(ApiUnavailableException ex, Model model) {
        model.addAttribute("titulo", "API temporariamente indisponível");
        model.addAttribute("mensagem",
                "Não foi possível conectar à API BRAPI. "
                + "Aguarde alguns instantes e tente novamente.");
        model.addAttribute("sugestao", "Se o problema persistir, a API da BRAPI pode estar fora do ar.");
        return "error";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneric(Exception ex, Model model) {
        model.addAttribute("titulo", "Erro inesperado");
        model.addAttribute("mensagem", "Ocorreu um erro inesperado. Por favor, tente novamente.");
        model.addAttribute("sugestao", "Se o problema persistir, reporte em nosso GitHub.");
        return "error";
    }
}
```

- [ ] **Step 2: Criar error.html**

Criar `src/main/resources/templates/error.html`:

```html
<!DOCTYPE html>
<html lang="pt-BR" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title th:text="${titulo} ?: 'Erro'">Erro</title>
    <link rel="stylesheet" th:href="@{/css/style.css}">
</head>
<body>
<div class="container">
    <header class="header-resultado">
        <a href="/" class="btn-voltar">← Voltar ao início</a>
    </header>

    <main>
        <div class="error-card">
            <div class="error-icon">⚠️</div>
            <h1 th:text="${titulo} ?: 'Erro'">Erro</h1>
            <p class="error-mensagem" th:text="${mensagem}">Ocorreu um erro.</p>
            <p class="error-sugestao" th:text="${sugestao}"></p>
            <a href="/" class="btn-primario">Tentar novamente</a>
        </div>
    </main>
</div>
</body>
</html>
```

- [ ] **Step 3: Adicionar estilos da página de erro ao style.css**

Adicionar ao final de `src/main/resources/static/css/style.css`:

```css
/* ========= ERROR PAGE ========= */
.error-card {
    text-align: center;
    padding: 3rem 2rem;
    background: #fff;
    border-radius: 12px;
    box-shadow: 0 2px 8px rgba(0,0,0,0.08);
    max-width: 600px;
    margin: 2rem auto;
}

.error-icon {
    font-size: 3rem;
    margin-bottom: 1rem;
}

.error-card h1 {
    font-size: 1.8rem;
    color: #2d3748;
    margin-bottom: 1rem;
}

.error-mensagem {
    color: #4a5568;
    font-size: 1.05rem;
    margin-bottom: 0.8rem;
}

.error-sugestao {
    color: #718096;
    font-size: 0.9rem;
    margin-bottom: 1.5rem;
}

.btn-primario {
    display: inline-block;
    padding: 0.7rem 1.8rem;
    background: #4299e1;
    color: #fff;
    border-radius: 6px;
    text-decoration: none;
    font-weight: 600;
    font-size: 1rem;
    transition: background 0.2s;
}

.btn-primario:hover {
    background: #3182ce;
}
```

- [ ] **Step 4: Rodar os testes**

```bash
./mvnw test -pl . -q
```

Saída esperada: `Tests run: 37, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/analisador/controller/GlobalExceptionHandler.java \
        src/main/resources/templates/error.html \
        src/main/resources/static/css/style.css
git commit -m "feat: add GlobalExceptionHandler and user-friendly error page

@ControllerAdvice handles TickerNotFoundException (404), ApiUnavailableException
(503) and generic exceptions with descriptive Portuguese error messages."
```

---

### Task 13: Mini form no resultado.html + loading overlay + validação B3

**Files:**
- Modify: `src/main/resources/templates/resultado.html`
- Modify: `src/main/resources/static/css/style.css`
- Modify: `src/main/java/br/com/analisador/controller/AnaliseController.java`

- [ ] **Step 1: Adicionar validação B3 e mensagem de erro ao AnaliseController**

No método `analisar` do `AnaliseController.java`, adicionar validação após o check de blank:

```java
    @GetMapping("/analisar")
    public String analisar(@RequestParam("ticker") String ticker, Model model) {
        if (ticker == null || ticker.isBlank()) {
            model.addAttribute("erro", "Informe um ticker (ex: PETR4, VALE3)");
            return "index";
        }

        String tickerUpper = ticker.trim().toUpperCase();

        // Validação de formato B3: 4 letras + 1-2 dígitos (ex: PETR4, BBAS3, BOVA11)
        if (!tickerUpper.matches("^[A-Z]{4}[0-9]{1,2}$")) {
            model.addAttribute("erro",
                    "Formato de ticker inválido: '" + tickerUpper + "'. "
                    + "Use 4 letras + 1 ou 2 dígitos (ex: PETR4, VALE3, BOVA11)");
            return "index";
        }

        Optional<RelatorioAnalise> relatorio = analiseService.analisar(tickerUpper);

        if (relatorio.isEmpty()) {
            model.addAttribute("erro", "Ticker '" + tickerUpper + "' não encontrado ou sem dados");
            return "index";
        }

        model.addAttribute("relatorio", relatorio.get());
        return "resultado";
    }
```

- [ ] **Step 2: Substituir resultado.html com mini form e loading overlay**

Substituir todo o conteúdo de `src/main/resources/templates/resultado.html`:

```html
<!DOCTYPE html>
<html lang="pt-BR" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title th:text="'Análise de ' + ${relatorio.ticker()}">Análise</title>
    <link rel="stylesheet" th:href="@{/css/style.css}">
</head>
<body>

<!-- Loading overlay -->
<div class="loading-overlay" id="loading">
    <div class="loading-spinner"></div>
    <p>Analisando...</p>
</div>

<div class="container">
    <header class="header-resultado">
        <a href="/" class="btn-voltar">← Nova análise</a>

        <!-- Mini form de re-análise rápida -->
        <form action="/analisar" method="get" class="mini-form" id="mini-form">
            <input type="text"
                   name="ticker"
                   placeholder="Outro ticker..."
                   maxlength="6"
                   pattern="[A-Za-z]{4}[0-9]{1,2}"
                   title="4 letras + 1 ou 2 dígitos (ex: PETR4)">
            <button type="submit">Analisar</button>
        </form>
    </header>

    <main>
        <section class="empresa-header">
            <img th:if="${relatorio.logoUrl()}" th:src="${relatorio.logoUrl()}" alt="Logo" class="logo-empresa">
            <div>
                <h1 th:text="${relatorio.ticker()}">TICKER</h1>
                <h2 th:text="${relatorio.nomeEmpresa()}">Nome da empresa</h2>
                <div class="preco-atual">
                    <span class="preco" th:text="'R$ ' + ${#numbers.formatDecimal(relatorio.precoAtual(), 1, 'POINT', 2, 'COMMA')}">R$ 0,00</span>
                    <span class="variacao"
                          th:classappend="${relatorio.variacaoPercentual() != null and relatorio.variacaoPercentual() >= 0} ? 'positivo' : 'negativo'"
                          th:text="${relatorio.variacaoPercentual() != null} ? (${#numbers.formatDecimal(relatorio.variacaoPercentual(), 1, 'POINT', 2, 'COMMA')} + '%') : '-'">0%</span>
                </div>
                <div class="setor-badge" th:text="'Setor: ' + ${relatorio.setor()}">Setor: —</div>
            </div>
        </section>

        <section class="pontuacao-card"
                 th:classappend="${relatorio.pontuacao()} >= 70 ? 'score-bom' : (${relatorio.pontuacao()} >= 40 ? 'score-neutro' : 'score-ruim')">
            <div class="pontuacao-numero">
                <span th:text="${relatorio.pontuacao()}">0</span><span class="small">/100</span>
            </div>
            <div class="pontuacao-texto">
                <h3 th:text="${relatorio.classificacao()}">Classificação</h3>
                <p th:text="${relatorio.resumo()}">Resumo da análise</p>
            </div>
        </section>

        <section class="metricas">
            <h2>Indicadores Fundamentalistas</h2>
            <div class="metricas-grid">
                <div th:each="metrica : ${relatorio.metricas()}"
                     class="metrica-card"
                     th:classappend="${metrica.status().name().toLowerCase()}">
                    <div class="metrica-header">
                        <h3 th:text="${metrica.nome()}">Nome</h3>
                        <span class="badge" th:text="${metrica.status()}">STATUS</span>
                    </div>
                    <div class="metrica-valor" th:text="${metrica.valor()}">0,00</div>
                    <p class="metrica-explicacao" th:text="${metrica.explicacao()}">Explicação</p>
                </div>
            </div>
        </section>

        <section class="disclaimer">
            <p>
                ⚠️ <strong>Disclaimer:</strong> esta análise é puramente educacional e baseada em heurísticas gerais de
                análise fundamentalista. Não constitui recomendação de compra ou venda. Análise fundamentalista
                completa requer estudo do setor, da governança, das perspectivas de mercado e muitas outras variáveis
                não cobertas aqui. Consulte sempre um assessor de investimentos certificado antes de tomar decisões.
            </p>
        </section>
    </main>
</div>

<script>
    document.getElementById('mini-form').addEventListener('submit', function() {
        document.getElementById('loading').style.display = 'flex';
    });
    document.getElementById('form-analisar') && document.getElementById('form-analisar').addEventListener('submit', function() {
        document.getElementById('loading').style.display = 'flex';
    });
</script>
</body>
</html>
```

- [ ] **Step 3: Adicionar estilos do mini form e loading overlay ao style.css**

Adicionar ao final de `src/main/resources/static/css/style.css`:

```css
/* ========= MINI FORM (resultado.html) ========= */
.header-resultado {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 0.5rem;
    margin-bottom: 1.5rem;
}

.mini-form {
    display: flex;
    gap: 0.5rem;
}

.mini-form input[type="text"] {
    padding: 0.4rem 0.8rem;
    border: 1px solid #cbd5e0;
    border-radius: 6px;
    font-size: 0.9rem;
    width: 140px;
}

.mini-form input[type="text"]:focus {
    outline: none;
    border-color: #4299e1;
    box-shadow: 0 0 0 2px rgba(66, 153, 225, 0.2);
}

.mini-form button {
    padding: 0.4rem 1rem;
    background: #4299e1;
    color: #fff;
    border: none;
    border-radius: 6px;
    font-size: 0.9rem;
    font-weight: 600;
    cursor: pointer;
    transition: background 0.2s;
}

.mini-form button:hover {
    background: #3182ce;
}

/* ========= LOADING OVERLAY ========= */
.loading-overlay {
    display: none;
    position: fixed;
    inset: 0;
    background: rgba(255, 255, 255, 0.85);
    z-index: 9999;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 1rem;
    font-size: 1.1rem;
    color: #4a5568;
}

.loading-spinner {
    width: 48px;
    height: 48px;
    border: 5px solid #e2e8f0;
    border-top-color: #4299e1;
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
}

@keyframes spin {
    to { transform: rotate(360deg); }
}
```

- [ ] **Step 4: Rodar todos os testes**

```bash
./mvnw test -pl . -q
```

Saída esperada: `Tests run: 37, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Verificar aplicação sobe corretamente**

```bash
./mvnw spring-boot:run -q &
sleep 8
curl -s "http://localhost:8080/analisar?ticker=XXXINVALID" | grep -c "Formato de ticker"
kill %1
```

Saída esperada: `1`

- [ ] **Step 6: Commit final**

```bash
git add src/main/java/br/com/analisador/controller/AnaliseController.java \
        src/main/resources/templates/resultado.html \
        src/main/resources/static/css/style.css
git commit -m "feat: B3 ticker validation, mini re-analysis form, and loading overlay

- Validates ticker format (^[A-Z]{4}[0-9]{1,2}$) before BRAPI call
- Mini form in resultado.html header for quick re-analysis without going back
- Pure CSS loading spinner shown on form submit
- Loading overlay hidden by default, activated via minimal JS addEventListener"
```

---

## Self-Review

### Spec Coverage Check

| Requisito | Task |
|---|---|
| Remover spring-boot-starter-webflux | Task 1 |
| RestClient síncrono | Task 1 |
| ~30 unit tests AnaliseService | Tasks 2, 3, 4 (34 testes) |
| Caffeine cache 5 min | Task 5 |
| Limiares por setor (FINANCEIRO, UTILIDADES, COMMODITIES, VAREJO, DEFAULT) | Tasks 6, 7 |
| Badge setor no resultado.html | Task 7 |
| GET /comparar com max 5 tickers | Task 9 |
| CompletableFuture.allOf() 10s timeout | Task 8 |
| comparar.html com tabela | Task 10 |
| Chart.js radar chart | Task 10 |
| Link comparação no index.html | Task 9 |
| TickerNotFoundException | Task 11 |
| ApiUnavailableException | Task 11 |
| @ExceptionHandler + error.html | Task 12 |
| Mini form resultado.html | Task 13 |
| Loading CSS spinner | Task 13 |
| Validação regex B3 | Task 13 |

### Placeholder Scan

Nenhum TBD, TODO, ou "similar ao task anterior" encontrado. Cada step tem código completo.

### Type Consistency Check

- `RelatorioAnalise` tem campo `setor` adicionado em Task 7 Step 2 e usado em comparar.html (Task 10) como `r.setor()` ✓
- `SectorThresholds` record definido em Task 6 Step 1, métodos acessados como `t.plBom()`, `t.plNeutro()` etc. em Task 7 Step 1 — nomes batem ✓
- `analisarSemCache` retorna `Optional<RelatorioAnalise>` em Task 8 Step 1, usado em `analisarMultiplos` na mesma task ✓
- `GlobalExceptionHandler` usa `ex.getTicker()` — definido em `TickerNotFoundException.getTicker()` Task 11 Step 1 ✓
- `comparar.html` acessa `r.metricas` (array JS) e `r.ticker` — JSON serializado por Thymeleaf inline JS ✓
