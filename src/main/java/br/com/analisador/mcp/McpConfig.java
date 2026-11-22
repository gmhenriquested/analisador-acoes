package br.com.analisador.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra as ferramentas anotadas com @Tool no servidor MCP. A auto-configuracao
 * do Spring AI expoe todo {@link ToolCallbackProvider} como ferramenta MCP.
 */
@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider analiseTools(AnaliseMcpTools analiseMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(analiseMcpTools)
                .build();
    }
}
