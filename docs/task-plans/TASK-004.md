# TASK-004 — Plano Técnico de Implementação

## 1. Identificação
- **Tarefa:** `TASK-004` — Prazo comercial e conversão cambial pura.
- **Objetivo:** Implementar os componentes de cálculo determinístico de prazo comercial e conversão cambial em Java 21 puro no pacote de domínio financeiro (`pricing.domain.calculation`), sem dependências de Spring, JPA/Hibernate, HTTP ou banco de dados. O cálculo de conversão deve reproduzir exatamente o Golden Case C3, validar defensivamente os limites do contrato OpenAPI, garantir arredondamento bancário `HALF_EVEN` em duas casas decimais e comprovar por teste determinístico a obrigatoriedade de conversão a partir de valor presente BRL previamente arredondado.
- **Status do planejamento:** `DONE` (Implementação concluída, validada por testes automatizados e aprovada pelo responsável).
- **Referências:** `SPEC.md` (§§1, 2, 3); `DECISIONS.md` (`DEC-002`, `DEC-003`, `DEC-004`, `DEC-005`, `DEC-006`, `DEC-010`); `AGENTS.md` (`G-01` a `G-04`); `TASKS.md` (`TASK-004`); `docs/openapi/srm-credit-engine.yaml`.
- **Dependências:** `TASK-003` (`DONE`).
- **Modelo de IA Responsável pelo Planejamento e Implementação:** Gemini 3.8 Flash (High).

## 2. Diagnóstico e Requisitos Técnicos
- **Isolamento e Responsabilidade Única:**
  - `CommercialTermCalculator`: cálculo exclusivo do prazo comercial em meses inteiros ($n \ge 1$) a partir de `LocalDate` (`operationDate`, `dueDate`). A conversão de datas é invocada na borda da aplicação conforme `DEC-005`, sem ser acoplada internamente ao `PricingEngine`.
  - `CurrencyConverter`: conversão exclusiva do valor presente BRL previamente arredondado para USD.
  - `PricingEngine` (TASK-003): permanece intocado, responsável exclusivamente pela precificação BRL.
- **Regras de Prazo Comercial (`CommercialTermCalculator`):**
  - Fórmula: $n = \lceil \text{dias corridos} / 30 \rceil$.
  - Invariante temporal: $dueDate > operationDate$. Se $dueDate \le operationDate$ ou datas nulas, lança exceção defensiva de domínio (`PricingValidationException`).
  - Dias corridos calculados via `ChronoUnit.DAYS.between(operationDate, dueDate)`.
- **Regras de Conversão Cambial (`CurrencyConverter`):**
  - Fórmula: $\text{PaidAmount}_{\text{USD}} = \text{VP}_{\text{BRL (arredondado)}} \div \text{rateBrl}$ com arredondamento `HALF_EVEN` em 2 casas decimais.
  - Invariantes de Cotação (`rateBrl`):
    - $0.000001 \le rateBrl \le 9999999.999999$
    - Escala máxima de 6 casas decimais ($scale \le 6$)
    - Não nulo, sem `double` ou `float`, sem arredondamento silencioso de entradas inválidas.
  - Invariantes de Entrada de Valor ($VP_{\text{BRL}}$):
    - Não nulo, não negativo ($>= 0$)
    - Escala máxima de 2 casas decimais ($scale \le 2$), rejeitando valores brutos não arredondados (ex.: $100.024$).
- **Golden Case C3:**
  - Duplicata Mercantil, $VF = \text{R\$}~100.000,00$, $n = 3$, $i_{\text{base}} = 0.010000$ (1,00% a.m.), spread $= 0.015000$ (1,50% a.m.).
  - $VP_{\text{BRL}} = \text{R\$}~92.859,94$, $\text{Deságio}_{\text{BRL}} = \text{R\$}~7.140,06$.
  - Cotação $= 5.432100$ (ou $5.4321$).
  - $\text{PaidAmount}_{\text{USD}} = 92.859,94 / 5.432100 = \text{US\$}~17.094,67$.
- **Cenário Determinístico de Ordem de Arredondamento:**
  - $VP_{\text{bruto}} = 100.024$, $VP_{\text{arredondado}} = 100.02$, Cotação $= 0.500000$.
  - Conversão correta (via $VP_{\text{arredondado}}$): $100.02 / 0.500000 = \text{USD}~200.04$.
  - Conversão direta do bruto: $100.024 / 0.500000 = 200.048 \rightarrow \text{USD}~200.05$.
  - Divergência: $200.04 \neq 200.05$. O teste comprova a divergência e valida a precisão do conversor.

## 3. Subtarefas
| Ordem | Subtarefa | Status |
|---|---|---|
| ST-01 | Criar plano técnico (`docs/task-plans/TASK-004.md`) e registrar `IN_PROGRESS` em `TASKS.md` | Concluído |
| ST-02 | Criar enum `CurrencyCode` (`BRL`, `USD`) em `pricing.domain.model` | Concluído |
| ST-03 | Implementar `CommercialTermCalculator` em `pricing.domain.calculation` | Concluído |
| ST-04 | Implementar `CurrencyConverter` em `pricing.domain.calculation` com limites OpenAPI e validações | Concluído |
| ST-05 | Implementar testes unitários isolados e integrados (`CommercialTermCalculatorTest`, `CurrencyConverterTest`, `PricingEngineCrossCurrencyTest`) | Concluído |
| ST-06 | Executar testes e suíte completa Maven em `backendcreditengine` registrando evidências reais | Concluído |
| ST-07 | Atualizar plano técnico com evidências reais e registrar `VALIDATION` em `TASKS.md` | Concluído |

## 4. Critérios de Aceite
- **CA-01:** Cálculo de prazo comercial exato em meses inteiros ($n \ge 1$) para $1$, $29$, $30$, $31$, $60$, $61$, $90$ dias corridos, viradas de mês e ano bissexto.
- **CA-02:** Rejeição defensiva com `PricingValidationException` para datas nulas ou $dueDate \le operationDate$.
- **CA-03:** Conversão BRL $\rightarrow$ USD precisa com divisão em `MathContext.DECIMAL128` e arredondamento `HALF_EVEN` em 2 casas decimais.
- **CA-04:** Validação estrita de limites da cotação ($[0.000001, 9999999.999999]$, $scale \le 6$) e rejeição de $VP_{\text{BRL}}$ com escala $> 2$.
- **CA-05:** Golden Case C3 reproduzido exatamente ao centavo: $\text{US\$}~17.094,67$ e deságio $\text{R\$}~7.140,06$.
- **CA-06:** Teste determinístico comprovando que a conversão sobre $VP_{\text{bruto}}$ ($100.024$) produziria $\text{USD}~200.05$, enquanto a conversão normativa sobre $VP_{\text{arredondado}}$ ($100.02$) produz $\text{USD}~200.04$.
- **CA-07:** Teste de integração financeira em `PricingEngineCrossCurrencyTest` compondo `PricingEngine` e `CurrencyConverter` sem modificar as classes da TASK-003.
- **CA-08:** Zero dependências de Spring, JPA/Hibernate ou HTTP nas classes de domínio.
- **CA-09:** Suíte completa Maven executada com sucesso (`BUILD SUCCESS`) e sem regressões nos testes da TASK-001/TASK-003.

## 5. Resultado da Execução
- **Status:** `DONE` (Implementação concluída, validada e aprovada pelo desenvolvedor).
- **Estrutura de Arquivos Criados:**
  - `backendcreditengine/src/main/java/com/claudiocarige/backendcreditengine/pricing/domain/model/CurrencyCode.java`
  - `backendcreditengine/src/main/java/com/claudiocarige/backendcreditengine/pricing/domain/calculation/CommercialTermCalculator.java`
  - `backendcreditengine/src/main/java/com/claudiocarige/backendcreditengine/pricing/domain/calculation/CurrencyConverter.java`
  - `backendcreditengine/src/test/java/com/claudiocarige/backendcreditengine/pricing/domain/calculation/CommercialTermCalculatorTest.java`
  - `backendcreditengine/src/test/java/com/claudiocarige/backendcreditengine/pricing/domain/calculation/CurrencyConverterTest.java`
  - `backendcreditengine/src/test/java/com/claudiocarige/backendcreditengine/pricing/domain/PricingEngineCrossCurrencyTest.java`
  - `docs/task-plans/TASK-004.md`
- **Classes Intocadas da TASK-003:**
  - `PricingEngine.java`, `PricingInput.java`, `PricingResult.java`, `ReceivableType.java`, `CompoundInterestCalculator.java`, `PricingStrategy.java`, `TradeBillPricingStrategy.java`, `PostDatedCheckPricingStrategy.java` e `DECISIONS.md` permaneceram 100% inalterados.
- **Comandos Executados e Evidências:**
  - `.\mvnw.cmd test "-Dtest=CommercialTermCalculatorTest,CurrencyConverterTest,PricingEngineCrossCurrencyTest"`:
    - `CommercialTermCalculatorTest`: 17 testes executados, 0 falhas, 0 erros (0.457 s).
    - `CurrencyConverterTest`: 16 testes executados, 0 falhas, 0 erros (0.094 s).
    - `PricingEngineCrossCurrencyTest`: 2 testes executados, 0 falhas, 0 erros (0.016 s).
    - Total de testes específicos da TASK-004: 35 testes, `BUILD SUCCESS` (7.024 s).
  - `.\mvnw.cmd test` (suíte completa com Testcontainers PostgreSQL 16.15):
    - `BackendcreditengineApplicationTests`: 1 teste executado, 0 falhas (33.46 s).
    - `CommercialTermCalculatorTest`: 17 testes executados, 0 falhas.
    - `CompoundInterestCalculatorTest`: 3 testes executados, 0 falhas.
    - `CurrencyConverterTest`: 16 testes executados, 0 falhas.
    - `PricingEngineCrossCurrencyTest`: 2 testes executados, 0 falhas.
    - `PricingEngineTest`: 18 testes executados, 0 falhas.
    - Total: **57 testes executados**, 0 falhas, 0 erros, `BUILD SUCCESS` (43.581 s).
- **Incidentes e Resolução Rastreável:**
  - Na primeira execução de `CurrencyConverterTest`, o teste parametrizado `shouldThrowWhenRateIsBelowMinimum` continha a entrada `"0.0000009"` (escala 7). A validação de escala ($\le 6$) disparou prioritariamente sobre a validação de limite mínimo ($0.000001$), gerando falha de mensagem esperada. A entrada foi corrigida para valores estritamente dentro da escala 6 (`"0.000000"`, `"-0.000001"`, `"-1.000000"`, `"-100.00"`), enquanto valores com escala $> 6$ são cobertos de forma independente pelo teste `shouldThrowWhenRateScaleExceedsSix`. O ajuste foi validado imediatamente.
- **Conformidade dos Critérios de Aceite:**
  - **CA-01 e CA-02 (Prazo Comercial):** APROVADOS (17 cenários cobrindo todos os limites temporais e rejeições defensivas).
  - **CA-03 e CA-04 (Conversão Cambial e Limites):** APROVADOS (limites $[0.000001, 9999999.999999]$, escala $\le 6$, rejeição de VP com escala $> 2$ e arredondamento `HALF_EVEN`).
  - **CA-05 (Golden Case C3):** APROVADO ($\text{US\$}~17.094,67$ e deságio $\text{R\$}~7.140,06$ ao centavo).
  - **CA-06 (Ordem de Arredondamento):** APROVADO (comprovação de $100.02 / 0.5 = 200.04 \neq 200.05$).
  - **CA-07 (Composição e Extensibilidade):** APROVADO (`PricingEngineCrossCurrencyTest` validando C3 e coexistência com C1 e C2 sem alterar classes de produção).
  - **CA-08 (Domínio Puro):** APROVADO (nenhuma dependência externa nos pacotes de cálculo).
  - **CA-09 (Suíte Verde):** APROVADO (57 testes passando, `BUILD SUCCESS`).
