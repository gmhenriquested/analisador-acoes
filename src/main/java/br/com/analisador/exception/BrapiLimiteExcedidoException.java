package br.com.analisador.exception;

/**
 * Limite de requisicoes da BRAPI atingido (429).
 */
public class BrapiLimiteExcedidoException extends BrapiException {

    public BrapiLimiteExcedidoException(String mensagemTecnica) {
        super("Limite de consultas à BRAPI atingido. Aguarde alguns instantes e tente novamente.",
                mensagemTecnica, null);
    }
}
