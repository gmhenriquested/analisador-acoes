package br.com.analisador.controller;

import br.com.analisador.model.MetricaAvaliada;
import br.com.analisador.model.MetricaAvaliada.Status;
import br.com.analisador.model.RelatorioAnalise;
import br.com.analisador.service.AnaliseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class AnaliseControllerTest {

    private AnaliseService analiseService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        analiseService = mock(AnaliseService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AnaliseController(analiseService)).build();
    }

    @Test
    void indexRenderizaFormulario() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));

        verifyNoInteractions(analiseService);
    }

    @Test
    void analisarRenderizaResultadoQuandoRelatorioExiste() throws Exception {
        when(analiseService.analisar("PETR4")).thenReturn(Optional.of(relatorio()));

        mockMvc.perform(get("/analisar").param("ticker", "PETR4"))
                .andExpect(status().isOk())
                .andExpect(view().name("resultado"))
                .andExpect(model().attribute("relatorio", relatorio()))
                .andExpect(model().attributeDoesNotExist("erro"));
    }

    @Test
    void analisarRemoveEspacosDoTicker() throws Exception {
        when(analiseService.analisar(anyString())).thenReturn(Optional.of(relatorio()));

        mockMvc.perform(get("/analisar").param("ticker", "  petr4  "))
                .andExpect(view().name("resultado"));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(analiseService).analisar(captor.capture());
        assertThat(captor.getValue()).isEqualTo("petr4");
    }

    @Test
    void analisarVoltaParaIndexComErroQuandoTickerEmBranco() throws Exception {
        mockMvc.perform(get("/analisar").param("ticker", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("erro", "Informe um ticker (ex: PETR4, VALE3)"));

        verifyNoInteractions(analiseService);
    }

    @Test
    void analisarVoltaParaIndexComErroQuandoTickerNaoEncontrado() throws Exception {
        when(analiseService.analisar("xxxx")).thenReturn(Optional.empty());

        mockMvc.perform(get("/analisar").param("ticker", "xxxx"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("erro", "Ticker 'XXXX' não encontrado ou sem dados"))
                .andExpect(model().attributeDoesNotExist("relatorio"));
    }

    @Test
    void analisarSemParametroTickerRetornaBadRequest() throws Exception {
        mockMvc.perform(get("/analisar"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(analiseService);
    }

    private static RelatorioAnalise relatorio() {
        return new RelatorioAnalise(
                "PETR4", "Petroleo Brasileiro SA", 38.5, 1.25, "https://logo.test/petr4.png",
                List.of(new MetricaAvaliada("P/L", "8.00", Status.BOM, "barata")),
                100, "Potencialmente atrativa", "resumo");
    }
}
