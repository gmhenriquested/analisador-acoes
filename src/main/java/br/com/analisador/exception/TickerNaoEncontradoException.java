package br.com.analisador.exception;

public class TickerNaoEncontradoException extends BrapiException {

    private final String ticker;

    public TickerNaoEncontradoException(String ticker) {
        super("Ticker '" + ticker + "' não encontrado ou sem dados disponíveis na BRAPI",
                "BRAPI não retornou resultados para o ticker " + ticker, null);
        this.ticker = ticker;
    }

    public String getTicker() {
        return ticker;
    }
}
