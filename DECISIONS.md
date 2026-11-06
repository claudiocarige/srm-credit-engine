# SRM Credit Engine — Decisões Técnicas

> **Estado:** baseline inicial para o primeiro commit; documento evolutivo.  
> **Escopo:** desafio técnico — Desenvolvedor Java Pleno.  
> **Referências:** enunciado, [`SPEC.md`](SPEC.md), [`AGENTS.md`](AGENTS.md) e [`TASKS.md`](TASKS.md).

## Propósito

Registrar **as decisões que orientam o início da implementação**, com justificativa e trade-offs, sem antecipar detalhes que serão definidos ao desenvolver cada feature. O `SPEC.md` continua sendo a fonte das regras financeiras e critérios de aceite; o `AGENTS.md` rege a atuação dos agentes. **Este documento não autoriza implementação nem alterações de escopo.**

- `APPROVED`: escolha validada pelo responsável e aplicável ao projeto.
- `PROPOSED`: alternativa em análise; não autoriza mudança.
- `DEFERRED`: funcionalidade intencionalmente adiada, com motivo e risco registrados.

## Decisões registradas nesta versão

| ID | Decisão | Status |
|---|---|---|
| DEC-001 | Stack Java 21, Spring Boot, JPA/Hibernate, PostgreSQL, Flyway e Angular | **APPROVED** |
| DEC-002 | Monólito modular e isolamento do domínio financeiro | **APPROVED** |
| DEC-003 | Precisão decimal e arredondamento financeiro | **APPROVED** |
| DEC-004 | Strategy Pattern aberto e composição | **APPROVED** |
| DEC-005 | `dueDate` → meses comerciais na entrada; núcleo recebe meses inteiros | Regras determinísticas e Golden Cases independentes | **PROPOSED** |
| DEC-006 | Câmbio manual por `effectiveAt` e snapshot imutável | Vigência consistente e rastreabilidade histórica | **PROPOSED** |
| DEC-007 | Liquidação ACID, idempotência e concorrência no PostgreSQL | **APPROVED** |
| DEC-008 | Flyway, constraints, projeções JPA, paginação e índices por necessidade | Integridade e consultas eficientes sem indexação prematura | **PROPOSED** |
| DEC-009 | Angular standalone, Reactive Forms, Signals locais e RxJS | UI reativa, sem cálculo financeiro local nem estado global excessivo | **PROPOSED** |
| DEC-010 | Testes rastreáveis às regras de negócio | **APPROVED** |
| DEC-011 | CI enxuta com GitHub Actions | Build e testes reproduzíveis, subordinados ao escopo Pleno | **APPROVED** |
| DEC-012 | Não implementar autenticação/autorização completas | Avaliação local; risco de acesso sem identidade documentado | **PROPOSED** |
| DEC-013 | Não integrar provedor cambial externo nem Circuit Breaker | Atualização manual é suficiente para o requisito | **PROPOSED** |
| DEC-014 | Não executar transferências bancárias reais | Liquidação do MVP representa fechamento interno | **PROPOSED** |
| DEC-015 | Não oferecer liquidação parcial nem estorno | Política de lote integral e simplificação consciente | **PROPOSED** |
| DEC-016 | Não utilizar microsserviços, filas nem EDA | Sem exigência de integração assíncrona/distribuída | **PROPOSED** |
| DEC-017 | Não adicionar Redis, sharding nem observabilidade avançada | Evitar infraestrutura desnecessária; preservar diagnóstico básico | **PROPOSED** |

> **Numeração intencional:** os IDs seguem o registro de decisões já discutido. Lacunas numéricas **não são erros**; outras decisões serão acrescentadas quando sua implementação se aproximar. Não renumerar registros existentes.

## DEC-001 — Stack tecnológica (`APPROVED`)

**Decisão.** Java 21, Spring Boot (Web MVC), Maven, Spring Data JPA/Hibernate, PostgreSQL, Flyway, Jakarta Bean Validation e Springdoc OpenAPI no backend; Angular e TypeScript no frontend; Docker Compose para execução integrada. JUnit 5, Mockito quando necessário e Testcontainers PostgreSQL para testes. Preferir mapeamentos explícitos simples; adicionar MapStruct apenas se reduzir repetição relevante.

**Motivo.** Ecossistema maduro, produtivo e conhecido, apropriado ao prazo e às transações financeiras. **Trade-off:** JPA requer atenção a `N+1` e queries geradas; consultas específicas poderão utilizar projeções ou SQL nativo parametrizado sem adotar MyBatis. Schema evolui por Flyway, com Hibernate em `ddl-auto=validate`.

## DEC-002 — Arquitetura modular pragmática (`APPROVED`)

**Decisão.** Um monólito modular Spring Boot, organizado por funcionalidade (*package by feature*) e responsabilidades de API, aplicação, domínio e persistência. O cálculo financeiro será Java puro, independente de HTTP, Spring e JPA.

**Motivo.** Coesão, testes rápidos e transações ACID locais, sem custo operacional de serviços distribuídos. **Trade-off:** evitar camadas, interfaces e adaptadores sem responsabilidade real; não adotar microsserviços ou arquitetura hexagonal estrita apenas por preferência estética.

## DEC-003 — Precisão financeira (`APPROVED`)

**Decisão.** Utilizar `BigDecimal` para valores monetários e taxas, sem `float`/`double`, com `MathContext.DECIMAL128` nos cálculos intermediários e `RoundingMode.HALF_EVEN` com duas casas decimais exclusivamente nos pontos definidos pelo `SPEC.md`.

- **Valor presente BRL:** calcular com precisão `DECIMAL128` e arredondar para duas casas decimais utilizando `HALF_EVEN`.
- **Deságio BRL:** calcular pela subtração exata do valor de face pelo valor presente BRL já arredondado, sem arredondamento adicional.
- **Conversão USD:** dividir o valor presente BRL previamente arredondado pela cotação cambial (BRL por USD), utilizando `DECIMAL128`, e arredondar o resultado USD para duas casas decimais com `HALF_EVEN`.
- **Validação cambial:** o `CurrencyConverter` deve rejeitar valores presentes BRL com mais de duas casas decimais, evitando arredondamento silencioso ou inversão da ordem de cálculo.
- **Transporte de dados:** representar valores monetários e taxas no JSON como strings decimais exatas, sem recálculo financeiro no Angular.

**Motivo.** Garantir precisão decimal, previsibilidade dos cálculos e reprodução exata dos Golden Cases C1, C2 e C3.

**Risco.** Arredondamentos intermediários indevidos, inversão da ordem de conversão ou utilização de `float`/`double` podem gerar divergências financeiras ao centavo.

**Rastreabilidade:** TASK-003 (precificação BRL) e TASK-004 (conversão cambial pura).

## DEC-004 — Strategy Pattern e composição (`APPROVED`)

**Decisão.** Utilizar `PricingStrategy` como interface **aberta (não sealed)**, com uma implementação por tipo de recebível e seleção por registro de estratégias no `PricingEngine`.

- **Duplicata Mercantil:** `TradeBillPricingStrategy`, com spread de `1,50% a.m.`.
- **Cheque Pré-datado:** `PostDatedCheckPricingStrategy`, com spread de `2,50% a.m.`.
- As Strategies compartilham `CompoundInterestCalculator` por **composição**, não por herança.
- O `PricingEngine` oferece um construtor padrão para as Strategies oficiais e outro que recebe `List<PricingStrategy>`, permitindo composição externa e extensibilidade.
- Organizar as responsabilidades em `domain.model`, `domain.strategy` e `domain.calculation`, mantendo a arquitetura **Package by Feature**.
- Utilizar `record` para dados imutáveis quando adequado.
- Manter a precificação exclusivamente em **BRL**. A conversão cambial ocorre **após o cálculo e arredondamento do valor presente BRL**, por meio de `CurrencyConverter`, sem modificar as Strategies ou o `PricingEngine`.
- A orquestração entre cálculo de prazo, precificação e conversão cambial pertence à camada de aplicação.

**Motivo.** Aplicar os princípios SOLID **SRP, OCP e DIP**, permitindo novas fórmulas ou ativos sem alterar as Strategies existentes, preservando a separação de responsabilidades e a testabilidade. O registro externo permite adicionar estratégias sem modificar o mecanismo de seleção; a inclusão no conjunto padrão exige atualizar sua configuração.

**Alternativas não adotadas:** `switch` central com cálculos, contrato limitado a `getSpread()`, `sealed interface` para `PricingStrategy` e conversão cambial dentro das Strategies, por aumentarem o acoplamento ou restringirem a extensibilidade.

**Rastreabilidade:** TASK-003 (motor financeiro e Strategy) e TASK-004 (conversão cambial pura).

### DEC-005 — Prazo comercial separado do cálculo (`APPROVED`)

**Decisão.** O `PricingEngine` recebe `termMonths` inteiro `>= 1`, sem conhecimento de datas ou relógio do sistema.

Quando a entrada fornecer `dueDate`, a camada de aplicação obtém `operationDate` a partir da data de referência definida pelo backend e utiliza `CommercialTermCalculator`, componente Java puro localizado em `pricing.domain.calculation`.

O componente calcula `days = DAYS.between(operationDate, dueDate)`, rejeita `days <= 0` e retorna `termMonths = ceil(days / 30)`, utilizando aritmética inteira para evitar imprecisões.

A obtenção da data de referência ocorre na camada de aplicação, permitindo utilização de `Clock` injetável e testes determinísticos.

**Justificativa.** Separar o cálculo do prazo comercial da precificação financeira preserva o princípio da responsabilidade única (SRP) e facilita testes isolados.

A regra representa uma **premissa provisória do MVP**:

- 1 a 30 dias correspondem a 1 mês.
- 31 a 60 dias correspondem a 2 meses.
- 61 a 90 dias correspondem a 3 meses.

O cálculo considera dias corridos, inclusive em anos bissextos, sem tratamento de dias úteis ou feriados bancários.

**Golden Cases.** Não aplicar a conversão de datas aos casos C1, C2 e C3, que já informam os prazos diretamente (3, 2 e 3 meses, respectivamente).

**Rastreabilidade:** TASK-004 (cálculo de prazo comercial) e TASK-007 (orquestração na camada de aplicação).

## DEC-007 — Liquidação, idempotência e concorrência (`APPROVED`)

**Decisão.** Processar cada lote integralmente em **uma transação PostgreSQL**, abrangendo efeitos financeiros, histórico auditável e registro definitivo de idempotência. Exigir `Idempotency-Key` persistente (armazenada apenas internamente, sem exposição nas consultas gerais de liquidações) e `request_hash` SHA-256 de payload canônico determinístico (`assignorId`, `paymentCurrency` e `receivableIds` ordenados sem duplicatas), com restrição `UNIQUE` na chave. A verificação da `Idempotency-Key` tem **precedência estrita** sobre a validação de estado dos títulos: criação inédita retorna **`201 Created`**; replay após commit com mesma chave e mesmo hash retorna **`200 OK`** com o snapshot original imutável (nunca `409 RECEIVABLE_ALREADY_SETTLED`); mesma chave com hash divergente retorna **`409 Conflict` (`IDEMPOTENCY_PAYLOAD_MISMATCH`)**.

**Proteções.** Prevenir dupla liquidação por intenções distintas com validação transacional de estado (`OPEN → SETTLED` em ordem determinística de `UUID`) e `UNIQUE(receivable_id)` nos itens de liquidação, retornando **`409 Conflict` (`RECEIVABLE_ALREADY_SETTLED`)** apenas quando outra chave já liquidou o título. Disputas concorrentes que excedam o timeout de lock retornam **`503 Service Unavailable` (`CONCURRENT_SETTLEMENT_TIMEOUT`)** com header **`Retry-After`** configurável — definido expressamente como orientação (*hint*) de nova tentativa, e não garantia de término da transação concorrente — preservando a mesma `Idempotency-Key` no retry. Qualquer falha no lote causa **rollback integral**. Preservar snapshot financeiro imutável na liquidação, conforme `SPEC.md` e [`docs/contracts.md`](docs/contracts.md).

**Alternativa rejeitada.** Não reservar `IN_PROGRESS` em transação independente, nem usar `409` para timeout transitório, Redis, Inbox/Outbox ou locks distribuídos: exigiriam recuperação de estados intermediários ou confundiriam conflito definitivo com contenção temporária. **Risco:** requisições concorrentes podem aguardar até o limite de lock configurado; a implementação deverá encerrar corretamente transações invalidadas.

## DEC-010 — Estratégia de testes (`APPROVED`)

**Decisão.** Exigir testes automatizados rastreáveis para **100% das regras de negócio especificadas e dos cenários de erro conhecidos e aplicáveis**; não confundir isso com 100% de linhas cobertas ou todas as falhas imagináveis. Testar domínio financeiro com JUnit 5 **sem mocks dos cálculos**, e transações, constraints, rollback, idempotência e concorrência com **Testcontainers PostgreSQL**. Utilizar Mockito apenas em fronteiras adequadas; testar contratos REST e fluxos essenciais do Angular.

**Critério de conclusão.** Cada tarefa que implemente uma regra deve apresentar testes positivos, negativos e de contorno aplicáveis, resultados reais e vínculo com o `SPEC.md`. Uma tarefa só é concluída após validação e aprovação humana, conforme `AGENTS.md` e `TASKS.md`.

## Evolução planejada (sem detalhamento antecipado)

Registrar progressivamente, **mantendo os IDs originais**, as decisões quando o trabalho correspondente chegar: `DEC-005` (conversão de vencimento), `DEC-006` (câmbio), `DEC-008` (consultas/índices), `DEC-009` (Angular), `DEC-011` (CI) e `DEC-012` a `DEC-017` (cortes de escopo). As **regras e entregas obrigatórias do `SPEC.md` e do enunciado permanecem válidas** mesmo quando uma decisão ainda não possui subseção neste arquivo.

**Regra de mudança:** diante de uma escolha técnica relevante não registrada ou de necessidade de alterar uma decisão aprovada, o agente deve apresentar contexto, alternativas, impactos e riscos **antes** de solicitar autorização. Não alterar decisões aprovadas nem implementar novas soluções silenciosamente. Registros antigos não devem ser renumerados ou apagados sem justificativa histórica.
