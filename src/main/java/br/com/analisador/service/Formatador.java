package br.com.analisador.service;

/** Formatacao dos valores exibidos nas metricas. */
final class Formatador {

    private Formatador() {
    }

    static String decimal(double valor) {
        return String.format("%.2f", valor);
    }

    static String percentual(double valor) {
        return String.format("%.2f%%", valor);
    }
}
