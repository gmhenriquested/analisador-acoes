package br.com.analisador.model;

/**
 * Representa uma metrica fundamentalista ja avaliada.
 * status: BOM, NEUTRO, RUIM ou INDISPONIVEL - usado no Thymeleaf pra colorir.
 */
public record MetricaAvaliada(
        String nome,
        String valor,
        Status status,
        String explicacao
) {
    public enum Status {
        BOM, NEUTRO, RUIM, INDISPONIVEL
    }
}
