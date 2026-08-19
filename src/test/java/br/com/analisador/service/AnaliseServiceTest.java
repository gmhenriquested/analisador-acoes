package br.com.analisador.service;

import br.com.analisador.client.BrapiClient;
import br.com.analisador.model.BrapiResponse.DefaultKeyStatistics;
import br.com.analisador.model.BrapiResponse.FinancialData;
import br.com.analisador.model.BrapiResponse.QuoteResult;
import br.com.analisador.model.MetricaAvaliada;
import br.com.analisador.model.MetricaAvaliada.Status;
import br.com.analisador.model.RelatorioAnalise;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnaliseServiceTest {

    private final BrapiClient brapiClient = mock(BrapiClient.class);
    private final AnaliseService service = new AnaliseService(brapiClient);

    @Test
    void tickerSemDadosNaoGeraRelatorio() {
        when(brapiClient.buscarCotacao(anyString())).thenReturn(Optional.empty());
        assertTrue(service.analisar("XXXX").isEmpty());
    }

    @Test
    void avaliaAsSeteMetricasEPontuaOsStatus() {
        when(brapiClient.buscarCotacao(anyString())).thenReturn(Optional.of(quote(
                8.0,
                new DefaultKeyStatistics(null, 0.8, null, null, null, null),
                new FinancialData(null, 0.20, null, 30.0, 2.0, null, null, 0.20, 0.20, null))));

        RelatorioAnalise r = service.analisar("PETR4").orElseThrow();

        assertEquals(List.of("P/L", "P/VP", "ROE", "Dívida/Patrimônio", "Liquidez Corrente",
                        "Margem Líquida", "Crescimento Receita"),
                r.metricas().stream().map(MetricaAvaliada::nome).toList());
        assertEquals(100, r.pontuacao());
        assertEquals("Potencialmente atrativa", r.classificacao());
    }

    @Test
    void modulosAusentesGeramMetricasIndisponiveisIgnoradasNaPontuacao() {
        when(brapiClient.buscarCotacao(anyString())).thenReturn(Optional.of(quote(8.0, null, null)));

        RelatorioAnalise r = service.analisar("PETR4").orElseThrow();

        assertEquals(6, r.metricas().stream().filter(m -> m.status() == Status.INDISPONIVEL).count());
        assertEquals(100, r.pontuacao());
    }

    @Test
    void semNenhumaMetricaDisponivelPontuacaoEZero() {
        when(brapiClient.buscarCotacao(anyString())).thenReturn(Optional.of(quote(null, null, null)));

        RelatorioAnalise r = service.analisar("PETR4").orElseThrow();

        assertEquals(0, r.pontuacao());
        assertEquals("Requer cautela", r.classificacao());
    }

    private QuoteResult quote(Double pl, DefaultKeyStatistics estatisticas, FinancialData financeiro) {
        return new QuoteResult("PETR4", "PETRO", "Petroleo Brasileiro SA", "BRL",
                30.0, 1.5, 100L, pl, 3.0, null, estatisticas, financeiro);
    }
}
