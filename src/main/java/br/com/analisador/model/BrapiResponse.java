package br.com.analisador.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.function.Function;

/**
 * Mapeia a resposta do endpoint /api/quote/{ticker} da BRAPI.
 * Uso @JsonIgnoreProperties(ignoreUnknown = true) pra ignorar campos que nao me interessam.
 */
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
            Double priceEarnings,          // P/L
            Double earningsPerShare,       // LPA
            String logourl,
            DefaultKeyStatistics defaultKeyStatistics,
            FinancialData financialData
    ) {
        /** Le um campo de financialData, devolvendo null quando o modulo nao veio na resposta. */
        public <T> T financeiro(Function<FinancialData, T> campo) {
            return financialData == null ? null : campo.apply(financialData);
        }

        /** Le um campo de defaultKeyStatistics, devolvendo null quando o modulo nao veio na resposta. */
        public <T> T estatistica(Function<DefaultKeyStatistics, T> campo) {
            return defaultKeyStatistics == null ? null : campo.apply(defaultKeyStatistics);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DefaultKeyStatistics(
            Double forwardPE,
            Double priceToBook,            // P/VP
            Double enterpriseToEbitda,     // EV/EBITDA
            Double bookValue,
            Double trailingEps,
            Double profitMargins
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FinancialData(
            Double currentPrice,
            Double returnOnEquity,         // ROE
            Double returnOnAssets,         // ROA
            Double debtToEquity,           // Divida/Patrimonio
            Double currentRatio,           // Liquidez Corrente
            Double grossMargins,
            Double operatingMargins,
            Double profitMargins,
            Double revenueGrowth,
            Double earningsGrowth
    ) {}
}
