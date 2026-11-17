package br.com.analisador.exception;

/**
 * A BRAPI recusou a chamada por falta de token ou token sem permissao (401/403).
 * Fora das quatro acoes liberadas (PETR4, VALE3, ITUB4, MGLU3) o token e obrigatorio.
 */
public class BrapiAutenticacaoException extends BrapiException {

    public BrapiAutenticacaoException(String mensagemTecnica) {
        super("A BRAPI recusou a consulta: este ticker exige um token válido. "
                        + "Configure a variável de ambiente BRAPI_TOKEN ou use PETR4, VALE3, ITUB4 ou MGLU3.",
                mensagemTecnica, null);
    }
}
