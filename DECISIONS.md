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

## DEC-008 — Persistência, Migrações Flyway, Relatórios e Índices (`APPROVED`)

### Contexto

O SRM Credit Engine necessita de persistência relacional confiável, integridade financeira, histórico auditável e consultas paginadas para o extrato de liquidações.

### Decisão arquitetural proposta

Adotar **Spring Data JPA/Hibernate** para persistência transacional, **PostgreSQL** como banco relacional e **Flyway** como mecanismo exclusivo de evolução do schema.

### Persistência e integridade

- Utilizar Flyway para criar e evoluir tabelas, constraints e índices.
- Configurar Hibernate com `spring.jpa.hibernate.ddl-auto=validate`.
- Preservar invariantes de negócio por meio de `NOT NULL`, `CHECK`, `FOREIGN KEY` e `UNIQUE`, conforme a especificação aprovada.
- Garantir integridade referencial e preservar o histórico imutável das liquidações.
- Manter as restrições de idempotência e unicidade de liquidação definidas na **DEC-007**.
- Utilizar transações Spring nas fronteiras dos casos de uso.

### Consultas e relatórios

- Utilizar Spring Data JPA como mecanismo principal.
- Priorizar projeções DTO para consultas do extrato.
- Executar filtros, ordenação e paginação diretamente no PostgreSQL.
- Utilizar `Pageable`, com tamanho máximo de página definido no contrato.
- Aplicar ordenação determinística, utilizando data/hora da liquidação e identificador único.
- Utilizar JPQL, Specifications ou SQL nativo parametrizado quando a necessidade da consulta justificar.
- Evitar consultas N+1 e carregamento desnecessário de entidades.

### Estratégia de índices

- Manter índices associados a chaves primárias, unicidade e integridade.
- Prever índice para consulta de cotação vigente, considerando moeda e `effectiveAt`.
- Definir índices adicionais do extrato conforme filtros, ordenação e consultas efetivamente implementadas.
- Avaliar índices compostos com base na seletividade e ordem das colunas.
- Utilizar `EXPLAIN (ANALYZE, BUFFERS)` quando necessário para investigar desempenho.
- Evitar índices redundantes e otimizações prematuras.

### Justificativa

A combinação JPA/Hibernate, PostgreSQL e Flyway oferece produtividade, integridade transacional, versionamento do schema e boa manutenção.

Projeções e paginação no banco permitem implementar o extrato sem carregar integralmente o histórico em memória.

A estratégia de indexação orientada às consultas reais evita custos desnecessários e permite otimizações justificadas por evidências.

### Alternativas consideradas

- **MyBatis:** não adotado por não apresentar vantagem necessária para o escopo e introduzir uma tecnologia adicional.
- **SQL nativo para todas as operações:** não adotado por aumentar a manutenção sem benefício proporcional.
- **Hibernate `ddl-auto=update`:** não adotado por não oferecer o controle versionado de migrações desejado.
- **Índices compostos definidos antecipadamente para todos os filtros:** não adotados por ausência de evidências dos padrões reais de acesso.

### Trade-offs e riscos

- O uso de JPA/Hibernate exige atenção a relacionamentos, carregamento de entidades e consultas N+1.
- Consultas paginadas podem exigir operações adicionais de contagem, especialmente com `Page<T>`.
- Índices precisam ser reavaliados conforme o volume de dados e os filtros utilizados.

### Validação

Realizar testes de integração com PostgreSQL via Testcontainers para comprovar:

- Execução correta das migrations Flyway.
- Compatibilidade entre entidades JPA e schema.
- Funcionamento das constraints.
- Integridade referencial.
- Paginação e filtros do extrato.
- Ordenação determinística.
- Ausência de consultas N+1 nos fluxos relevantes.
- Preservação do histórico financeiro.

**Status:** `APPROVED` — aguardando aprovação.

---

## DEC-009 — Arquitetura Frontend Angular (`APPROVED`)

### Contexto

O SRM Credit Engine necessita de uma interface para simulação financeira, liquidação de recebíveis e consulta de extrato paginado, preservando a precisão monetária e a separação entre apresentação e regras de negócio.

### Decisão arquitetural proposta

Adotar **Angular com TypeScript estrito, Standalone Components, Reactive Forms, Signals e RxJS**, organizando a aplicação por funcionalidades (*feature-based*).

### Arquitetura e responsabilidades

- Utilizar Standalone Components, evitando módulos e camadas desnecessárias.
- Utilizar Reactive Forms para entrada e validação dos dados.
- Utilizar Signals para estados locais de apresentação.
- Utilizar RxJS para fluxos assíncronos, debounce e tratamento de requisições HTTP.
- Centralizar a comunicação REST em serviços tipados utilizando `HttpClient`.
- Manter o backend como única fonte de verdade para cálculos financeiros.
- Executar filtros, ordenação e paginação do extrato no backend.
- Não introduzir NgRx ou gerenciamento global de estado sem necessidade comprovada.

### Precisão financeira

Valores monetários e taxas serão transportados como **strings decimais exatas**, conforme o contrato aprovado no `SPEC.md`.

O frontend será responsável exclusivamente pela validação de entrada, apresentação e formatação dos resultados, sem recalcular valor presente, deságio ou conversão cambial.

A formatação deverá preservar a precisão dos valores recebidos, evitando conversões inseguras para `Number`.

### Simulação financeira

- Utilizar Reactive Forms com validação de campos obrigatórios.
- Aplicar debounce às alterações válidas do formulário.
- Utilizar `switchMap` para impedir que resultados obsoletos de simulação substituam resultados mais recentes.
- Exibir estados de carregamento, sucesso e erro.
- Não executar cálculos financeiros no navegador.

### Liquidação e idempotência

- Impedir envios duplicados enquanto uma solicitação de liquidação estiver em andamento.
- Gerar uma `Idempotency-Key` para cada intenção de liquidação.
- Reutilizar a mesma chave em tentativas de repetição da operação original.
- Não executar retries automáticos de operações financeiras sem política aprovada.
- Não utilizar o cancelamento de requisições HTTP como garantia de cancelamento financeiro.
- Delegar ao backend todas as garantias de idempotência, integridade e concorrência previstas na **DEC-007**.

### Extrato financeiro

- Implementar grid com filtros por período, cedente e moeda.
- Utilizar paginação *server-side*.
- Preservar os filtros durante a navegação entre páginas.
- Tratar estados de carregamento, lista vazia e erro.
- Evitar carregar todo o histórico financeiro no navegador.

### Justificativa

A combinação de Standalone Components, Reactive Forms, Signals e RxJS oferece uma arquitetura simples, moderna e adequada às funcionalidades exigidas.

A separação de responsabilidades facilita manutenção, testes e evolução, sem introduzir complexidade desnecessária de gerenciamento de estado.

### Alternativas consideradas

- **NgRx:** não adotado por ausência de estado global complexo.
- **Cálculos financeiros no frontend:** rejeitados para evitar divergência e perda de precisão.
- **Paginação client-side:** rejeitada por não atender ao requisito Pleno.
- **Arquitetura com Facades e camadas intermediárias obrigatórias:** rejeitada por complexidade desnecessária.

### Trade-offs e riscos

- A integração entre Signals e RxJS exige disciplina para evitar estados duplicados.
- Valores financeiros precisam ser formatados sem perda de precisão.
- O bloqueio visual de ações não substitui a idempotência persistente do backend.
- O tratamento de requisições assíncronas deve impedir apresentação de dados obsoletos.

### Validação

Testes automatizados deverão verificar:

- Validação dos formulários.
- Parâmetros enviados à API.
- Preservação de valores monetários.
- Simulações assíncronas sem resultados obsoletos.
- Tratamento de erros HTTP.
- Prevenção de múltiplos envios pela interface.
- Reutilização da `Idempotency-Key` em retries.
- Filtros e paginação *server-side*.
- Estados de carregamento, erro e ausência de dados.

**Status:** `APPROVED` — aguardando aprovação.

## DEC-010 — Estratégia de Testes e Validação (`APPROVED`)

### Contexto

O SRM Credit Engine manipula valores financeiros e realiza operações de liquidação que exigem precisão decimal, integridade transacional, idempotência, controle de concorrência e auditabilidade.

A estratégia de testes deve priorizar a **corretude das regras de negócio**, a preservação das invariantes financeiras e a prevenção de inconsistências transacionais.

O objetivo não é produzir testes artificiais para aumentar indicadores de cobertura, mas estabelecer evidências automatizadas de que os requisitos especificados estão corretamente implementados.

### Decisão arquitetural

Adotar uma estratégia de testes automatizados **orientada aos riscos do negócio e rastreável aos requisitos**, utilizando:

- **JUnit 5:** testes unitários e parametrizados.
- **Mockito:** isolamento de dependências externas quando necessário.
- **Spring Boot Test:** testes de integração que exigem o contexto da aplicação.
- **MockMvc:** validação dos contratos REST e comportamentos HTTP.
- **Testcontainers PostgreSQL:** validação real de persistência, constraints, transações e concorrência.
- **Ferramentas de testes do Angular:** validação de componentes, formulários e integração com serviços HTTP.

A estratégia deverá garantir cobertura automatizada de **100% das regras de negócio documentadas e aprovadas no `SPEC.md`**, incluindo os cenários de erro conhecidos e aplicáveis.

Essa exigência representa **cobertura funcional integral dos requisitos especificados**, não 100% de cobertura de linhas, métodos ou branches.

---

### 1. Testes unitários — Domínio financeiro

Utilizar JUnit 5 para testar diretamente as classes de domínio financeiro.

Não utilizar Mockito para substituir cálculos financeiros puros ou dependências que possam ser instanciadas diretamente sem infraestrutura externa.

#### Validação obrigatória

- Golden Cases C1, C2 e C3, com resultados financeiros exatos.
- Cálculo do valor presente e do deságio.
- Utilização de `BigDecimal` e `MathContext.DECIMAL128`.
- Aplicação de `RoundingMode.HALF_EVEN`.
- Precisão decimal e pontos corretos de arredondamento.
- Spreads financeiros definidos para cada tipo de recebível.
- Comportamento das implementações de `PricingStrategy`.
- Inclusão de uma Strategy adicional com fórmula diferente, comprovando a extensibilidade do `PricingEngine`.
- Rejeição de valores monetários, taxas e prazos inválidos.
- Conversão de datas em meses comerciais por meio de `CommercialTermCalculator`.
- Validação de datas e condições de contorno.
- Conversão monetária por meio de `CurrencyConverter`.
- Ordem correta entre cálculo financeiro, arredondamento em BRL e conversão para USD.

O cálculo do prazo comercial deverá ser validado independentemente dos Golden Cases C1, C2 e C3, preservando os prazos originalmente especificados nesses cenários.

Os resultados esperados deverão ser explícitos e independentes da implementação testada.

---

### 2. Testes dos serviços de aplicação

Utilizar JUnit 5 e Mockito quando necessário para isolar dependências externas ou componentes de infraestrutura.

Os testes deverão validar a **orquestração dos casos de uso**, sem reproduzir a lógica interna da implementação.

#### Validação obrigatória

- Coordenação entre os componentes de domínio.
- Seleção correta das Strategies.
- Aplicação das regras financeiras existentes.
- Cálculo do prazo comercial na camada apropriada.
- Seleção da cotação cambial vigente no instante da operação.
- Tratamento das exceções de domínio e aplicação.
- Propagação adequada dos resultados.
- Ausência de operações persistentes indevidas.
- Respeito às responsabilidades entre as camadas.

Não utilizar mocks para substituir os cálculos financeiros cuja corretude precisa ser comprovada.

A utilização de mocks deverá ocorrer apenas quando contribuir para o isolamento e a clareza do teste.

---

### 3. Testes de integração — PostgreSQL

Utilizar Spring Boot Test e Testcontainers com PostgreSQL real para verificar comportamentos que dependem de características específicas do banco de dados.

Não substituir esses testes por mocks de repositórios ou por bancos em memória.

#### Persistência e integridade

Validar:

- Execução correta das migrations Flyway.
- Compatibilidade entre entidades JPA e schema PostgreSQL.
- Funcionamento das constraints `NOT NULL`, `CHECK`, `FOREIGN KEY` e `UNIQUE`, conforme aplicável.
- Integridade referencial.
- Persistência e recuperação dos dados.
- Imutabilidade dos registros históricos.
- Seleção correta da cotação cambial conforme `effectiveAt`.
- Preservação do histórico de cotações.
- Persistência dos snapshots financeiros.

#### Liquidação e idempotência

Validar obrigatoriamente:

- Atomicidade da liquidação de lotes.
- Rollback integral diante de falha em qualquer item.
- Ausência de registros financeiros parcialmente confirmados.
- Criação inédita de liquidação com resposta `201 Created`.
- Repetição após commit com mesma `Idempotency-Key` e payload equivalente, retornando `200 OK` e o snapshot original.
- Reutilização da mesma chave com payload divergente, retornando `409 Conflict`.
- Precedência da verificação de idempotência sobre a validação do estado dos recebíveis.
- Requisições simultâneas utilizando a mesma chave.
- Chaves distintas tentando liquidar o mesmo recebível.
- Prevenção de dupla liquidação.
- Tratamento de contenção transacional e timeout conforme a DEC-007.
- Recuperação correta do resultado original após commit.
- Persistência correta dos valores financeiros, taxas, cotação e timestamps.
- Preservação da integridade financeira após falhas transacionais.

#### Concorrência real

Os testes de concorrência deverão utilizar:

- Transações independentes.
- Conexões transacionais adequadas ao cenário.
- Mecanismos de sincronização controlados.
- Verificação dos resultados efetivamente persistidos no PostgreSQL.

Não utilizar atrasos arbitrários, como `Thread.sleep()`, como mecanismo principal de sincronização.

Os testes deverão comprovar que **duas requisições concorrentes não conseguem produzir duas liquidações confirmadas para o mesmo recebível**.

Para a mesma `Idempotency-Key`, os testes devem verificar a recuperação do resultado original quando a transação inicial for confirmada e o tratamento contratual aplicável quando ocorrer timeout.

O comportamento deve permanecer compatível com a DEC-007.

#### Consultas e relatórios

Validar:

- Filtros do extrato financeiro.
- Paginação executada no PostgreSQL.
- Ordenação determinística.
- Aplicação correta dos parâmetros de consulta.
- Preservação do histórico financeiro.
- Ausência de consultas N+1 nos fluxos em que esse risco seja relevante.

---

### 4. Testes da API REST

Utilizar MockMvc para testes de comportamento HTTP e testes de integração quando for necessário verificar o fluxo completo da aplicação.

#### Validação obrigatória

- Requisições válidas e inválidas.
- Campos obrigatórios.
- Formatos e tipos de dados.
- Precisão decimal dos valores financeiros.
- Rejeição de payloads incompatíveis com o contrato.
- Códigos HTTP conforme o OpenAPI aprovado.
- Respostas padronizadas utilizando `ProblemDetail`.
- Presença dos códigos de erro definidos no contrato.
- Conflitos de idempotência.
- Conflitos relacionados ao estado dos recebíveis.
- Ausência de exposição de informações internas.
- Filtros e paginação *server-side*.
- Contratos de entrada e saída da API.
- Comportamento esperado diante de falhas de aplicação e infraestrutura.

Os testes deverão verificar o conteúdo das respostas, não apenas o status HTTP.

Quando aplicável, também deverão verificar headers HTTP exigidos pelo contrato ou pelo protocolo.

---

### 5. Testes do frontend Angular

Utilizar o runner de testes configurado no projeto Angular, evitando a introdução de ferramentas adicionais sem necessidade comprovada.

#### Validação obrigatória

- Campos obrigatórios e validações dos Reactive Forms.
- Envio correto dos parâmetros ao backend.
- Preservação dos valores monetários como strings decimais.
- Exibição dos resultados financeiros sem recálculo local.
- Tratamento de respostas e erros HTTP.
- Atualização das simulações sem apresentação de resultados obsoletos.
- Prevenção de envios duplicados durante operações de liquidação.
- Reutilização da `Idempotency-Key` em tentativas da mesma operação.
- Filtros e paginação executados no servidor.
- Estados de carregamento, sucesso, erro e ausência de resultados.

O bloqueio visual de ações não deverá ser considerado substituto das garantias de idempotência e concorrência implementadas no backend.

---

### 6. Determinismo e confiabilidade

Os testes deverão apresentar resultados reproduzíveis, evitando dependências de condições externas não controladas.

#### Diretrizes obrigatórias

- Utilizar dados financeiros conhecidos e resultados esperados explícitos.
- Controlar datas e instantes por meio de `Clock` injetável quando necessário.
- Utilizar cotações cambiais fixas nos cenários de teste.
- Evitar dependência de serviços externos.
- Não utilizar atrasos arbitrários para sincronizar testes concorrentes.
- Preservar o isolamento entre testes.
- Evitar dependência da ordem de execução.
- Garantir independência entre dados persistidos pelos diferentes cenários.
- Verificar resultados financeiros e estados persistidos, não apenas chamadas de métodos.
- Investigar testes intermitentes antes de considerar uma funcionalidade validada.

Testes que dependam de tempo, concorrência ou persistência deverão possuir mecanismos de controle adequados ao comportamento avaliado.

---

### 7. Cobertura integral das regras de negócio e cenários de falha

#### Decisão

Estabelecer como requisito de qualidade que **100% das regras de negócio documentadas e aprovadas no `SPEC.md` possuam cobertura por testes automatizados**.

Para cada regra, deverão ser identificados e testados os cenários conhecidos e aplicáveis de:

- Sucesso.
- Validação negativa.
- Condições de contorno.
- Falhas operacionais relevantes.
- Concorrência, quando aplicável.
- Integridade e consistência financeira.

#### Critérios obrigatórios

**1. Rastreabilidade**

Manter uma matriz que relacione os requisitos e regras de negócio aos respectivos testes automatizados.

A rastreabilidade deverá permitir identificar quais testes comprovam cada comportamento especificado.

**2. Cobertura funcional integral**

Nenhuma regra de negócio poderá ser considerada concluída sem testes que comprovem seu comportamento esperado.

**3. Cenários negativos**

Todas as falhas de negócio identificadas, aplicáveis e previstas na especificação deverão possuir testes que validem o erro retornado e a ausência de efeitos financeiros indevidos.

**4. Condições de contorno**

Testar valores limites, datas especiais, arredondamentos, taxas, prazos e demais condições relevantes.

**5. Integridade financeira**

Garantir testes de atomicidade, rollback, idempotência, concorrência e imutabilidade dos registros históricos.

**6. Testes determinísticos**

Utilizar datas controladas, cotações fixas e dados conhecidos para garantir resultados reproduzíveis.

**7. Validação real do banco**

Utilizar PostgreSQL com Testcontainers nos testes que dependam de transações, constraints e concorrência.

**8. Qualidade das verificações**

Os testes deverão validar resultados e invariantes de negócio, não apenas a execução de métodos ou a quantidade de interações com mocks.

**9. Critério de conclusão**

Nenhuma tarefa que implemente ou altere uma regra de negócio poderá ser marcada como concluída sem a execução bem-sucedida dos testes correspondentes e a aprovação humana prevista no `AGENTS.md`.

**10. Evolução dos testes**

Qualquer alteração futura em uma regra de negócio deverá incluir a revisão e atualização dos testes afetados.

#### Meta de qualidade

- **100% das regras de negócio especificadas** devem estar rastreadas e cobertas por testes automatizados.
- **100% dos cenários de erro identificados, aplicáveis e previstos na especificação** devem possuir testes automatizados.
- Nenhum defeito financeiro crítico conhecido poderá permanecer sem correção na entrega.
- A cobertura de linhas de código será utilizada apenas como indicador complementar, não como garantia de qualidade.

#### Limitação reconhecida

Não é possível demonstrar cobertura exaustiva de todas as falhas imagináveis de software e infraestrutura.

A estratégia deverá priorizar cobertura integral dos requisitos conhecidos e análise sistemática dos riscos residuais.

A exigência de cobertura funcional integral não representa garantia de ausência absoluta de defeitos.

---

### 8. Priorização dos testes

A priorização considera o impacto potencial das falhas na integridade financeira, na consistência dos dados e no atendimento aos requisitos obrigatórios.

#### P0 — Críticos e essenciais

- Golden Cases C1, C2 e C3.
- Regras financeiras e precisão decimal.
- Arredondamento e aplicação das Strategies.
- Cálculo correto do valor presente e deságio.
- Conversão cambial e seleção da cotação vigente.
- Atomicidade das liquidações.
- Rollback integral de lotes.
- Idempotência e recuperação do snapshot original.
- Prevenção de dupla liquidação.
- **Concorrência real que envolva integridade financeira.**
- Constraints e garantias transacionais críticas para a liquidação.
- Ausência de efeitos financeiros parcialmente confirmados.

#### P1 — Importantes

- Migrations Flyway e compatibilidade JPA.
- Demais constraints e integridade referencial.
- Contratos REST.
- Validações de entrada.
- Tratamento de erros HTTP.
- Filtros, paginação e ordenação determinística.
- Persistência e recuperação de dados.
- Testes funcionais essenciais do frontend.

#### P2 — Complementares

- Testes adicionais de experiência do usuário.
- Verificações aprofundadas de acessibilidade.
- Testes adicionais de desempenho.
- Otimizações não exigidas pelos critérios de aceite.
- Cenários exploratórios que não representem requisitos obrigatórios ou riscos críticos identificados.

**Importante:** a classificação por prioridade orienta a ordem de implementação e validação dos testes. Ela não autoriza omitir testes necessários para comprovar requisitos obrigatórios do `SPEC.md`.

Todos os critérios de aceite obrigatórios deverão possuir evidências de validação antes da entrega.

---

### Justificativa

Essa abordagem oferece elevada confiança nas regras financeiras e nas garantias transacionais sem exigir infraestrutura desnecessária.

Testes unitários rápidos validam o domínio financeiro, enquanto testes com PostgreSQL real comprovam propriedades que não podem ser verificadas adequadamente por mocks.

A combinação de testes unitários, testes de aplicação, integração, contratos REST e frontend permite validar o comportamento do sistema em diferentes níveis.

A estratégia prioriza a detecção de defeitos relevantes em vez de percentuais artificiais de cobertura.

---

### Alternativas consideradas

- **Mockar repositórios em todos os testes:** rejeitado porque mocks não comprovam integridade transacional, constraints ou concorrência real.
- **Utilizar H2 para testes transacionais:** não adotado como substituto do PostgreSQL, pois existem diferenças relevantes de comportamento entre os bancos.
- **Utilizar Spring Boot Test em todos os testes:** rejeitado pelo custo desnecessário de inicialização do contexto.
- **Exigir 100% de cobertura de linhas de código:** rejeitado por não garantir corretude funcional.
- **Executar testes de carga extensivos:** adiado por não ser requisito obrigatório do desafio Pleno.

---

### Trade-offs e riscos

- Testcontainers exige um ambiente Docker disponível.
- Testes concorrentes precisam ser construídos cuidadosamente para evitar resultados intermitentes.
- Testes completos de integração possuem custo de execução superior aos testes unitários.
- A matriz de rastreabilidade exige manutenção conforme os requisitos evoluem.
- O conjunto de testes deverá evoluir quando novas regras financeiras forem introduzidas.
- Testes automatizados reduzem riscos, mas não eliminam a necessidade de revisão técnica e análise dos resultados.

---

### Critérios de aprovação

Uma funcionalidade somente será considerada validada quando:

1. Atender aos critérios de aceite aplicáveis do `SPEC.md`.
2. Possuir testes capazes de detectar violações das regras de negócio.
3. Apresentar execução bem-sucedida das verificações necessárias.
4. Não possuir falhas críticas conhecidas sem tratamento.
5. Apresentar evidências de teste e limitações residuais.
6. Demonstrar rastreabilidade entre os requisitos implementados e os testes correspondentes.
7. Comprovar as garantias transacionais e de concorrência quando aplicáveis.

A funcionalidade somente poderá ser aprovada quando **100% das regras de negócio aplicáveis estiverem rastreadas e cobertas por testes automatizados**, incluindo os cenários de erro identificados e previstos na especificação.

A execução de comandos deverá respeitar a política **G-03 do `AGENTS.md`**.

A conclusão das tarefas dependerá da validação e aprovação humana previstas no `AGENTS.md` e no `TASKS.md`.

**Status:** `APPROVED`.

**Rastreabilidade:** aplica-se a todas as tarefas que implementem ou modifiquem requisitos funcionais, regras financeiras, operações transacionais ou contratos de integração do SRM Credit Engine.

## DEC-011 — Integração Contínua com GitHub Actions (`APPROVED`)

### Contexto

O SRM Credit Engine necessita de um processo automatizado e reproduzível para verificar a compilação, os testes automatizados e a integridade das alterações antes de sua integração à branch principal.

Embora a implementação de integração contínua (CI) seja classificada como expectativa do nível Sênior no enunciado, sua adoção simplificada agrega qualidade ao projeto Pleno, desde que não comprometa a implementação dos requisitos obrigatórios.

A integração contínua deverá contribuir para a identificação antecipada de regressões, inconsistências de ambiente e falhas de integração.

### Decisão arquitetural

Adotar **GitHub Actions** como ferramenta de integração contínua, utilizando um workflow enxuto para executar o build e os testes automatizados do backend Java e, quando disponível, do frontend Angular.

A implementação será **incremental**, acompanhando a evolução das funcionalidades do projeto.

O pipeline deverá priorizar:

- Reprodutibilidade das verificações.
- Execução dos testes automatizados obrigatórios.
- Identificação antecipada de regressões.
- Segurança na utilização de credenciais e permissões.
- Simplicidade operacional.
- Ausência de infraestrutura desnecessária.

A integração contínua não substituirá a revisão técnica, os critérios de aceite nem a aprovação humana definidos no `AGENTS.md` e no `TASKS.md`.

---

### 1. Estratégia de implementação

#### 1.1. Workflow

Criar um único arquivo de configuração:

`.github/workflows/ci.yml`

O workflow poderá conter jobs independentes para backend e frontend, preservando uma estrutura simples e de fácil manutenção.

Não criar workflows adicionais sem necessidade técnica comprovada.

#### 1.2. Gatilhos de execução

Configurar execução automática:

- Em Pull Requests direcionados à branch `main`.
- Em pushes realizados na branch `main`.

A execução em outras branches poderá ocorrer quando necessária à validação de alterações, sem ampliar desnecessariamente os gatilhos.

O pipeline deverá oferecer evidências claras de sucesso ou falha das verificações executadas.

#### 1.3. Backend Java

Configurar o ambiente de execução com:

- Java 21.
- Distribuição JDK compatível com o projeto.
- Maven Wrapper versionado no repositório.
- Dependências e configurações já definidas no projeto.

Executar a fase Maven:

`verify`

A configuração deverá garantir que os testes automatizados obrigatórios sejam efetivamente executados, incluindo testes unitários e de integração.

A execução da fase `verify` não será considerada evidência suficiente caso testes obrigatórios estejam excluídos ou não estejam vinculados ao ciclo de build.

Não utilizar configurações que ignorem ou ocultem falhas de testes.

#### 1.4. Persistência e integração

Executar os testes de integração com PostgreSQL real utilizando **Testcontainers**.

O ambiente de CI deverá disponibilizar suporte ao Docker necessário para iniciar os containers utilizados nos testes.

Validar os comportamentos relacionados a:

- Migrations Flyway.
- Mapeamentos JPA/Hibernate.
- Constraints e integridade referencial.
- Persistência e recuperação dos dados.
- Integridade financeira.
- Transações ACID.
- Idempotência.
- Rollback.
- Concorrência, quando aplicável à funcionalidade implementada.

Os testes deverão utilizar as configurações de integração aprovadas no projeto.

Não substituir PostgreSQL por H2 ou mocks para contornar dificuldades de execução no pipeline.

#### 1.5. Frontend Angular

O frontend será incorporado à CI **quando sua implementação estiver disponível no repositório**.

A ausência temporária do frontend durante as etapas iniciais não deverá impedir a validação do backend.

Após a disponibilização do Angular, configurar:

- Versão Node.js compatível com a versão Angular adotada.
- Instalação reproduzível de dependências utilizando `npm ci`.
- Execução dos testes automatizados em modo não interativo.
- Validação do build de produção.
- Verificações de qualidade já configuradas no projeto.

Os comandos utilizados deverão corresponder aos scripts efetivamente definidos no `package.json`.

Após a incorporação do frontend, seus testes e build passarão a integrar as verificações obrigatórias da CI.

Não manter exclusões permanentes que permitam ignorar falhas do frontend.

#### 1.6. Análise estática

Executar verificações de lint ou qualidade de código quando já estiverem configuradas no projeto.

Não introduzir ferramentas adicionais, serviços externos ou dependências de análise estática sem necessidade demonstrada.

Ferramentas avançadas de análise poderão ser avaliadas futuramente, conforme a evolução do projeto.

#### 1.7. Reprodutibilidade

Utilizar:

- Versões compatíveis e controladas das ferramentas.
- Maven Wrapper para execução do backend.
- Dependências versionadas.
- Arquivos de lock quando aplicáveis.
- Cache Maven e npm quando apropriado.

O cache deverá funcionar exclusivamente como mecanismo de otimização de execução, sem comprometer a atualização correta das dependências ou a confiabilidade dos testes.

#### 1.8. Tratamento de falhas

O pipeline deverá falhar diante de:

- Erros de compilação.
- Falhas de testes unitários.
- Falhas de testes de integração.
- Falhas de build do frontend, quando implementado.
- Falhas nas validações obrigatórias.
- Incompatibilidades relevantes entre aplicação e persistência.

Não serão permitidos mecanismos destinados a produzir sucesso artificial, como:

- Ignorar testes obrigatórios.
- Desabilitar verificações para ocultar defeitos.
- Permitir continuidade após falhas obrigatórias.
- Substituir testes de integração reais por mocks para contornar erros.
- Excluir permanentemente funcionalidades implementadas das verificações.

Falhas de infraestrutura deverão ser diagnosticadas e tratadas sem reduzir as garantias de qualidade estabelecidas.

#### 1.9. Segurança

O workflow deverá utilizar permissões mínimas necessárias à execução das verificações.

Não armazenar:

- Senhas.
- Tokens de acesso.
- Chaves privadas.
- Credenciais de banco de dados de ambientes reais.
- Outros segredos sensíveis.

Não expor credenciais ou informações sensíveis nos logs.

Caso sejam necessárias credenciais para integrações futuras, deverão ser utilizados os mecanismos apropriados de gerenciamento de segredos do GitHub Actions.

A CI inicial não deverá depender de credenciais de produção.

#### 1.10. Priorização e evolução incremental

A implementação da integração contínua acompanhará a evolução do projeto.

**Fase inicial — Backend**

- Compilação com Java 21.
- Execução do Maven Wrapper.
- Testes unitários.
- Testes de integração PostgreSQL com Testcontainers.
- Validação da fase Maven `verify`.

**Fase posterior — Frontend**

Quando o Angular estiver implementado:

- Instalação de dependências.
- Execução dos testes.
- Build de produção.
- Verificações de qualidade já configuradas.

**Evolução futura**

Adicionar verificações adicionais somente quando houver necessidade justificada.

A implementação incremental não autoriza ignorar testes de funcionalidades já concluídas.

---

### 2. Integração com a DEC-010

A estratégia de CI deverá respeitar integralmente a **DEC-010 — Estratégia de Testes e Validação**.

O pipeline deverá executar a suíte automatizada obrigatória correspondente às funcionalidades implementadas.

Isso inclui, quando aplicável:

- Golden Cases C1, C2 e C3.
- Regras financeiras.
- Precisão decimal e arredondamentos.
- Estratégias de precificação.
- Conversão cambial.
- Persistência e integridade referencial.
- Idempotência.
- Rollback integral.
- Concorrência financeira.
- Prevenção de dupla liquidação.
- Contratos REST.
- Testes funcionais do frontend.

A cobertura integral das regras de negócio deverá continuar sendo verificada por meio da matriz de rastreabilidade prevista na DEC-010.

**Uma execução bem-sucedida do pipeline não comprova, isoladamente, que 100% dos requisitos foram cobertos por testes.**

A conformidade dependerá também da rastreabilidade entre requisitos, testes e critérios de aceite.

Nenhuma falha de teste obrigatório deverá ser ignorada para viabilizar uma execução artificialmente bem-sucedida.

---

### 3. Justificativa

O GitHub Actions oferece um mecanismo integrado ao repositório para executar verificações automatizadas e reproduzíveis.

Sua utilização reduz riscos relacionados a:

- Regressões introduzidas por alterações de código.
- Diferenças entre ambientes de desenvolvimento.
- Falhas identificadas somente após a integração de alterações.
- Problemas de compilação e dependências.
- Incompatibilidades entre aplicação e banco de dados.

A utilização de um único workflow, com jobs independentes quando necessário, proporciona uma solução simples e proporcional ao escopo do desafio.

A adoção incremental permite introduzir qualidade automatizada sem antecipar infraestrutura destinada a componentes ainda não implementados.

---

### 4. Alternativas consideradas

- **Jenkins:** não adotado por exigir configuração e manutenção adicionais sem benefício proporcional ao escopo atual.

- **GitLab CI:** alternativa tecnicamente válida, mas não selecionada porque o projeto utiliza GitHub como plataforma de versionamento.

- **Pipeline completo de build e deploy:** não adotado por não existir requisito de implantação automatizada em ambiente externo.

- **SonarQube e ferramentas avançadas de análise:** adiados para evolução futura, evitando dependências e complexidade desnecessárias.

- **Workflows independentes para cada funcionalidade:** não adotados inicialmente por aumentarem a complexidade de manutenção sem benefício proporcional.

---

### 5. Trade-offs e riscos

- A execução dos testes com Testcontainers exige suporte ao Docker no ambiente de CI.
- A duração do pipeline poderá aumentar conforme os testes de integração e concorrência forem adicionados.
- Erros de configuração poderão produzir diferenças entre o ambiente local e o ambiente de CI.
- O uso de cache exige atenção à atualização correta das dependências.
- Testes concorrentes mal sincronizados poderão apresentar comportamento intermitente.
- A execução bem-sucedida do pipeline não substitui a revisão técnica.
- A CI não comprova, isoladamente, cobertura integral dos requisitos.
- A implementação incremental exige atualização do workflow conforme novas funcionalidades forem disponibilizadas.

A estratégia deverá priorizar confiabilidade e clareza dos resultados, evitando otimizações que comprometam as verificações obrigatórias.

---

### 6. Validação

O workflow deverá apresentar evidências de execução bem-sucedida das verificações aplicáveis ao estágio atual do projeto.

#### Backend

- Compilação utilizando Java 21.
- Execução do Maven Wrapper.
- Execução dos testes unitários.
- Execução dos testes de integração com PostgreSQL.
- Execução dos testes financeiros obrigatórios.
- Validação de persistência, migrations e constraints.
- Falha do pipeline quando um teste obrigatório falhar.

#### Frontend

Após sua implementação:

- Instalação reproduzível das dependências.
- Compilação bem-sucedida do Angular.
- Execução dos testes automatizados.
- Build de produção.
- Falha do pipeline diante de erros obrigatórios.

#### Integração contínua

- Execução automática em Pull Requests direcionados à `main`.
- Execução automática em pushes na `main`.
- Identificação clara do status dos jobs.
- Reprodutibilidade das verificações.
- Ausência de mecanismos que ocultem falhas.
- Aplicação de permissões mínimas.
- Ausência de exposição de informações sensíveis.

#### Critérios de aprovação

A implementação da CI será considerada validada quando:

1. Executar corretamente as verificações obrigatórias das funcionalidades existentes.
2. Apresentar resultados reproduzíveis.
3. Falhar adequadamente diante de defeitos introduzidos nos testes ou na compilação.
4. Executar testes de integração com PostgreSQL real, quando aplicáveis.
5. Respeitar a estratégia de testes definida na DEC-010.
6. Não introduzir infraestrutura desnecessária.
7. Apresentar evidências verificáveis de execução no GitHub Actions.

A implementação e a execução de comandos deverão respeitar as políticas de governança estabelecidas no `AGENTS.md`, especialmente G-01, G-02 e G-03.

A conclusão da tarefa correspondente dependerá da validação e aprovação humana previstas no `TASKS.md`.

---

**Status:** `APPROVED`.

**Rastreabilidade:** DEC-001 (stack tecnológica), DEC-002 (arquitetura modular), DEC-010 (estratégia de testes), `SPEC.md`, `AGENTS.md` e tarefas de implementação da integração contínua previstas no `TASKS.md`.

## DEC-012 — Autenticação e Autorização Completas (`APPROVED`)

### Contexto

O SRM Credit Engine será desenvolvido como um desafio técnico de avaliação, com execução prevista em ambiente local e controlado.

Embora autenticação e autorização sejam requisitos fundamentais para sistemas financeiros em produção, o enunciado não exige a implementação de uma infraestrutura completa de identidade e controle de acesso.

A introdução desses mecanismos aumentaria a complexidade da solução sem contribuir diretamente para a validação das funcionalidades prioritárias do desafio.

### Decisão de adiamento

Adiar a implementação de mecanismos completos de autenticação e autorização, incluindo:

- OAuth 2.0.
- OpenID Connect (OIDC).
- Validação de JWT para autenticação de operadores.
- Integração com provedores de identidade.
- Gerenciamento de usuários.
- Controle de acesso baseado em papéis (RBAC).
- Gerenciamento de sessões autenticadas.
- Políticas avançadas de autorização por recurso.

A aplicação será desenvolvida para execução local e demonstração técnica, sem exigir autenticação dos operadores.

**Esse adiamento não representa uma recomendação de executar aplicações financeiras em produção sem autenticação ou autorização.**

### Justificativa

A implementação de um provedor de identidade e mecanismos completos de autorização exigiria configurações, fluxos de autenticação, gerenciamento de tokens e regras adicionais de segurança.

Essas funcionalidades não estão entre os requisitos obrigatórios do desafio.

A prioridade será garantir a correta implementação de:

- Regras financeiras.
- Precisão decimal.
- Integridade transacional ACID.
- Idempotência.
- Controle de concorrência.
- Persistência e auditoria.
- Validação de entradas.
- Tratamento seguro de erros.

A decisão preserva o foco no escopo funcional e reduz a complexidade operacional do MVP.

### Requisitos de segurança preservados

O adiamento da autenticação não elimina as demais responsabilidades de segurança da aplicação.

Deverão ser preservados:

- Validação de entradas e contratos REST.
- Rejeição de payloads inválidos.
- Tratamento global e seguro de exceções.
- Ausência de exposição desnecessária de informações internas.
- Não inclusão de credenciais sensíveis no código-fonte.
- Configuração adequada de variáveis de ambiente.
- Logs sem exposição desnecessária de dados sensíveis.
- Restrição da exposição de serviços ao ambiente local de avaliação.
- Integridade financeira garantida pelo backend e pelo PostgreSQL.

O sistema não deverá ser considerado seguro para exposição pública apenas por possuir essas proteções básicas.

### Limitações e riscos

- A API não autenticará individualmente os operadores.
- Não haverá autorização baseada em identidade ou papéis.
- Endpoints de operações financeiras não terão proteção de acesso por usuário.
- Endpoints de atualização manual de câmbio não serão protegidos por autenticação.
- Não haverá rastreabilidade da identidade autenticada do operador.
- A aplicação não estará preparada para exposição pública ou utilização em produção.
- O ambiente de demonstração deverá permanecer isolado de redes e usuários não autorizados.

Os registros de auditoria poderão preservar informações técnicas e financeiras da operação, mas não deverão ser apresentados como comprovação da identidade de um operador autenticado.

### Evolução futura

Em um cenário de produção, avaliar:

- Spring Security OAuth2 Resource Server.
- Integração com provedor de identidade corporativo compatível com OIDC.
- Validação de tokens JWT.
- Autorização baseada em papéis e permissões.
- Proteção de endpoints financeiros.
- Registro da identidade autenticada do operador na auditoria.
- Políticas de segurança e gestão de credenciais apropriadas ao ambiente.

A adoção dessas funcionalidades dependerá de requisitos específicos de segurança e autorização.

### Rastreabilidade

- **DEC-001:** stack tecnológica.
- **DEC-002:** arquitetura modular.
- **DEC-007:** integridade e segurança transacional das liquidações.
- **SPEC.md:** requisitos funcionais e não funcionais obrigatórios.
- **AGENTS.md:** governança e controle de alterações.

**Status:** `APPROVED` — implementação completa de autenticação e autorização adiada para evolução futura.

---

## DEC-013 — Integração Externa de Câmbio e Resiliência Avançada (`APPROVED`)

### Contexto

O SRM Credit Engine necessita utilizar cotações cambiais para operações financeiras em USD.

O enunciado permite que as taxas sejam cadastradas manualmente, sem exigir integração com provedores externos de câmbio.

A consulta a serviços externos introduziria dependências de rede, disponibilidade de terceiros, tratamento de falhas distribuídas e mecanismos adicionais de resiliência.

Essas funcionalidades não são necessárias para atender ao escopo obrigatório do desafio.

### Decisão de adiamento

Adiar a integração com provedores externos reais de câmbio e os mecanismos avançados de resiliência associados a essas integrações.

Não implementar inicialmente:

- Consumo de APIs externas de cotação cambial.
- Integração com provedores financeiros de mercado.
- Atualização automática ou periódica de taxas.
- Circuit Breaker para provedores cambiais.
- Mecanismos distribuídos de retry.
- Estratégias de failover entre provedores.
- Cache distribuído de cotações.
- Sincronização automática com fontes externas.

A obtenção das taxas permanecerá baseada em **cadastro manual e consulta ao histórico persistido no PostgreSQL**.

### Funcionalidades obrigatórias preservadas

O adiamento da integração externa **não altera os requisitos do Currency Engine**.

Deverão ser preservados:

- Cadastro manual de cotações cambiais.
- Endpoint REST de atualização manual.
- Persistência do histórico de taxas.
- Utilização de `BigDecimal` para precisão decimal.
- Controle de vigência por `effectiveAt`.
- Seleção da cotação aplicável ao instante da operação.
- Preservação do histórico cambial.
- Rejeição de valores cambiais inválidos.
- Tratamento explícito da ausência de cotação aplicável.
- Utilização da cotação selecionada nas operações financeiras.
- Preservação de snapshot cambial imutável nas liquidações, conforme o `SPEC.md` e a DEC-007.

A conversão monetária deverá continuar respeitando a ordem de cálculo e arredondamento definida na DEC-003.

### Justificativa

A atualização manual de cotações atende ao requisito estabelecido para o desafio.

A introdução de integração externa aumentaria a complexidade técnica e operacional sem oferecer benefício proporcional ao escopo atual.

A solução proposta permite demonstrar:

- Modelagem e persistência de taxas.
- Controle temporal das cotações.
- Consulta de histórico.
- Seleção determinística da taxa aplicável.
- Precisão financeira.
- Rastreabilidade das operações.

O Currency Engine continuará independente da disponibilidade de serviços cambiais externos.

### Limitações e riscos

- As cotações dependerão de atualização manual.
- Uma cotação poderá permanecer disponível mesmo quando estiver defasada.
- Não haverá atualização automática baseada em condições de mercado.
- Não haverá comparação com taxas fornecidas por instituições externas.
- A aplicação não garantirá que a cotação cadastrada represente o valor atual de mercado.
- Não haverá failover para fontes alternativas de câmbio.

A ausência de cotação aplicável deverá produzir o erro de negócio previsto no contrato.

**Não será permitido utilizar silenciosamente uma cotação alternativa, taxa fixa ou valor presumido.**

Não será introduzida política de validade máxima das cotações sem aprovação de uma regra de negócio específica.

### Evolução futura

Quando houver necessidade de integração externa, avaliar:

- Adaptador para provedores cambiais.
- Cliente HTTP com timeout configurável.
- Circuit Breaker.
- Retry controlado com critérios explícitos.
- Sincronização periódica de cotações.
- Política de validade máxima das taxas.
- Monitoramento de falhas de integração.
- Estratégias de recuperação diante da indisponibilidade do provedor.

Essas funcionalidades deverão ser introduzidas conforme os requisitos de negócio e disponibilidade.

### Rastreabilidade

- **DEC-002:** arquitetura modular e separação de responsabilidades.
- **DEC-003:** precisão financeira e conversão monetária.
- **DEC-006:** câmbio manual, vigência e histórico.
- **DEC-007:** preservação do snapshot financeiro na liquidação.
- **TASK-006:** Currency Engine.
- **SPEC.md:** regras cambiais e critérios de aceite.

**Status:** `APPROVED` — integração externa de câmbio e mecanismos avançados de resiliência adiados para evolução futura.

---

## DEC-014 — Integração Bancária Externa (`APPROVED`)

### Contexto

O SRM Credit Engine deverá permitir a precificação e liquidação operacional de recebíveis, preservando integridade financeira, idempotência e histórico auditável.

Entretanto, o escopo do desafio não exige execução de transferências bancárias reais nem integração com sistemas externos de pagamento.

A liquidação será representada pelo fechamento interno de uma operação financeira, registrado no PostgreSQL.

Essa operação não deverá ser confundida com uma transferência efetiva de recursos entre instituições financeiras.

### Decisão de adiamento

Adiar a implementação de integrações bancárias externas e mecanismos associados à movimentação financeira real.

Não implementar inicialmente:

- Transferências Pix.
- Transferências TED.
- Integrações com o Sistema de Pagamentos Brasileiro (SPB).
- Transferências internacionais via SWIFT.
- Integrações com APIs bancárias de pagamento.
- Envio de ordens financeiras a instituições externas.
- Processamento de retornos bancários.
- Conciliação de pagamentos externos.
- Webhooks de confirmação bancária.
- Processamento assíncrono de liquidações externas.

A liquidação do MVP será realizada **exclusivamente no contexto transacional interno do SRM Credit Engine**.

### Funcionalidades obrigatórias preservadas

O adiamento da integração bancária não altera as regras de liquidação previstas no projeto.

Deverão ser preservados:

- Validação dos recebíveis elegíveis.
- Precificação financeira conforme as regras aprovadas.
- Liquidação integral do lote.
- Atomicidade transacional.
- Idempotência persistente.
- Controle de concorrência.
- Prevenção de dupla liquidação.
- Atualização consistente dos estados dos recebíveis.
- Persistência dos itens liquidados.
- Registro auditável da operação.
- Preservação de snapshots financeiros imutáveis.
- Rollback integral diante de falhas.

Essas garantias serão implementadas utilizando Spring e PostgreSQL, conforme a DEC-007.

### Significado do status SETTLED

No escopo do MVP, o status `SETTLED` representará uma **liquidação operacional concluída internamente no SRM Credit Engine**.

Esse status indica que a operação foi registrada e confirmada de acordo com as regras transacionais do sistema.

**O status `SETTLED` não comprova que houve transferência bancária, crédito em conta ou pagamento efetivo ao cedente.**

A aplicação deverá preservar essa distinção conceitual na documentação e nos fluxos de negócio.

### Justificativa

A integração com sistemas bancários reais exigiria mecanismos adicionais de segurança, autenticação, comunicação, tratamento de falhas, reconciliação e controle de idempotência externa.

Também introduziria questões operacionais que ultrapassam o escopo do desafio.

A decisão permite concentrar a implementação nas garantias fundamentais de engenharia financeira:

- Precisão dos cálculos.
- Integridade das operações.
- Consistência transacional.
- Idempotência.
- Controle de concorrência.
- Auditoria.
- Rastreabilidade histórica.

A solução continuará demonstrando o comportamento de liquidação exigido, sem executar movimentações financeiras reais.

### Limitações e riscos

- Nenhuma transferência bancária será executada.
- Não haverá confirmação externa de pagamento.
- O sistema não comprovará o recebimento de recursos pelo cedente.
- Não serão processados retornos bancários.
- Não haverá conciliação financeira com instituições externas.
- Não serão tratados eventos externos de rejeição, devolução ou falha de pagamento.
- O status interno da liquidação não representará necessariamente o estado de uma transferência em um sistema bancário real.

A aplicação deverá ser apresentada como uma solução de liquidação operacional interna para fins de demonstração técnica.

### Evolução futura

Em um cenário de integração bancária real, avaliar:

- Integração com APIs de instituições financeiras.
- Processamento de ordens de pagamento.
- Comunicação assíncrona quando necessária.
- Transactional Outbox para publicação confiável de eventos.
- Idempotência nas chamadas aos provedores externos.
- Controle de estados intermediários de pagamento.
- Recebimento de confirmações bancárias.
- Tratamento de falhas e rejeições externas.
- Reconciliação de pagamentos.
- Registros compensatórios quando aplicáveis.

A introdução desses mecanismos exigirá decisões específicas sobre consistência entre a transação local e os sistemas financeiros externos.

### Rastreabilidade

- **DEC-002:** monólito modular e transações locais.
- **DEC-007:** liquidação ACID, idempotência e concorrência.
- **DEC-010:** estratégia de testes.
- **SPEC.md:** regras de liquidação e auditoria.
- **TASKS.md:** tarefas relacionadas à liquidação.

**Status:** `APPROVED` — integração bancária externa e conciliação de pagamentos reais adiadas para evolução futura.

## DEC-015 — Liquidação Parcial e Estornos Financeiros (`APPROVED`)

### Contexto

O SRM Credit Engine deverá garantir liquidações financeiras consistentes, auditáveis e protegidas contra duplicidade, preservando a integridade dos recebíveis e dos registros históricos.

Embora sistemas financeiros possam oferecer liquidação parcial, cancelamento e estorno de operações, essas funcionalidades exigem regras adicionais de negócio, controle de estados, rastreabilidade e mecanismos específicos de compensação financeira.

Para o MVP, o escopo aprovado estabelece a **liquidação integral de lotes**, sem suporte a liquidações parciais ou reversão de operações já confirmadas.

### Decisão de adiamento

Adiar a implementação de:

- Liquidação parcial de lotes.
- Liquidação parcial individual de recebíveis.
- Liquidação de valores inferiores ao valor financeiro integral previsto para o recebível.
- Cancelamento de liquidações já confirmadas.
- Estorno de operações financeiras concluídas.
- Reversão automática de estados financeiros.
- Registros compensatórios de estorno.
- Fluxos de aprovação ou autorização de reversões financeiras.

A liquidação do MVP seguirá uma política de **processamento integral do lote (*all-or-nothing*)**, conforme estabelecido na DEC-007.

### Política de liquidação integral

Todos os recebíveis pertencentes a um lote deverão ser validados e processados dentro de uma única transação PostgreSQL.

A operação deverá garantir:

- Validação de todos os recebíveis do lote.
- Verificação dos estados financeiros aplicáveis.
- Processamento integral dos itens elegíveis.
- Persistência atômica dos efeitos financeiros.
- Registro dos itens de liquidação.
- Preservação do snapshot financeiro imutável.
- Controle de idempotência.
- Prevenção de dupla liquidação.
- Rollback integral diante de qualquer falha.

Caso qualquer item apresente erro que impeça a conclusão da liquidação, **nenhum efeito financeiro do lote poderá ser confirmado parcialmente**.

A operação deverá preservar a consistência dos estados e registros persistidos.

### Distinção entre atomicidade e liquidação parcial

A ausência de liquidação parcial é uma **decisão de negócio e de escopo**, não uma limitação imposta pelo princípio ACID.

A atomicidade garante que uma transação seja confirmada integralmente ou revertida integralmente.

Entretanto, uma transação atômica poderia, tecnicamente, implementar uma liquidação parcial caso existissem regras de negócio específicas para esse comportamento.

No SRM Credit Engine, a política aprovada estabelece que o lote deverá ser liquidado integralmente.

Da mesma forma, a ausência de estornos não representa uma impossibilidade decorrente da imutabilidade histórica.

Uma solução futura poderá realizar reversões por meio de **registros financeiros compensatórios**, preservando os registros originais.

### Operações posteriores e idempotência

A execução de operações posteriores deverá respeitar integralmente as garantias estabelecidas na DEC-007.

#### Repetição de liquidação concluída

Quando uma liquidação tiver sido confirmada com sucesso:

- A repetição da mesma requisição, utilizando a mesma `Idempotency-Key` e payload equivalente, deverá recuperar o resultado original.
- O sistema não deverá executar novamente os efeitos financeiros.
- O snapshot financeiro original deverá permanecer imutável.
- A resposta deverá respeitar o comportamento HTTP definido na DEC-007.

A utilização de uma nova `Idempotency-Key` não poderá permitir uma segunda liquidação de recebíveis já liquidados.

#### Novas operações de liquidação

Novas operações poderão envolver somente recebíveis elegíveis conforme as regras de estado e os critérios de negócio aprovados.

Recebíveis que já estejam com status `SETTLED` não poderão participar de outra liquidação integral confirmada.

A tentativa de liquidar novamente um recebível já liquidado, utilizando uma intenção distinta, deverá produzir o conflito previsto na DEC-007.

#### Correção de operações

Operações rejeitadas ou revertidas integralmente por rollback não representam liquidações financeiras confirmadas.

Uma nova tentativa deverá respeitar as regras de validação, estado e idempotência aplicáveis.

Para liquidações já confirmadas, o MVP não oferecerá mecanismos de alteração, cancelamento ou estorno.

**Correções de liquidações concluídas exigirão uma funcionalidade específica de estorno ou compensação financeira, fora do escopo atual.**

Não será permitido modificar diretamente os registros históricos para simular uma reversão.

### Justificativa

A implementação de liquidações parciais e estornos exigiria regras adicionais relacionadas a:

- Distribuição de valores entre liquidações.
- Controle de saldos remanescentes.
- Novos estados financeiros.
- Atualização de snapshots e saldos.
- Reconciliação de operações.
- Idempotência de estornos.
- Tratamento de concorrência em reversões.
- Preservação de registros originais e compensatórios.
- Auditoria de operações de correção.

Esses mecanismos aumentariam a complexidade da solução sem serem necessários para os requisitos obrigatórios do desafio.

A política de liquidação integral permite concentrar a implementação na correta aplicação de:

- Transações ACID.
- Integridade financeira.
- Idempotência.
- Controle de concorrência.
- Prevenção de dupla liquidação.
- Persistência de snapshots imutáveis.
- Auditoria.

A decisão preserva o foco no escopo funcional do MVP e reduz o risco de introduzir comportamentos financeiros não especificados.

### Limitações e riscos

- Um recebível inválido poderá impedir a conclusão do lote inteiro.
- Não será possível confirmar apenas os itens válidos de um lote parcialmente inválido.
- Não será possível liquidar parcialmente um recebível.
- Liquidações confirmadas não poderão ser canceladas ou estornadas no MVP.
- Não haverá mecanismo de reversão financeira automática.
- Correções de liquidações concluídas não poderão ser realizadas por simples alteração dos registros persistidos.
- Não haverá cálculo de saldo remanescente decorrente de liquidações parciais.
- A ausência de estornos limita a capacidade de corrigir operações financeiras já confirmadas.

Essas limitações são consideradas aceitáveis para o ambiente de demonstração e para os requisitos atuais do desafio.

### Garantias preservadas

O adiamento dessas funcionalidades não altera as obrigações estabelecidas na DEC-007.

Deverão permanecer garantidos:

- Atomicidade transacional.
- Rollback integral.
- Idempotência persistente.
- Precedência da verificação de idempotência sobre a validação do estado dos recebíveis.
- Prevenção de dupla liquidação.
- Tratamento de conflitos de estado.
- Controle de concorrência.
- Integridade referencial.
- Persistência dos itens liquidados.
- Preservação do snapshot financeiro original.
- Imutabilidade do histórico financeiro.

Nenhuma simplificação de escopo poderá comprometer essas garantias.

### Evolução futura

Caso surjam requisitos para liquidações parciais ou estornos, avaliar:

#### Liquidação parcial

- Regras de elegibilidade para liquidações parciais.
- Controle de saldo financeiro remanescente.
- Novos estados de recebíveis.
- Registro individualizado das parcelas liquidadas.
- Regras de cálculo e arredondamento para valores parciais.
- Controle de concorrência sobre o saldo disponível.
- Restrições para impedir liquidações acima do saldo remanescente.

#### Estornos e compensações financeiras

- Operações de estorno explicitamente identificadas.
- Registros financeiros compensatórios.
- Preservação imutável da liquidação original.
- Relacionamento entre liquidação e estorno.
- Idempotência específica para operações de reversão.
- Controle de autorização e auditoria.
- Regras de elegibilidade para estorno.
- Tratamento de concorrência entre liquidações e reversões.
- Reconciliação dos efeitos financeiros.

A evolução deverá preservar a rastreabilidade histórica e evitar modificações destrutivas nos registros financeiros originais.

Essas funcionalidades dependerão de requisitos específicos, atualização das decisões arquiteturais e aprovação conforme o `AGENTS.md`.

### Rastreabilidade

- **DEC-002:** arquitetura modular e separação de responsabilidades.
- **DEC-007:** liquidação ACID, idempotência e concorrência.
- **DEC-008:** persistência e integridade relacional.
- **DEC-010:** estratégia de testes e validação.
- **DEC-014:** adiamento de integrações bancárias externas.
- **SPEC.md:** regras financeiras, estados e critérios de liquidação.
- **AGENTS.md:** governança e controle de alterações.
- **TASKS.md:** planejamento das funcionalidades de liquidação.

**Status:** `APPROVED` — liquidação parcial, cancelamento e estornos financeiros adiados para evolução futura.

## DEC-016 — Microsserviços, Mensageria e Arquitetura Orientada a Eventos (`APPROVED`)

### Contexto

O SRM Credit Engine necessita implementar regras financeiras, persistência relacional, processamento de liquidações e consultas auditáveis, garantindo consistência transacional, idempotência e controle de concorrência.

Embora arquiteturas distribuídas, microsserviços e mensageria sejam alternativas utilizadas em sistemas financeiros de maior escala, o escopo do desafio não exige processamento distribuído ou integração assíncrona entre sistemas independentes.

A introdução dessas tecnologias aumentaria a complexidade de implementação, operação e testes, sem apresentar benefícios proporcionais aos requisitos atuais.

### Decisão de adiamento

Adiar a adoção de **microsserviços, mensageria e arquitetura orientada a eventos** para o processamento principal do SRM Credit Engine.

Não implementar inicialmente:

- Decomposição da aplicação em microsserviços independentes.
- Comunicação entre serviços por Kafka ou RabbitMQ.
- Processamento distribuído de liquidações.
- Arquitetura orientada a eventos (EDA).
- Orquestração ou coreografia de transações distribuídas.
- Padrão Saga para coordenação de transações.
- Transactional Outbox para publicação de eventos externos.
- Consumidores assíncronos de eventos financeiros.
- Mecanismos de consistência eventual entre serviços independentes.
- Infraestrutura dedicada de mensageria.

A aplicação permanecerá organizada como um **monólito modular desenvolvido em Spring Boot**, utilizando PostgreSQL como banco de dados relacional.

A separação entre funcionalidades seguirá a arquitetura *Package by Feature*, conforme estabelecido na DEC-002.

### Arquitetura preservada

A decisão de não utilizar microsserviços não elimina a necessidade de uma arquitetura bem estruturada.

Deverão ser preservados:

- Organização modular por funcionalidade.
- Separação entre responsabilidades de API, aplicação, domínio e persistência.
- Isolamento do domínio financeiro em Java puro.
- Reutilização dos componentes financeiros existentes.
- Baixo acoplamento entre funcionalidades.
- Alta coesão dos módulos.
- Contratos REST definidos e versionados.
- Testes automatizados por responsabilidade.
- Persistência relacional com PostgreSQL.
- Evolução do schema por Flyway.
- Transações locais ACID para operações financeiras.
- Controle de idempotência e concorrência no banco de dados.
- Preservação do histórico auditável e dos snapshots financeiros.

O monólito modular deverá permitir evolução funcional sem exigir comunicação distribuída entre seus componentes.

**A decisão de adiamento não autoriza a introdução de dependências circulares entre módulos ou a concentração de todas as regras de negócio em uma única camada.**

### Justificativa

A utilização de um monólito modular é adequada às características do desafio porque permite:

- Manter as operações financeiras em transações locais.
- Utilizar as garantias ACID do PostgreSQL.
- Simplificar o controle de concorrência.
- Reduzir a complexidade da idempotência.
- Facilitar o desenvolvimento e a execução dos testes.
- Diminuir as dependências de infraestrutura.
- Simplificar a execução local por Docker Compose.
- Preservar a rastreabilidade das operações financeiras.

A introdução de microsserviços exigiria mecanismos adicionais para lidar com comunicação entre serviços, falhas de rede, consistência distribuída e observabilidade.

Essas responsabilidades não são necessárias para atender aos requisitos atuais.

A arquitetura escolhida oferece um equilíbrio adequado entre organização, confiabilidade, simplicidade operacional e possibilidade de evolução futura.

### Limitações e riscos

- As operações permanecerão predominantemente síncronas.
- A disponibilidade das funcionalidades dependerá da aplicação e de suas dependências locais, especialmente o PostgreSQL.
- Não haverá publicação assíncrona de eventos financeiros para sistemas externos.
- Não haverá processamento distribuído de liquidações.
- A aplicação não será dimensionada inicialmente para processamento distribuído em alta escala.
- Funcionalidades que futuramente exigirem integração assíncrona poderão demandar adaptações arquiteturais.
- O monólito exigirá disciplina para preservar as fronteiras entre funcionalidades.

A escolha pelo monólito modular não representa garantia automática de desempenho ou escalabilidade.

Essas características deverão ser avaliadas conforme os requisitos e volumes reais de utilização.

### Evolução futura

Caso surjam requisitos de integração assíncrona, processamento distribuído ou escalabilidade independente de funcionalidades, avaliar:

- Publicação de eventos de domínio para integrações externas.
- Transactional Outbox.
- Kafka ou RabbitMQ.
- Consumidores idempotentes.
- Processamento assíncrono de operações não transacionais.
- Mecanismos de retry e tratamento de mensagens.
- Estratégias de consistência entre serviços.
- Extração gradual de módulos para serviços independentes.
- Observabilidade distribuída.
- Padrões de coordenação transacional quando necessários.

A adoção dessas tecnologias deverá ser fundamentada em requisitos concretos, considerando impactos sobre consistência financeira, idempotência, operação e testes.

Não realizar a extração de módulos para microsserviços apenas por preferência arquitetural.

### Rastreabilidade

- **DEC-001:** stack tecnológica.
- **DEC-002:** arquitetura de monólito modular e isolamento do domínio financeiro.
- **DEC-007:** liquidação ACID, idempotência e concorrência.
- **DEC-008:** persistência e integridade relacional.
- **DEC-010:** estratégia de testes e validação.
- **DEC-014:** adiamento de integrações bancárias externas.
- **SPEC.md:** requisitos funcionais e regras financeiras.
- **AGENTS.md:** governança e controle de alterações.

**Status:** `APPROVED` — adoção de microsserviços, mensageria e arquitetura orientada a eventos adiada para evolução futura.

---

## DEC-017 — Cache Distribuído, Escalabilidade e Observabilidade Avançada (`APPROVED`)

### Contexto

O SRM Credit Engine necessita garantir precisão financeira, integridade transacional, rastreabilidade histórica e consultas eficientes aos dados persistidos.

Embora recursos como cache distribuído, sharding, tracing distribuído e plataformas avançadas de observabilidade sejam utilizados em sistemas de alta escala, o desafio não apresenta requisitos que justifiquem inicialmente essa infraestrutura.

A adoção prematura dessas tecnologias aumentaria a complexidade operacional e poderia introduzir riscos adicionais de consistência, especialmente nas operações financeiras e na seleção de cotações cambiais.

### Decisão de adiamento

Adiar a introdução de **cache distribuído, mecanismos avançados de escalabilidade e infraestrutura dedicada de observabilidade**.

Não implementar inicialmente:

- Redis como mecanismo de cache distribuído.
- Cache distribuído de cotações cambiais.
- Locks distribuídos.
- Sharding de banco de dados.
- Particionamento de dados sem necessidade comprovada.
- Tracing distribuído.
- Infraestrutura dedicada de métricas.
- Dashboards avançados de monitoramento.
- Monitoramento distribuído entre múltiplos serviços.
- Plataformas externas de observabilidade.
- Sistemas avançados de alertas operacionais.
- Estratégias complexas de escalabilidade horizontal.

O PostgreSQL continuará sendo a fonte de verdade para persistência, consultas e garantias de integridade.

A estratégia inicial de desempenho será baseada em **consultas adequadas, paginação, índices justificados e otimizações orientadas por evidências**.

### Recursos essenciais preservados

O adiamento de infraestrutura avançada não elimina os requisitos básicos de desempenho, diagnóstico e qualidade operacional.

Deverão ser preservados:

- Tratamento global e padronizado de exceções.
- Logs suficientes para diagnosticar falhas relevantes.
- Ausência de informações sensíveis desnecessárias nos logs.
- Consultas eficientes ao PostgreSQL.
- Paginação executada no banco de dados.
- Ordenação determinística dos resultados.
- Índices associados às constraints necessárias.
- Criação de índices adicionais quando justificados pelas consultas.
- Prevenção de consultas N+1 nos fluxos relevantes.
- Controle de transações e integridade financeira.
- Testes automatizados de persistência e concorrência.
- Configuração adequada para execução local com Docker Compose.

Poderá ser utilizado um **endpoint simples de healthcheck**, caso seja útil para verificar a inicialização e a disponibilidade da aplicação durante a execução local.

A inclusão desse endpoint não deverá exigir a introdução de uma infraestrutura completa de monitoramento.

### Estratégia de desempenho

O desempenho da aplicação deverá ser tratado de forma incremental e orientada por evidências.

#### Persistência

- Utilizar consultas parametrizadas.
- Evitar carregamento desnecessário de entidades.
- Priorizar projeções para consultas de relatórios quando apropriado.
- Executar filtros, ordenação e paginação diretamente no PostgreSQL.
- Avaliar índices conforme os padrões reais de consulta.
- Evitar índices compostos redundantes.
- Utilizar `EXPLAIN (ANALYZE, BUFFERS)` quando necessário para investigar consultas relevantes.

#### Processamento financeiro

- Preservar os cálculos financeiros utilizando `BigDecimal`.
- Evitar cálculos financeiros duplicados entre camadas.
- Reutilizar os componentes de domínio existentes.
- Não introduzir cache de resultados financeiros sem necessidade e regras explícitas de consistência.
- Preservar os snapshots financeiros imutáveis das liquidações.

#### Currency Engine

- Consultar a cotação vigente conforme as regras temporais aprovadas.
- Preservar o histórico de cotações.
- Não introduzir cache cambial distribuído.
- Não utilizar taxas potencialmente desatualizadas por causa de invalidação inadequada de cache.
- Não substituir silenciosamente a cotação aplicável por valores em memória ou taxas presumidas.

A decisão de não utilizar cache cambial preserva a simplicidade e evita uma nova responsabilidade de sincronização entre cache e PostgreSQL.

### Justificativa

O escopo atual não exige arquitetura distribuída de alta escala.

O PostgreSQL oferece recursos suficientes para as necessidades iniciais de persistência, integridade, controle transacional e consultas do SRM Credit Engine.

A utilização de consultas adequadas, índices justificados e paginação permite atender ao escopo inicial sem introduzir dependências adicionais.

A ausência de Redis e cache distribuído também evita a necessidade de implementar:

- Estratégias de invalidação.
- Sincronização entre cache e banco.
- Controle de consistência de cotações.
- Políticas de expiração de dados financeiros.
- Tratamento de indisponibilidade de serviços de cache.

Da mesma forma, a ausência de infraestrutura avançada de observabilidade reduz custos e dependências operacionais sem impedir a utilização de logs e mecanismos básicos de diagnóstico.

A solução deverá priorizar **corretude financeira, integridade dos dados e simplicidade operacional**.

### Limitações e riscos

- Não haverá dashboards avançados de métricas financeiras.
- Não haverá tracing distribuído.
- Não haverá alertas automatizados de infraestrutura.
- Não haverá cache distribuído para reduzir consultas ao banco.
- O PostgreSQL será responsável diretamente pelas operações de persistência e consulta.
- A arquitetura não foi dimensionada inicialmente para grandes volumes de dados.
- Não haverá garantia de desempenho sob cargas elevadas ainda não medidas.
- Consultas poderão exigir otimizações conforme o volume de dados aumentar.
- A ausência de métricas avançadas limitará a capacidade de análise operacional em ambientes de maior escala.

Essas limitações são consideradas aceitáveis para o ambiente local de demonstração e para os requisitos atuais do desafio.

**O adiamento de observabilidade avançada não autoriza a ausência de logs úteis ou o tratamento inadequado de erros.**

### Evolução futura

Quando surgirem requisitos concretos de desempenho, disponibilidade ou monitoramento, avaliar:

#### Observabilidade

- Micrometer para instrumentação de métricas.
- Prometheus para coleta de métricas.
- Grafana para visualização de indicadores.
- OpenTelemetry para tracing.
- Monitoramento de latência e erros.
- Métricas de operações financeiras.
- Alertas operacionais baseados em indicadores relevantes.

#### Desempenho e escalabilidade

- Otimização de consultas baseada em medições.
- Revisão de índices PostgreSQL.
- Ajustes de pool de conexões.
- Particionamento de tabelas quando necessário.
- Estratégias de cache com regras explícitas de consistência.
- Redis quando houver benefício comprovado.
- Avaliação de escalabilidade horizontal.
- Evolução da infraestrutura conforme requisitos de carga.

#### Consistência financeira

Qualquer mecanismo futuro de cache, particionamento ou distribuição deverá considerar:

- Integridade transacional.
- Consistência dos dados financeiros.
- Imutabilidade do histórico.
- Seleção correta das cotações cambiais.
- Idempotência.
- Controle de concorrência.
- Auditabilidade das operações.

A introdução dessas tecnologias dependerá de justificativa técnica, evidências de necessidade e aprovação arquitetural.

### Rastreabilidade

- **DEC-001:** stack tecnológica.
- **DEC-002:** arquitetura modular.
- **DEC-006:** Currency Engine e seleção temporal de cotações.
- **DEC-007:** liquidação ACID, idempotência e concorrência.
- **DEC-008:** persistência, consultas e índices.
- **DEC-010:** testes e validação.
- **DEC-016:** adiamento de arquitetura distribuída.
- **SPEC.md:** requisitos financeiros e não funcionais.
- **AGENTS.md:** governança e controle de mudanças.

**Status:** `APPROVED` — cache distribuído, mecanismos avançados de escalabilidade e infraestrutura dedicada de observabilidade adiados para evolução futura.