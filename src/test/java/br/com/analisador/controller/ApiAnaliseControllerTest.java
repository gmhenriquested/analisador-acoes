package br.com.analisador.controller;

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

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiAnaliseController.class)
class ApiAnaliseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnaliseService analiseService;

    @Test
    void retornaJsonComAnaliseQuandoTickerValido() throws Exception {
        given(analiseService.analisar("PETR4")).willReturn(new RelatorioAnalise(
                "PETR4", "Petrobras", 38.5, 1.2, null,
                List.of(new MetricaAvaliada("P/L", "5.00", MetricaAvaliada.Status.BOM, "ok")),
                100, "Potencialmente atrativa", "resumo"));

        mockMvc.perform(get("/api/analise/petr4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("PETR4"))
                .andExpect(jsonPath("$.pontuacao").value(100))
                .andExpect(jsonPath("$.metricas[0].nome").value("P/L"));
    }

    @Test
    void retorna400ComJsonQuandoTickerInvalido() throws Exception {
        mockMvc.perform(get("/api/analise/nao_e_ticker"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());

        verifyNoInteractions(analiseService);
    }

    @Test
    void retorna404ComJsonQuandoTickerNaoExiste() throws Exception {
        given(analiseService.analisar("XXXX9")).willThrow(new TickerNaoEncontradoException("XXXX9"));

        mockMvc.perform(get("/api/analise/xxxx9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void retorna502ComJsonQuandoBrapiIndisponivel() throws Exception {
        given(analiseService.analisar("PETR4"))
                .willThrow(new BrapiIndisponivelException("timeout", null));

        mockMvc.perform(get("/api/analise/petr4"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.erro").exists());
    }
}
