package br.com.analisador.service;

import br.com.analisador.model.MetricaAvaliada;
import br.com.analisador.model.MetricaAvaliada.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CriterioMetricaTest {

    private static final CriterioMetrica PL = CriterioMetrica.de("P/L", "indisponível")
            .quando(pl -> pl < 0, Status.RUIM, "prejuízo")
            .quando(pl -> pl < 10, Status.BOM, "barata")
            .padrao(Status.NEUTRO, "justo");

    @Test
    void valorNuloGeraMetricaIndisponivel() {
        MetricaAvaliada m = PL.avaliar(null);
        assertEquals(new MetricaAvaliada("P/L", "-", Status.INDISPONIVEL, "indisponível"), m);
    }

    @Test
    void primeiraFaixaQueCasaDefineStatus() {
        assertEquals(Status.RUIM, PL.avaliar(-3.0).status());
        assertEquals(Status.BOM, PL.avaliar(8.0).status());
        assertEquals(Status.NEUTRO, PL.avaliar(25.0).status());
    }

    @Test
    void formatoDecimalComDuasCasas() {
        assertEquals(String.format("%.2f", 8.0), PL.avaliar(8.0).valor());
    }

    @Test
    void percentualMultiplicaFracaoPorCem() {
        CriterioMetrica roe = CriterioMetrica.de("ROE", "indisponível")
                .emPercentual()
                .quando(v -> v > 15, Status.BOM, "rentável")
                .padrao(Status.RUIM, "baixa");

        MetricaAvaliada m = roe.avaliar(0.185);
        assertEquals(String.format("%.2f%%", 18.5), m.valor());
        assertEquals(Status.BOM, m.status());
    }

    @Test
    void proporcaoDividePercentualPorCemEAplicaSufixo() {
        CriterioMetrica divida = CriterioMetrica.de("Dívida/Patrimônio", "indisponível")
                .comoProporcao("x")
                .quando(v -> v < 0.5, Status.BOM, "saudável")
                .padrao(Status.RUIM, "alto");

        MetricaAvaliada m = divida.avaliar(40.0);
        assertEquals(String.format("%.2f", 0.4) + "x", m.valor());
        assertEquals(Status.BOM, m.status());
    }
}
