package br.com.analisador.controller;

import br.com.analisador.exception.BrapiAutenticacaoException;
import br.com.analisador.exception.BrapiIndisponivelException;
import br.com.analisador.exception.TickerNaoEncontradoException;
import br.com.analisador.model.MetricaAvaliada;
import br.com.analisador.model.RelatorioAnalise;
import br.com.analisador.service.AnaliseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AnaliseController.class)
class AnaliseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnaliseService analiseService;

    @Test
    void exibeRelatorioQuandoAnaliseFunciona() throws Exception {
        given(analiseService.analisar("PETR4")).willReturn(new RelatorioAnalise(
                "PETR4", "Petrobras", 38.5, 1.2, null,
                List.of(new MetricaAvaliada("P/L", "5.00", MetricaAvaliada.Status.BOM, "ok")),
                100, "Potencialmente atrativa", "resumo"));

        mockMvc.perform(get("/analisar").param("ticker", "petr4"))
                .andExpect(status().isOk())
                .andExpect(view().name("resultado"))
                .andExpect(model().attributeExists("relatorio"));
    }

    @Test
    void retorna404ComMensagemQuandoTickerNaoExiste() throws Exception {
        given(analiseService.analisar("XXXX9")).willThrow(new TickerNaoEncontradoException("XXXX9"));

        mockMvc.perform(get("/analisar").param("ticker", "XXXX9"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("erro"));
    }

    @Test
    void retorna502ComMensagemQuandoBrapiEstaIndisponivel() throws Exception {
        given(analiseService.analisar("PETR4"))
                .willThrow(new BrapiIndisponivelException("timeout", null));

        mockMvc.perform(get("/analisar").param("ticker", "PETR4"))
                .andExpect(status().isBadGateway())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("erro",
                        new BrapiIndisponivelException("timeout", null).getMensagemUsuario()));
    }

    @Test
    void retorna502ComOrientacaoDeTokenQuandoBrapiRecusaAutenticacao() throws Exception {
        given(analiseService.analisar("WEGE3")).willThrow(new BrapiAutenticacaoException("401"));

        mockMvc.perform(get("/analisar").param("ticker", "WEGE3"))
                .andExpect(status().isBadGateway())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("erro",
                        new BrapiAutenticacaoException("401").getMensagemUsuario()));
    }

    @Test
    void retorna500ComMensagemAmigavelQuandoErroInesperado() throws Exception {
        given(analiseService.analisar("PETR4")).willThrow(new IllegalStateException("bug"));

        mockMvc.perform(get("/analisar").param("ticker", "PETR4"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("erro"));
    }

    @Test
    void rejeitaTickerComFormatoInvalidoSemChamarOServico() throws Exception {
        mockMvc.perform(get("/analisar").param("ticker", "nao-e-ticker"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("erro"));

        verifyNoInteractions(analiseService);
    }

    @Test
    void tratatickerAusenteComMensagemNaPaginaInicial() throws Exception {
        mockMvc.perform(get("/analisar"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("erro"));

        verifyNoInteractions(analiseService);
    }

    @Test
    void tratatickerEmBrancoComMensagemNaPaginaInicial() throws Exception {
        mockMvc.perform(get("/analisar").param("ticker", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("erro"));

        verifyNoInteractions(analiseService);
    }

    @Test
    void naoChamaServicoComTickerNaoNormalizado() throws Exception {
        given(analiseService.analisar(anyString())).willThrow(new TickerNaoEncontradoException("X"));

        mockMvc.perform(get("/analisar").param("ticker", " vale3 "))
                .andExpect(status().isNotFound());
    }
}
