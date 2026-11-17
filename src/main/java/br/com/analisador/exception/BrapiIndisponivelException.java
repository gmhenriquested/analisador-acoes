package br.com.analisador.exception;

/**
 * BRAPI fora do ar, com erro inesperado, resposta ilegivel ou lenta demais.
 */
public class BrapiIndisponivelException extends BrapiException {

    public BrapiIndisponivelException(String mensagemTecnica, Throwable causa) {
        super("Não foi possível consultar a BRAPI agora. Tente novamente em alguns instantes.",
                mensagemTecnica, causa);
    }
}
