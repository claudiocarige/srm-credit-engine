# TASK-002 — Plano Técnico de Implementação

## 1. Identificação
- **Tarefa:** `TASK-002` — Definir contratos de dados e API REST com OpenAPI (Contract First).
- **Objetivo:** estabelecer e documentar, antes das migrations e da implementação dos endpoints, o modelo mínimo de dados, os fluxos de negócio e a especificação formal OpenAPI da API REST. O contrato OpenAPI será a fonte oficial dos endpoints, parâmetros, schemas, validações e respostas HTTP. As regras complementares permanecerão documentadas em `docs/contracts.md`.
- **Status do planejamento:** `APROVADO PELO RESPONSÁVEL (REVISÃO 1.6 — NOTA DE DATA DE REFERÊNCIA E CLOCK NOS EXEMPLOS)`.
- **Referências:** `SPEC.md` (§§1, 2, 3 e 5); `DECISIONS.md` (`DEC-001` a `DEC-004`, `DEC-007`, `DEC-010`); `AGENTS.md` (`G-01` a `G-04`); `TASKS.md` (`TASK-002`, §§7.1–7.2).
- **Dependências:** `TASK-001` (`DONE`).
- **Ferramenta e modelo de IA elaborador:** Google Antigravity — Gemini 4 Argon.

## 2. Diagnóstico do Estado Atual
- **Estrutura existente:** `TASK-001` concluída com Spring Boot `4.1.2`, Java `21`, PostgreSQL `16` via Testcontainers `2.0.5`, Flyway ativo (`db/migration/.gitkeep`) e `ddl-auto=validate`.
- **Lacunas contratuais antes desta tarefa (`TASKS.md` §§7.1–7.2):**
  1. Indefinição sobre como cedentes e recebíveis nascem e são consultados pelo operador antes da liquidação em lote, sem inflar escopo com CRUD completo.
  2. Necessidade de formalizar como simulação (4 campos obrigatórios, sem efeito persistente nem trava cambial) se conecta operacionalmente ao cadastro de recebível `OPEN` e à liquidação em lote no Angular.
  3. Necessidade de fixar a canonização `SHA-256` do payload de liquidação e a precedência estrita da `Idempotency-Key` sobre a validação de estado `SETTLED` dos títulos.
  4. Indefinição da semântica HTTP para timeout de disputa concorrente (`503` + `Retry-After` orientativo vs `409` definitivo), limites máximos de paginação do extrato e tetos numéricos de entrada para impedir overflow dos totais de lote em `NUMERIC(19,2)`.
  5. Os contratos REST foram documentados em `docs/contracts.md`, mas ainda não existe uma especificação OpenAPI formal, validável por ferramentas e independente da implementação dos controllers Spring Boot.

## 3. Abordagem Técnica Proposta
- **Solução recomendada:** adotar Contract First, estabelecendo `docs/openapi/srm-credit-engine.yaml` como fonte oficial dos contratos HTTP da API REST. O arquivo `docs/contracts.md` permanecerá como documentação complementar das regras operacionais, financeiras e transacionais que exigirem explicações além dos schemas OpenAPI.
  1. **Cedentes e recebíveis enxutos:** cedentes providos via seed + `GET /api/v1/assignors` (somente leitura); recebíveis providos via seed + cadastro explícito `POST /api/v1/receivables` (`201 Created` com `Location`, nascendo sempre `OPEN`), consulta individual `GET /api/v1/receivables/{id}` (`200`/`404`) e consulta por cedente/estado `GET /api/v1/receivables?assignorId={uuid}&status=OPEN` (sem `PUT`/`PATCH`/`DELETE`), com unicidade `UNIQUE (assignor_id, document_number)` normalizada (`trim` + `UPPERCASE`).
  2. **Simulação desacoplada:** `POST /api/v1/simulations` recebe exatamente os 4 campos obrigatórios (`type`, `faceValueBrl`, `dueDate`, `paymentCurrency`), sem persistência e sem *quote lock*.
  3. **Idempotência com precedência estrita e transação ACID única:** `POST /api/v1/settlements` exige `Idempotency-Key`, canoniza o lote ordenando `receivableIds` sem duplicatas (`SHA-256`) e avalia a `Idempotency-Key` **antes** da elegibilidade/estado dos títulos (`201 Created` na criação inédita; `200 OK` com snapshot original em replay válido; `409 IDEMPOTENCY_PAYLOAD_MISMATCH` em divergência; `409 RECEIVABLE_ALREADY_SETTLED` exclusivo para outra chave/intenção). `Idempotency-Key` não é exposta nas consultas gerais de liquidações.
  4. **Timeout concorrente transitório:** expiração de `lock-timeout-ms` retorna `503 Service Unavailable` (`CONCURRENT_SETTLEMENT_TIMEOUT`) com header `Retry-After` configurável (`srm.settlement.retry-after-seconds`, padrão `2`), documentado como orientação (*hint*) de nova tentativa — e não garantia de conclusão da transação concorrente — preservando a mesma `Idempotency-Key` no retry.
  5. **Limites numéricos e totalização *bottom-up*:** mantêm-se `NUMERIC(19,2)`, `NUMERIC(19,6)` e `NUMERIC(10,6)` do `SPEC.md`, limitando na entrada `faceValueBrl` a `0.01..999999999.99`, `rateBrl` a `0.000001..9999999.999999` e o lote a `1..100` itens, eliminando matematicamente qualquer overflow em `BRL` ou `USD`. Totais do lote equivalem à soma exata dos snapshots arredondados dos itens.
  6. **Extrato paginado por lote + detalhe sob demanda:** `GET /api/v1/settlements` pagina lotes (`size` padrão `20`, máximo `100`, ordenado por `settled_at DESC, id DESC`, sem carregar `items[]` na listagem) e `GET /api/v1/settlements/{settlementId}` retorna o lote com os 16 campos obrigatórios de cada item.
  7. **Contrato OpenAPI oficial e validação estrita de entrada:** definir previamente a especificação `docs/openapi/srm-credit-engine.yaml`, utilizando OpenAPI 3.x. O documento deverá conter todos os endpoints REST aprovados, operações HTTP, parâmetros, headers, schemas reutilizáveis, exemplos, validações, paginação, códigos de resposta e erros padronizados (`ProblemDetail` com `errorCode`). Os quatro schemas de request (`CreateReceivableRequest`, `CreateExchangeRateRequest`, `SimulationRequest` e `SettlementRequest`) declaram `additionalProperties: false` para rejeitar campos desconhecidos com `HTTP 400 Bad Request` (`application/problem+json`, `errorCode: VALIDATION_ERROR`, sem expor detalhes internos), e os `pattern` decimais de entrada bloqueiam valores zero (`"0.00"` e `"0.000000"`). Valores monetários deverão utilizar `type: string`, preservando a precisão decimal. O contrato deverá ser validado antes da implementação dos controllers, permanecendo como fonte de verdade para backend e frontend.
- **Justificativa:** fecha todas as ambiguidades antes do banco e das APIs mantendo simplicidade (KISS/YAGNI), rastreabilidade integral (`SPEC.md` §1.4), ausência de `N+1`, proteção contra overflow e semântica HTTP precisa.
- **Alternativas consideradas:**
  - *Alternativa A — Recebíveis exclusivamente por seed (sem `POST /api/v1/receivables`):* substituída na Revisão `1.2`, pois impedia o operador de simular um novo título, cadastrá-lo explicitamente como `OPEN` e liquidá-lo na sequência.
  - *Alternativa B — Liquidar recebíveis ad-hoc diretamente no payload de `POST /api/v1/settlements` sem pré-cadastro:* rejeitada por enfraquecer a prevenção estrutural contra dupla liquidação do mesmo título por requisições distintas (`UNIQUE(receivable_id)`).
  - *Alternativa C — Responder timeout de lock concorrente com `409 Conflict` + `Retry-After`:* substituída na Revisão `1.1` por `503 Service Unavailable` + `Retry-After`, separando conflitos definitivos de negócio (`409`) de indisponibilidade transitória por contenção (`503`).
  - *Alternativa D — Paginar o extrato por item liquidado em vez de por lote:* rejeitada porque a unidade transacional e cambial da operação é o lote; o detalhe dos itens fica acessível em `GET /api/v1/settlements/{settlementId}` sem `N+1` na listagem.

## 4. Escopo e Restrições
- **O que será entregue nesta tarefa:**
  - Criação normativa de `docs/contracts.md` e deste plano `docs/task-plans/TASK-002.md`.
  - Refinamento pontual da `DEC-007` em `DECISIONS.md` (preservando a evolução incremental das demais decisões ainda `PROPOSED`).
  - Atualização de rastreabilidade da `TASK-002` e das pendências §§7.1–7.2 em `TASKS.md`.
  - Criação e validação da especificação formal OpenAPI em `docs/openapi/srm-credit-engine.yaml`.
  - Revisão de `docs/contracts.md` para manter somente regras complementares e referências à especificação OpenAPI, evitando duplicação de contratos.
  - Definição de OpenAPI como fonte oficial para implementação futura dos endpoints e integração com o frontend Angular.
- **O que não será implementado:**
  - Nenhum código Java, script SQL Flyway, componente Angular, dependência Maven/npm ou alteração em `SPEC.md`.
  - Não será configurado Swagger UI ou Springdoc nesta tarefa.
  - Não serão criados controllers, DTOs Java ou implementações de endpoints a partir do contrato.
  - A integração e disponibilização da documentação OpenAPI no Spring Boot ocorrerão nas tarefas de implementação das APIs.

## 5. Arquivos Previstos
| Arquivo | Ação | Justificativa |
|---|---|---|
| `docs/openapi/srm-credit-engine.yaml` | Criar na Revisão 1.4 | Estabelecer o contrato OpenAPI oficial, validável e independente da implementação |
| `docs/contracts.md` | Revisar (criado na Revisão 1.3) | Manter regras complementares sem duplicar schemas e endpoints do OpenAPI |
| `docs/task-plans/TASK-002.md` | Atualizar | Registrar a Revisão 1.4, aprovação, execução e evidências |
| `TASKS.md` | Atualizar | Incorporar OpenAPI aos critérios de aceite da TASK-002 e alinhar a TASK-007 |
| `DECISIONS.md` | Sem nova alteração prevista | Preservar o refinamento da DEC-007 realizado na Revisão 1.3 |

## 6. Subtarefas de Implementação
| Ordem | Subtarefa | Resultado esperado |
|---|---|---|
| 1 | Consolidar especificação de contratos em `docs/contracts.md` | Documento único, enxuto e sem ambiguidades para guiar `TASK-005` a `TASK-015` |
| 2 | Registrar planejamento e trilha de revisões em `docs/task-plans/TASK-002.md` | Histórico completo `1.0 → 1.3` rastreável conforme `REGRA 3` e `REGRA 5` |
| 3 | Atualizar pontualmente `DEC-007` em `DECISIONS.md` | Decisão de concorrência/idempotência alinhada ao contrato, sem antecipar `DEC-008`/`DEC-009` |
| 4 | Atualizar `TASKS.md` (`TASK-002` e §§7.1–7.2) e verificar consistência e formatação Git | Documentação íntegra e verificada com `git status --short` e `git diff --check` |
| 5 | Criar a especificação `docs/openapi/srm-credit-engine.yaml` | Contrato formal OpenAPI com todos os endpoints, schemas, parâmetros e respostas aprovados |
| 6 | Validar sintaxe, estrutura OpenAPI, referências e exemplos | Especificação válida, sem referências quebradas e compatível com `docs/contracts.md` |
| 7 | Atualizar a rastreabilidade documental e registrar as evidências da Revisão 1.4 | Contrato formal aprovado antes da implementação dos endpoints |

## 7. Critérios de Aceite
- **CA-01:** Origem, cadastro (`POST /api/v1/receivables`), consulta individual (`GET /api/v1/receivables/{id}`) e listagem (`GET /api/v1/receivables`) dos recebíveis definidos, com estados `OPEN`/`SETTLED` e unicidade `UNIQUE(assignor_id, document_number)`.
- **CA-02:** Identificação de cedente (`GET /api/v1/assignors`, seed) e vínculo com os recebíveis especificados sem CRUD desnecessário.
- **CA-03:** Payloads e moedas explícitas de simulação, câmbio manual e liquidação em lote especificados em `String` decimal exata.
- **CA-04:** Precedência estrita da `Idempotency-Key` sobre `RECEIVABLE_ALREADY_SETTLED`, canonização `SHA-256` insensível à ordem e omissão da chave nas consultas gerais definidas.
- **CA-05:** Comportamento HTTP para timeout concorrente definido como `503 Service Unavailable` com `Retry-After` configurável e expressamente orientativo (*hint*).
- **CA-06:** Limites financeiros de entrada (`faceValueBrl`, `rateBrl`, `1..100` itens) definidos matematicamente contra overflow em `NUMERIC(19,2)`.
- **CA-07:** Tamanho máximo de paginação do extrato (`size <= 100`, padrão `20`) por lote e endpoint de detalhamento de itens definidos.
- **CA-08:** Aprovação humana obtida antes de iniciar migrations (`TASK-005`) ou código de domínio/API.
- **CA-09:** Existe uma especificação OpenAPI 3.x válida em `docs/openapi/srm-credit-engine.yaml`, contendo todos os endpoints essenciais definidos na TASK-002.
- **CA-10:** A especificação documenta métodos HTTP, parâmetros de rota e consulta, headers obrigatórios, schemas de request/response, enums, formatos, limites, exemplos, paginação, status HTTP e respostas `ProblemDetail`.
- **CA-11:** Todos os valores monetários e taxas trafegam como strings decimais, com schemas que não permitam perda de precisão por representação numérica JSON.
- **CA-12:** A especificação OpenAPI foi validada estruturalmente e revisada quanto à consistência com `SPEC.md`, `DECISIONS.md` e `docs/contracts.md`, antes da implementação dos endpoints.
- **CA-13:** Os quatro schemas de request declaram `additionalProperties: false`, o envelope `ProblemDetail` e seus exemplos utilizam `errorCode`, os `pattern` decimais de entrada proíbem valores zero (`"0.00"` / `"0.000000"`) e o planejamento das `TASK-006` a `TASK-009` exige comprovação com `MockMvc` da rejeição de propriedades JSON desconhecidas pelo Jackson no Spring Boot.

## 8. Estratégia de Testes e Verificação
*(Por se tratar de tarefa exclusivamente documental/contratual, a validação nesta etapa é estática e cruzada; os testes automatizados correspondentes serão executados nas `TASK-005` a `TASK-015`.)*

| Critério | Verificação | Resultado esperado |
|---|---|---|
| CA-01 a CA-07 | Inspeção cruzada entre `docs/contracts.md`, `SPEC.md` e `DECISIONS.md` (`DEC-001` a `DEC-004`, `DEC-007`) | Zero contradição de fórmulas, escalas `NUMERIC`, códigos HTTP ou regras de lote/idempotência |
| Exemplos numéricos | Conferência aritmética dos exemplos JSON de simulação e lote contra C1, C2 e C3 | Valores em `BRL` e `USD` exatos ao centavo (`HALF_EVEN`, 2 casas) e somas *bottom-up* íntegras |
| CA-09 a CA-11 | Validação da especificação com ferramenta compatível com OpenAPI 3.x | YAML válido, schemas corretos, operações documentadas e referências `$ref` resolvidas |
| CA-12 | Conferência dos endpoints, payloads, códigos HTTP e exemplos financeiros com `SPEC.md`, `DECISIONS.md` e `docs/contracts.md` | Nenhuma divergência contratual; C1, C2 e C3 preservados |
| Higiene do repositório | Execução de `git status --short` e `git diff --check` | Apenas os arquivos expressamente autorizados para cada revisão foram alterados, sem erros de whitespace. |

## 9. Riscos e Mitigações
| Risco | Impacto | Mitigação |
|---|---|---|
| Replay idempotente avaliado após checagem de `status == SETTLED` retornar `409` indevido | Quebra de idempotência em retries de rede | Ordem contratual obrigatória registrada em `docs/contracts.md` (§3.5) e `DEC-007`: chave verificada antes de estado/elegibilidade |
| Overflow de `NUMERIC(19,2)` em lotes grandes ou cotações muito baixas | Falha SQL não tratada na gravação do lote | Tetos declarativos `faceValueBrl <= 999999999.99`, `rateBrl >= 0.000001` e `receivableIds <= 100` garantem soma `< 10^17` |
| Interpretação de `Retry-After` pelo cliente como garantia de término do lock | Falha de UX caso ocorra segundo `503` | Contrato explicita `Retry-After` como *hint* e exige preservação da mesma `Idempotency-Key` entre tentativas |
| Divergência entre `docs/contracts.md` e a especificação OpenAPI | Backend e frontend implementarem contratos diferentes | OpenAPI será a fonte oficial dos contratos HTTP; `docs/contracts.md` manterá somente regras complementares e referências ao YAML |
| Implementação dos controllers divergir do OpenAPI aprovado | Quebra de integração e perda de confiabilidade da documentação | Exigir verificação de conformidade entre implementação e contrato durante as TASK-006 a TASK-011 |

## 10. Revisão e Aprovação Humana
- **Plano inicial apresentado:** 2026-11-04
- **Ajustes solicitados e incorporados:** Revisões `1.1`, `1.2`, `1.3`, `1.4`, `1.5` e `1.6` (detalhadas na Seção 11).
- **Situação:** APROVADO PARA EXECUÇÃO DOCUMENTAL E OPENAPI (`sim`, `autorizo com uma correção: ... São 10 operações em 8 paths sob /api/v1`, `autorizado` na Revisão `1.5` e `AUTORIZADO` na Revisão `1.6` em 2026-11-05).

## 11. Histórico de Revisões
| Revisão | Alteração | Justificativa | Aprovação |
|---|---|---|---|
| 1.0 | Proposta inicial de contratos e modelo mínimo da `TASK-002` | Definir entidades, estados `OPEN`/`SETTLED`, lote ACID e erros antes das migrations | Revisado pelo responsável |
| 1.1 | Fluxo Angular sem *quote lock*, canonização `SHA-256` ordenada sem IDs repetidos, troca de `409` por `503` em timeout concorrente, campos do snapshot, extrato por lote e preservação incremental do `DECISIONS.md` | Separar falhas transitórias (`503`) de conflitos definitivos (`409`) e evitar antecipar `DEC-008`/`DEC-009` | Revisado pelo responsável |
| 1.2 | Inclusão de `POST /api/v1/receivables` + unicidade `UNIQUE(assignor_id, document_number)`, precedência explícita de idempotência (`201` nova / `200` replay), configurabilidade de `Retry-After`, moeda explícita de cada atributo, totalização *bottom-up* e endpoints de câmbio/extrato | Permitir simular → cadastrar `OPEN` → liquidar sem CRUD completo e eliminar ambiguidade de replay | Revisado pelo responsável |
| 1.3 | Inclusão de `GET /api/v1/receivables/{id}` (`200`/`404`), limites financeiros contra overflow em lotes de até 100 itens, omissão de `Idempotency-Key` nas consultas de liquidações e explicitação de `Retry-After` como orientação (*hint*) | Garantir navegabilidade do header `Location`, segurança numérica em `NUMERIC(19,2)` e encapsulamento da chave de idempotência | Aprovado (`sim`, 2026-11-05) |
| 1.4 | Inclusão de Contract First, criação de `docs/openapi/srm-credit-engine.yaml` (OpenAPI `3.0.3`, 10 operações em 8 paths, `ProblemDetail` RFC 9457), validação estrutural via `@redocly/cli` e refatoração enxuta de `docs/contracts.md` | Antecipar a definição formal validável do contrato da API, orientar backend/frontend e eliminar duplicação documental | Aprovado (2026-11-05) |
| 1.5 | Inclusão de `additionalProperties: false` nos 4 schemas de request, renomeação de `code` para `errorCode` em `ProblemDetail` e nos 12 exemplos de erro, correção dos `pattern` de `FaceValueBrlInput` e `ExchangeRateBrlInput` contra zero e registro de conformidade Jackson/`MockMvc` nas `TASK-006` a `TASK-009` | Rejeitar atributos JSON desconhecidos (ex.: `manualDiscount`), padronizar `errorCode` e impedir valores zero na validação contratual | Aprovado (`autorizado`, 2026-11-05) |
| 1.6 | Observação em `srm-credit-engine.yaml` e `contracts.md` esclarecendo que os exemplos dos *Golden Cases* usam data de operação fixa (`2026-11-04`) para demonstração determinística, enquanto a execução real utiliza o `Clock` do backend (`Clock` controlado nos testes a partir da `TASK-004`) | Evitar interpretação equivocada caso o mesmo payload de exemplo seja executado em outra data | Aprovado (`AUTORIZADO`, 2026-11-05) |

## 12. Resultado da Execução
- **Status:** `APPROVED` (entregáveis documentais das Revisões `1.3` a `1.6` criados e validados estruturalmente; aguardando aprovação final do responsável para marcar `DONE`).
- **Arquivos realmente alterados:**
  - `docs/openapi/srm-credit-engine.yaml` (criado na Revisão `1.4`, endurecido na Revisão `1.5` e documentado com nota de `Clock` na Revisão `1.6` — OpenAPI `3.0.3`, 10 operações em 8 paths sob `/api/v1`, `additionalProperties: false` nos 4 requests, strings decimais estritamente positivas na entrada, `ProblemDetail` `application/problem+json` com `errorCode`, `Idempotency-Key`, `Retry-After` e exemplos C1, C2 e C3).
  - `docs/contracts.md` (criado na Revisão `1.3`, refatorado na Revisão `1.4` e atualizado até a Revisão `1.6` com `errorCode`, política de rejeição de propriedades JSON desconhecidas e nota de `Clock` nos exemplos).
  - `docs/task-plans/TASK-002.md` (criado e atualizado até a Revisão `1.6`).
  - `DECISIONS.md` (modificado na Revisão `1.3` — refinamento exclusivo da `DEC-007`, mantendo as demais decisões intactas).
  - `TASKS.md` (modificado — evidências da `TASK-002`, exigência de conformidade Jackson/`MockMvc` nas `TASK-006` a `TASK-009`, correção de links relativos para `docs/openapi/srm-credit-engine.yaml`, itens 7.1–7.2 e registro de execução).
- **Comandos executados:**
  - Revisão `1.4`: `npx --yes @redocly/cli@latest lint docs/openapi/srm-credit-engine.yaml`, `git status --short`, `git diff --check`
  - Revisão `1.5`: verificação automatizada local (`node -e`) das expressões regulares (`FaceValueBrlInput` e `ExchangeRateBrlInput`) e invariantes do YAML (`additionalProperties: false`, `errorCode`, contagem de paths/operações/schemas), `git status --short` e `git diff --check` *(execução externa `npx @redocly/cli` recusada na Revisão `1.5`)*
- **Verificações e resultados:**
  - **CA-01 a CA-07 (Consistência com `SPEC.md` e `DECISIONS.md`):** APROVADA (nenhuma fórmula, arredondamento ou decisão arquitetural violada; evolução incremental do `DECISIONS.md` preservada).
  - **CA-09 a CA-13 (Validação estrutural e segurança OpenAPI `3.0.3`):** APROVADA (`@redocly/cli lint` aprovado com `0` erros e `0` avisos na Revisão `1.4`; na Revisão `1.5`, verificação local confirmou os 4 schemas de request com `additionalProperties: false`, `0` ocorrências remanescentes de `code:`, preservação integral de `8` paths, `10` operações e `25` schemas, e aprovação de 100% dos casos de teste válidos/inválidos dos novos `pattern` decimais).
  - **Exatidão aritmética dos exemplos (C1, C2 e C3):** APROVADA (`C1 = BRL 92859.94` / deságio `7140.06`; `C2 = BRL 23337.77` / deságio `1662.23`; `C3 = USD 17094.67` / deságio `BRL 7140.06`; lote combinado `USD 21390.94`).
  - **`git diff --check`:** APROVADO (sem erros de formatação ou espaços finais).
- **Erros reais da IA detectados e corrigidos (rastreabilidade para `AI_USAGE.md`):**
  1. Na apresentação textual do plano da Revisão `1.4`, a IA contabilizou "10 operações em 7 paths sob `/api/v1`" em vez de **10 operações em 8 paths** (`/assignors`, `/receivables`, `/receivables/{id}`, `/exchange-rates`, `/exchange-rates/current`, `/simulations`, `/settlements` e `/settlements/{settlementId}`), erro prontamente identificado pelo responsável na revisão humana.
  2. Na primeira validação com `@redocly/cli lint` (Revisão `1.4`), o linter detectou erro de indentação na linha 1095, uso de `$ref` sob `example` (corrigido para `examples` nomeado) e `nullable: true` combinado com `allOf` sem `type: string` explícito em OpenAPI `3.0.3` (corrigido com o schema reutilizável `NullableExchangeRateString`).
  3. Na revisão humana do YAML da Revisão `1.4`, o responsável identificou que os regexes decimais de entrada gerados pela IA (`^(0|[1-9][0-9]{0,8})(\.[0-9]{1,2})?$` e `^(0|[1-9][0-9]{0,6})(\.[0-9]{1,6})?$`) aceitavam zero (`"0.00"` e `"0.000000"`) proibido pela regra de negócio, além da omissão de `additionalProperties: false` nos 4 schemas de request; corrigidos e validados na Revisão `1.5`.
- **Desvios do planejamento:** nenhum após os ajustes acima.
- **Riscos remanescentes:** nenhum bloqueio para `TASK-003` ou `TASK-005`; os testes de conformidade HTTP (incluindo rejeição de propriedades desconhecidas via Jackson + `MockMvc`) serão executados nas `TASK-006` a `TASK-015`.
- **Aprovação final:** pendente de validação humana para conclusão (`DONE`).