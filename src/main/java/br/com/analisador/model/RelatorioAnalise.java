package br.com.analisador.model;

import java.util.List;

/**
 * Relatorio final da analise - e o que o controller passa pro template Thymeleaf.
 */
public record RelatorioAnalise(
        String ticker,
        String nomeEmpresa,
        Double precoAtual,
        Double variacaoPercentual,
        String logoUrl,
        List<MetricaAvaliada> metricas,
        int pontuacao,          // 0 a 100
        String classificacao,   // "Atrativa", "Neutra", "Evitar"
        String resumo
) {}
