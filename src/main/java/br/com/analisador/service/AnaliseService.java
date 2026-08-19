package br.com.analisador.service;

import br.com.analisador.client.BrapiClient;
import br.com.analisador.model.BrapiResponse;
import br.com.analisador.model.BrapiResponse.DefaultKeyStatistics;
import br.com.analisador.model.BrapiResponse.FinancialData;
import br.com.analisador.model.MetricaAvaliada;
import br.com.analisador.model.MetricaAvaliada.Status;
import br.com.analisador.model.RelatorioAnalise;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Servico principal - busca dados na BRAPI e avalia cada metrica
 * segundo criterios fundamentalistas classicos.
 *
 * IMPORTANTE: os criterios sao heuristicas educacionais, nao recomendacao de investimento.
 * Valores ideais variam por setor (banco tem divida alta "normal", por exemplo).
 */
@Service
public class AnaliseService {

    private static final CriterioMetrica PL = CriterioMetrica
            .de("P/L", "Preço sobre Lucro não disponível")
            .quando(pl -> pl < 0, Status.RUIM, "P/L negativo indica que a empresa está dando prejuízo")
            .quando(pl -> pl < 10, Status.BOM, "P/L abaixo de 10 sugere ação potencialmente barata")
            .quando(pl -> pl <= 20, Status.NEUTRO, "P/L entre 10 e 20 é considerado justo")
            .padrao(Status.RUIM, "P/L acima de 20 pode indicar ação cara");

    private static final CriterioMetrica PVP = CriterioMetrica
            .de("P/VP", "Preço sobre Valor Patrimonial não disponível")
            .quando(pvp -> pvp < 1, Status.BOM, "Cotada abaixo do valor patrimonial — pode estar descontada")
            .quando(pvp -> pvp <= 2, Status.NEUTRO, "P/VP entre 1 e 2 é considerado normal")
            .padrao(Status.RUIM, "P/VP alto pode indicar ação sobrevalorizada");

    private static final CriterioMetrica ROE = CriterioMetrica
            .de("ROE", "Retorno sobre Patrimônio não disponível")
            .emPercentual()
            .quando(roe -> roe > 15, Status.BOM, "ROE acima de 15% indica empresa muito rentável")
            .quando(roe -> roe >= 10, Status.NEUTRO, "ROE entre 10% e 15% é razoável")
            .padrao(Status.RUIM, "ROE abaixo de 10% indica baixa rentabilidade");

    // BRAPI retorna o endividamento em percentual (ex: 50.0 = 0.5x)
    private static final CriterioMetrica DIVIDA_PATRIMONIO = CriterioMetrica
            .de("Dívida/Patrimônio", "Endividamento não disponível")
            .comoProporcao("x")
            .quando(ratio -> ratio < 0.5, Status.BOM, "Endividamento baixo — empresa financeiramente saudável")
            .quando(ratio -> ratio <= 1.0, Status.NEUTRO, "Endividamento moderado")
            .padrao(Status.RUIM, "Endividamento alto — risco financeiro elevado");

    private static final CriterioMetrica LIQUIDEZ = CriterioMetrica
            .de("Liquidez Corrente", "Liquidez não disponível")
            .quando(lc -> lc > 1.5, Status.BOM, "Empresa tem boa capacidade de pagar obrigações de curto prazo")
            .quando(lc -> lc >= 1.0, Status.NEUTRO, "Liquidez adequada mas sem folga")
            .padrao(Status.RUIM, "Liquidez abaixo de 1 — pode ter dificuldade de honrar dívidas");

    private static final CriterioMetrica MARGEM_LIQUIDA = CriterioMetrica
            .de("Margem Líquida", "Margem líquida não disponível")
            .emPercentual()
            .quando(pct -> pct > 15, Status.BOM, "Margem alta — empresa converte bem receita em lucro")
            .quando(pct -> pct >= 5, Status.NEUTRO, "Margem razoável")
            .padrao(Status.RUIM, "Margem baixa ou negativa");

    private static final CriterioMetrica CRESCIMENTO_RECEITA = CriterioMetrica
            .de("Crescimento Receita", "Crescimento não disponível")
            .emPercentual()
            .quando(pct -> pct > 10, Status.BOM, "Empresa crescendo fortemente")
            .quando(pct -> pct >= 0, Status.NEUTRO, "Crescimento modesto")
            .padrao(Status.RUIM, "Receita em queda");

    private final BrapiClient brapiClient;

    public AnaliseService(BrapiClient brapiClient) {
        this.brapiClient = brapiClient;
    }

    public Optional<RelatorioAnalise> analisar(String ticker) {
        Optional<BrapiResponse.QuoteResult> dadosOpt = brapiClient.buscarCotacao(ticker);
        if (dadosOpt.isEmpty()) {
            return Optional.empty();
        }

        BrapiResponse.QuoteResult d = dadosOpt.get();
        List<MetricaAvaliada> metricas = List.of(
                PL.avaliar(d.priceEarnings()),
                PVP.avaliar(d.estatistica(DefaultKeyStatistics::priceToBook)),
                ROE.avaliar(d.financeiro(FinancialData::returnOnEquity)),
                DIVIDA_PATRIMONIO.avaliar(d.financeiro(FinancialData::debtToEquity)),
                LIQUIDEZ.avaliar(d.financeiro(FinancialData::currentRatio)),
                MARGEM_LIQUIDA.avaliar(d.financeiro(FinancialData::profitMargins)),
                CRESCIMENTO_RECEITA.avaliar(d.financeiro(FinancialData::revenueGrowth))
        );

        int pontuacao = calcularPontuacao(metricas);
        String classificacao = classificar(pontuacao);
        String resumo = gerarResumo(metricas, classificacao);

        return Optional.of(new RelatorioAnalise(
                d.symbol(),
                d.longName() != null ? d.longName() : d.shortName(),
                d.regularMarketPrice(),
                d.regularMarketChangePercent(),
                d.logourl(),
                metricas,
                pontuacao,
                classificacao,
                resumo
        ));
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
}
