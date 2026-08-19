package br.com.analisador.service;

import br.com.analisador.model.MetricaAvaliada;
import br.com.analisador.model.MetricaAvaliada.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;
import java.util.function.DoublePredicate;
import java.util.function.DoubleUnaryOperator;

/**
 * Criterio declarativo de avaliacao de uma metrica fundamentalista: nome, como
 * normalizar/formatar o valor bruto e as faixas (condicao -> status + explicacao)
 * avaliadas em ordem.
 *
 * Uso:
 * <pre>
 * CriterioMetrica.de("P/VP", "Preço sobre Valor Patrimonial não disponível")
 *         .quando(v -&gt; v &lt; 1, Status.BOM, "Cotada abaixo do valor patrimonial")
 *         .padrao(Status.RUIM, "P/VP alto pode indicar ação sobrevalorizada");
 * </pre>
 */
public final class CriterioMetrica {

    private static final String VALOR_INDISPONIVEL = "-";

    private final String nome;
    private final String mensagemIndisponivel;
    private final DoubleUnaryOperator normalizador;
    private final DoubleFunction<String> formatador;
    private final List<Faixa> faixas;
    private final Faixa padrao;

    private record Faixa(DoublePredicate condicao, Status status, String explicacao) {}

    private CriterioMetrica(String nome, String mensagemIndisponivel, DoubleUnaryOperator normalizador,
                            DoubleFunction<String> formatador, List<Faixa> faixas, Faixa padrao) {
        this.nome = nome;
        this.mensagemIndisponivel = mensagemIndisponivel;
        this.normalizador = normalizador;
        this.formatador = formatador;
        this.faixas = faixas;
        this.padrao = padrao;
    }

    public static Builder de(String nome, String mensagemIndisponivel) {
        return new Builder(nome, mensagemIndisponivel);
    }

    public MetricaAvaliada avaliar(Double valorBruto) {
        if (valorBruto == null) {
            return new MetricaAvaliada(nome, VALOR_INDISPONIVEL, Status.INDISPONIVEL, mensagemIndisponivel);
        }
        double valor = normalizador.applyAsDouble(valorBruto);
        for (Faixa faixa : faixas) {
            if (faixa.condicao().test(valor)) {
                return new MetricaAvaliada(nome, formatador.apply(valor), faixa.status(), faixa.explicacao());
            }
        }
        return new MetricaAvaliada(nome, formatador.apply(valor), padrao.status(), padrao.explicacao());
    }

    public static final class Builder {

        private final String nome;
        private final String mensagemIndisponivel;
        private final List<Faixa> faixas = new ArrayList<>();
        private DoubleUnaryOperator normalizador = DoubleUnaryOperator.identity();
        private DoubleFunction<String> formatador = Formatador::decimal;

        private Builder(String nome, String mensagemIndisponivel) {
            this.nome = nome;
            this.mensagemIndisponivel = mensagemIndisponivel;
        }

        /** Converte a fracao devolvida pela BRAPI (0.15) em percentual (15,00%). */
        public Builder emPercentual() {
            this.normalizador = v -> v * 100;
            this.formatador = Formatador::percentual;
            return this;
        }

        /** Converte o percentual devolvido pela BRAPI (50.0) na proporcao equivalente (0,50x). */
        public Builder comoProporcao(String sufixo) {
            this.normalizador = v -> v / 100.0;
            this.formatador = v -> Formatador.decimal(v) + sufixo;
            return this;
        }

        public Builder quando(DoublePredicate condicao, Status status, String explicacao) {
            faixas.add(new Faixa(condicao, status, explicacao));
            return this;
        }

        public CriterioMetrica padrao(Status status, String explicacao) {
            return new CriterioMetrica(nome, mensagemIndisponivel, normalizador, formatador,
                    List.copyOf(faixas), new Faixa(v -> true, status, explicacao));
        }
    }
}
