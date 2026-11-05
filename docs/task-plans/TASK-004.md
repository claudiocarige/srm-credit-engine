# TASK-004 — Plano Técnico de Implementação

## 1. Identificação
- **Tarefa:** `TASK-004` — Prazo comercial e conversão cambial pura.
- **Objetivo:** Implementar os componentes de cálculo determinístico de prazo comercial e conversão cambial em Java 21 puro no pacote de domínio financeiro (`pricing.domain.calculation`), sem dependências de Spring, JPA/Hibernate, HTTP ou banco de dados. O cálculo de conversão deve reproduzir exatamente o Golden Case C3, validar defensivamente os limites do contrato OpenAPI, garantir arredondamento bancário `HALF_EVEN` em duas casas decimais e comprovar por teste determinístico a obrigatoriedade de conversão a partir de valor presente BRL previamente arredondado.
- **Status do planejamento:** `IN_PROGRESS` (Implementação autorizada em andamento).
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
| ST-01 | Criar plano técnico (`docs/task-plans/TASK-004.md`) e registrar `IN_PROGRESS` em `TASKS.md` | Em andamento |
| ST-02 | Criar enum `CurrencyCode` (`BRL`, `USD`) em `pricing.domain.model` | Pendente |
| ST-03 | Implementar `CommercialTermCalculator` em `pricing.domain.calculation` | Pendente |
| ST-04 | Implementar `CurrencyConverter` em `pricing.domain.calculation` com limites OpenAPI e validações | Pendente |
| ST-05 | Implementar testes unitários isolados e integrados (`CommercialTermCalculatorTest`, `CurrencyConverterTest`, `PricingEngineCrossCurrencyTest`) | Pendente |
| ST-06 | Executar testes e suíte completa Maven em `backendcreditengine` registrando evidências reais | Pendente |
| ST-07 | Atualizar plano técnico com evidências reais e registrar `VALIDATION` em `TASKS.md` | Pendente |

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
*(A ser preenchido ao concluir as subtarefas ST-02 a ST-06)*
