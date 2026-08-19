package br.com.analisador.service;

import br.com.analisador.client.BrapiClient;
import br.com.analisador.model.BrapiResponse;
import br.com.analisador.model.MetricaAvaliada;
import br.com.analisador.model.MetricaAvaliada.Status;
import br.com.analisador.model.RelatorioAnalise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnaliseServiceTest {

    private BrapiClient brapiClient;
    private AnaliseService service;

    @BeforeEach
    void setUp() {
        brapiClient = mock(BrapiClient.class);
        service = new AnaliseService(brapiClient);
    }

    @Test
    void retornaVazioQuandoClienteNaoAchaTicker() {
        when(brapiClient.buscarCotacao("XXXX")).thenReturn(Optional.empty());

        assertThat(service.analisar("XXXX")).isEmpty();
    }

    @Test
    void montaRelatorioComTodasAsSeteMetricas() {
        when(brapiClient.buscarCotacao(anyString())).thenReturn(Optional.of(quote(
                8.0, 0.8, 0.20, 40.0, 2.0, 0.20, 0.20)));

        RelatorioAnalise r = service.analisar("PETR4").orElseThrow();

        assertThat(r.ticker()).isEqualTo("PETR4");
        assertThat(r.nomeEmpresa()).isEqualTo("Petroleo Brasileiro SA");
        assertThat(r.precoAtual()).isEqualTo(38.5);
        assertThat(r.variacaoPercentual()).isEqualTo(1.25);
        assertThat(r.logoUrl()).isEqualTo("https://logo.test/petr4.png");
        assertThat(nomes(r.metricas())).containsExactly(
                "P/L", "P/VP", "ROE", "Dívida/Patrimônio", "Liquidez Corrente",
                "Margem Líquida", "Crescimento Receita");
    }

    @Test
    void usaShortNameQuandoLongNameAusente() {
        BrapiResponse.QuoteResult q = new BrapiResponse.QuoteResult(
                "MGLU3", "MAGAZ LUIZA", null, "BRL", 10.0, 0.0, 1L,
                12.0, 1.0, null, null, null);
        when(brapiClient.buscarCotacao("MGLU3")).thenReturn(Optional.of(q));

        assertThat(service.analisar("MGLU3").orElseThrow().nomeEmpresa()).isEqualTo("MAGAZ LUIZA");
    }

    @Test
    void marcaMetricasComoIndisponivelQuandoModulosVeemNulos() {
        BrapiResponse.QuoteResult q = new BrapiResponse.QuoteResult(
                "ABCD3", "ABCD", "ABCD SA", "BRL", 5.0, 0.0, 1L,
                null, null, null, null, null);
        when(brapiClient.buscarCotacao("ABCD3")).thenReturn(Optional.of(q));

        RelatorioAnalise r = service.analisar("ABCD3").orElseThrow();

        assertThat(r.metricas()).allSatisfy(m -> {
            assertThat(m.status()).isEqualTo(Status.INDISPONIVEL);
            assertThat(m.valor()).isEqualTo("-");
        });
        assertThat(r.pontuacao()).isZero();
        assertThat(r.classificacao()).isEqualTo("Requer cautela");
    }

    @Test
    void leMetricasDeDefaultKeyStatisticsMesmoSemFinancialData() {
        BrapiResponse.QuoteResult q = new BrapiResponse.QuoteResult(
                "ITUB4", "ITAU", "Itau Unibanco SA", "BRL", 30.0, 0.0, 1L,
                9.0, 3.0, null,
                new BrapiResponse.DefaultKeyStatistics(8.0, 0.9, 5.0, 20.0, 3.0, 0.3),
                null);
        when(brapiClient.buscarCotacao("ITUB4")).thenReturn(Optional.of(q));

        List<MetricaAvaliada> metricas = service.analisar("ITUB4").orElseThrow().metricas();

        assertThat(metricaPorNome(metricas, "P/L").status()).isEqualTo(Status.BOM);
        assertThat(metricaPorNome(metricas, "P/VP").status()).isEqualTo(Status.BOM);
        assertThat(metricaPorNome(metricas, "ROE").status()).isEqualTo(Status.INDISPONIVEL);
    }

    @ParameterizedTest
    @CsvSource({
            "-1.0, RUIM",
            "9.99, BOM",
            "10.0, NEUTRO",
            "20.0, NEUTRO",
            "20.01, RUIM"
    })
    void avaliaPLPorFaixa(double pl, Status esperado) {
        assertThat(avaliar("P/L", pl, null, null, null, null, null, null).status()).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource({
            "0.99, BOM",
            "1.0, NEUTRO",
            "2.0, NEUTRO",
            "2.01, RUIM"
    })
    void avaliaPVPPorFaixa(double pvp, Status esperado) {
        assertThat(avaliar("P/VP", null, pvp, null, null, null, null, null).status()).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource({
            "0.1501, BOM",
            "0.15, NEUTRO",
            "0.10, NEUTRO",
            "0.0999, RUIM"
    })
    void avaliaROEPorFaixa(double roe, Status esperado) {
        assertThat(avaliar("ROE", null, null, roe, null, null, null, null).status()).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource({
            "49.0, BOM",
            "50.0, NEUTRO",
            "100.0, NEUTRO",
            "101.0, RUIM"
    })
    void avaliaDividaPatrimonioPorFaixa(double debtToEquity, Status esperado) {
        MetricaAvaliada m = avaliar("Dívida/Patrimônio", null, null, null, debtToEquity, null, null, null);

        assertThat(m.status()).isEqualTo(esperado);
        assertThat(m.valor()).endsWith("x");
    }

    @ParameterizedTest
    @CsvSource({
            "1.51, BOM",
            "1.5, NEUTRO",
            "1.0, NEUTRO",
            "0.99, RUIM"
    })
    void avaliaLiquidezPorFaixa(double liquidez, Status esperado) {
        assertThat(avaliar("Liquidez Corrente", null, null, null, null, liquidez, null, null).status())
                .isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource({
            "0.1501, BOM",
            "0.15, NEUTRO",
            "0.05, NEUTRO",
            "-0.01, RUIM"
    })
    void avaliaMargemLiquidaPorFaixa(double margem, Status esperado) {
        assertThat(avaliar("Margem Líquida", null, null, null, null, null, margem, null).status())
                .isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource({
            "0.1001, BOM",
            "0.10, NEUTRO",
            "0.0, NEUTRO",
            "-0.0001, RUIM"
    })
    void avaliaCrescimentoReceitaPorFaixa(double crescimento, Status esperado) {
        assertThat(avaliar("Crescimento Receita", null, null, null, null, null, null, crescimento).status())
                .isEqualTo(esperado);
    }

    @Test
    void formataValoresComDuasCasasEPercentual() {
        List<MetricaAvaliada> metricas = analisar(quote(
                8.123, 0.826, 0.2049, 42.5, 1.789, 0.1234, 0.0567)).metricas();

        assertThat(metricaPorNome(metricas, "P/L").valor()).isEqualTo("8.12");
        assertThat(metricaPorNome(metricas, "P/VP").valor()).isEqualTo("0.83");
        assertThat(metricaPorNome(metricas, "ROE").valor()).isEqualTo("20.49%");
        assertThat(metricaPorNome(metricas, "Dívida/Patrimônio").valor()).isEqualTo("0.43x");
        assertThat(metricaPorNome(metricas, "Liquidez Corrente").valor()).isEqualTo("1.79");
        assertThat(metricaPorNome(metricas, "Margem Líquida").valor()).isEqualTo("12.34%");
        assertThat(metricaPorNome(metricas, "Crescimento Receita").valor()).isEqualTo("5.67%");
    }

    @Test
    void pontuacaoMaximaClassificaComoAtrativa() {
        RelatorioAnalise r = analisar(quote(5.0, 0.5, 0.30, 10.0, 2.0, 0.30, 0.30));

        assertThat(r.pontuacao()).isEqualTo(100);
        assertThat(r.classificacao()).isEqualTo("Potencialmente atrativa");
        assertThat(r.resumo()).contains("7 métrica(s) positiva(s), 0 negativa(s)");
    }

    @Test
    void pontuacaoMinimaClassificaComoRequerCautela() {
        RelatorioAnalise r = analisar(quote(30.0, 5.0, 0.01, 300.0, 0.5, 0.01, -0.5));

        assertThat(r.pontuacao()).isZero();
        assertThat(r.classificacao()).isEqualTo("Requer cautela");
        assertThat(r.resumo()).contains("0 métrica(s) positiva(s), 7 negativa(s)");
    }

    @Test
    void pontuacaoIntermediariaClassificaComoNeutra() {
        RelatorioAnalise r = analisar(quote(15.0, 1.5, 0.12, 80.0, 1.2, 0.10, 0.05));

        assertThat(r.pontuacao()).isEqualTo(50);
        assertThat(r.classificacao()).isEqualTo("Neutra");
    }

    @Test
    void ignoraMetricasIndisponiveisNaPontuacao() {
        BrapiResponse.QuoteResult q = new BrapiResponse.QuoteResult(
                "PETR4", "PETRO", "Petroleo SA", "BRL", 38.5, 1.25, 1L,
                5.0, 3.0, null,
                null,
                new BrapiResponse.FinancialData(38.5, 0.30, null, null, null, null, null, null, null, null));

        RelatorioAnalise r = analisar(q);

        // Apenas P/L e ROE disponiveis, ambos BOM -> 4/4 pontos
        assertThat(r.pontuacao()).isEqualTo(100);
        assertThat(r.resumo()).contains("2 métrica(s) positiva(s), 0 negativa(s)");
    }

    @Test
    void resumoTrazAvisoEducacional() {
        assertThat(analisar(quote(8.0, 0.8, 0.20, 40.0, 2.0, 0.20, 0.20)).resumo())
                .contains("análise automatizada educacional")
                .contains("consulte um profissional antes de investir");
    }

    @Test
    void metricasTrazemExplicacaoNaoVazia() {
        assertThat(analisar(quote(8.0, 0.8, 0.20, 40.0, 2.0, 0.20, 0.20)).metricas())
                .allSatisfy(m -> assertThat(m.explicacao()).isNotBlank());
    }

    // ===== helpers =====

    private MetricaAvaliada avaliar(String nome, Double pl, Double pvp, Double roe, Double debtToEquity,
                                    Double liquidez, Double margem, Double crescimento) {
        RelatorioAnalise r = analisar(quote(pl, pvp, roe, debtToEquity, liquidez, margem, crescimento));
        return metricaPorNome(r.metricas(), nome);
    }

    private RelatorioAnalise analisar(BrapiResponse.QuoteResult quote) {
        when(brapiClient.buscarCotacao(anyString())).thenReturn(Optional.of(quote));
        return service.analisar(quote.symbol()).orElseThrow();
    }

    private static BrapiResponse.QuoteResult quote(Double pl, Double pvp, Double roe, Double debtToEquity,
                                                  Double liquidez, Double margem, Double crescimento) {
        return new BrapiResponse.QuoteResult(
                "PETR4", "PETROBRAS PN", "Petroleo Brasileiro SA", "BRL",
                38.5, 1.25, 500_000_000L, pl, 4.2, "https://logo.test/petr4.png",
                new BrapiResponse.DefaultKeyStatistics(7.5, pvp, 4.0, 25.0, 4.2, margem),
                new BrapiResponse.FinancialData(38.5, roe, 0.1, debtToEquity, liquidez,
                        0.4, 0.3, margem, crescimento, 0.1));
    }

    private static List<String> nomes(List<MetricaAvaliada> metricas) {
        return metricas.stream().map(MetricaAvaliada::nome).toList();
    }

    private static MetricaAvaliada metricaPorNome(List<MetricaAvaliada> metricas, String nome) {
        return metricas.stream()
                .filter(m -> m.nome().equals(nome))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Metrica nao encontrada: " + nome));
    }
}
