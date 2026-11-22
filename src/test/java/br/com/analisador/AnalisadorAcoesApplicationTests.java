package br.com.analisador;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Sobe o contexto inteiro da aplicacao, incluindo o servidor MCP (Spring AI) e as
 * ferramentas registradas. Garante que a aplicacao inicializa de verdade e serve de
 * rede de seguranca para a fiacao do MCP, que os testes de slice nao exercitam.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AnalisadorAcoesApplicationTests {

    @Test
    void contextLoads() {
    }
}
