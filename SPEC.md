# SRM Credit Engine — Especificação (`SPEC.md`)

## 1. Requisitos do Enunciado

1. **Gestão de Câmbio:** armazenar e prover taxas de câmbio (`BRL`/`USD`) com data/hora de vigência, via atualização manual ou integração mockada.
2. **Precificação (Strategy):** desacoplar o cálculo por tipo de recebível via padrão *Strategy* (permitindo novos tipos com spreads ou fórmulas distintas):
   - Fórmula base: $\text{VP}_{\text{BRL}} = \frac{\text{VF}_{\text{BRL}}}{(1 + i_{\text{base}} + i_{\text{spread}})^{n}}$
   - Spreads: **Duplicata Mercantil** = `1,50% a.m.`; **Cheque Pré-datado** = `2,50% a.m.`.
   - *Cross-currency* (título em `BRL`, pagamento em `USD`): conversão cambial ao final.
3. **Simulação e Liquidação:** simulação em tempo real sem efeito persistente e liquidação de lote de recebíveis com transação **ACID** (sem liquidação parcial/incompleta) e **idempotência**.
4. **Auditabilidade:**  cada liquidação deve gerar um registro histórico imutável contendo valor de face, valor presente, deságio, taxa base, spread aplicado, prazo utilizado, moeda de pagamento, cotação efetivamente utilizada (quando aplicável), data/hora de vigência da cotação e timestamp da liquidação. Os valores e parâmetros financeiros devem representar um snapshot da operação, permitindo sua rastreabilidade e reconstrução para auditoria. Liquidações concluídas não podem ser alteradas ou excluídas.

5. **API e Frontend (Pleno):** API RESTful semântica documentada via OpenAPI, validação de entrada e tratamento global de erros; extrato filtrável por **período, cedente e moeda**; painel do operador no Angular com campos obrigatórios de entrada (**valor de face, data de vencimento, tipo de recebível e moeda de pagamento**) e grid com paginação *server-side*; execução via Docker Compose.

---

## 2. Precisão Numérica e Golden Cases

### 2.1. Política Numérica e Arredondamento
- **Tipos:** proibido `float`/`double` no backend e cálculos financeiros em `number` no frontend. Uso estrito de `BigDecimal` na aplicação e `NUMERIC` no PostgreSQL (`NUMERIC(19,2)` para valores monetários e `NUMERIC(19,6)` / `NUMERIC(10,6)` para câmbio e taxas em razão decimal).
- **Precisão intermediária e momento do arredondamento:** operações intermediárias utilizam `MathContext.DECIMAL128` (`HALF_EVEN`), aplicando arredondamento para **2 casas decimais (`HALF_EVEN`)** exclusivamente:
  1. Na obtenção do Valor Presente em BRL ($\text{VP}_{\text{BRL (arredondado)}}$);
  2. Na conversão para USD: $\text{Valor Liquidado}_{\text{USD}} = \text{VP}_{\text{BRL (arredondado)}} \div \text{Cotação}_{\text{BRL/USD}}$.
- **Cálculo do deságio:** obtido em BRL subtraindo o valor presente em BRL já arredondado do valor de face ($\text{Deságio}_{\text{BRL}} = \text{VF}_{\text{BRL}} - \text{VP}_{\text{BRL (arredondado)}}$), sem arredondamento adicional desnecessário.
- **Contrato Java ↔ Angular:** valores monetários e taxas trafegam no JSON como `String` decimal exata (ex.: `"92859.94"`); o frontend apenas exibe e formata os valores calculados pelo backend.

### 2.2. Casos de Aferição Obrigatória (Golden Cases)
Premissas fixas: $i_{\text{base}} = 1,00\%\text{ a.m.}$, prazo em meses inteiros, juros compostos mensais e `HALF_EVEN` (2 casas no final):

| # | Tipo | Valor de Face | Prazo | Moeda Pgto. | Câmbio (BRL/USD) | Valor Presente Esperado | Deságio |
|---|---|---:|---:|---|---:|---:|---:|
| **C1** | Duplicata Mercantil | R$ 100.000,00 | 3 meses | BRL | — | **R$ 92.859,94** | R$ 7.140,06 |
| **C2** | Cheque Pré-datado | R$ 25.000,00 | 2 meses | BRL | — | **R$ 23.337,77** | R$ 1.662,23 |
| **C3** | Duplicata Mercantil | R$ 100.000,00 | 3 meses | USD | 5,4321 | **US$ 17.094,67** | R$ 7.140,06 |

**Regra de aferição:** os Golden Cases C1, C2 e C3 utilizam diretamente os prazos inteiros em meses informados no enunciado (3, 2 e 3 meses, respectivamente). 
Esses casos não dependem da conversão de datas de vencimento em meses comerciais. 
A conversão de datas é uma regra de entrada da aplicação, que deve ser validada por testes independentes, sem alterar os resultados esperados dos Golden Cases.
---

## 3. Premissas e Escolhas do Projeto para Ambiguidades

1. **Taxa Base:** configurável na aplicação (`1,00% a.m.` padrão) e gravada em cada liquidação para preservar o histórico.
2. **Prazo Financeiro (Premissa Provisória):** o núcleo financeiro recebe meses inteiros ($n \ge 1$). Quando informada data de vencimento (`dueDate`), a **data de referência da operação é determinada pelo backend** (`dueDate > data_operacao`, senão `422`) e converte-se os dias corridos em meses comerciais de 30 dias com arredondamento para cima (`ceil(dias / 30)`). *Limitações conhecidas:* prazos de 1 a 29 dias sofrem deságio de 1 mês inteiro, e intervalos de 31 dias corridos (comuns em meses de 31 dias) são enquadrados em 2 meses comerciais.
A conversão de datas em meses comerciais é aplicada exclusivamente quando a entrada da operação fornece uma data de vencimento. Quando o prazo em meses inteiros já estiver definido, como nos Golden Cases C1, C2 e C3, o motor financeiro deve utilizar diretamente esse prazo, sem recalculá-lo a partir de datas.
3. **Gestão, Seleção e Imutabilidade Cambial:** adota-se endpoint de registro/atualização manual de cotações (`BRL por 1 USD > 0`, com vigência `effectiveAt` e carga inicial para desenvolvimento). A operação seleciona a cotação com maior `effectiveAt <= instante_operacao` (sem cotação vigente, retorna `422`). A liquidação recalcula no servidor e grava um snapshot imutável da taxa aplicada: novas cotações jamais alteram retroativamente liquidações já concluídas.
4. **Escopo e Atomicidade do Lote:** cada requisição de liquidação processa `1..N` recebíveis de um único cedente e em uma única moeda de pagamento (`BRL` ou `USD`), sob política *all-or-nothing* (qualquer item inválido reverte todo o lote).
5. **Idempotência vs. Concorrência e Dupla Liquidação:**
   - *Idempotência (mesma operação repetida via `Idempotency-Key`):* mesma chave + mesmo payload retorna a liquidação original sem duplicar efeitos; mesma chave + payload divergente retorna `409 Conflict`; envios simultâneos com a mesma chave recebem tratamento determinístico sem gerar duplicidade (a política detalhada de resposta HTTP durante processamento concorrente será registrada no `DECISIONS.md`).
   - *Unicidade de liquidação por recebível (operações distintas):* além da identificação única do recebível, a associação entre recebível e liquidação garante que cada recebível integre **no máximo uma liquidação integral efetivada** (via restrição de unicidade do vínculo recebível–liquidação combinada ao controle transacional de estado/concorrência). Tentativas distintas — sequenciais ou concorrentes — de liquidar o mesmo recebível resultam em `409 Conflict` e rollback do lote conflitante.
6. **Relatórios:** o extrato paginado utilizará projeções JPA, query builder ou SQL nativo parametrizado conforme a necessidade da consulta (detalhes e cortes de escopo registrados em `DECISIONS.md`).

---

## 4. Perguntas à Área de Negócio (Projeto Real)

1. **Convenção de prazo e feriados:** a operação real utiliza dias úteis ANBIMA/252 com capitalização diária $(1+i)^{du/21}$ ou base comercial? Vencimentos em dias não úteis ajustam o prazo para o próximo dia útil?
2. **Taxa base dinâmica:** a taxa base varia por vértice de prazo (curva DI) e *rating* do cedente/sacado?
3. **Política cambial:** utiliza-se PTAX D-1, cotação *spot* com trava temporária (*quote lock* entre simulação e liquidação) e qual a idade máxima antes de considerar uma cotação expirada (*stale*)?
4. **Processamento parcial de lotes:** em lotes extensos, um item inelegível deve abortar todo o lote ou permitir liquidação parcial dos válidos?
5. **Custos, registradoras e estorno:** incidem IOF/tarifas em USD? Haverá conciliação síncrona com registradoras (CERC/TAG/B3) contra dupla cessão e qual evento compensatório aplica-se em caso de falha bancária pós-liquidação?

---

## 5. Critérios de Aceite

- **Corretude e Domínio:** reprodução exata ao centavo de **C1, C2 e C3**; extensibilidade comprovada para novas *Strategies* (spread ou fórmula distinta); separação estrita entre simulação e liquidação; atomicidade ACID, imutabilidade histórica (inclusive cambial) e testes automatizados cobrindo idempotência e tentativa de dupla liquidação do mesmo recebível.
- **Usabilidade:** painel Angular exigindo valor de face, data de vencimento, tipo de recebível e moeda de pagamento, com simulação automática via `debounce`, bloqueio de reenvio concorrente na UI, feedback claro de carregamento/vazio/erro e grid com filtros dinâmicos e paginação *server-side*.
- **Segurança:** queries 100% parametrizadas, validação de entrada na API e no domínio, erros padronizados sem exposição de stack trace/infraestrutura e credenciais via variáveis de ambiente.
- **Desempenho e Operação:** filtros e paginação executados integralmente no banco com limite máximo por página, ausência de consultas `N+1`, índices compatíveis com os filtros do extrato e vigência cambial, e subida reproduzível via `docker compose up --build`.

