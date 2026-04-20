# Analisador Fundamentalista de Ações

Aplicação web em Spring Boot que consulta dados de ações da B3 via API da BRAPI e
gera um relatório de análise fundamentalista automatizada.

## Stack

- **Java 17**
- **Spring Boot 3.3**
- **Spring WebFlux** (WebClient para chamar a BRAPI)
- **Thymeleaf** (templates)
- **Maven**

## Estrutura

```
src/main/java/br/com/analisador/
├── AnalisadorAcoesApplication.java   # Main
├── client/
│   └── BrapiClient.java              # Cliente HTTP da BRAPI
├── config/
│   └── WebClientConfig.java          # Bean do WebClient
├── controller/
│   └── AnaliseController.java        # Rotas / e /analisar
├── model/
│   ├── BrapiResponse.java            # DTO da resposta da BRAPI
│   ├── MetricaAvaliada.java          # Métrica com status (BOM/NEUTRO/RUIM)
│   └── RelatorioAnalise.java         # Relatório final
└── service/
    └── AnaliseService.java           # Lógica de análise
```

## Como rodar

### 1. Obter token da BRAPI (opcional)

Para as 4 ações de teste (**PETR4, VALE3, ITUB4, MGLU3**) não precisa de token.

Para outras ações, registre-se gratuitamente em https://brapi.dev/dashboard
e defina a variável de ambiente:

```bash
export BRAPI_TOKEN=seu_token_aqui
```

### 2. Rodar a aplicação

```bash
mvn spring-boot:run
```

### 3. Acessar

Abra http://localhost:8080 e digite um ticker (ex: `PETR4`).

## Indicadores avaliados

| Indicador | Bom | Neutro | Ruim |
|-----------|-----|--------|------|
| P/L (Preço/Lucro) | < 10 | 10–20 | > 20 ou negativo |
| P/VP | < 1 | 1–2 | > 2 |
| ROE | > 15% | 10–15% | < 10% |
| Dívida/Patrimônio | < 0.5x | 0.5–1.0x | > 1.0x |
| Liquidez Corrente | > 1.5 | 1.0–1.5 | < 1.0 |
| Margem Líquida | > 15% | 5–15% | < 5% |
| Crescimento Receita | > 10% | 0–10% | negativo |

A pontuação final é calculada como:
- **BOM**: 2 pontos
- **NEUTRO**: 1 ponto
- **RUIM**: 0 pontos

Escala final: (pontos obtidos / pontos máximos) × 100.

Classificação:
- **70–100**: Potencialmente atrativa
- **40–69**: Neutra
- **0–39**: Requer cautela

## Avisos importantes

⚠️ **Isto é uma ferramenta educacional.** Não constitui recomendação de investimento.

Os critérios usados são heurísticas fundamentalistas clássicas (inspiradas em Graham,
Fisher e manuais do mercado brasileiro), mas **não consideram particularidades de cada
setor**. Por exemplo:

- **Bancos**: naturalmente têm Dívida/Patrimônio altíssimo — isso não é ruim pra eles.
- **Empresas em crescimento**: podem ter P/L alto por expectativa de lucros futuros.
- **Commodities**: ciclos de preços influenciam margens drasticamente.

Sempre combine análise quantitativa com qualitativa, estude o setor, leia relatórios
da empresa e **consulte um assessor certificado** antes de investir.

## Próximos passos sugeridos

- [ ] Cache com Caffeine pra evitar chamar a BRAPI toda hora
- [ ] Comparação side-by-side de múltiplas ações
- [ ] Histórico de análises em H2/PostgreSQL
- [ ] Ajuste de critérios por setor (banco, varejo, indústria, etc.)
- [ ] Gráficos de evolução de preço com Chart.js
- [ ] Testes unitários no `AnaliseService`
