# TASK-003 — Plano Técnico de Implementação

## 1. Identificação
- **Tarefa:** `TASK-003` — Motor financeiro BRL e Strategy.
- **Objetivo:** implementar o motor financeiro em Java puro para precificação de recebíveis em BRL, isolado de Spring, JPA/Hibernate, HTTP e banco de dados, utilizando o padrão Strategy aberto e composição. O motor deve reproduzir exatamente os Golden Cases C1 e C2, aplicar arredondamento bancário `HALF_EVEN` nas etapas normativas e respeitar o Open/Closed Principle.
- **Status do planejamento:** `DONE` (Implementação concluída, validada e aprovada).
- **Referências:** `SPEC.md` (§§1, 2); `DECISIONS.md` (`DEC-002`, `DEC-003`, `DEC-004`, `DEC-010`); `AGENTS.md` (`G-01` a `G-04`); `TASKS.md` (`TASK-003`).
- **Dependências:** `TASK-001` (`DONE`), `TASK-002` (`VALIDATION`).

## 2. Diagnóstico e Requisitos
- **Fórmula de Valor Presente BRL:**
  $$\text{VP} = \frac{\text{VF}}{(1 + i_{\text{total}})^n}$$
  onde $i_{\text{total}} = i_{\text{base}} + i_{\text{spread}}$, calculado em `MathContext.DECIMAL128`.
- **Deságio:**
  $$\text{Deságio} = \text{VF} - \text{VP}_{\text{arredondado}}$$
- **Spreads Aprovados:**
  - Duplicata Mercantil: `0.015000` (1,50% a.m.).
  - Cheque Pré-datado: `0.025000` (2,50% a.m.).
- **Golden Cases:**
  - **C1 (Duplicata Mercantil):** $\text{VF} = 100.000,00$, $n = 3$, $i_{\text{base}} = 0.010000 \rightarrow \text{VP} = 92.859,94$, $\text{Deságio} = 7.140,06$.
  - **C2 (Cheque Pré-datado):** $\text{VF} = 25.000,00$, $n = 2$, $i_{\text{base}} = 0.010000 \rightarrow \text{VP} = 23.337,77$, $\text{Deságio} = 1.662,23$.
- **Limites e Invariantes:**
  - $0,01 \le \text{VF} \le 999.999.999,99$ com escala $\le 2$.
  - $n \ge 1$ (meses inteiros).
  - $i_{\text{base}} \ge 0$ com escala $\le 6$ (`NUMERIC(10,6)`). Sem arredondamento silencioso de taxas inválidas.
- **Arquitetura (SOLID / OCP / Composição e Package by Feature):**
  - `pricing.domain.model`: tipos imutáveis (`ReceivableType`, `PricingInput`, `PricingResult`).
  - `pricing.domain.calculation`: `CompoundInterestCalculator` com responsabilidade exclusiva do cálculo matemático de valor presente e arredondamento `HALF_EVEN` para duas casas decimais.
  - `pricing.domain.strategy`: `PricingStrategy`, `TradeBillPricingStrategy` e `PostDatedCheckPricingStrategy`. Cada estratégia executa seu cálculo, soma seu spread, invoca o calculador, obtém o deságio por subtração exata e monta o `PricingResult`.
  - `pricing.domain`: `PricingEngine` (orquestrador injetável via construtor com validação defensiva de limites) e `PricingValidationException`.

## 3. Subtarefas
| Ordem | Subtarefa | Status |
|---|---|---|
| ST-01 | Criar plano técnico e registrar `IN_PROGRESS` em `TASKS.md` | Concluído |
| ST-02 | Criar tipos básicos (`ReceivableType`, `PricingInput`, `PricingResult`, `PricingValidationException`) | Concluído |
| ST-03 | Implementar `CompoundInterestCalculator` em `DECIMAL128` com `HALF_EVEN` | Concluído |
| ST-04 | Implementar `PricingStrategy`, `TradeBillPricingStrategy` e `PostDatedCheckPricingStrategy` | Concluído |
| ST-05 | Implementar `PricingEngine` com registro defensivo e validação de limites | Concluído |
| ST-06 | Implementar suíte de testes unitários (`CompoundInterestCalculatorTest` e `PricingEngineTest`) | Concluído |
| ST-07 | Executar suíte completa Maven e registrar evidências | Concluído |
| ST-08 | Refatoração estrutural em subpacotes (`model`, `strategy`, `calculation`, `domain`) e validação | Concluído |

## 4. Critérios de Aceite
- **CA-01:** Golden Case C1 reproduzido exatamente: $\text{VP} = \text{R\$}~92.859,94$ e $\text{Deságio} = \text{R\$}~7.140,06$.
- **CA-02:** Golden Case C2 reproduzido exatamente: $\text{VP} = \text{R\$}~23.337,77$ e $\text{Deságio} = \text{R\$}~1.662,23$.
- **CA-03:** Arredondamento `HALF_EVEN` comprovado em situações de empate exato contra `HALF_UP`.
- **CA-04:** Rejeição de valores fora dos limites contratuais ($\text{VF} < 0,01$, $\text{VF} > 999.999.999,99$, escalas inválidas, $n < 1$, taxa negativa ou com escala $> 6$).
- **CA-05:** Injeção de estratégias via construtor com validação contra nulos e duplicidade.
- **CA-06:** Comprovação de extensibilidade com estratégia personalizada e com fórmula alternativa sem alteração no `PricingEngine`.
- **CA-07:** Zero dependências de Spring, JPA/Hibernate ou HTTP no pacote de domínio financeiro.
- **CA-08:** 100% dos testes passando na suíte Maven.
- **CA-09:** Estrutura organizada por subpacotes coesos (`model`, `strategy`, `calculation`, `domain`) preservando encapsulamento e ausência de arquivos duplicados.

## 5. Resultado da Execução
- **Status:** `DONE` (Implementação concluída, validada e aprovada pelo desenvolvedor).
- **Estrutura Final de Arquivos:**
  - `backendcreditengine/src/main/java/com/claudiocarige/backendcreditengine/pricing/domain/model/ReceivableType.java`
  - `backendcreditengine/src/main/java/com/claudiocarige/backendcreditengine/pricing/domain/model/PricingInput.java`
  - `backendcreditengine/src/main/java/com/claudiocarige/backendcreditengine/pricing/domain/model/PricingResult.java`
  - `backendcreditengine/src/main/java/com/claudiocarige/backendcreditengine/pricing/domain/calculation/CompoundInterestCalculator.java`
  - `backendcreditengine/src/main/java/com/claudiocarige/backendcreditengine/pricing/domain/strategy/PricingStrategy.java`
  - `backendcreditengine/src/main/java/com/claudiocarige/backendcreditengine/pricing/domain/strategy/TradeBillPricingStrategy.java`
  - `backendcreditengine/src/main/java/com/claudiocarige/backendcreditengine/pricing/domain/strategy/PostDatedCheckPricingStrategy.java`
  - `backendcreditengine/src/main/java/com/claudiocarige/backendcreditengine/pricing/domain/PricingEngine.java`
  - `backendcreditengine/src/main/java/com/claudiocarige/backendcreditengine/pricing/domain/PricingValidationException.java`
  - `backendcreditengine/src/test/java/com/claudiocarige/backendcreditengine/pricing/domain/calculation/CompoundInterestCalculatorTest.java`
  - `backendcreditengine/src/test/java/com/claudiocarige/backendcreditengine/pricing/domain/PricingEngineTest.java`
  - `docs/task-plans/TASK-003.md`
- **Comandos Executados e Evidências:**
  - `.\mvnw.cmd test "-Dtest=PricingEngineTest,CompoundInterestCalculatorTest"`:
    - `CompoundInterestCalculatorTest`: 3 testes executados, 0 falhas, 0 erros (0.094 s).
    - `PricingEngineTest`: 18 testes executados, 0 falhas, 0 erros (0.211 s).
    - Total de testes específicos nos subpacotes: 21 testes, `BUILD SUCCESS` (8.450 s).
  - `.\mvnw.cmd test` (suíte completa com `DOCKER_HOST=npipe:////./pipe/docker_engine`):
    - `BackendcreditengineApplicationTests`: 1 teste executado, 0 falhas (Testcontainers PostgreSQL 16.15 iniciado com sucesso).
    - `CompoundInterestCalculatorTest`: 3 testes executados, 0 falhas.
    - `PricingEngineTest`: 18 testes executados, 0 falhas.
    - Total: 22 testes executados, 0 falhas, 0 erros, `BUILD SUCCESS` (34.253 s).
- **Resultados dos Critérios de Aceite:**
  - **CA-01 e CA-02 (C1 e C2):** APROVADOS com exatidão ao centavo.
  - **CA-03 (HALF_EVEN vs HALF_UP):** APROVADO (empates exatos em `1.025` e cenário completo $1.23 / 1.20 = 1.025 \rightarrow 1.02$).
  - **CA-04 (Limites contratuais e escala):** APROVADO (matriz com limites aceitos e rejeição de escalas $> 2$ no VF e $> 6$ na taxa base).
  - **CA-05 e CA-06 (Injeção e Extensibilidade OCP):** APROVADOS (validação de construtor e estratégias com spread customizado e desconto simples).
  - **CA-07 (Isolamento de Domínio):** APROVADO (nenhuma dependência de Spring/JPA nos subpacotes de `pricing.domain`).
  - **CA-08 (Build e Suíte Verde):** APROVADO (`BUILD SUCCESS`).
  - **CA-09 (Subpacotes Coesos):** APROVADO (`model`, `strategy`, `calculation`, `domain`).


