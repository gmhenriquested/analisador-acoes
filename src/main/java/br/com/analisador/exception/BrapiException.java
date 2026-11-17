package br.com.analisador.exception;

/**
 * Falha ao consultar a BRAPI. Cada subclasse representa um motivo distinto e
 * carrega uma mensagem adequada para exibir ao usuario, separada da mensagem
 * tecnica que vai para o log.
 */
public abstract class BrapiException extends RuntimeException {

    private final String mensagemUsuario;

    protected BrapiException(String mensagemUsuario, String mensagemTecnica, Throwable causa) {
        super(mensagemTecnica, causa);
        this.mensagemUsuario = mensagemUsuario;
    }

    public String getMensagemUsuario() {
        return mensagemUsuario;
    }
}
