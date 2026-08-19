package br.com.analisador.service;

import br.com.analisador.client.BrapiClient;
import br.com.analisador.model.BrapiResponse;
import br.com.analisador.model.MetricaAvaliada;
import br.com.analisador.model.MetricaAvaliada.Status;
import br.com.analisador.model.RelatorioAnalise;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Servico principal - busca dados na BRAPI e avalia cada metrica
 * segundo criterios fundamentalistas classicos.
 *
 * IMPORTANTE: os criterios sao heuristicas educacionais, nao recomendacao de investimento.
 * Valores ideais variam por setor (banco tem divida alta "normal", por exemplo).
 */
@Service
public class AnaliseService {

    private final BrapiClient brapiClient;

    public AnaliseService(BrapiClient brapiClient) {
        this.brapiClient = brapiClient;
    }

    /**
     * @throws br.com.analisador.exception.BrapiException se a consulta a BRAPI falhar
     *         (ticker inexistente, token invalido, limite excedido, indisponibilidade)
     */
    public RelatorioAnalise analisar(String ticker) {
        BrapiResponse.QuoteResult d = brapiClient.buscarCotacao(ticker);
        List<MetricaAvaliada> metricas = new ArrayList<>();

        // 1. P/L (Preco / Lucro) - quanto menor, mais barata a acao em relacao ao lucro
        metricas.add(avaliarPL(d.priceEarnings()));

        // 2. P/VP (Preco / Valor Patrimonial)
        Double pvp = d.defaultKeyStatistics() != null ? d.defaultKeyStatistics().priceToBook() : null;
        metricas.add(avaliarPVP(pvp));

        // 3. ROE (Return on Equity) - rentabilidade sobre patrimonio
        Double roe = d.financialData() != null ? d.financialData().returnOnEquity() : null;
        metricas.add(avaliarROE(roe));

        // 4. Divida / Patrimonio
        Double dividaPatrimonio = d.financialData() != null ? d.financialData().debtToEquity() : null;
        metricas.add(avaliarDividaPatrimonio(dividaPatrimonio));

        // 5. Liquidez Corrente
        Double liquidez = d.financialData() != null ? d.financialData().currentRatio() : null;
        metricas.add(avaliarLiquidez(liquidez));

        // 6. Margem Liquida
        Double margem = d.financialData() != null ? d.financialData().profitMargins() : null;
        metricas.add(avaliarMargemLiquida(margem));

        // 7. Crescimento da Receita
        Double crescReceita = d.financialData() != null ? d.financialData().revenueGrowth() : null;
        metricas.add(avaliarCrescimentoReceita(crescReceita));

        int pontuacao = calcularPontuacao(metricas);
        String classificacao = classificar(pontuacao);
        String resumo = gerarResumo(metricas, classificacao);

        return new RelatorioAnalise(
                d.symbol(),
                d.longName() != null ? d.longName() : d.shortName(),
                d.regularMarketPrice(),
                d.regularMarketChangePercent(),
                d.logourl(),
                metricas,
                pontuacao,
                classificacao,
                resumo
        );
    }

    // ========= AVALIACOES INDIVIDUAIS =========

    private MetricaAvaliada avaliarPL(Double pl) {
        if (pl == null) return indisponivel("P/L", "Preço sobre Lucro não disponível");
        if (pl < 0) return new MetricaAvaliada("P/L", fmt(pl), Status.RUIM,
                "P/L negativo indica que a empresa está dando prejuízo");
        if (pl < 10) return new MetricaAvaliada("P/L", fmt(pl), Status.BOM,
                "P/L abaixo de 10 sugere ação potencialmente barata");
        if (pl <= 20) return new MetricaAvaliada("P/L", fmt(pl), Status.NEUTRO,
                "P/L entre 10 e 20 é considerado justo");
        return new MetricaAvaliada("P/L", fmt(pl), Status.RUIM,
                "P/L acima de 20 pode indicar ação cara");
    }

    private MetricaAvaliada avaliarPVP(Double pvp) {
        if (pvp == null) return indisponivel("P/VP", "Preço sobre Valor Patrimonial não disponível");
        if (pvp < 1) return new MetricaAvaliada("P/VP", fmt(pvp), Status.BOM,
                "Cotada abaixo do valor patrimonial — pode estar descontada");
        if (pvp <= 2) return new MetricaAvaliada("P/VP", fmt(pvp), Status.NEUTRO,
                "P/VP entre 1 e 2 é considerado normal");
        return new MetricaAvaliada("P/VP", fmt(pvp), Status.RUIM,
                "P/VP alto pode indicar ação sobrevalorizada");
    }

    private MetricaAvaliada avaliarROE(Double roe) {
        if (roe == null) return indisponivel("ROE", "Retorno sobre Patrimônio não disponível");
        double roePct = roe * 100;
        if (roePct > 15) return new MetricaAvaliada("ROE", fmtPct(roePct), Status.BOM,
                "ROE acima de 15% indica empresa muito rentável");
        if (roePct >= 10) return new MetricaAvaliada("ROE", fmtPct(roePct), Status.NEUTRO,
                "ROE entre 10% e 15% é razoável");
        return new MetricaAvaliada("ROE", fmtPct(roePct), Status.RUIM,
                "ROE abaixo de 10% indica baixa rentabilidade");
    }

    private MetricaAvaliada avaliarDividaPatrimonio(Double dp) {
        if (dp == null) return indisponivel("Dívida/Patrimônio", "Endividamento não disponível");
        // BRAPI retorna em percentual (ex: 50.0 = 0.5x)
        double ratio = dp / 100.0;
        if (ratio < 0.5) return new MetricaAvaliada("Dívida/Patrimônio", fmt(ratio) + "x", Status.BOM,
                "Endividamento baixo — empresa financeiramente saudável");
        if (ratio <= 1.0) return new MetricaAvaliada("Dívida/Patrimônio", fmt(ratio) + "x", Status.NEUTRO,
                "Endividamento moderado");
        return new MetricaAvaliada("Dívida/Patrimônio", fmt(ratio) + "x", Status.RUIM,
                "Endividamento alto — risco financeiro elevado");
    }

    private MetricaAvaliada avaliarLiquidez(Double lc) {
        if (lc == null) return indisponivel("Liquidez Corrente", "Liquidez não disponível");
        if (lc > 1.5) return new MetricaAvaliada("Liquidez Corrente", fmt(lc), Status.BOM,
                "Empresa tem boa capacidade de pagar obrigações de curto prazo");
        if (lc >= 1.0) return new MetricaAvaliada("Liquidez Corrente", fmt(lc), Status.NEUTRO,
                "Liquidez adequada mas sem folga");
        return new MetricaAvaliada("Liquidez Corrente", fmt(lc), Status.RUIM,
                "Liquidez abaixo de 1 — pode ter dificuldade de honrar dívidas");
    }

    private MetricaAvaliada avaliarMargemLiquida(Double margem) {
        if (margem == null) return indisponivel("Margem Líquida", "Margem líquida não disponível");
        double pct = margem * 100;
        if (pct > 15) return new MetricaAvaliada("Margem Líquida", fmtPct(pct), Status.BOM,
                "Margem alta — empresa converte bem receita em lucro");
        if (pct >= 5) return new MetricaAvaliada("Margem Líquida", fmtPct(pct), Status.NEUTRO,
                "Margem razoável");
        return new MetricaAvaliada("Margem Líquida", fmtPct(pct), Status.RUIM,
                "Margem baixa ou negativa");
    }

    private MetricaAvaliada avaliarCrescimentoReceita(Double cresc) {
        if (cresc == null) return indisponivel("Crescimento Receita", "Crescimento não disponível");
        double pct = cresc * 100;
        if (pct > 10) return new MetricaAvaliada("Crescimento Receita", fmtPct(pct), Status.BOM,
                "Empresa crescendo fortemente");
        if (pct >= 0) return new MetricaAvaliada("Crescimento Receita", fmtPct(pct), Status.NEUTRO,
                "Crescimento modesto");
        return new MetricaAvaliada("Crescimento Receita", fmtPct(pct), Status.RUIM,
                "Receita em queda");
    }

    // ========= CONSOLIDACAO =========

    private int calcularPontuacao(List<MetricaAvaliada> metricas) {
        int totalConsideradas = 0;
        int pontos = 0;
        for (MetricaAvaliada m : metricas) {
            if (m.status() == Status.INDISPONIVEL) continue;
            totalConsideradas++;
            switch (m.status()) {
                case BOM -> pontos += 2;
                case NEUTRO -> pontos += 1;
                case RUIM -> pontos += 0;
                default -> {}
            }
        }
        if (totalConsideradas == 0) return 0;
        return (int) ((pontos * 100.0) / (totalConsideradas * 2));
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

    private String fmt(double v) {
        return String.format("%.2f", v);
    }

    private String fmtPct(double v) {
        return String.format("%.2f%%", v);
    }
}
