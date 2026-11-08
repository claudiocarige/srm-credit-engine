# TASK-007 — Plano Técnico de Implementação

## 1. Identificação
- **Tarefa:** `TASK-007` — API REST de Simulação e Integração OpenAPI/Swagger UI.
- **Objetivo:** disponibilizar o endpoint stateless de simulação de precificação de recebíveis em tempo real (`POST /api/v1/simulations`) estritamente aderente ao contrato oficial (`docs/openapi/srm-credit-engine.yaml`), integrando o motor financeiro puro (`PricingEngine`, `CommercialTermCalculator`, `CurrencyConverter`) e o Currency Engine (`ExchangeRateService`), com controle temporal único via `Clock` UTC injetável, rejeição de propriedades JSON desconhecidas e de coerções numéricas em strings decimais, tratamento padronizado de erros via `ProblemDetail` e disponibilização do Swagger UI referenciando exclusivamente o contrato OpenAPI oficial como fonte única de verdade.
- **Status do planejamento:** `PLANO APROVADO (READY) — AGUARDANDO INÍCIO DA IMPLEMENTAÇÃO`.
- **Referências:**
  - `SPEC.md` (§§1, 2, 3 e 5);
  - `DECISIONS.md`:
    - Decisões arquiteturais e financeiras `APPROVED`: `DEC-001` (Stack Java 21, Spring Boot, JPA/Hibernate, PostgreSQL, Flyway e Springdoc OpenAPI), `DEC-002` (Monólito modular e isolamento do domínio financeiro), `DEC-003` (Precisão decimal `DECIMAL128` e arredondamento `HALF_EVEN`), `DEC-004` (Strategy Pattern aberto e composição), `DEC-005` (`dueDate` → meses comerciais na camada de aplicação via `Clock` injetável), `DEC-006` (Câmbio manual por `effectiveAt` e vigência consistente) e `DEC-010` (Estratégia de testes orientada aos riscos do negócio);
    - Funcionalidades formalmente adiadas (`DEFERRED`, com decisão de adiamento aprovada em `DECISIONS.md`): `DEC-012` (Autenticação e autorização completas adiadas), `DEC-013` (Integração externa de câmbio e Circuit Breaker adiados), `DEC-016` (Microsserviços, mensageria e EDA adiados) e `DEC-017` (Cache distribuído e observabilidade avançada adiados);
  - `docs/contracts.md` (§§1.1, 1.2, 1.3, 4.2, 4.3);
  - `docs/openapi/srm-credit-engine.yaml` (path `/api/v1/simulations`; schemas `SimulationRequest`, `SimulationResponse`, `ReceivableType`, `CurrencyCode`, `FaceValueBrlInput`, `MonetaryAmountString`, `MonthlyRateString`, `NullableExchangeRateString`, `ProblemDetail`);
  - `AGENTS.md` (`G-01` a `G-04`, §12 Code Review);
  - `TASKS.md` (`TASK-007`);
  - `docs/task-plans/TASK-003.md`, `docs/task-plans/TASK-004.md` e `docs/task-plans/TASK-006.md`.
- **Dependências:** `TASK-002` (`DONE`), `TASK-003` (`DONE`), `TASK-004` (`DONE`), `TASK-006` (`DONE`).
- **Ferramenta e modelo elaborador:** Google Antigravity — Gemini 4 Argon (Medium).

---

## 2. Diagnóstico do Estado Atual do Projeto

A inspeção do workspace após a conclusão da `TASK-006` revelou o seguinte panorama:

| Componente | Situação no Workspace | Alinhamento com TASK-007 |
|---|---|---|
| **Motor de Precificação BRL (`PricingEngine`)** | Implementado em `pricing.domain` (TASK-003), com `TradeBillPricingStrategy` (`0.015000`) e `PostDatedCheckPricingStrategy` (`0.025000`), `CompoundInterestCalculator` e validação de limites (`0.01` a `999999999.99`). | **Pronto e validado** — classe Java pura; necessita apenas de exposição via `@Bean` em classe de configuração Spring para injeção no serviço de aplicação. |
| **Cálculo de Prazo Comercial (`CommercialTermCalculator`)** | Implementado em `pricing.domain.calculation` (TASK-004). Calcula `ceil(days / 30)` e rejeita `dueDate <= operationDate`. | **Pronto e validado** — classe Java pura; será exposta como `@Bean` e chamada pelo `SimulationService`. |
| **Conversor Cambial (`CurrencyConverter`)** | Implementado em `pricing.domain.calculation` (TASK-004). Converte `presentValueBrl` (escala `<= 2`) para `USD` com `DECIMAL128` e `HALF_EVEN` (2 casas). | **Pronto e validado** — classe Java pura; será exposta como `@Bean` e chamada pelo `SimulationService` quando `paymentCurrency == USD`. |
| **Currency Engine (`ExchangeRateService`)** | Implementado em `exchangerate.application` (TASK-006). Disponibiliza `getExchangeRate(CurrencyCode currency, Instant operationInstant)`, lançando `ExchangeRateUnavailableException` quando não há cotação elegível. | **Pronto e validado** — assinatura já desenhada para reutilização direta pelo `SimulationService` passando o instante único da simulação. |
| **Relógio (`ClockConfig`)** | Implementado em `config.ClockConfig` (TASK-006), provendo `Clock.systemUTC()`. | **Pronto e validado**. |
| **Configuração Jackson (`application.yaml`)** | Já possui `spring.jackson.deserialization.fail-on-unknown-properties: true`. | **Pronta** — garante `additionalProperties: false` em `SimulationRequest`. |
| **Deserializador Estrito (`StrictStringDeserializer`)** | Localizado atualmente em `exchangerate.web.dto.StrictStringDeserializer`. | **Ajuste de pacote** — será movido para `common.web.StrictStringDeserializer` para permitir reutilização limpa entre `exchangerate` e `simulation` sem acoplamento cruzado entre pacotes de features. |
| **Tratamento Global de Erros (`GlobalExceptionHandler`)** | Implementado em `common.web` (TASK-006). Já trata `MethodArgumentNotValidException` (`400`), `HttpMessageNotReadableException` (`400`), `ExchangeRateUnavailableException` (`422`), `404`, `405` e `500`. | **Complementar** — necessita adicionar handler específico para `InvalidDueDateException` retornando `HTTP 422` com `errorCode: INVALID_DUE_DATE`. |
| **Taxa Base Mensal (`baseRateMonthly`)** | Ainda não parametrizada no `application.yaml`. | **A parametrizar** — adicionar `srm.pricing.base-rate-monthly: "0.010000"` conforme `SPEC.md` §3.1. |
| **Módulo de Simulação e Swagger UI** | Pacote `simulation` e exposição do contrato OpenAPI/Swagger UI ainda inexistentes. | **Escopo central desta tarefa**. |

---

## 3. Abordagem Técnica e Arquitetura Proposta

### 3.1. Organização de Pacotes (Package by Feature + Simplicidade Pragmática)
Seguindo a arquitetura aprovada (`DEC-002`) e os princípios **KISS**, **YAGNI** e **DRY**, o módulo `simulation` será estruturado sem interfaces redundantes ou mappers complexos, mantendo separação estrita entre DTOs HTTP (`web.dto`), serviço de aplicação (`application`) e núcleo financeiro puro (`pricing.domain`):

```text
com.claudiocarige.backendcreditengine
├── config/
│   ├── ClockConfig.java                          (Existente — Bean java.time.Clock UTC)
│   └── PricingConfig.java                        (NOVO — Expõe beans PricingEngine, CommercialTermCalculator e CurrencyConverter)
├── common/web/
│   ├── GlobalExceptionHandler.java               (ALTERAR — Adicionar handler InvalidDueDateException -> 422 INVALID_DUE_DATE)
│   └── StrictStringDeserializer.java             (MOVER de exchangerate.web.dto para common.web)
└── simulation/
    ├── application/
    │   ├── SimulationService.java                (NOVO — Caso de uso stateless de simulação em BRL e USD)
    │   ├── SimulationResult.java                 (NOVO — Record imutável de saída da camada de aplicação)
    │   └── exception/
    │       └── InvalidDueDateException.java      (NOVO — Exceção negocial para dueDate <= operationDate -> HTTP 422)
    └── web/
        ├── SimulationController.java             (NOVO — Controller REST para POST /api/v1/simulations)
        └── dto/
            ├── SimulationRequest.java            (NOVO — DTO de request com Bean Validation e StrictStringDeserializer)
            └── SimulationResponse.java           (NOVO — DTO de response com os 14 atributos obrigatórios do OpenAPI)
```

### 3.2. Responsabilidades dos Componentes e Direção das Dependências
- **`PricingConfig` (`config`):** Registra como beans gerenciados pelo Spring as classes puras `PricingEngine`, `CommercialTermCalculator` e `CurrencyConverter`. Isso preserva o pacote `pricing.domain` 100% livre de dependências do framework (`DEC-002`) e permite a reutilização dos mesmos beans na futura `TASK-009` (Liquidação).
- **`SimulationService` (`simulation.application`):**
  - Não recebe `SimulationRequest` nem retorna `SimulationResponse`.
  - Assinatura pública:
    ```java
    public SimulationResult simulate(
        ReceivableType type,
        BigDecimal faceValueBrl,
        LocalDate dueDate,
        CurrencyCode paymentCurrency
    )
    ```
  - **Fluxo determinístico e atômico em memória:**
    1. Captura **uma única vez** o instante de referência: `Instant simulatedAt = clock.instant();`.
    2. Obtém a data de operação em UTC: `LocalDate operationDate = LocalDate.ofInstant(simulatedAt, ZoneOffset.UTC);`.
    3. Valida a elegibilidade temporal: se `!dueDate.isAfter(operationDate)`, lança `InvalidDueDateException(operationDate, dueDate)` antes de invocar o domínio.
    4. Calcula o prazo comercial em meses inteiros: `int termMonths = commercialTermCalculator.calculateTermMonths(operationDate, dueDate);`.
    5. Executa a precificação em BRL: `PricingResult pricing = pricingEngine.calculate(new PricingInput(type, faceValueBrl, baseRateMonthly, termMonths));`.
    6. Se `paymentCurrency == CurrencyCode.BRL`:
       - Não realiza qualquer consulta ao `ExchangeRateService` (operação 100% em memória, sem tocar no banco).
       - Define `exchangeRateApplied = null`, `exchangeRateEffectiveAt = null` e `paidAmount = pricing.presentValueBrl()`.
    7. Se `paymentCurrency == CurrencyCode.USD`:
       - Consulta a cotação vigente para o **mesmo instante** `simulatedAt`: `ExchangeRate rate = exchangeRateService.getExchangeRate(CurrencyCode.USD, simulatedAt);`.
       - Caso não exista cotação com `effectiveAt <= simulatedAt`, o `ExchangeRateService` lança `ExchangeRateUnavailableException` (traduzida em `HTTP 422 EXCHANGE_RATE_UNAVAILABLE`).
       - Converte o valor presente BRL já arredondado para USD: `BigDecimal paidAmountUsd = currencyConverter.convertToUsd(pricing.presentValueBrl(), rate.getRateBrl());`.
       - Define `exchangeRateApplied = rate.getRateBrl()`, `exchangeRateEffectiveAt = rate.getEffectiveAt()` e `paidAmount = paidAmountUsd`.
    8. Retorna `SimulationResult` imutável.
- **`SimulationController` (`simulation.web`):**
  - Possui **responsabilidade exclusiva** pela API REST de simulação (`POST /api/v1/simulations`).
  - Recebe `@Valid @RequestBody SimulationRequest request`.
  - Converte `request.faceValueBrl()` para `BigDecimal` e delega para `simulationService.simulate(...)`.
  - Converte `SimulationResult` para `SimulationResponse` via `SimulationResponse.from(result)` e retorna `HTTP 200 OK`.
  - **Isolamento de infraestrutura:** A configuração e o redirecionamento do Swagger UI permanecem exclusivamente na infraestrutura/configuração HTTP (Springdoc / `application.yaml`), sem misturar rotas de documentação na lógica do controller.

### 3.3. Alternativas Consideradas
1. **Acoplar `SimulationRequest` / `SimulationResponse` diretamente no `SimulationService`:** *Rejeitada*, pois violaria a separação entre a camada HTTP e a camada de aplicação consolidada na `TASK-006`.
2. **Gerar especificação OpenAPI dinamicamente por reflexão de anotações:** *Rejeitada*, pois o `TASKS.md` determina expressamente a política *Contract First* e proíbe estabelecer uma segunda especificação concorrente e potencialmente divergente de `docs/openapi/srm-credit-engine.yaml`.

---

## 4. Contrato HTTP e Endpoint REST

### `POST /api/v1/simulations` — Simular Precificação de Recebível em Tempo Real
- **Operação OpenAPI:** `simulateReceivablePricing`.
- **Natureza:** Stateless (não cria registros em `receivables`, `settlements` ou `settlement_items`, não exige `Idempotency-Key` e não realiza trava de cotação).
- **Request Body (`SimulationRequest`):**
  - `additionalProperties: false` garantido pela configuração `spring.jackson.deserialization.fail-on-unknown-properties: true`.
  - `type`: obrigatório (`@NotNull`), valores aceitos: `DUPLICATA_MERCANTIL`, `CHEQUE_PRE_DATADO`.
  - `faceValueBrl`: obrigatório (`@NotBlank`), desserializado via `@JsonDeserialize(using = StrictStringDeserializer.class)` e validado pela regex contratual `FaceValueBrlInput`:
    `^(?:0\.(?:0[1-9]|[1-9][0-9]?)|[1-9][0-9]{0,8}(?:\.[0-9]{1,2})?)$`
  - `dueDate`: obrigatório (`@NotNull`), `LocalDate` no formato `YYYY-MM-DD`.
  - `paymentCurrency`: obrigatório (`@NotNull`), valores aceitos: `BRL`, `USD`.
- **Respostas:**
  - `200 OK` (`application/json`): Retorna `SimulationResponse` com os 14 atributos obrigatórios.
  - `400 Bad Request` (`application/problem+json`): `ProblemDetail` com `errorCode: VALIDATION_ERROR` para propriedades desconhecidas, coerção numérica em `faceValueBrl`, violação de regex/escala, campos nulos ou formato inválido de data/enum.
  - `422 Unprocessable Entity` (`application/problem+json`):
    - `errorCode: INVALID_DUE_DATE` quando `dueDate <= operationDate`.
    - `errorCode: EXCHANGE_RATE_UNAVAILABLE` quando `paymentCurrency == USD` e não houver cotação vigente em `simulatedAt`.
  - `500 Internal Server Error` (`application/problem+json`): `ProblemDetail` com `errorCode: INTERNAL_ERROR`.

---

## 5. Estratégia de Configuração Jackson e Validação Estrita

1. **Rejeição de propriedades desconhecidas (`additionalProperties: false`):**
   - Já ativa em `application.yaml` (`spring.jackson.deserialization.fail-on-unknown-properties: true`). Qualquer campo extra no JSON de `SimulationRequest` dispara `HttpMessageNotReadableException`, mapeada pelo `GlobalExceptionHandler` para `HTTP 400` + `errorCode: VALIDATION_ERROR`, sem expor classes internas.
2. **Rejeição de números JSON em `faceValueBrl`:**
   - O uso de `@JsonDeserialize(using = StrictStringDeserializer.class)` no campo `faceValueBrl` garante que payloads como `{"faceValueBrl": 100000.00}` sejam rejeitados na desserialização com `HTTP 400 VALIDATION_ERROR`.
3. **Compartilhamento limpo do `StrictStringDeserializer`:**
   - A classe `StrictStringDeserializer` será movida de `exchangerate.web.dto` para `common.web`, servindo tanto a `CreateExchangeRateRequest` (TASK-006) quanto a `SimulationRequest` (TASK-007) e futuras requisições com strings decimais.

---

## 6. Modelagem dos DTOs e Formatação Canônica

### 6.1. `SimulationRequest`
```java
public record SimulationRequest(
    @NotNull(message = "type is required")
    ReceivableType type,

    @NotBlank(message = "faceValueBrl is required")
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Pattern(
        regexp = "^(?:0\\.(?:0[1-9]|[1-9][0-9]?)|[1-9][0-9]{0,8}(?:\\.[0-9]{1,2})?)$",
        message = "faceValueBrl must be a decimal string between 0.01 and 999999999.99 with at most 2 decimal places"
    )
    String faceValueBrl,

    @NotNull(message = "dueDate is required")
    LocalDate dueDate,

    @NotNull(message = "paymentCurrency is required")
    CurrencyCode paymentCurrency
) {}
```

### 6.2. `SimulationResponse`
Todos os 14 campos obrigatórios definidos no schema `SimulationResponse` do OpenAPI:
```java
public record SimulationResponse(
    Instant simulatedAt,
    LocalDate operationDate,
    ReceivableType type,
    LocalDate dueDate,
    int termMonths,
    String faceValueBrl,
    String baseRateMonthly,
    String spreadMonthly,
    String presentValueBrl,
    String discountBrl,
    CurrencyCode paymentCurrency,
    String exchangeRateApplied,
    Instant exchangeRateEffectiveAt,
    String paidAmount
) {
    public static SimulationResponse from(SimulationResult result) {
        return new SimulationResponse(
            result.simulatedAt(),
            result.operationDate(),
            result.type(),
            result.dueDate(),
            result.termMonths(),
            formatMonetary(result.faceValueBrl()),
            formatRate(result.baseRateMonthly()),
            formatRate(result.spreadMonthly()),
            formatMonetary(result.presentValueBrl()),
            formatMonetary(result.discountBrl()),
            result.paymentCurrency(),
            result.exchangeRateApplied() != null ? formatRate(result.exchangeRateApplied()) : null,
            result.exchangeRateEffectiveAt(),
            formatMonetary(result.paidAmount())
        );
    }

    private static String formatMonetary(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String formatRate(BigDecimal value) {
        return value.setScale(6, RoundingMode.UNNECESSARY).toPlainString();
    }
}
```
- **Nota sobre campos nulos:** Em simulações `BRL`, `exchangeRateApplied` e `exchangeRateEffectiveAt` são serializados explicitamente como `null` no JSON, cumprindo a lista `required` de 14 atributos do schema `SimulationResponse` no OpenAPI.

---

## 7. Golden Cases e Cenários Integrados Equivalentes via HTTP (`Clock` em `2026-11-04T15:25:00Z`)

- **Distinção normativa (`SPEC.md` §2.2 e `DEC-005`):** Os Golden Cases originais **C1, C2 e C3** utilizam diretamente os prazos inteiros em meses definidos no enunciado (`3`, `2` e `3` meses, respectivamente) e permanecem validados de forma independente nos testes unitários de domínio (`PricingEngineTest`, `PricingEngineCrossCurrencyTest` e `CommercialTermCalculatorTest`), sem qualquer modificação nas fórmulas financeiras ou nos resultados esperados.
- **Cenários Integrados Equivalentes na TASK-007:** Como o endpoint `POST /api/v1/simulations` recebe `dueDate` (e não `termMonths`), os testes HTTP da TASK-007 exercitam **cenários integrados equivalentes** que utilizam a data de referência fixa documentada no OpenAPI (`operationInstant = 2026-11-04T15:25:00Z`, `operationDate = 2026-11-04`) e datas de vencimento fixas que produzem exatamente os mesmos prazos comerciais (`3`, `2` e `3` meses) e reproduzem os mesmos resultados financeiros ao centavo:

| Cenário Integrado Equivalente | Entrada (`SimulationRequest`) | Prazo Comercial Calculado | Saída Esperada (`SimulationResponse`) |
|---|---|---|---|
| **Equivalente HTTP a C1** | `DUPLICATA_MERCANTIL`, `faceValueBrl: "100000.00"`, `dueDate: "2027-02-02"`, `paymentCurrency: "BRL"` | `90` dias corridos $\rightarrow$ `termMonths: 3` | `presentValueBrl: "92859.94"`, `discountBrl: "7140.06"`, `spreadMonthly: "0.015000"`, `exchangeRateApplied: null`, `exchangeRateEffectiveAt: null`, `paidAmount: "92859.94"` |
| **Equivalente HTTP a C2** | `CHEQUE_PRE_DATADO`, `faceValueBrl: "25000.00"`, `dueDate: "2027-01-03"`, `paymentCurrency: "BRL"` | `60` dias corridos $\rightarrow$ `termMonths: 2` | `presentValueBrl: "23337.77"`, `discountBrl: "1662.23"`, `spreadMonthly: "0.025000"`, `exchangeRateApplied: null`, `exchangeRateEffectiveAt: null`, `paidAmount: "23337.77"` |
| **Equivalente HTTP a C3** | `DUPLICATA_MERCANTIL`, `faceValueBrl: "100000.00"`, `dueDate: "2027-02-02"`, `paymentCurrency: "USD"` (cotação vigente `"5.432100"` em `2026-11-04T12:00:00Z`) | `90` dias corridos $\rightarrow$ `termMonths: 3` | `presentValueBrl: "92859.94"`, `discountBrl: "7140.06"`, `spreadMonthly: "0.015000"`, `exchangeRateApplied: "5.432100"`, `exchangeRateEffectiveAt: "2026-11-04T12:00:00Z"`, `paidAmount: "17094.67"` |

---

## 8. Tratamento Global de Erros (`GlobalExceptionHandler`)

Será adicionado ao `GlobalExceptionHandler` existente:
- **Constante:** `ERROR_CODE_INVALID_DUE_DATE = "INVALID_DUE_DATE"`.
- **Handler para `InvalidDueDateException`:**
  - Status HTTP: `422 Unprocessable Entity` (`HttpStatus.UNPROCESSABLE_ENTITY`).
  - `title`: `"Unprocessable Entity"`.
  - `detail`: mensagem clara informando que `dueDate` deve ser estritamente posterior a `operationDate`.
  - `errorCode`: `"INVALID_DUE_DATE"`.
  - `instance`: URI da requisição (`/api/v1/simulations`).
  - `timestamp`: instante UTC obtido do `Clock`.

Os demais cenários (`VALIDATION_ERROR` em `400`, `EXCHANGE_RATE_UNAVAILABLE` em `422`, `404`, `405`, `409` e `INTERNAL_ERROR` em `500`) permanecem inalterados e serão revalidados após a edição para garantir zero regressão nos códigos HTTP e headers (`application/problem+json`).

---

## 9. Integração OpenAPI e Swagger UI (Estratégia Única Contract First no Spring Boot 4.1.2)

### 9.1. Requisitos e Comportamento do Springdoc com `api-docs.enabled=false`
- A documentação oficial do Springdoc confirma a compatibilidade da linha **`3.1.x–3.2.x`** (`org.springdoc:springdoc-openapi-starter-webmvc-ui`) com **Spring Boot `4.1.x`**.
- O arquivo `docs/openapi/srm-credit-engine.yaml` deve permanecer como **única fonte de verdade** dos contratos HTTP (`TASKS.md` §1), sendo proibido gerar uma segunda especificação concorrente em runtime.
- **Atenção técnica documentada pelo Springdoc:** Definir `springdoc.api-docs.enabled=false` desabilita a `SpringDocConfiguration` (que expõe `/v3/api-docs` e `/v3/api-docs/swagger-config`), podendo desativar também beans de propriedades (`SpringDocConfigProperties`) exigidos pela autoconfiguração do Swagger UI (`SwaggerConfig` / `SwaggerWelcomeWebMvc`). Portanto, **não se deve assumir** que `/v3/api-docs/swagger-config` estará disponível automaticamente quando `api-docs.enabled=false`.

### 9.2. Estratégia Única de Implementação
1. **Dependência Maven (`backendcreditengine/pom.xml`):**
   - Adicionar `org.springdoc:springdoc-openapi-starter-webmvc-ui` (versão da linha `3.1.x–3.2.x` compatível com o Spring Boot `4.1.2`).
2. **Exposição do Contrato Oficial sem Duplicação no Git (`maven-resources-plugin`):**
   - Configurar o `maven-resources-plugin` no `backendcreditengine/pom.xml` para copiar `../docs/openapi/srm-credit-engine.yaml` para `target/classes/static/openapi/srm-credit-engine.yaml` durante o build.
   - Dessa forma, o arquivo `docs/openapi/srm-credit-engine.yaml` permanece único no repositório e fica acessível estaticamente via Spring Web MVC em `GET /openapi/srm-credit-engine.yaml`.
3. **Configuração Recomendada para YAML Próprio com Geração Dinâmica Desabilitada (`application.yaml`):**
   - Configurar no `backendcreditengine/src/main/resources/application.yaml`:
     ```yaml
     springdoc:
       api-docs:
         enabled: false
       swagger-ui:
         url: /openapi/srm-credit-engine.yaml
         disable-swagger-default-url: true
     ```
   - **Papel de cada propriedade:**
     - `springdoc.api-docs.enabled: false`: desativa a geração dinâmica de OpenAPI por introspecção dos controllers (`OpenApiResource` em `/v3/api-docs`).
     - `springdoc.swagger-ui.url: /openapi/srm-credit-engine.yaml`: define o caminho estático do contrato oficial que o `SwaggerIndexPageTransformer` injeta diretamente no script inicializador do Swagger UI (`/swagger-ui/swagger-initializer.js`).
     - `springdoc.swagger-ui.disable-swagger-default-url: true`: remove a URL demonstrativa padrão do Swagger UI (`https://petstore.swagger.io/v2/swagger.json`), garantindo que apenas `/openapi/srm-credit-engine.yaml` seja carregado.
4. **Componente Mínimo de Suporte (`OpenApiConfig.java` em `config/`):**
   - Como `springdoc.api-docs.enabled=false` desativa a `SpringDocConfiguration` principal, caso o `SwaggerConfig` do Springdoc 3.x exija a presença de `SpringDocConfigProperties` / `SpringDocConfiguration` para inicializar os recursos estáticos e o `SwaggerIndexPageTransformer`, a classe mínima `OpenApiConfig.java` registrará apenas os beans de configuração necessários (`@ConditionalOnMissingBean`) para manter o Swagger UI ativo sem habilitar o `OpenApiResource` (`/v3/api-docs`).
   - O `SimulationController` permanece com responsabilidade exclusiva sobre `POST /api/v1/simulations`, sem qualquer código de documentação ou redirecionamento.

---

## 10. Critérios de Aceite

- **CA-01:** Endpoint `POST /api/v1/simulations` implementado e retornando `HTTP 200 OK` com os 14 atributos do schema `SimulationResponse`.
- **CA-02:** Reprodução exata ao centavo dos cenários integrados equivalentes aos **Golden Cases C1, C2 e C3** via chamada HTTP no endpoint de simulação, preservando intocados os testes independentes do `PricingEngine` e do `CommercialTermCalculator`.
- **CA-03:** Rejeição de propriedades JSON desconhecidas (`additionalProperties: false`) e de números JSON sem aspas em `faceValueBrl` com `HTTP 400 Bad Request`, `Content-Type: application/problem+json` e `errorCode: VALIDATION_ERROR`, sem vazar nomes de classes Java.
- **CA-04:** Rejeição de `faceValueBrl` fora do padrão `FaceValueBrlInput` (`"0.00"`, negativos, `> 999999999.99` ou mais de 2 casas decimais) e de campos obrigatórios nulos/ausentes com `HTTP 400 VALIDATION_ERROR` e `fieldErrors`.
- **CA-05:** Rejeição de `dueDate <= operationDate` com `HTTP 422 Unprocessable Entity` e `errorCode: INVALID_DUE_DATE`.
- **CA-06:** Retorno de `HTTP 422 Unprocessable Entity` e `errorCode: EXCHANGE_RATE_UNAVAILABLE` quando `paymentCurrency == USD` e não existir cotação vigente em `simulatedAt`.
- **CA-07:** Simulações em `BRL` não consultam o `ExchangeRateService` e retornam `exchangeRateApplied: null` e `exchangeRateEffectiveAt: null`.
- **CA-08:** Ausência comprovada de efeitos persistentes: a execução de `POST /api/v1/simulations` não insere, altera ou remove registros nas tabelas do banco de dados (verificado antes e depois da simulação).
- **CA-09:** Validação integral do OpenAPI e Swagger UI comprovando: (1) contrato oficial acessível por HTTP (`GET /openapi/srm-credit-engine.yaml`), (2) Swagger UI funcional (`GET /swagger-ui/index.html`), (3) carregamento efetivo de `/openapi/srm-credit-engine.yaml` pelo inicializador/configuração do Swagger UI, (4) ausência do Petstore padrão (`petstore.swagger.io`) e (5) ausência de especificação dinâmica concorrente (`/v3/api-docs` desabilitado).
- **CA-10:** Zero regressão sobre os testes da `TASK-006` (Currency Engine e `GlobalExceptionHandler`) e demais tarefas anteriores.

---

## 11. Plano de Testes Obrigatórios (`DEC-010`)

### 11.1. Testes Unitários da Camada de Aplicação (`SimulationServiceTest`)
Utilizando instâncias reais de `PricingEngine`, `CommercialTermCalculator` e `CurrencyConverter` (sem mockar cálculos financeiros puros, conforme `DEC-010` §2), `Clock` fixo em `2026-11-04T15:25:00Z` e mock apenas para `ExchangeRateService`:
1. Cenário equivalente ao **Golden Case C1** (`DUPLICATA_MERCANTIL`, `100000.00`, `2027-02-02`, `BRL` $\rightarrow$ `termMonths = 3`, `presentValueBrl = 92859.94`, `discountBrl = 7140.06`, `paidAmount = 92859.94`, sem interações com `ExchangeRateService`).
2. Cenário equivalente ao **Golden Case C2** (`CHEQUE_PRE_DATADO`, `25000.00`, `2027-01-03`, `BRL` $\rightarrow$ `termMonths = 2`, `presentValueBrl = 23337.77`, `discountBrl = 1662.23`, `paidAmount = 23337.77`).
3. Cenário equivalente ao **Golden Case C3** (`DUPLICATA_MERCANTIL`, `100000.00`, `2027-02-02`, `USD` com cotação `5.432100` $\rightarrow$ `paidAmount = 17094.67`, `exchangeRateApplied = 5.432100`).
4. Verificação de fronteiras de prazo comercial (`1` dia $\rightarrow$ `1` mês; `30` dias $\rightarrow$ `1` mês; `31` dias $\rightarrow$ `2` meses).
5. Rejeição de `dueDate == operationDate` e `dueDate < operationDate` com `InvalidDueDateException`, sem invocar `ExchangeRateService`.
6. Propagação de `ExchangeRateUnavailableException` quando `paymentCurrency == USD` e não há cotação vigente.
7. Garantia de que o mesmo instante `simulatedAt` capturado do `Clock` é repassado para `exchangeRateService.getExchangeRate(CurrencyCode.USD, simulatedAt)`.

### 11.2. Testes de Contrato REST (`SimulationControllerTest` — `@WebMvcTest`)
1. `POST /api/v1/simulations` nos cenários equivalentes a **C1, C2 e C3** com validação integral dos 14 campos JSON (incluindo `exchangeRateApplied: null` e `exchangeRateEffectiveAt: null` em C1/C2).
2. Rejeição de propriedade JSON desconhecida (`"manualDiscount": "10.00"`) com `400 VALIDATION_ERROR` e `Content-Type: application/problem+json`.
3. Rejeição de `faceValueBrl` enviado como número JSON (`100000.00` sem aspas) com `400 VALIDATION_ERROR`.
4. Rejeição de `faceValueBrl` inválido (`"0.00"`, `"-50.00"`, `"100.123"`, `"1000000000.00"`) com `400 VALIDATION_ERROR` e `fieldErrors` para `faceValueBrl`.
5. Rejeição de campos obrigatórios nulos/ausentes (`type`, `faceValueBrl`, `dueDate`, `paymentCurrency`) com `400 VALIDATION_ERROR`.
6. Tradução de `InvalidDueDateException` para `422 Unprocessable Entity` (`application/problem+json`) com `errorCode: INVALID_DUE_DATE`.
7. Tradução de `ExchangeRateUnavailableException` para `422 Unprocessable Entity` (`application/problem+json`) com `errorCode: EXCHANGE_RATE_UNAVAILABLE`.

### 11.3. Testes de Integração (`SimulationIntegrationTest` — `@SpringBootTest` + Testcontainers PostgreSQL)
1. Fluxo integrado completo para os cenários equivalentes a **C1, C2 e C3** (cadastrando cotação USD `5.432100` via `POST /api/v1/exchange-rates` e executando `POST /api/v1/simulations` com `Clock` controlado em `2026-11-04T15:25:00Z`).
2. **Comprovação de ausência de efeitos persistentes:**
   - Realizar toda a preparação necessária ao teste (ex.: cadastro prévio da cotação USD) **antes** de aferir o estado inicial do banco.
   - Aferir a contagem/estado antes e depois da execução de `POST /api/v1/simulations` (em `BRL` e `USD`) em todas as tabelas financeiras existentes no schema (`receivables`, `exchange_rates`, `assignors`, bem como `settlements` e `settlement_items` caso presentes no schema), comprovando que nenhuma linha é inserida, alterada ou removida pela simulação.
3. Cenário integrado de `422 EXCHANGE_RATE_UNAVAILABLE` quando a tabela `exchange_rates` está vazia ou possui apenas cotação futura.
4. Cenário integrado de `422 INVALID_DUE_DATE` quando `dueDate <= operationDate`.
5. **Verificação completa da integração OpenAPI e Swagger UI (sem depender de endpoint inexistente e além de simples HTTP 200 no HTML):**
   - **Contrato oficial acessível por HTTP:** `GET /openapi/srm-credit-engine.yaml` retorna `200 OK` contendo a especificação oficial (`openapi: 3.0.3`, `/api/v1/simulations`).
   - **Swagger UI funcional:** `GET /swagger-ui/index.html` retorna `200 OK`.
   - **Carregamento efetivo do YAML oficial e ausência do Petstore padrão:** `GET /swagger-ui/swagger-initializer.js` (transformado pelo `SwaggerIndexPageTransformer` do Springdoc) retorna `200 OK`, contém a referência explícita a `/openapi/srm-credit-engine.yaml` e **não** contém referência a `petstore.swagger.io`.
   - **Ausência de especificação dinâmica concorrente:** `GET /v3/api-docs` retorna `404 Not Found`, comprovando que a geração dinâmica de OpenAPI está desabilitada.

---

## 12. Matriz de Arquivos Previstos para Criação ou Modificação

| Arquivo | Operação | Justificativa |
|---|---|---|
| `docs/task-plans/TASK-007.md` | **CRIAR / ATUALIZAR** | Planejamento técnico individual da TASK-007. |
| `TASKS.md` | **ALTERAR** | Atualização de status e registro de evidências da TASK-007. |
| `backendcreditengine/pom.xml` | **ALTERAR** | Adição de `springdoc-openapi-starter-webmvc-ui` (`3.1.x–3.2.x`) e configuração do `maven-resources-plugin` para empacotar `docs/openapi/srm-credit-engine.yaml`. |
| `backendcreditengine/src/main/resources/application.yaml` | **ALTERAR** | Adição de `srm.pricing.base-rate-monthly: "0.010000"`, `springdoc.api-docs.enabled: false`, `springdoc.swagger-ui.url: /openapi/srm-credit-engine.yaml` e `springdoc.swagger-ui.disable-swagger-default-url: true`. |
| `.../config/OpenApiConfig.java` | **CRIAR** | Configuração mínima de suporte ao Swagger UI quando `springdoc.api-docs.enabled=false` (garantindo os beans de propriedades do Swagger UI sem ativar `/v3/api-docs`). |
| `.../common/web/StrictStringDeserializer.java` | **MOVER** | Movido de `exchangerate.web.dto` para `common.web` para compartilhamento limpo entre módulos. |
| `.../exchangerate/web/dto/CreateExchangeRateRequest.java` | **ALTERAR** | Atualização do import de `StrictStringDeserializer`. |
| `.../config/PricingConfig.java` | **CRIAR** | Exposição dos beans `PricingEngine`, `CommercialTermCalculator` e `CurrencyConverter`. |
| `.../simulation/application/exception/InvalidDueDateException.java` | **CRIAR** | Exceção negocial para `dueDate <= operationDate`. |
| `.../common/web/GlobalExceptionHandler.java` | **ALTERAR** | Handler para `InvalidDueDateException` (`422 INVALID_DUE_DATE`). |
| `.../simulation/application/SimulationResult.java` | **CRIAR** | Record de retorno da camada de aplicação. |
| `.../simulation/application/SimulationService.java` | **CRIAR** | Orquestrador stateless da simulação. |
| `.../simulation/web/dto/SimulationRequest.java` | **CRIAR** | DTO de entrada conforme schema OpenAPI. |
| `.../simulation/web/dto/SimulationResponse.java` | **CRIAR** | DTO de saída conforme schema OpenAPI. |
| `.../simulation/web/SimulationController.java` | **CRIAR** | Controller REST com responsabilidade exclusiva por `POST /api/v1/simulations` (sem lógica ou redirecionamento de Swagger UI). |
| `.../simulation/application/SimulationServiceTest.java` | **CRIAR** | Testes unitários da camada de aplicação. |
| `.../simulation/web/SimulationControllerTest.java` | **CRIAR** | Testes `MockMvc` do contrato REST. |
| `.../simulation/SimulationIntegrationTest.java` | **CRIAR** | Testes de integração com PostgreSQL real via Testcontainers + validação de ausência de efeitos persistentes e carregamento do contrato no Swagger UI. |

### Arquivos Explicitamente Intocados
- `PricingEngine.java`, `PricingInput.java`, `PricingResult.java`, `ReceivableType.java`, `CurrencyCode.java`, `CompoundInterestCalculator.java`, `TradeBillPricingStrategy.java`, `PostDatedCheckPricingStrategy.java`, `CommercialTermCalculator.java`, `CurrencyConverter.java` e seus respectivos testes unitários (`PricingEngineTest`, `PricingEngineCrossCurrencyTest`, `CommercialTermCalculatorTest`, `CurrencyConverterTest`).
- Migrations Flyway (`V1`, `V2`) e entidades JPA (`Assignor`, `Receivable`, `ExchangeRate`).
- `docs/openapi/srm-credit-engine.yaml`, `SPEC.md` e `DECISIONS.md`.

---

## 13. Subtarefas Sequenciais de Implementação

| Ordem | Subtarefa | Descrição e Verificação |
|---|---|---|
| **ST-01** | Atualizar `TASKS.md` para `IN_PROGRESS` | Registrar início formal da execução autorizada da TASK-007. |
| **ST-02** | Mover `StrictStringDeserializer` para `common.web`, criar `PricingConfig` e configurar `srm.pricing.base-rate-monthly` | Disponibilizar deserializer compartilhado e beans de domínio; **executar imediatamente `ExchangeRateControllerTest` para comprovar ausência de regressão na validação do Currency Engine**. |
| **ST-03** | Implementar `InvalidDueDateException`, `SimulationResult` e `SimulationService` | Implementar orquestração temporal, cálculo de prazo, precificação BRL e conversão USD sem transações artificiais. |
| **ST-04** | Atualizar `GlobalExceptionHandler` para `InvalidDueDateException` | Mapear retorno `HTTP 422` (`application/problem+json`) com `errorCode: INVALID_DUE_DATE`; **executar imediatamente `ExchangeRateControllerTest` para confirmar que códigos HTTP e headers existentes continuam íntegros**. |
| **ST-05** | Implementar `SimulationRequest`, `SimulationResponse` e `SimulationController` | Expor exclusivamente `POST /api/v1/simulations` em conformidade com o OpenAPI. |
| **ST-06** | Configurar Springdoc (`3.1.x–3.2.x`), `OpenApiConfig` e entrega estática do `srm-credit-engine.yaml` | Empacotar YAML oficial via `maven-resources-plugin` e configurar `springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.url=/openapi/srm-credit-engine.yaml` e `springdoc.swagger-ui.disable-swagger-default-url=true`. |
| **ST-07** | Implementar e executar `SimulationServiceTest` | Validar cenários equivalentes a C1, C2, C3, fronteiras de prazo e exceções (`InvalidDueDateException`, `ExchangeRateUnavailableException`). |
| **ST-08** | Implementar e executar `SimulationControllerTest` | Validar contrato HTTP `200`, `400` (`additionalProperties`, número JSON, regex) e `422`. |
| **ST-09** | Implementar e executar `SimulationIntegrationTest` | Validar integração com PostgreSQL (Testcontainers), ausência de efeitos persistentes antes/depois nas tabelas relevantes e carregamento do YAML oficial pelo Swagger UI (`/swagger-ui/swagger-initializer.js`), ausência de `petstore.swagger.io` e desativação de `/v3/api-docs`. |
| **ST-10** | Executar suíte completa `mvn test` e `git diff --check` | Confirmar 100% dos testes verdes (incluindo regressão completa da TASK-006 e domínio financeiro) e registrar evidências em `TASK-007.md` e `TASKS.md` (`VALIDATION`). |

---

## 14. Riscos e Medidas Preventivas

| Risco | Impacto | Mitigação |
|---|---|---|
| Desativação de beans internos do Swagger UI ao definir `springdoc.api-docs.enabled=false` | Swagger UI não inicializar ou exibir Petstore padrão | Adicionar `springdoc.swagger-ui.disable-swagger-default-url=true`, prover `OpenApiConfig.java` para os beans de configuração do Swagger UI quando `api-docs.enabled=false` e validar por teste automatizado o conteúdo de `/swagger-ui/swagger-initializer.js`, `/openapi/srm-credit-engine.yaml` e a desativação de `/v3/api-docs`. |
| Captura de instantes distintos de `Clock` durante a mesma simulação | Divergência entre `simulatedAt`, `operationDate` e a cotação USD selecionada | Capturar `Instant simulatedAt = clock.instant()` uma única vez no início do método `simulate(...)` e reutilizar a mesma variável em todas as etapas. |
| Coerção silenciosa de números JSON em `faceValueBrl` ou regressão na `TASK-006` | Violação do contrato de transporte decimal como String | Aplicar `@JsonDeserialize(using = StrictStringDeserializer.class)` em `faceValueBrl` e reexecutar `ExchangeRateControllerTest` logo após mover o deserializer para `common.web`. |
| Omissão de campos `null` no JSON de `SimulationResponse` em simulações BRL | Quebra do schema `SimulationResponse` que exige as 14 propriedades em `required` | Manter serialização explícita de `exchangeRateApplied: null` e `exchangeRateEffectiveAt: null` e validar via `jsonPath` nos testes `MockMvc`. |

---

## 15. Resultados e Evidências de Execução

- **Estado atual:** `READY` (Plano técnico aprovado em 2026-11-07T13:19:44-03:00).
- **Evidências:** Serão preenchidas durante a execução das subtarefas ST-01 a ST-10.
