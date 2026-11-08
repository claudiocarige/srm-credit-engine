# SRM Credit Engine — Plano Inicial de Implementação (`TASKS.md`)

> **Perfil-alvo:** Desenvolvedor Java Pleno  
> **Situação:** planejamento proposto; **nenhuma implementação foi iniciada ou autorizada por este arquivo**.  
> **Prazo do enunciado:** até **5 dias corridos**; priorizar a entrega obrigatória e a defesa técnica.

## 1. Propósito e fontes de verdade

Este documento transforma os requisitos em **tarefas incrementais, verificáveis e rastreáveis**. Não substitui as regras de negócio nem autoriza o agente a alterar arquivos.

Consultar, nesta ordem e apenas nos trechos relevantes à tarefa:

1. Enunciado `desafio-tecnico-srm-credit-engine-v2 (6).md` — entregáveis e critérios da avaliação.
2. `SPEC.md` — fórmulas, premissas, comportamento e critérios de aceite.
3. `DECISIONS.md` — decisões arquiteturais oficialmente
   registradas e aprovadas. O documento evolui
   incrementalmente durante a implementação.
4. `AGENTS.md` — governança G-01 a G-04, segurança e execução.
5. Skills especializadas efetivamente necessárias à tarefa.

### Política de desenvolvimento Contract First

O SRM Credit Engine adotará a abordagem API Contract First.

A especificação `docs/openapi/srm-credit-engine.yaml`, criada e
aprovada na TASK-002, será a fonte oficial dos contratos HTTP da API.

Responsabilidades dos documentos:

- SPEC.md: requisitos e regras de negócio.
- DECISIONS.md: decisões arquiteturais aprovadas.
- docs/openapi/srm-credit-engine.yaml: contratos HTTP oficiais.
- docs/contracts.md: regras contratuais e operacionais complementares.
- TASKS.md: planejamento, execução e critérios de aceite.
- docs/task-plans/: planejamento técnico individual de cada tarefa.

Os controllers, DTOs, serviços HTTP e consumidores Angular deverão
respeitar a especificação OpenAPI aprovada.

É proibido introduzir ou modificar unilateralmente endpoints,
payloads, schemas ou códigos HTTP que contrariem o contrato oficial.

Alterações contratuais deverão ser propostas, justificadas e
aprovadas conforme G-01 e G-04, com atualização do OpenAPI antes
da implementação correspondente.

A documentação gerada em runtime e o Swagger UI deverão refletir
o contrato aprovado, sem estabelecer uma segunda fonte independente.

### Evolução progressiva das decisões

O TASKS.md pode referenciar decisões DEC-001 a DEC-017
mesmo quando ainda não estiverem detalhadas no
DECISIONS.md principal.

Essas referências preservam o planejamento previamente
discutido, mas não constituem autorização de implementação.

Antes de implementar uma tarefa que dependa de uma
decisão ainda não registrada:

1. Consultar o SPEC.md e os requisitos aplicáveis.
2. Identificar a decisão técnica necessária.
3. Apresentar alternativas, justificativas e riscos.
4. Obter aprovação explícita do responsável.
5. Incorporar a decisão aprovada ao DECISIONS.md
   durante a execução ou conclusão da tarefa autorizada.

Nenhum agente poderá presumir a aprovação de uma decisão
apenas porque seu ID aparece no TASKS.md.

**Não inventar regras financeiras ou contratos.** Ambiguidades que afetem dados, cálculos, API, concorrência ou auditoria devem ser apresentadas para decisão humana conforme G-04. Não criar estrutura adicional de Spec Kit/ADRs por padrão.

## 2. Política de execução

- **Uma tarefa por vez:** no máximo uma tarefa em `IN_PROGRESS` ou `VALIDATION`; não iniciar a próxima antes da aprovação da anterior.
- **G-01:** antes de criar/editar qualquer arquivo do projeto, o agente apresenta objetivo, arquivos afetados, alterações pretendidas, riscos e validações e obtém autorização explícita. A presença da tarefa neste documento **não** equivale a autorização.
- **G-02:** antes de cada tarefa, apresentar plano curto e sequencial; só executar o escopo aprovado.
- **G-03:** comandos locais de baixo risco e não destrutivos podem ser executados **dentro do escopo previamente aprovado**, sem pedir autorização individual; commits, pushes, instalações, exclusões, migrações destrutivas e operações externas exigem aprovação específica. Relatar comandos realmente executados, sem logs extensos desnecessários.
- **G-04:** não mudar unilateralmente requisitos, políticas financeiras, DEC aprovada ou contrato da API. Propor e obter aprovação antes.
- Cada tarefa implementa **seu código e seus testes correspondentes**. Não adiar a validação de regras financeiras para a última tarefa.
- Atualizar status e evidências no `TASKS.md` **somente dentro de uma autorização de escrita**. Não declarar testes como executados sem evidência.
- Registrar aprendizados e eventuais erros reais de IA para subsidiar `AI_USAGE.md`, sem inventar ocorrências.,
- **Contract First:** antes da implementação de qualquer endpoint
  REST ou consumidor Angular, verificar sua definição em
  `docs/openapi/srm-credit-engine.yaml`. Caso o contrato esteja
  ausente ou precise ser modificado, a tarefa deverá apresentar
  uma proposta de atualização e obter aprovação humana antes
  de alterar o código correspondente.

### Política de versionamento Git

- Utilizar branches para incrementos funcionais.
- Adotar Conventional Commits.
- Criar Pull Requests com descrição das alterações,
  justificativas e evidências de validação.
- Um incremento pode envolver mais de uma TASK quando
  houver dependência funcional indivisível.
- Não integrar funcionalidades incompletas à branch main.
- Commits, pushes e criação de PRs seguem a política
  de autorização G-03 do AGENTS.md.
- Não reescrever o histórico existente apenas para
  corrigir formatos de mensagens antigas.

**Estados:** `PENDING` (não iniciada) · `READY` (plano aprovado, implementação ainda não iniciada) · `IN_PROGRESS` (execução autorizada) · `VALIDATION` (aguardando revisão/aprovação) · `BLOCKED` (impedimento) · `DONE` (validada e aprovada pelo responsável).

**Prioridades:** `P0` = obrigatório ou bloqueador de requisito Pleno; `P1` = diferencial aprovado, subordinado à entrega P0. Priorização não dispensa critérios obrigatórios de aceitação.

## Planejamento técnico obrigatório por tarefa

### REGRA 1 — Planejamento obrigatório

Toda TASK deve possuir um plano técnico individual antes de iniciar sua implementação.

O planejamento deverá ser produzido pela rota de planejamento definida no `AGENTS.md`.

### REGRA 2 — Arquivo individual

O planejamento deverá ser registrado em:

`docs/task-plans/TASK-XXX.md`

Onde `XXX` corresponde ao identificador original da tarefa (ex.: `TASK-001` → `docs/task-plans/TASK-001.md`).

### REGRA 3 — Conteúdo mínimo

Cada arquivo deverá apresentar:

- Identificação e objetivo.
- Referências e dependências.
- Diagnóstico do estado atual do projeto.
- Abordagem técnica proposta.
- Justificativa da abordagem.
- Alternativas consideradas, quando relevantes.
- Arquivos previstos para criação ou modificação.
- Subtarefas sequenciais.
- Critérios de aceite.
- Testes obrigatórios.
- Riscos e medidas preventivas.
- Aprovação humana.
- Resultados e evidências de execução.
- Desvios relevantes em relação ao planejamento.
- Identificação da ferramenta e do modelo de IA responsável por sua elaboração (incluindo a versão, quando disponível), garantindo rastreabilidade das contribuições dos agentes ao longo do desenvolvimento.

### REGRA 4 — Aprovação antes da implementação

O agente deverá apresentar o planejamento para revisão humana. A criação do arquivo de planejamento não representa aprovação para implementar a tarefa.

A implementação somente poderá iniciar após:

1. Revisão do plano.
2. Resolução de ambiguidades críticas.
3. Aprovação humana do plano técnico.
4. Autorização explícita das modificações e comandos aplicáveis, respeitando G-01 e G-03.

### REGRA 5 — Preservação do histórico

Mudanças relevantes no planejamento deverão registrar:

- O que foi alterado e o motivo da alteração.
- Alternativa substituída, quando aplicável.
- Aprovação humana.
- Impacto nos critérios de aceite ou testes.

Não apagar justificativas e decisões anteriores sem necessidade. Utilizar o histórico Git como mecanismo complementar de rastreabilidade.

### REGRA 6 — Evolução progressiva

Os planos devem possuir detalhamento proporcional à complexidade de cada tarefa: tarefas simples recebem planos concisos; tarefas financeiras, transacionais ou de alto risco recebem análise mais detalhada. Evitar documentação redundante e repetição integral de `SPEC.md` e `DECISIONS.md`.

### REGRA 7 — Conclusão da tarefa

Antes de marcar uma TASK como `DONE`:

- Atualizar seu arquivo de planejamento com resultados reais.
- Registrar os testes executados e seus resultados.
- Informar desvios relevantes e decisões arquiteturais incorporadas.
- Registrar problemas e erros reais da IA, quando ocorrerem.
- Obter aprovação humana.

Nenhuma tarefa poderá ser considerada concluída sem essa atualização.

## 2.1. Definition of Ready (DoR)

Uma tarefa somente poderá iniciar sua implementação quando:

1. Suas dependências obrigatórias estiverem concluídas.
2. Seu objetivo, escopo e critérios de aceite estiverem claros.
3. Os requisitos aplicáveis do SPEC.md tiverem sido identificados.
4. As decisões arquiteturais necessárias estiverem aprovadas,
   mesmo que sua incorporação documental ocorra durante a tarefa.
5. Os cenários de teste aplicáveis estiverem identificados.
6. O agente tiver analisado o estado atual do projeto.
7. O agente tiver apresentado os arquivos previstos e um
   plano de implementação com riscos e validações.
8. O responsável tiver autorizado explicitamente a execução.
9. Para tarefas que implementam ou consomem APIs REST,
   os contratos HTTP aplicáveis devem estar definidos,
   validados e aprovados no OpenAPI oficial.

Caso exista uma ambiguidade crítica, a tarefa permanecerá
BLOCKED até sua resolução.

Uma tarefa marcada como READY não representa autorização
para modificar arquivos além do escopo aprovado.

## 3. Definition of Done (DoD)

Uma tarefa só pode receber `DONE` quando:

1. Seu escopo aprovado foi implementado sem alterações não autorizadas.
2. Os critérios aplicáveis de `SPEC.md` e `DECISIONS.md` foram satisfeitos.
3. **100% das regras de negócio aplicáveis** à tarefa têm testes automatizados rastreáveis; cenários conhecidos de sucesso, erro e fronteira foram cobertos conforme DEC-010.
4. Os testes pertinentes foram **efetivamente executados** e passaram; falhas são corrigidas ou mantêm a tarefa aberta/bloqueada. Para transações, constraints e concorrência, utilizar PostgreSQL com Testcontainers.
5. Foram verificadas ausência de efeitos financeiros indevidos, integridade e segurança quando aplicáveis.
6. O agente apresentou arquivos alterados, verificações executadas, resultados e riscos remanescentes.
7. **O responsável pelo projeto aprovou explicitamente a conclusão.**
8. As decisões técnicas relevantes surgidas durante a tarefa foram registradas ou atualizadas no DECISIONS.md, mediante autorização do responsável.
9. As alterações foram versionadas conforme a política Git aprovada. Quando o incremento funcional estiver completo, o Pull Request deverá apresentar justificativas e evidências dos testes.
10. A tarefa possui arquivo de planejamento individual, previamente aprovado, atualizado com resultados e evidências reais, incluindo os desvios relevantes identificados durante a implementação.
11. Para tarefas que implementam ou consomem APIs REST, a implementação foi verificada quanto à conformidade com `docs/openapi/srm-credit-engine.yaml`, incluindo schemas, validações, códigos HTTP e respostas de erro.
12. Para tarefas exclusivamente documentais, a validação corresponde aos critérios documentais aplicáveis. A TASK-002 exige validação estrutural da especificação OpenAPI, consistência com SPEC.md e aprovação humana,  sem exigir testes de código ainda não implementado.
13. **Validação estrita de requests:** as tarefas responsáveis por implementar endpoints REST deverão garantir que propriedades JSON não definidas no contrato OpenAPI sejam rejeitadas com HTTP 400, utilizando o padrão ProblemDetail, e comprovar esse comportamento com testes automatizados.
**Nota:** cobertura de regras conhecidas não é sinônimo de 100% de linhas de código nem de exaustividade sobre todas as falhas imagináveis.

## 4. Visão geral — backlog inicial

> IDs estáveis para rastreio. A ordem orienta a execução, mas dependências e prioridades prevalecem. Nenhuma tarefa foi iniciada.

| ID | Entrega | Prioridade | Depende de | Status |
|---|---|---|---|---|
| TASK-001 | Estrutura mínima do backend Java e testes | P0 | — | `DONE` |
| TASK-002 | Contratos de dados/API com OpenAPI Contract First | P0 | 001 | `VALIDATION` |
| TASK-003 | Motor financeiro BRL + Strategy + C1/C2 | P0 | 001, 002 | `DONE` |
| TASK-004 | Prazo comercial, conversão BRL→USD + C3 | P0 | 003 | `DONE` |
| TASK-005 | PostgreSQL, Flyway e modelo persistente inicial | P0 | 002 | `DONE` |
| TASK-006 | Currency Engine: histórico, vigência e API manual | P0 | 004, 005 | `DONE` |
| TASK-007 | API REST de simulação + conformidade OpenAPI e Swagger UI | P0 | 003, 004, 006 | `READY` |
| TASK-008 | Cadastro/identificação mínima de cedentes e recebíveis | P0 | 002, 005 | `PENDING` |
| TASK-009 | Liquidação de lote ACID + auditoria imutável | P0 | 006, 007, 008 | `PENDING` |
| TASK-010 | Idempotência, dupla liquidação e concorrência | P0 | 009 | `PENDING` |
| TASK-011 | API do extrato com filtros e paginação no banco | P0 | 010 | `PENDING` |
| TASK-012 | Estrutura mínima do frontend Angular | P0 | 002 | `PENDING` |
| TASK-013 | Painel Angular de simulação em tempo real | P0 | 007, 012 | `PENDING` |
| TASK-014 | Interface Angular de liquidação com retry seguro | P0 | 010, 012 | `PENDING` |
| TASK-015 | Grid Angular de extrato com filtros e paginação | P0 | 011, 012 | `PENDING` |
| TASK-016 | Docker/Compose e execução integrada local | P0 | 011, 013, 014, 015 | `PENDING` |
| TASK-017 | `REVIEW.md`: code review reverso do Anexo A | P0 | 003, 010 | `PENDING` |
| TASK-018 | CI enxuta GitHub Actions | P1 | 010, 012 | `PENDING` |
| TASK-019 | README, `AI_USAGE.md`, Git/PRs e checklist de entrega | P0 | 016, 017 | `PENDING` |
| TASK-020 | Homologação final, rastreabilidade e preparo da defesa | P0 | 016, 017, 019 | `PENDING` |

### Dependências de integração

TASK-009 e TASK-010 compõem um único incremento
funcional de liquidação.

A TASK-009 poderá ser validada tecnicamente como etapa
intermediária, mas a funcionalidade de liquidação não
poderá ser integrada à main nem considerada pronta
para entrega antes da aprovação da TASK-010.

As duas tarefas poderão utilizar a mesma branch,
mantendo execução e aprovação individuais.

**Observação de sequenciamento:** TASK-018 pode ser antecipada assim que os builds e testes mínimos estiverem funcionais; após criada, deve evoluir junto à suíte. Se o prazo estiver sob risco, qualquer adiamento deve ser comunicado e aprovado, sem sacrificar requisitos Pleno.

## 5. Detalhamento das tarefas

### TASK-001 — Consolidar backend existente e base de testes

- **Objetivo:** validar e consolidar o scaffold Spring Boot
  existente, preservando sua estrutura funcional.

- **Escopo:** inspecionar o projeto backendcreditengine/,
  verificar Java 21, Spring Boot, Maven Wrapper,
  dependências existentes, configurações e testes.
  Adicionar somente dependências fundamentais necessárias
  e aprovadas, após verificar compatibilidade de versões.

- **Restrições:** não recriar o projeto, renomear pacotes,
  atualizar frameworks indiscriminadamente ou sobrescrever
  configurações existentes sem justificativa e autorização.

- **Referências:** DEC-001, DEC-002, DEC-010;
- **Aceite/testes:** compilação executada, dependências
  verificadas, configuração local segura, ausência de
  credenciais reais e estratégia definida para testes
  de inicialização que dependam do PostgreSQL.

- **Evidências:** Ver [`docs/task-plans/TASK-001.md`](docs/task-plans/TASK-001.md) — `.\mvnw.cmd -B clean test` `BUILD SUCCESS` em Java `21.0.11` com PostgreSQL `16.15` (Testcontainers `2.0.5`), Flyway e Hibernate `7.4.13` (`ddl-auto=validate`).

### TASK-002 — Definir contratos de dados e API com OpenAPI Contract First

- **Objetivo:** estabelecer e aprovar os contratos HTTP oficiais
  do SRM Credit Engine antes da implementação dos endpoints,
  controllers e consumidores Angular.

- **Escopo:**
  - Consolidar o modelo mínimo de cedentes, recebíveis,
    simulações, câmbio, liquidações e extrato.
  - Definir endpoints, métodos HTTP, parâmetros, headers,
    schemas, validações, respostas e códigos de erro.
  - Criar `docs/openapi/srm-credit-engine.yaml` como
    especificação OpenAPI oficial.
  - Manter `docs/contracts.md` como documentação
    complementar, evitando duplicar o contrato HTTP.
  - Formalizar as regras aprovadas de idempotência,
    precisão financeira, snapshots e paginação.
  - Não implementar controllers ou endpoints nesta tarefa.

- **Referências:** SPEC §§1, 2, 3 e 5;
  DEC-002, DEC-003, DEC-007, DEC-010;
  AGENTS G-01 a G-04. ver [Contrato OpenAPI](docs/openapi/srm-credit-engine.yaml).

- **Aceite/testes:**
  - Especificação OpenAPI 3.x criada e validada.
  - Endpoints REST essenciais documentados.
  - Schemas de request e response definidos.
  - Valores financeiros representados como strings decimais.
  - Header Idempotency-Key e seus comportamentos definidos.
  - Respostas de erro padronizadas com ProblemDetail.
  - Regras de paginação, filtros e limites documentadas.
  - Exemplos financeiros consistentes com C1, C2 e C3.
  - Referências $ref válidas e ausência de erros estruturais.
  - Contrato consistente com SPEC.md e DECISIONS.md.
  - Aprovação humana antes da implementação das APIs.

- **Evidências:** Ver [`docs/openapi/srm-credit-engine.yaml`](docs/openapi/srm-credit-engine.yaml), [`docs/contracts.md`](docs/contracts.md) e [`docs/task-plans/TASK-002.md`](docs/task-plans/TASK-002.md) — OpenAPI `3.0.3` oficial (10 operações em 8 paths sob `/api/v1`, strings decimais estritamente positivas na entrada, `additionalProperties: false` nos 4 schemas de request, `ProblemDetail` RFC 9457 com `errorCode`, `Idempotency-Key`, `Retry-After` e exemplos C1–C3; `@redocly/cli lint` executado na Revisão `1.4` + verificação local de invariantes/regexes e `git diff --check` na Revisão `1.5`).

### TASK-003 — Motor financeiro BRL e Strategy
- **Objetivo:** calcular valor presente e deságio em BRL, isolado de Spring/JPA/HTTP.
- **Escopo:** `PricingStrategy` aberta, Strategies de duplicata (1,5% a.m.) e cheque (2,5% a.m.), calculador composto compartilhado por composição, DTOs `record`, seleção simples de Strategy, taxa base padrão configurável (1,0% a.m.).
- **Referências:** SPEC §§1–2; DEC-003, DEC-004, DEC-010.
- **Aceite/testes:** **C1 = BRL 92.859,94** e **C2 = BRL 23.337,77**, com deságios exatos; `BigDecimal`, `DECIMAL128`, `HALF_EVEN`; negativos/zero/prazo/taxas inválidos; demonstração de extensibilidade sem alterar Strategies existentes.
    - Criar testes específicos que diferenciem HALF_EVEN
    de HALF_UP em situações de empate exato.
    - Verificar arredondamentos com dígitos anteriores
    pares e ímpares.
    - Utilizar resultados esperados calculados
    independentemente da implementação testada.
- **Evidências:** Ver [`docs/task-plans/TASK-003.md`](docs/task-plans/TASK-003.md) — Motor financeiro BRL em Java puro (`CompoundInterestCalculator`, `TradeBillPricingStrategy`, `PostDatedCheckPricingStrategy`, `PricingEngine`) aprovado com 21 testes unitários (`CompoundInterestCalculatorTest`, `PricingEngineTest`) reproduzindo C1 (VP R$ 92.859,94 / Deságio R$ 7.140,06) e C2 (VP R$ 23.337,77 / Deságio R$ 1.662,23), diferenciação exata de `HALF_EVEN` vs `HALF_UP` em empates (.XX5), limites financeiros de entrada e extensibilidade OCP comprovada. Suíte completa Maven com 22 testes passando (`BUILD SUCCESS`).

### TASK-004 — Prazo comercial e conversão cambial pura
- **Objetivo:** completar as regras financeiras independentes de infraestrutura.
- **Escopo:** `dueDate` → `ceil(dias corridos/30)` com data da operação provida pelo backend; conversão ao final de VP_BRL já arredondado para USD, cotação BRL por USD, sem novo cálculo no frontend.
- **Referências:** SPEC §§2–3; DEC-003, DEC-005, DEC-006.
- **Aceite/testes:** **C3 = USD 17.094,67**, deságio BRL 7.140,06; golden cases recebem prazo inteiro diretamente; 1/30/31 dias, viradas de mês, ano bissexto e vencimento inválido; taxa de câmbio inválida; nenhum arredondamento prematuro.
    - Criar caso financeiro adicional em que converter o
    valor presente antes de seu arredondamento em BRL
    produza resultado diferente de converter o valor
    presente BRL previamente arredondado.
    - Garantir que esse teste falhe caso a ordem de
    arredondamento seja implementada incorretamente.
    - Utilizar Clock controlável para testes envolvendo
    data da operação, vencimento e prazo comercial.
- **Evidências:** Ver [`docs/task-plans/TASK-004.md`](docs/task-plans/TASK-004.md) — Componentes de prazo comercial (`CommercialTermCalculator`) e conversão cambial (`CurrencyConverter`) implementados em Java puro com 35 testes unitários e de integração (`CommercialTermCalculatorTest`, `CurrencyConverterTest`, `PricingEngineCrossCurrencyTest`) cobrindo prazo comercial em meses inteiros (1, 30, 31, 60, 90 dias, bissexto), limites contratuais da cotação ([0.000001, 9999999.999999]), HALF_EVEN em empates, salvaguarda contra VP bruto não arredondado, Golden Case C3 exato (US$ 17.094,67 e Deságio R$ 7.140,06) e teste determinístico de divergência da ordem de arredondamento (US$ 200.04 != US$ 200.05). Suíte completa Maven com 57 testes passando (`BUILD SUCCESS`).

### TASK-005 — Banco PostgreSQL, migrations e entidades iniciais
- **Objetivo:** estabelecer persistência reproduzível e invariantes de domínio.
- **Escopo:** Flyway como única origem do schema, `ddl-auto=validate`, entidades/mapeamentos mínimos aprovados; tabelas e constraints necessárias às funcionalidades desta fase. As tabelas de liquidação podem evoluir em migration posterior na TASK-009.
    - Criar o diagrama Entidade-Relacionamento (ER)
    correspondente ao modelo relacional aprovado.
    - Documentar entidades, relacionamentos, cardinalidades,
    chaves primárias, estrangeiras e constraints relevantes.
    - Manter o diagrama consistente com as migrations Flyway.
- Preparar infraestrutura PostgreSQL local mínima quando
  necessária para validação da persistência.
- **Referências:** DEC-001, DEC-007, DEC-008, DEC-010; SPEC §§1–3.
- **Aceite/testes:** migrations executadas em PostgreSQL Testcontainers; `NUMERIC` com escalas aprovadas; PK/FK/CHECK/UNIQUE aplicáveis; validação de mapeamento; sem `ddl-auto=update`, N+1 gratuito ou remoção destrutiva de histórico.
    - Diagrama ER disponível e coerente com o schema.
    - Modelo relacional normalizado conforme o domínio aprovado.
    - Migrations reproduzíveis em PostgreSQL.
    - Testes de integração executados com Testcontainers.
- **Evidências:** Ver [`docs/task-plans/TASK-005.md`](docs/task-plans/TASK-005.md) e [`docs/er-diagram.md`](docs/er-diagram.md) — Migrations Flyway `V1__create_assignors_and_receivables.sql` e `V2__create_exchange_rates.sql` implementadas e validadas; entidades JPA `Assignor`, `Receivable`, `ExchangeRate` e enum `ReceivableStatus` mapeados e validados contra schema PostgreSQL via Hibernate `ddl-auto=validate`; 33 novos testes adicionados (12 unitários + 21 de integração PostgreSQL/Testcontainers cobrindo constraints PK, FK RESTRICT, UK, CHECKs de dígitos/regex/faixa monetária/câmbio, LAZY loading e metadados). Suíte completa Maven com 90 testes passando sem regressões (`BUILD SUCCESS`). Diagrama ER criado em Mermaid.

### TASK-006 — Implementar Currency Engine
- **Objetivo:** cadastrar e recuperar a cotação vigente para uma operação. 
- **Escopo:** endpoint de atualização manual, histórico append-only, `effectiveAt`, seleção da maior vigência `<= instante da operação`, índice apropriado e massa inicial apenas de desenvolvimento. Implementar os endpoints de cadastro e consulta de cotações conforme as operações, parâmetros e schemas definidos em `docs/openapi/srm-credit-engine.yaml`, avaliando e configurando a desserialização do Jackson para a versão do Spring Boot adotada a fim de rejeitar propriedades JSON desconhecidas em `CreateExchangeRateRequest` (`400 Bad Request`, `application/problem+json`, `errorCode: VALIDATION_ERROR`, sem expor classes internas).
- **Referências:** SPEC §§1 e 3; DEC-006, DEC-008, DEC-013. ver [Contrato OpenAPI](docs/openapi/srm-credit-engine.yaml).
- **Aceite/testes:** cotação positiva (`rateBrl >= 0.000001`, rejeitando `"0.000000"`); vigências anteriores/futuras, retroatividade e ausência de taxa (`422`); novas cotações não mudam históricos já concluídos; teste de API e integração no PostgreSQL. Validar por testes automatizados (`MockMvc` / integração) a conformidade dos endpoints de câmbio com o contrato OpenAPI, incluindo rejeição efetiva de propriedades JSON desconhecidas com `HTTP 400` + `errorCode: VALIDATION_ERROR`, payloads, validações, códigos HTTP e respostas de erro.
- **Evidências:** Ver [`docs/task-plans/TASK-006.md`](docs/task-plans/TASK-006.md) — Endpoints REST `/api/v1/exchange-rates` (POST) e `/api/v1/exchange-rates/current` (GET) implementados em conformidade com o OpenAPI oficial; bean `Clock` UTC gerenciado; `ExchangeRateRepository` com query otimizada por índice de vigência e verificação pontual `existsByCurrencyAndEffectiveAt`; `ExchangeRateService` com `saveAndFlush` e tradução estruturada de violação de constraint (SQLState 23505 e constraint `uk_exchange_rates_currency_effective_at`) para `DuplicateExchangeRateException` (`409 Conflict`) e busca por instante de operação com `ExchangeRateUnavailableException` (`422 Unprocessable Entity`); `GlobalExceptionHandler` nativo (`ProblemDetail`) padronizando `errorCode` e `fieldErrors` com nomes públicos contratuais (`currency`, `effectiveAt`, `rateBrl`), incluindo tratamento explícito de 404 (`RESOURCE_NOT_FOUND`) e 405 (`METHOD_NOT_ALLOWED`); configuração estrita do Jackson (`fail-on-unknown-properties: true`) e deserializador estrito `StrictStringDeserializer` para rejeição de números JSON em `rateBrl` com HTTP 400 `VALIDATION_ERROR`; validação prévia de `currency=USD` no GET rejeitando `BRL` e `EUR` com HTTP 400 antes de consultas de persistência; seed condicional de desenvolvimento `ExchangeRateDevDataInitializer` (`@Profile("dev")`) com verificação pontual exata e Clock injetado. 30 novos testes adicionados (2 no dev initializer, 9 unitários no serviço, 14 de contrato `MockMvc` no controller e 5 de integração com Testcontainers PostgreSQL). Suíte completa Maven com 121 testes passando sem regressões (`BUILD SUCCESS`). `git diff --check` aprovado sem erros. Tarefa aprovada pelo responsável e concluída.

### TASK-007 — API REST de simulação e integração OpenAPI/Swagger UI

- **Objetivo:** implementar a API REST de simulação
  conforme o contrato OpenAPI aprovado na TASK-002
  e disponibilizar a documentação interativa da API.

- **Escopo:**
  - Implementar o endpoint de simulação conforme o
    schema e as operações do OpenAPI oficial.
  - Aplicar Bean Validation e validações de domínio.
  - Avaliar e garantir a configuração do Jackson na versão do Spring Boot utilizada para rejeitar propriedades JSON desconhecidas durante a desserialização dos requests (`additionalProperties: false`).
  - Integrar o motor financeiro e o Currency Engine.
  - Implementar o tratamento global de erros (`ProblemDetail` com `errorCode`, traduzindo propriedades desconhecidas para `400 Bad Request`, `application/problem+json` e `errorCode: VALIDATION_ERROR` sem expor tipos internos).
  - Disponibilizar Swagger UI utilizando o contrato
    OpenAPI oficial como referência.
  - Verificar compatibilidade das bibliotecas de
    integração com a versão do Spring Boot adotada.
  - Evitar gerar uma especificação concorrente
    e divergente do YAML aprovado.

- **Referências:** SPEC §§1–3 e 5;
  DEC-003 a DEC-006, DEC-010;
  TASK-002; docs/openapi/srm-credit-engine.yaml.

- **Aceite/testes:**
  - Endpoint implementado conforme o contrato OpenAPI.
  - Request e response aderentes aos schemas aprovados.
  - Validação de entradas (incluindo rejeição de `"0.00"` em `faceValueBrl`) e tratamento de erros com `errorCode`.
  - Comprovação por testes automatizados com `MockMvc` da rejeição de propriedades JSON desconhecidas em `SimulationRequest` (`HTTP 400`, `application/problem+json`, `errorCode: VALIDATION_ERROR`, mensagem segura).
  - Valores monetários retornados como strings decimais.
  - Simulação sem efeitos persistentes de liquidação.
  - Swagger UI acessível no ambiente de desenvolvimento.
  - Documentação exibida consistente com o contrato oficial.
  - Testes automatizados dos comportamentos HTTP aplicáveis.

### TASK-008 — Identificação e persistência mínima dos recebíveis
- **Objetivo:** disponibilizar títulos e cedentes identificáveis e elegíveis para a liquidação, conforme o fluxo aprovado na TASK-002.
- **Escopo:** somente criação/consulta e estado mínimo indispensáveis; validar vínculo com cedente, integridade e elegibilidade, sem workflow bancário ou autorização avançada. Implementar os endpoints de cadastro, consulta individual e listagem de recebíveis conforme o contrato OpenAPI aprovado, incluindo header Location, schemas, rejeição de propriedades JSON desconhecidas pelo Jackson em `CreateReceivableRequest` e validações.
- **Referências:** SPEC §§1 e 3; DEC-007, DEC-008, DEC-014. ver [Contrato OpenAPI](docs/openapi/srm-credit-engine.yaml).
- **Aceite/testes:** IDs/vínculos consistentes, valores (`faceValueBrl >= 0.01`, rejeitando `"0.00"`) e datas válidos, rejeição de dados inválidos ou duplicados conforme contrato, constraints comprovadas em PostgreSQL; sem criar regras não especificadas. Validar a conformidade contratual dos endpoints, incluindo comprovação via `MockMvc` da rejeição de propriedades JSON desconhecidas (`HTTP 400` + `errorCode: VALIDATION_ERROR`), criação com HTTP 201, consulta com HTTP 200, recursos inexistentes com HTTP 404, conflitos HTTP 409 e respostas de validação.
- **Evidências:** `PENDENTE`.

### TASK-009 — Liquidação ACID e snapshots auditáveis
- **Objetivo:** liquidar lotes válidos integralmente, persistindo os parâmetros financeiros efetivamente utilizados.
- **Escopo:** transação Spring única, lote de `1..N` itens do mesmo cedente/moeda, recálculo no servidor, itens e snapshot imutável, migrations complementares, sem integração bancária externa. Implementar a estrutura de request/response da liquidação conforme o contrato OpenAPI aprovado, garantindo na desserialização do Jackson a rejeição de propriedades desconhecidas em `SettlementRequest` (ex.: `"manualDiscount"`), incluindo schemas do lote, itens e snapshots financeiros, preservando a transação ACID única.
    - Definir desde a migration inicial de liquidação as restrições estruturais de integridade.
    - Garantir UNIQUE(receivable_id) na associação entre recebíveis e itens de liquidação.
    - Garantir unicidade persistente de Idempotency-Key no modelo definido para a operação.
    - Preservar uma única transação financeira.
    - Não disponibilizar a funcionalidade de liquidação como pronta antes da conclusão da TASK-010.
- **Referências:** SPEC §§1 e 3; DEC-003, DEC-006, DEC-007, DEC-015.ver [Contrato OpenAPI](docs/openapi/srm-credit-engine.yaml).
- **Aceite/testes:** lote válido com valores corretos; item inválido no meio causa rollback integral; cotação e taxas gravadas no snapshot; histórico não editável/excluível pela aplicação. Validar a estrutura dos snapshots e valores retornados conforme os schemas OpenAPI aplicáveis e comprovar com `MockMvc` que propriedades JSON desconhecidas em `SettlementRequest` retornam `HTTP 400` (`application/problem+json`, `errorCode: VALIDATION_ERROR`). A conformidade completa dos comportamentos de idempotência e concorrência será validada na TASK-010.**Esta etapa não libera o endpoint como concluído para entrega antes da TASK-010.**
    - Verificar rollback integral do lote.
    - Verificar ausência de registros financeiros parciais.
    - Verificar persistência correta do snapshot.
    - Verificar que novas cotações não alteram liquidações
    históricas.
    - Verificar que a API não permite alterar ou excluir
    liquidações concluídas.
- **Evidências:** `PENDENTE`.

### TASK-010 — Idempotência e controle de concorrência
- **Objetivo:** garantir que retry e operações simultâneas não criem dupla liquidação.
- **Escopo:** `Idempotency-Key`, hash canônico SHA-256, registro definitivo da chave na **mesma transação** que os efeitos financeiros, unicidade de chave e de `settlement_items.receivable_id`, proteção de estado, timeout/transação e tradução de conflitos. Implementar o contrato HTTP de idempotência e concorrência definido no OpenAPI, incluindo Idempotency-Key, replay HTTP 200, conflitos HTTP 409, timeout HTTP 503 e header Retry-After.
- **Referências:** SPEC §3.5; DEC-007, DEC-008, DEC-010. ver [Contrato OpenAPI](docs/openapi/srm-credit-engine.yaml).
- **Aceite/testes:** mesma chave/payload recupera resultado; payload diferente `409`; concorrência com mesma chave e com chaves distintas sobre o mesmo título; primeira transação abortada permite retry seguro; timeout sem duplicação; rollback completo; **não** criar reserva `IN_PROGRESS` em transação separada, Inbox/Outbox ou Redis. Executar testes automatizados de conformidade contratual
para criação inédita HTTP 201, replay HTTP 200, payload divergente HTTP 409, dupla liquidação HTTP 409
e timeout HTTP 503, preservando a mesma Idempotency-Key.
    - Garantir registro atômico da chave de idempotência
    antes da produção dos efeitos financeiros.
    - Não utilizar findByKey seguido de save como
    única proteção contra concorrência.
    - Tratar conflitos de unicidade sem continuar
    utilizando transações PostgreSQL invalidadas.
    - Recuperar o resultado original após o encerramento
    adequado da transação conflitante.
    - Processar recebíveis em ordem determinística
    quando necessário para reduzir deadlocks.
    - Executar testes concorrentes com transações
    independentes e sincronização controlada.
    - Não depender de Thread.sleep para determinar
    o resultado dos testes.
    - Validar comportamento de timeout e retry seguro.
- **Evidências:** `PENDENTE`.

### TASK-011 — Extrato de liquidações paginado
- **Objetivo:** consultar histórico por período, cedente e moeda, com paginação server-side.
- **Escopo:** endpoint REST, filtros opcionais conforme contrato, `Pageable` com limite, projeções JPA e ordenação determinística; SQL nativo apenas se justificado. Implementar os endpoints de listagem e detalhamento de liquidações conforme o contrato OpenAPI, respeitando os filtros, schemas de resposta,
paginação e ordenação aprovados.
- **Referências:** SPEC §§1 e 5; DEC-008, DEC-010. ver [Contrato OpenAPI](docs/openapi/srm-credit-engine.yaml).
- **Aceite/testes:** combinações de filtros, limites e páginas vazias/inválidas; ordenação por instante + ID único; nenhuma paginação em memória; sem N+1 relevante; índices do extrato definidos por consultas reais, não antecipados sem evidência. Validar a conformidade dos parâmetros, filtros, limites de paginação, estrutura das respostas e códigos HTTP com a especificação OpenAPI.
- **Evidências:** `PENDENTE`.

### TASK-012 — Inicializar frontend Angular
- **Objetivo:** estabelecer frontend mínimo, tipado e organizado por funcionalidade.
- **Escopo:** versão Angular/Node compatível aprovada no plano, Standalone Components, TypeScript estrito, serviços HttpClient, Reactive Forms e infraestrutura de testes, sem NgRx. Preparar os modelos TypeScript e a estrutura dos serviços HTTP para consumir os contratos definidos em `docs/openapi/srm-credit-engine.yaml`, evitando definições independentes ou divergentes dos schemas oficiais.
- **Referências:** DEC-001, DEC-002, DEC-009, DEC-010. ver [Contrato OpenAPI](docs/openapi/srm-credit-engine.yaml).
- **Aceite/testes:** projeto compila e executa teste mínimo; organização por features; sem cálculo financeiro ou conversão insegura para `number`. Verificar que os modelos e contratos HTTP preparados no
frontend são compatíveis com os schemas OpenAPI aprovados, sem exigir endpoints ainda não implementados.
- **Evidências:** `PENDENTE`.

### TASK-013 — Painel de simulação Angular
- **Objetivo:** permitir ao operador simular recebíveis em tempo real.
- **Escopo:** formulário com valor de face, vencimento, tipo e moeda, validações, `debounceTime`/`switchMap`, apresentação precisa e estados de carregamento/vazio/erro. Consumir o endpoint de simulação utilizando os schemas de request/response, validações e códigos HTTP definidos no contrato OpenAPI oficial.
- **Referências:** SPEC §§1 e 5; DEC-003, DEC-009, DEC-010. ver [Contrato OpenAPI](docs/openapi/srm-credit-engine.yaml).
- **Aceite/testes:** entradas inválidas não chamam API, mudanças rápidas não exibem respostas antigas, resultado exibido preserva centavos, ausência de câmbio tratada, sem fórmula financeira no navegador. Validar que os dados enviados e recebidos pelo Angular respeitam o OpenAPI, incluindo representação decimal como String, campos obrigatórios e tratamento de erros.
- **Evidências:** `PENDENTE`.

### TASK-014 — Interface Angular de liquidação
- **Objetivo:** executar a operação de liquidação aprovada sem duplicidade induzida pela interface.
- **Escopo:** seleção/confirmação de título ou lote conforme TASK-002, envio com `Idempotency-Key` por intenção, bloqueio durante envio e retry manual seguro da mesma intenção. Consumir o contrato OpenAPI de liquidação, implementando corretamente Idempotency-Key, schemas de request/response e tratamento dos status HTTP 200, 201, 409 e 503.
- **Referências:** SPEC §§1 e 3; DEC-007, DEC-009, DEC-010. ver [Contrato OpenAPI](docs/openapi/srm-credit-engine.yaml).
- **Aceite/testes:** duplo clique bloqueado; mesmo retry usa a chave original e payload equivalente; nova intenção usa nova chave; erro/confirmação claros; **não** usar `switchMap` como cancelamento financeiro nem retry automático não aprovado. Validar por testes que o frontend respeita o contrato
OpenAPI, preserva a mesma Idempotency-Key em retries da mesma intenção e trata corretamente os conflitos e timeouts transitórios.
- **Evidências:** `PENDENTE`.

### TASK-015 — Grid de extrato Angular
- **Objetivo:** oferecer histórico operacional pesquisável e paginado.
- **Escopo:** filtros por período/cedente/moeda, página/tamanho controlados pelo backend, ordenação contratada e estados de lista vazia/carregamento/erro. Consumir os endpoints de extrato e detalhamento de
liquidações conforme o contrato OpenAPI, utilizando os filtros, parâmetros de paginação e schemas oficiais.
- **Referências:** SPEC §§1 e 5; DEC-008, DEC-009, DEC-010. ver [Contrato OpenAPI](docs/openapi/srm-credit-engine.yaml).
- **Aceite/testes:** filtros e páginas enviados corretamente, troca de filtros reinicia paginação quando aplicável, não baixa todo o extrato e não faz agregações financeiras no cliente. Validar que os parâmetros enviados, os modelos TypeScript e a apresentação dos dados financeiros correspondem aos schemas e respostas do OpenAPI aprovado.
- **Evidências:** `PENDENTE`.

### TASK-016 — Docker e Docker Compose
- **Objetivo:** permitir subir e testar a aplicação completa localmente, de forma reproduzível.
- **Escopo:** Dockerfiles e Compose para backend, frontend e PostgreSQL conforme arquitetura de execução aprovada; variáveis de ambiente e readiness/healthcheck simples quando justificadas.
- **Referências:** SPEC §§1 e 5; DEC-001, DEC-012, DEC-017; enunciado Pleno.
- **Aceite/testes:** `docker compose up --build` inicia o sistema; migrações corretas; frontend consome API; fluxo mínimo simulação→liquidação→extrato; nenhuma credencial real, portas adequadas a ambiente local.
- **Evidências:** `PENDENTE`.

### TASK-017 — `REVIEW.md` do Anexo A
- **Objetivo:** entregar revisão crítica **do código TypeScript defeituoso fornecido no enunciado**.
- **Escopo:** listar problemas por severidade e, para cada um, indicar impacto financeiro/operacional, causa e correção; cobrir principalmente SQL injection, taxas em unidade errada, precisão, tratamento de erros, transação, concorrência, idempotência, câmbio e códigos HTTP.
- **Referências:** enunciado §5 e Anexo A; SPEC; DEC-003, DEC-006, DEC-007, DEC-010.
- **Aceite/validação:** achados tecnicamente defensáveis, prioridade por risco, referência ao trecho e proposta de correção viável; revisão humana, sem fatos inventados sobre produção.
- **Evidências:** `PENDENTE`.

### TASK-018 — GitHub Actions (CI enxuta)
- **Objetivo:** automatizar compilação e suíte obrigatória sem prejudicar os requisitos P0. Validar estruturalmente a especificação OpenAPI e verificar a conformidade contratual da API, conforme as ferramentas adotadas e os testes disponíveis no projeto.
- **Escopo:** um workflow em `.github/workflows/ci.yml` com jobs backend/frontend, Java 21, Maven `verify` incluindo integração Testcontainers, Angular testes/build, permissões mínimas e lint se já configurado.
- **Referências:** DEC-010, DEC-011; enunciado Sênior (diferencial para Pleno).
- **Aceite/testes:** execução em PR/push para `main`, workflow falha quando teste obrigatório falha, sem `skipTests` para mascarar falhas, sem deploy/segredos reais. A esteira falha quando a validação OpenAPI ou os testes obrigatórios de conformidade HTTP falharem. **Não confundir CI verde com cobertura integral das regras.**
- **Evidências:** `PENDENTE`.

### TASK-019 — Documentação final e práticas de Git
- **Objetivo:** entregar documentação verificável e histórico de desenvolvimento justificável. Documentar no README.md a abordagem Contract First, a localização da especificação OpenAPI oficial, o acesso ao Swagger UI e o procedimento de atualização e validação dos contratos.
- **Escopo:** `README.md` (setup, execução, arquitetura, regras, API, testes e escolhas), `AI_USAGE.md` (prompts/specs estratégicos, **erro real da IA** e como foi detectado, decisões não delegadas), revisão de `SPEC.md`/`DECISIONS.md` apenas se houver mudança aprovada; commits convencionais e PR(s) descritivos mediante autorização.
- **Referências:** enunciado §§3, 7, 8, 10 e 12; DEC-001 a DEC-017; AGENTS.
- **Aceite/validação:** instruções reproduzíveis e conferidas; `AI_USAGE.md` com evidências autênticas (se o caso de erro ainda não existir, registrar pendência — **não fabricar**); repositório privado e convite aos avaliadores conforme orientações recebidas, mediante ação/autorização humana.
- **Evidências:** `PENDENTE`.

### TASK-020 — Homologação e preparação da defesa
- **Objetivo:** verificar a entrega integral e garantir capacidade de explicar/modificar o próprio código. Verificar que os endpoints implementados e os consumidores Angular estão consistentes com o OpenAPI aprovado, incluindo payloads financeiros, respostas HTTP, validações, idempotência e paginação.
- **Escopo:** auditoria da matriz de rastreabilidade, suíte completa, smoke test via Compose, revisão de segurança/integridade, conferência dos entregáveis e ensaio de walkthrough + mudança local guiada.
- **Referências:** SPEC §§2 e 5; DEC-010; enunciado §§9–11.
- **Aceite/validação:** C1/C2/C3 exatos; todas as regras conhecidas e erros aplicáveis testados; fluxo completo, idempotência e rollback comprovados; todos os entregáveis existentes; demonstração de arquitetura em ~10 min e exercício de alteração em 30–40 min **sem comprometer o repositório principal**; riscos residuais documentados.
- **Evidências:** `PENDENTE`.

## 6. Matriz inicial de rastreabilidade (SPEC → TASK → TESTE)

> IDs `R-xx` são **marcadores de planejamento** extraídos do `SPEC.md` e do enunciado, não novas regras de negócio. Substituir `A DEFINIR` por nomes reais dos testes **após implementá-los**. Revisar se todas as regras e erros conhecidos estão representados antes da entrega.

| ID | Requisito / invariante | Tarefas responsáveis | Testes / evidências |
|---|---|---|---|
| R-01 | Duplicata: spread 1,5%, VP e deságio exatos (C1) | 003 | `PricingEngineTest.shouldCalculateGoldenCaseC1` |
| R-02 | Cheque: spread 2,5%, VP e deságio exatos (C2) | 003 | `PricingEngineTest.shouldCalculateGoldenCaseC2` |
| R-03 | Câmbio BRL→USD somente ao final, C3 e arredondamento | 004, 006, 007 | `PricingEngineCrossCurrencyTest`, `CurrencyConverterTest` |
| R-04 | `BigDecimal`, DECIMAL128, HALF_EVEN e valores JSON como strings | 003, 004, 005, 007, 013 | `A DEFINIR` |
| R-05 | Prazo inteiro e dueDate→ceil(dias/30); vencimento futuro | 004, 007 | `CommercialTermCalculatorTest` |
| R-06 | Taxa base configurável, snapshot financeiro | 003, 009 | `A DEFINIR` |
| R-07 | Câmbio vigente `effectiveAt`, sem fallback, histórico append-only | 006, 009 | `A DEFINIR` |
| R-08 | Simulação sem efeitos persistentes de liquidação | 007, 013 | `A DEFINIR` |
| R-09 | Lote de 1..N, mesmo cedente/moeda, ACID all-or-nothing | 008, 009 | `A DEFINIR` |
| R-10 | Idempotência: chave+payload igual ou divergente | 010, 014 | `A DEFINIR` |
| R-11 | Concorrência: mesma chave e chaves distintas; título uma vez | 010 | `A DEFINIR` |
| R-12 | Auditoria imutável de todos os parâmetros e timestamps | 009, 010 | `A DEFINIR` |
| R-13 | API REST Contract First: OpenAPI oficial, validação, erros semânticos/seguros e conformidade dos endpoints | 002, 006, 007, 008, 009, 010, 011 | `A DEFINIR` |
| R-14 | Extrato: período, cedente, moeda e paginação server-side | 011, 015 | `A DEFINIR` |
| R-15 | Angular: campos obrigatórios, simulação em tempo real, sem cálculo | 013 | `A DEFINIR` |
| R-16 | Execução local com Docker/Compose e PostgreSQL | 016 | `A DEFINIR` |
| R-17 | Code review do Anexo A (`REVIEW.md`) | 017 | `A DEFINIR` |
| R-18 | Uso de IA documentado com erro real (`AI_USAGE.md`) | 019 | `A DEFINIR` |
| R-19 |  README com justificativa da stack, Git convencional, PRs e entrega privada | 001, 019, 020 | `A DEFINIR` |
| R-20 | Testes de regras/erros aplicáveis e validação final | 003–020 | `A DEFINIR` |
| R-21 | CI automatizada (DEC-011, diferencial) | 018 | `A DEFINIR` |
| R-22 | Diagrama ER e modelo relacional normalizado | 005, 009 | `A DEFINIR` |
| R-23 | Extensibilidade da Strategy com nova fórmula | 003 | `PricingEngineTest.shouldDemonstrateExtensibilityWithAlternativeFormulaStrategy` |
| R-24 | Data da operação controlada pelo backend | 004, 007 | `A DEFINIR` |
| R-25 | Novas cotações não alteram liquidações concluídas | 006, 009 | `A DEFINIR` |
| R-26 | Especificação OpenAPI oficial criada e validada antes da implementação das APIs - Validação estrutural OpenAPI | 002 | `docs/openapi/srm-credit-engine.yaml` (`@redocly/cli lint` — válido, 0 erros/avisos) |
| R-27 | Implementação backend e consumidores Angular aderentes ao contrato OpenAPI aprovado - Testes de conformidade | 006–015, 020 | `A DEFINIR` |
| R-28 | Swagger UI disponibiliza o contrato oficial sem divergências - Verificação de integração | 007, 020 | `A DEFINIR` |

## 7. Pendências de definição antes da implementação

1. **Ciclo de vida dos recebíveis (TASK-002 — RESOLVIDO em [`docs/openapi/srm-credit-engine.yaml`](docs/openapi/srm-credit-engine.yaml) e [`docs/contracts.md`](docs/contracts.md)):** cedentes por seed + `GET /api/v1/assignors`; recebíveis por seed + `POST /api/v1/receivables` (`OPEN`), `GET /api/v1/receivables/{id}` e `GET /api/v1/receivables?assignorId=...&status=OPEN`, com `UNIQUE(assignor_id, document_number)` normalizado.
2. **Contrato HTTP (TASK-002 — RESOLVIDO em [`docs/openapi/srm-credit-engine.yaml`](docs/openapi/srm-credit-engine.yaml) e [`docs/contracts.md`](docs/contracts.md)):** OpenAPI `3.0.3` Contract First (10 operações em 8 paths), `ProblemDetail` RFC 9457, strings decimais, limites numéricos contra overflow em `NUMERIC(19,2)`, precedência estrita de `Idempotency-Key` (`201` nova / `200` replay, chave omitida em consultas), timeout concorrente `503` com `Retry-After` orientativo e paginação do extrato por lote (`size <= 100`).
3. **Detalhes de infraestrutura:** versões compatíveis de Spring Boot, Angular e Node, convenções de nomes, localização física das migrations, forma de servir o frontend e variáveis de ambiente serão propostas dentro das tarefas pertinentes — não decididas automaticamente por este backlog.
4. **Evolução documental:** manter o DECISIONS.md enxuto como registro oficial e acrescentar progressivamente as decisões necessárias às tarefas, mediante aprovação humana. As referências a decisões futuras no TASKS.md devem ser preservadas.

## 8. Registro de execução (preencher ao longo do projeto)

| Data | Tarefa | Mudança autorizada | Testes/comandos | Resultado | Erro de IA detectado / validação | Aprovação |
|---|---|---|---|---|---|---|
| 2026-11-04 | TASK-001 | `pom.xml`, `application.yaml`, `db/migration/.gitkeep`, `TestcontainersConfiguration`, `BackendcreditengineApplicationTests` | `.\mvnw.cmd help:effective-pom`, `.\mvnw.cmd -B clean test` (JDK `21.0.11` + Testcontainers `postgres:16-alpine`) | `BUILD SUCCESS` (`Tests run: 1, Failures: 0, Errors: 0`) | Prevenido via `effective-pom`: Spring Boot `4.1.2` usa Testcontainers `2.0.5` (`testcontainers-postgresql` e pacote `org.testcontainers.postgresql`) em vez das coordenadas 1.x | APROVADO (2026-11-04) |
| 2026-11-05 | TASK-002 | `docs/openapi/srm-credit-engine.yaml`, `docs/contracts.md`, `docs/task-plans/TASK-002.md`, `DECISIONS.md` (`DEC-007`), `TASKS.md` | `@redocly/cli lint` (Rev. `1.4`), verificação local `node` dos regexes/invariantes OpenAPI (Rev. `1.5`), `git status --short`, `git diff --check` | `VALIDATION` (`allRegexTestsPassed: true`, `4` requests com `additionalProperties: false`, `0` chaves `code:` remanescentes, `8` paths / `10` ops / `25` schemas) | Humano corrigiu `7` para `8` paths no plano (1.4) e identificou na revisão (1.5) ausência de `additionalProperties: false` nos 4 requests, uso de `code` em vez de `errorCode` e regexes decimais de entrada aceitando zero (`"0.00"`/`"0.000000"`) | AGUARDANDO VALIDAÇÃO |
| 2026-11-05 | TASK-003 | `docs/task-plans/TASK-003.md`, `TASKS.md`, 9 classes de domínio Java puro em `pricing.domain`, 2 classes de teste JUnit 5 | `.\mvnw.cmd test "-Dtest=PricingEngineTest,CompoundInterestCalculatorTest"`, `.\mvnw.cmd test` | `DONE` (`Tests run: 21, Failures: 0, Errors: 0` específicos; `Tests run: 22, Failures: 0, Errors: 0` suíte completa) | Identificado no planejamento que switch violaria OCP (corrigido com injeção de Strategies via construtor) e que taxas mensais devem exigir escala <= 6 sem arredondamento silencioso | APROVADO (2026-11-05) |

> **Próxima ação:** revisar/aprovar este backlog como **plano inicial**. Depois, solicitar ao Antigravity **somente o plano detalhado da TASK-001**; execução e modificações continuam sujeitas à autorização G-01/G-03.
