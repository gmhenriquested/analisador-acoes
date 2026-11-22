package br.com.analisador.mcp;

import br.com.analisador.model.RelatorioAnalise;
import br.com.analisador.service.AnaliseService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * Ferramentas expostas via MCP (Model Context Protocol). Um agente de IA conectado
 * ao servidor MCP desta aplicacao pode chamar {@link #analisarAcao(String)} para
 * obter a mesma analise fundamentalista da interface web.
 *
 * E a mesma logica do {@link AnaliseService}; aqui so embrulhamos como ferramenta.
 */
@Service
public class AnaliseMcpTools {

    private final AnaliseService analiseService;

    public AnaliseMcpTools(AnaliseService analiseService) {
        this.analiseService = analiseService;
    }

    @Tool(description = "Analisa uma acao da B3 por fundamentos e devolve uma nota de 0 a 100 "
            + "junto com 7 indicadores avaliados (P/L, P/VP, ROE, Divida/Patrimonio, Liquidez "
            + "Corrente, Margem Liquida e Crescimento da Receita), cada um com status e explicacao. "
            + "Ferramenta educacional, nao e recomendacao de investimento.")
    public RelatorioAnalise analisarAcao(
            @ToolParam(description = "Ticker da acao na B3, ex: PETR4, VALE3, ITUB4") String ticker) {
        String simbolo = ticker == null ? "" : ticker.trim().toUpperCase();
        return analiseService.analisar(simbolo);
    }
}
