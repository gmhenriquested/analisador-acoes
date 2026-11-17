package br.com.analisador.service;

import br.com.analisador.client.BrapiClient;
import br.com.analisador.exception.BrapiIndisponivelException;
import br.com.analisador.exception.TickerNaoEncontradoException;
import br.com.analisador.model.BrapiResponse;
import br.com.analisador.model.MetricaAvaliada.Status;
import br.com.analisador.model.RelatorioAnalise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
}
