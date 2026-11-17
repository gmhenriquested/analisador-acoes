package br.com.analisador.service;

import br.com.analisador.client.BrapiClient;
import br.com.analisador.exception.BrapiIndisponivelException;
import br.com.analisador.exception.TickerNaoEncontradoException;
import br.com.analisador.model.BrapiResponse;
import br.com.analisador.model.BrapiResponse.DefaultKeyStatistics;
import br.com.analisador.model.BrapiResponse.FinancialData;
import br.com.analisador.model.BrapiResponse.QuoteResult;
import br.com.analisador.model.MetricaAvaliada;
import br.com.analisador.model.MetricaAvaliada.Status;
import br.com.analisador.model.RelatorioAnalise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AnaliseServiceTest {

    @Mock
    private BrapiClient brapiClient;

    @Test
    void propagaFalhaDeTickerInexistente() {
        AnaliseService service = new AnaliseService(brapiClient);
        given(brapiClient.buscarCotacao("XXXX9")).willThrow(new TickerNaoEncontradoException("XXXX9"));

        assertThatThrownBy(() -> service.analisar("XXXX9"))
                .isInstanceOf(TickerNaoEncontradoException.class);
    }

    @Test
    void propagaFalhaDeIndisponibilidade() {
        AnaliseService service = new AnaliseService(brapiClient);
        given(brapiClient.buscarCotacao("PETR4"))
                .willThrow(new BrapiIndisponivelException("timeout", null));

        assertThatThrownBy(() -> service.analisar("PETR4"))
                .isInstanceOf(BrapiIndisponivelException.class);
    }

    @Test
    void marcaMetricasComoIndisponiveisQuandoModulosVemNulos() {
        AnaliseService service = new AnaliseService(brapiClient);
        given(brapiClient.buscarCotacao("PETR4")).willReturn(new BrapiResponse.QuoteResult(
                "PETR4", "PETROBRAS", null, "BRL", 38.5, 1.0, null,
                null, null, null, null, null));

        RelatorioAnalise relatorio = service.analisar("PETR4");

        assertThat(relatorio.metricas())
                .allMatch(m -> m.status() == Status.INDISPONIVEL);
        assertThat(relatorio.pontuacao()).isZero();
        assertThat(relatorio.nomeEmpresa()).isEqualTo("PETROBRAS");
    }

    @Test
    void avaliaAsSeteMetricasEPontuaOsStatus() {
        given(brapiClient.buscarCotacao(anyString())).willReturn((quote(
                8.0,
                new DefaultKeyStatistics(null, 0.8, null, null, null, null),
                new FinancialData(null, 0.20, null, 30.0, 2.0, null, null, 0.20, 0.20, null))));

        RelatorioAnalise r = new AnaliseService(brapiClient).analisar("PETR4");

        assertEquals(List.of("P/L", "P/VP", "ROE", "Dívida/Patrimônio", "Liquidez Corrente",
                        "Margem Líquida", "Crescimento Receita"),
                r.metricas().stream().map(MetricaAvaliada::nome).toList());
        assertEquals(100, r.pontuacao());
        assertEquals("Potencialmente atrativa", r.classificacao());
    }

    @Test
    void modulosAusentesGeramMetricasIndisponiveisIgnoradasNaPontuacao() {
        given(brapiClient.buscarCotacao(anyString())).willReturn((quote(8.0, null, null)));

        RelatorioAnalise r = new AnaliseService(brapiClient).analisar("PETR4");

        assertEquals(6, r.metricas().stream().filter(m -> m.status() == Status.INDISPONIVEL).count());
        assertEquals(100, r.pontuacao());
    }

    @Test
    void semNenhumaMetricaDisponivelPontuacaoEZero() {
        given(brapiClient.buscarCotacao(anyString())).willReturn((quote(null, null, null)));

        RelatorioAnalise r = new AnaliseService(brapiClient).analisar("PETR4");

        assertEquals(0, r.pontuacao());
        assertEquals("Requer cautela", r.classificacao());
    }

    private QuoteResult quote(Double pl, DefaultKeyStatistics estatisticas, FinancialData financeiro) {
        return new QuoteResult("PETR4", "PETRO", "Petroleo Brasileiro SA", "BRL",
                30.0, 1.5, 100L, pl, 3.0, null, estatisticas, financeiro);
    }
}
