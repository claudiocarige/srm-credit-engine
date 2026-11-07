# TASK-006 — Plano Técnico de Implementação

## 1. Identificação
- **Tarefa:** `TASK-006` — Currency Engine: Histórico, Vigência e API Manual de Câmbio.
- **Objetivo:** disponibilizar o registro manual append-only de cotações cambiais (USD/BRL) e a recuperação determinística da taxa vigente para a operação (`effectiveAt <= instante da operação`), expondo endpoints REST estritamente aderentes à especificação OpenAPI oficial (`docs/openapi/srm-credit-engine.yaml`), com controle temporal por `Clock` injetável, validação de escala cambial, rejeição estrita de propriedades JSON desconhecidas e tratamento padronizado de erros via `ProblemDetail` nativo do Spring.
- **Status do planejamento:** `CONCLUÍDO (DONE)`.
- **Referências:**
  - `SPEC.md` (§§1, 3);
  - `DECISIONS.md` (`DEC-001`, `DEC-002`, `DEC-003`, `DEC-006`, `DEC-008`, `DEC-010`, `DEC-013`);
  - `docs/contracts.md` (§§1.3, 2.3);
  - `docs/openapi/srm-credit-engine.yaml` (paths `/api/v1/exchange-rates` e `/api/v1/exchange-rates/current`; schemas `CreateExchangeRateRequest`, `ExchangeRateResponse`, `ExchangeCurrencyCode`, `ExchangeRateBrlInput`, `ProblemDetail`);
  - `AGENTS.md` (`G-01` a `G-04`, §12 Code Review);
  - `TASKS.md` (`TASK-006`);
  - `docs/task-plans/TASK-004.md` (motor financeiro e `CurrencyConverter`);
  - `docs/task-plans/TASK-005.md` (modelo persistente inicial e tabela `exchange_rates`).
- **Dependências:** `TASK-004` (`DONE`), `TASK-005` (`DONE`).
- **Ferramenta e modelo elaborador:** Google Antigravity — Gemini 3.8 Flash (High).

---

## 2. Diagnóstico do Estado Atual e Divergências do Workspace

A inspeção detalhada do workspace revelou o seguinte panorama:

| Componente | Situação no Workspace | Alinhamento com TASK-006 |
|---|---|---|
| **Tabela PostgreSQL** | Tabela `exchange_rates` criada na migration `V2__create_exchange_rates.sql` (TASK-005), com PK UUID, constraints `uk_exchange_rates_currency_effective_at`, `ck_exchange_rates_currency` (`currency = 'USD'`) e `ck_exchange_rates_rate_brl_range` (`[0.000001, 9999999.999999]`). | **Pronta e validada** — nenhuma alteração de schema ou migration necessária. |
| **Entidade JPA `ExchangeRate`** | Localizada em `com.claudiocarige.backendcreditengine.exchangerate.persistence.ExchangeRate`. Marcada como `@Immutable`, com colunas imutáveis, validação de escala `<= 6` e intervalo contratual. | **Pronta e validada** — O rascunho anterior indicava erroneamente que `effectiveAt` era truncado; a entidade no workspace já valida e rejeita instantes com precisão sub-microssegundo (`effectiveAt.getNano() % 1_000 != 0`), preservando `effectiveAt` intacto e truncando apenas `createdAt`. |
| **Conversor Matemático** | `com.claudiocarige.backendcreditengine.pricing.domain.calculation.CurrencyConverter` implementado na TASK-004. | **Pronto e preservado** — Responsável exclusivo pelo cálculo matemático BRL → USD com `MathContext.DECIMAL128` e `RoundingMode.HALF_EVEN`. Não será duplicado. |
| **Enums de Moeda** | `com.claudiocarige.backendcreditengine.pricing.domain.model.CurrencyCode` (`BRL`, `USD`). | **Pronto e preservado**. |
| **Dependências Maven** | `pom.xml` já declara `spring-boot-starter-validation`, `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa` e starters de teste. | **Completo** — Nenhuma dependência externa nova necessária. |
| **Configuração de Jackson** | `application.yaml` atual não possui `spring.jackson.deserialization.fail-on-unknown-properties: true`. | **Lacuna** — Necessário adicionar propriedade nativa do Spring Boot. |
| **Camada de Aplicação / Repositório / Web** | Repositório Spring Data, serviço de aplicação, DTOs, controller REST e `GlobalExceptionHandler` inexistentes. | **A implementar nesta tarefa**. |
| **Relógio (`Clock`)** | Não há bean de `java.time.Clock` injetável no contexto da aplicação. | **A implementar nesta tarefa**. |

### Divergência Sanada: Política Temporal de `ExchangeRate`
O rascunho preliminar continha a informação desatualizada de que `effectiveAt` sofria truncamento silencioso. 
A política arquitetural definitiva, já implementada em `ExchangeRate.java`, estabelece:
1. **`effectiveAt` (instante de vigência):** Não sofre truncamento silencioso. Representa o fato temporal exato da vigência. Se a entrada possuir precisão sub-microssegundo (nanossegundos residuais: `getNano() % 1_000 != 0`), ela é rejeitada, pois o PostgreSQL (`TIMESTAMPTZ`) possui resolução de microssegundos.
2. **`createdAt` (auditoria do registro):** Gerado e controlado pelo backend através do `Clock`, normalizado com `truncatedTo(ChronoUnit.MICROS)`.
3. **Compatibilidade OpenAPI (Pendente G-04):** O contrato OpenAPI vigente define `effectiveAt` genericamente como `type: string, format: date-time`, sem limitar expressamente a precisão máxima de frações de segundo. A rejeição de sub-microssegundos é uma política de integridade já implementada na entidade JPA, mas sua formalização contratual permanece como decisão pendente de deliberação G-04. O arquivo `docs/openapi/srm-credit-engine.yaml` permanece inalterado.

---

## 3. Abordagem Técnica e Arquitetura Proposta

### 3.1. Organização de Pacotes (Package by Feature Simplificado)
Adotando o princípio da simplicidade pragmática (**KISS/YAGNI**), eliminamos a interface redundante `CurrencyEngine`. O componente `ExchangeRateService` assume diretamente a orquestração dos casos de uso de câmbio.

```text
com.claudiocarige.backendcreditengine
├── config/
│   └── ClockConfig.java                          (Disponibiliza bean java.time.Clock UTC)
├── common/web/
│   └── GlobalExceptionHandler.java               (RestControllerAdvice padronizado com ProblemDetail nativo)
└── exchangerate/
    ├── persistence/
    │   ├── ExchangeRate.java                     (Entidade JPA existente — imutável)
    │   └── ExchangeRateRepository.java           (Spring Data JPA)
    ├── application/
    │   ├── ExchangeRateService.java              (Casos de uso: registro e recuperação vigente)
    │   └── exception/
    │       ├── ExchangeRateUnavailableException.java (Exceção de negócio -> HTTP 422)
    │       └── DuplicateExchangeRateException.java    (Exceção de conflito -> HTTP 409)
    ├── web/
    │   ├── ExchangeRateController.java           (Controller REST Contract First)
    │   └── dto/
    │       ├── CreateExchangeRateRequest.java    (DTO de request com validação de string canônica)
    │       └── ExchangeRateResponse.java         (DTO de response com taxa canônica de 6 casas)
    └── bootstrap/
        └── ExchangeRateDevDataInitializer.java   (Carga de desenvolvimento condicional @Profile("dev"))
```

### 3.2. Responsabilidades dos Componentes
- **`ExchangeRateRepository`:** Interface Spring Data JPA estendendo `JpaRepository<ExchangeRate, UUID>`. Fornece a consulta otimizada pela constraint de unicidade:
  ```java
  Optional<ExchangeRate> findFirstByCurrencyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
      CurrencyCode currency,
      Instant effectiveAt
  );
  ```
- **`ExchangeRateService`:** Camada de aplicação isolada de DTOs HTTP:
  1. *Registrar cotação manual:* Método:
     ```java
     public ExchangeRate registerExchangeRate(CurrencyCode currency, BigDecimal rateBrl, Instant effectiveAt)
     ```
     O serviço não recebe `CreateExchangeRateRequest`. O `ExchangeRateController` extrai os parâmetros validados e os repassa ao serviço. O serviço instancia a entidade `ExchangeRate`, invoca `repository.saveAndFlush(...)`, intercepta violações da constraint `uk_exchange_rates_currency_effective_at` traduzindo em `DuplicateExchangeRateException`, e retorna a entidade `ExchangeRate` persistida.
  2. *Recuperar cotação por instante da operação:* Método central:
     ```java
     public ExchangeRate getExchangeRate(CurrencyCode currency, Instant operationInstant)
     ```
     Executa a busca no repositório com `effectiveAt <= operationInstant` e retorna a entidade `ExchangeRate`. Se não encontrar cotação elegível, lança `ExchangeRateUnavailableException`. Não implementa fallbacks silenciosos nem valores padrão.
  3. *Orquestração e conversão de DTO no Controller:* O `ExchangeRateController` chama `service.getExchangeRate(currency, clock.instant())` e converte a entidade retornada para `ExchangeRateResponse` (via `ExchangeRateResponse.fromDomain(entity)`). Isso preserva a direção correta das dependências (`web -> application -> persistence`), permitindo que as futuras TASK-007 (Simulação) e TASK-009 (Liquidação) consumam diretamente `getExchangeRate(currency, operationInstant)` recebendo a entidade de domínio com precisão exata, sem acoplamento a DTOs de transporte HTTP.
  4. *Isolamento matemático:* O `ExchangeRateService` não executa conversão matemática BRL/USD (esta responsabilidade permanece isolada no `CurrencyConverter` da TASK-004).
- **`ExchangeRateController`:** Camada HTTP Contract First, mapeando endpoints `/api/v1/exchange-rates` e `/api/v1/exchange-rates/current`, transformando DTOs de request em parâmetros de serviço, validando requisições e convertendo entidades `ExchangeRate` retornadas para `ExchangeRateResponse`.
- **`GlobalExceptionHandler`:** Centraliza a tradução de exceções para `ProblemDetail` nativo do Spring MVC, garantindo as respostas exigidas pelo OpenAPI sem criar classes intermediárias desnecessárias.

---

## 4. Contratos HTTP e Endpoints REST

### 4.1. `POST /api/v1/exchange-rates` — Registrar Nova Cotação
- **Operação OpenAPI:** `createExchangeRate`.
- **Cabeçalhos:** Conforme OpenAPI, este endpoint **não** declara header `Location`. Nenhum header concorrente será inventado.
- **Request Body (`CreateExchangeRateRequest`):**
  - `additionalProperties: false` garantido na desserialização.
  - `currency`: obrigatório. Como `CurrencyCode` contempla `BRL` e `USD`, `@NotNull` é insuficiente. Validação explícita (`@AssertTrue` no DTO ou validação customizada) deve rejeitar `BRL` com HTTP 400 e `errorCode: VALIDATION_ERROR` (`fieldErrors: [ { "field": "currency", "message": "currency must be USD" } ]`).
  - `rateBrl`: obrigatório (`@NotBlank`), string decimal canônica validada por regex contratual `ExchangeRateBrlInput`:
    `^(?:0\.(?:[1-9][0-9]{0,5}|0[1-9][0-9]{0,4}|00[1-9][0-9]{0,3}|000[1-9][0-9]{0,2}|0000[1-9][0-9]?|00000[1-9])|[1-9][0-9]{0,6}(?:\.[0-9]{1,6})?)$`
    - Intervalo permitido: `[0.000001, 9999999.999999]`.
    - Escala máxima: 6 casas decimais. Valores com mais de 6 casas ou menores que o mínimo são sumariamente rejeitados.
    - **Rejeição estrita de coerção numérica:** Como `FAIL_ON_UNKNOWN_PROPERTIES` não impede coerção de números para String, o campo utilizará desserializador estrito (`@JsonDeserialize(using = StrictStringDeserializer.class)`) que exige `JsonToken.VALUE_STRING`, rejeitando números brutos (ex.: `{"rateBrl": 5.432100}`) com HTTP 400 `VALIDATION_ERROR`, comprovado por teste `MockMvc`.
  - `effectiveAt`: obrigatório (`@NotNull`), `Instant` ISO-8601 em UTC. Validado na camada de entrada quanto à precisão temporal (`effectiveAt.getNano() % 1_000 == 0`), garantindo que instantes com precisão sub-microssegundo resultem em HTTP 400 `VALIDATION_ERROR`, e não em HTTP 500.
- **Respostas:**
  - `201 Created`: Retorna `ExchangeRateResponse` (`id`, `currency`, `rateBrl`, `effectiveAt`, `createdAt`).
  - `400 Bad Request`: `ProblemDetail` (`errorCode: VALIDATION_ERROR`) com lista de `fieldErrors` ou detalhe de desserialização (campos desconhecidos ou formato inválido).
  - `409 Conflict`: `ProblemDetail` (`errorCode: DUPLICATE_EXCHANGE_RATE_EFFECTIVE_AT`) quando já existir cotação para o mesmo par `(currency, effectiveAt)`.
  - `500 Internal Server Error`: `ProblemDetail` (`errorCode: INTERNAL_ERROR`) genérico sem vazamento de stack trace.

### 4.2. `GET /api/v1/exchange-rates/current` — Consultar Cotação Vigente
- **Operação OpenAPI:** `getCurrentExchangeRate`.
- **Query Parameter:** `currency` (opcional, padrão `USD`).
- **Validação Antecipada de Moeda:**
  - O enum Java `CurrencyCode` possui `BRL` e `USD`, mas o contrato cambial permite exclusivamente `USD`. A conversão automática de enum aceitaria `BRL`, o que é proibido pelo contrato.
  - O controller recebe o parâmetro e valida explicitamente que a moeda informada seja estritamente `USD`. Essa validação ocorre antes de qualquer consulta ao repositório ou serviço, impedindo que moedas inválidas resultem incorretamente em HTTP 422.
  - Matriz de comportamento obrigatória:

  | Requisição | Resultado esperado |
  |---|---|
  | `GET /current` | Consulta USD (padrão) |
  | `GET /current?currency=USD` | Consulta USD |
  | `GET /current?currency=BRL` | HTTP 400 — `VALIDATION_ERROR` |
  | `GET /current?currency=EUR` | HTTP 400 — `VALIDATION_ERROR` |
  | `GET /current?currency=USD` sem cotação vigente | HTTP 422 — `EXCHANGE_RATE_UNAVAILABLE` |

- **Comportamento e Regra de Vigência:**
  - Instante de referência: obtido via `clock.instant()`.
  - O controller invoca `exchangeRateService.getExchangeRate(CurrencyCode.USD, clock.instant())` e converte a entidade retornada para `ExchangeRateResponse`.
  - Consulta banco: `WHERE currency = :currency AND effective_at <= :operationInstant ORDER BY effective_at DESC LIMIT 1`.
  - Utiliza o índice da restrição `uk_exchange_rates_currency_effective_at` (alta performance sem table scan).
  - **Proibição de cotação futura:** Cotações com `effectiveAt > clock.instant()` nunca são selecionadas.
  - **Proibição de fallback:** Se nenhuma cotação elegível for encontrada para USD, retorna obrigatoriamente HTTP 422. É estritamente proibido retornar cotação padrão (ex.: `1.000000`), valor arbitrário ou última cotação futura.
- **Respostas:**
  - `200 OK`: `ExchangeRateResponse` com `rateBrl` formatado com exatamente 6 casas decimais (ex.: `"5.432100"`).
  - `400 Bad Request`: `ProblemDetail` (`errorCode: VALIDATION_ERROR`) caso informado parâmetro `currency` inválido ou não suportado (ex.: `BRL`, `EUR`).
  - `422 Unprocessable Entity`: `ProblemDetail` (`errorCode: EXCHANGE_RATE_UNAVAILABLE`) detalhando que nenhuma taxa válida vigora no instante atual para USD.

---

## 5. Estratégia de Configuração Jackson (Spring Boot 4.1.2)

Em conformidade com a preferência por configuração nativa sem overengineering:

1. **Habilitação via `application.yaml`:**
   Adicionar ao arquivo `backendcreditengine/src/main/resources/application.yaml`:
   ```yaml
   spring:
     jackson:
       deserialization:
         fail-on-unknown-properties: true
   ```
2. **Rejeição de propriedades desconhecidas:**
   Com essa propriedade, o `ObjectMapper` configurado pelo Spring Boot falha com `UnrecognizedPropertyException` sempre que o JSON de entrada contiver atributos não mapeados no DTO (cumprindo a regra `additionalProperties: false` do OpenAPI).
3. **Rejeição estrita de coerções escalares (Strings numéricas):**
   A propriedade `fail-on-unknown-properties` não impede o Jackson de converter tokens numéricos JSON em `String`. Para blindar o transporte contra coerção silenciosa sem complexidade global excessiva, introduz-se o componente localizado `StrictStringDeserializer`, que assegura que apenas literais entre aspas (`JsonToken.VALUE_STRING`) sejam aceitos em `rateBrl`. Casos de envio de número JSON disparam `HttpMessageNotReadableException`, traduzida para HTTP 400 com `errorCode: VALIDATION_ERROR`.
4. **Tratamento no `GlobalExceptionHandler`:**
   O Spring Web MVC encapsula essas exceções em `HttpMessageNotReadableException`. O `GlobalExceptionHandler` intercepta essa exceção e:
   - Verifica se a causa raiz é `UnrecognizedPropertyException`, `MismatchedInputException` ou erro de sintaxe/coerção de tipo JSON.
   - Retorna HTTP 400 com `ProblemDetail`:
     - `title: Validation Error`
     - `status: 400`
     - `detail: JSON request payload contains unrecognized property, invalid field format or unquoted numeric value.`
     - `errorCode: VALIDATION_ERROR`
     - `instance: <caminho da requisição>`
     - `timestamp: <instante UTC>`
   - **Segurança da Informação:** Não expõe nomes de pacotes, classes Java internas ou stack traces na mensagem de detalhe.
5. **Simplificação:** Não é necessário criar uma classe Java `JacksonConfig.java` global. A rejeição de campos desconhecidos é nativa no YAML e a rejeição de coerção numérica é localizada via deserializador no DTO.

---

## 6. Modelagem dos DTOs e Precisão Financeira

### 6.1. DTO `CreateExchangeRateRequest`
```java
public record CreateExchangeRateRequest(
    @NotNull(message = "currency is required")
    CurrencyCode currency,

    @NotBlank(message = "rateBrl is required")
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @Pattern(
        regexp = "^(?:0\\.(?:[1-9][0-9]{0,5}|0[1-9][0-9]{0,4}|00[1-9][0-9]{0,3}|000[1-9][0-9]{0,2}|0000[1-9][0-9]?|00000[1-9])|[1-9][0-9]{0,6}(?:\\.[0-9]{1,6})?)$",
        message = "rateBrl must be a decimal string between 0.000001 and 9999999.999999 with at most 6 decimal places"
    )
    String rateBrl,

    @NotNull(message = "effectiveAt is required")
    Instant effectiveAt
) {
    @AssertTrue(message = "currency must be USD")
    public boolean isCurrency() {
        return currency == null || currency == CurrencyCode.USD;
    }

    @AssertTrue(message = "effectiveAt must have microsecond precision")
    public boolean isEffectiveAt() {
        return effectiveAt == null || effectiveAt.getNano() % 1_000 == 0;
    }
}
```
- **Nomes de campos públicos em erros de validação:** A convenção JavaBean nos métodos `@AssertTrue` (`isCurrency()` e `isEffectiveAt()`) garante que o framework de validação e o Spring MVC gerem como nome de propriedade exatamente os nomes públicos dos atributos contratuais (`"currency"` e `"effectiveAt"`), em vez de nomes sintéticos de método como `"currencySupported"` ou `"effectiveAtPrecisionValid"`.
- Conversão segura para persistência: `new BigDecimal(request.rateBrl())` — o regex garante sintaxe decimal válida e limites, as guardas do DTO asseguram que `currency` seja `USD` e que `effectiveAt` não contenha sub-microssegundos (retornando HTTP 400 `VALIDATION_ERROR` via Bean Validation com `field: "currency"` e `field: "effectiveAt"`), e o construtor da entidade `ExchangeRate` preserva as invariantes de domínio.

### 6.2. DTO `ExchangeRateResponse`
```java
public record ExchangeRateResponse(
    UUID id,
    CurrencyCode currency,
    String rateBrl,
    Instant effectiveAt,
    Instant createdAt
) {
    public static ExchangeRateResponse fromDomain(ExchangeRate entity) {
        return new ExchangeRateResponse(
            entity.getId(),
            entity.getCurrency(),
            entity.getRateBrl().setScale(6, RoundingMode.UNNECESSARY).toPlainString(),
            entity.getEffectiveAt(),
            entity.getCreatedAt()
        );
    }
}
```
- Garante a representação canônica exata de 6 casas decimais exigida pelo schema OpenAPI `ExchangeRateString`:
  - `5.43` → `"5.430000"`
  - `5.4321` → `"5.432100"`
  - `0.000001` → `"0.000001"`

---

## 7. Tratamento de Erros com `ProblemDetail` Nativo

O `GlobalExceptionHandler` utilizará a classe nativa `org.springframework.http.ProblemDetail` do Spring Boot, adicionando as propriedades contratuais necessárias via `.setProperty(...)`:

| Cenário de Erro | Exceção Capturada | Status HTTP | `errorCode` | Formato e Campos Adicionais |
|---|---|---|---|---|
| Falha de validação de DTO | `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` | `fieldErrors: [ { "field": "...", "message": "..." } ]` |
| Propriedade desconhecida ou JSON malformado | `HttpMessageNotReadableException` | 400 | `VALIDATION_ERROR` | Mensagem limpa e segura sem expor classes internas |
| Parâmetro query inválido | `MethodArgumentTypeMismatchException` / `HandlerMethodValidationException` | 400 | `VALIDATION_ERROR` | Detalhe do parâmetro inválido |
| Conflito de cotação duplicada | `DuplicateExchangeRateException` | 409 | `DUPLICATE_EXCHANGE_RATE_EFFECTIVE_AT` | Informa que já existe cotação para o instante `effectiveAt` |
| Cotação não vigente / ausente | `ExchangeRateUnavailableException` | 422 | `EXCHANGE_RATE_UNAVAILABLE` | Informa ausência de cotação para a moeda no instante atual |
| Erro interno não previsto | `Exception` (fallback genérico) | 500 | `INTERNAL_ERROR` | Detalhe genérico; log interno com stack trace completo |

### 7.1. Padronização de `fieldErrors`
No tratamento de `MethodArgumentNotValidException`, o `GlobalExceptionHandler` extrai a lista de erros de campo (`ex.getBindingResult().getFieldErrors()`) mapeando-os para uma lista contendo exatamente os nomes públicos contratuais (`"currency"`, `"rateBrl"`, `"effectiveAt"`), sem vazar nomes sintéticos de métodos de validação:

```json
{
  "type": "about:blank",
  "title": "Validation Error",
  "status": 400,
  "detail": "Invalid request parameters.",
  "instance": "/api/v1/exchange-rates",
  "errorCode": "VALIDATION_ERROR",
  "timestamp": "2026-11-06T13:00:00Z",
  "fieldErrors": [
    {
      "field": "currency",
      "message": "currency must be USD"
    }
  ]
}
```

Todos os erros retornam `Content-Type: application/problem+json` e preenchem `type`, `title`, `status`, `detail`, `instance`, `errorCode` e `timestamp`. Sem necessidade de criar `ApiErrorResponse.java`.

---

## 8. Concorrência, Idempotência e Unicidade

1. **Garantia Relacional no PostgreSQL:** A unicidade é resguardada no banco pela constraint:
   `CONSTRAINT uk_exchange_rates_currency_effective_at UNIQUE (currency, effective_at)`
2. **Prevenção de Race Conditions:** Não confiar em `repository.existsBy...()` prévio para tomada de decisão, pois duas requisições concorrentes poderiam passar pela checagem antes do commit.
3. **Flushing Transacional Imediato:** O serviço deve utilizar `repository.saveAndFlush(entity)` para que qualquer violação de constraint dispare o erro de integridade imediatamente no método do serviço, antes da montagem da resposta HTTP 201.
4. **Tradução Específica de Constraint:**
   Ao capturar `DataIntegrityViolationException`:
   - Inspecionar a cadeia de causas até localizar a `PSQLException`.
   - Verificar se `psqlException.getSQLState().equals("23505")` E se a constraint violada (`getServerErrorMessage().getConstraint()`) é exatamente `"uk_exchange_rates_currency_effective_at"`.
   - Se confirmado, lançar `DuplicateExchangeRateException`.
   - Se for outra violação (ex.: erro de check constraint inesperado), relançar a exceção original, evitando mascarar falhas estruturais como duplicidade cambial.
5. **Histórico Append-Only:** Novas cotações cadastradas são estritamente aditivas. Elas nunca alteram registros prévios, garantindo que operações e liquidações passadas permaneçam auditáveis.

---

## 9. Gestão de Tempo e Determinismo com `Clock` Injetável

1. **Configuração de Produção:**
   Classe `config/ClockConfig.java`:
   ```java
   @Configuration(proxyBeanMethods = false)
   public class ClockConfig {
       @Bean
       public Clock clock() {
           return Clock.systemUTC();
       }
   }
   ```
2. **Utilização nos Serviços:**
   O `ExchangeRateService` recebe `Clock` via injeção de dependência por construtor. Para obter o instante atual, invoca sempre `clock.instant()`.
   **Proibido:** Chamar `Instant.now()` ou `LocalDateTime.now()` diretamente no fluxo de negócio.
3. **Determinismo em Testes:**
   Em testes unitários e de integração, pode-se injetar `Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC)`, simulando com precisão absoluta momentos anteriores, exatos ou posteriores à vigência cadastrada.

---

## 10. Dados Iniciais de Desenvolvimento (Seed Dev)

Em conformidade com a decisão D-05 (TASK-005):
1. **Isolamento de Ambiente:** A carga inicial de desenvolvimento é implementada em `ExchangeRateDevDataInitializer.java`, anotada exclusivamente com `@Profile("dev")`.
2. **Não afeta Produção nem Testes:**
   - Em produção (`prod`), a classe não é instanciada.
   - Na suíte padrão de testes automatizados, o perfil `dev` permanece inativo, garantindo banco limpo para os testes de cotação inexistente (`422 EXCHANGE_RATE_UNAVAILABLE`).
3. **Idempotência de Reinicialização:**
   Antes de inserir o registro demonstrativo, o initializer verifica se a cotação para o instante já existe, evitando falhas de inicialização em múltiplos restarts locais:
   - Moeda: `USD`
   - Cotação: `5.432100` (dado demonstrativo de desenvolvimento)
   - Vigência: `2026-11-04T00:00:00Z`

---

## 11. Estratégia de Testes e Critérios de Aceite

A verificação da TASK-006 será composta por testes unitários, testes de camada web com `MockMvc` e testes de integração com Testcontainers PostgreSQL 16.

### 11.1. Cenários de Teste Planejados

1. **Testes Unitários de Serviço (`ExchangeRateServiceTest`):**
   - Registro com sucesso retornando entidade persistida.
   - Violação de unicidade da constraint traduzida para `DuplicateExchangeRateException`.
   - Consulta vigente com sucesso selecionando a cotação correta (`effectiveAt <= clock.instant()`).
   - Consulta quando só existem cotações futuras lançando `ExchangeRateUnavailableException`.
   - Consulta com múltiplas cotações elegíveis selecionando estritamente a de maior `effectiveAt`.
   - Cotação exatamente no mesmo microssegundo do `clock.instant()` selecionada com sucesso.
2. **Testes Web de Contrato (`ExchangeRateControllerTest` — `MockMvc`):**
   - `POST /api/v1/exchange-rates`: payload válido retorna HTTP 201 com schema `ExchangeRateResponse`.
   - `POST /api/v1/exchange-rates`: envio de propriedade JSON desconhecida (ex.: `"foo": "bar"`) retorna HTTP 400 com `errorCode: VALIDATION_ERROR` sem vazar classes Java.
   - `POST /api/v1/exchange-rates`: envio de `rateBrl` como número JSON (ex.: `5.4321`) ou string fora do formato (ex.: `"0.000000"`, `"5.1234567"`) retorna HTTP 400 `VALIDATION_ERROR`.
   - `POST /api/v1/exchange-rates`: moeda diferente de `USD` retorna HTTP 400 `VALIDATION_ERROR`.
   - `POST /api/v1/exchange-rates`: conflito de data/hora retorna HTTP 409 `DUPLICATE_EXCHANGE_RATE_EFFECTIVE_AT`.
   - `GET /api/v1/exchange-rates/current`: cotação encontrada retorna HTTP 200 com taxa formatada com 6 casas decimais.
   - `GET /api/v1/exchange-rates/current`: nenhuma cotação vigente retorna HTTP 422 `EXCHANGE_RATE_UNAVAILABLE`.
   - `GET /api/v1/exchange-rates/current?currency=BRL` e `GET /api/v1/exchange-rates/current?currency=EUR`: moedas não permitidas retornam HTTP 400 `VALIDATION_ERROR` antes de qualquer consulta ao repositório ou serviço.
3. **Testes de Integração com Banco Real (`ExchangeRateIntegrationTest` — Testcontainers PostgreSQL):**
   - Round-trip completo HTTP POST persistindo no PostgreSQL real.
   - Disparo real da constraint `uk_exchange_rates_currency_effective_at` retornando HTTP 409 em tentativas simultâneas/duplicadas.
   - Rejeição de `effectiveAt` com frações de sub-microssegundos retornando HTTP 400.
   - Consulta HTTP GET recuperando registro real ordenado por vigência.
   - Preservação do histórico cambial em inserções retroativas (inserir cotação com `effectiveAt` passado não altera nem sobrescreve cotações prévias; a validação de snapshots de liquidação pertence à TASK-009).

### 11.2. Critérios de Aceite
- **CA-01:** `POST /api/v1/exchange-rates` cadastra cotação com sucesso e retorna `201 Created` aderente ao schema OpenAPI.
- **CA-02:** Tentativa de cadastro duplicado para o mesmo instante e moeda retorna `409 Conflict` com `errorCode: DUPLICATE_EXCHANGE_RATE_EFFECTIVE_AT`.
- **CA-03:** `GET /api/v1/exchange-rates/current` retorna `200 OK` com a cotação de maior `effectiveAt <= clock.instant()`.
- **CA-04:** `GET /api/v1/exchange-rates/current` retorna `422 Unprocessable Entity` com `errorCode: EXCHANGE_RATE_UNAVAILABLE` quando não houver cotação vigente elegível.
- **CA-05:** Envio de campos JSON desconhecidos ou envio de `rateBrl` como número JSON em `CreateExchangeRateRequest` resulta em `400 Bad Request` com `errorCode: VALIDATION_ERROR`.
- **CA-06:** `rateBrl` aceita somente strings decimais canônicas no intervalo `[0.000001, 9999999.999999]` com até 6 casas decimais, retornando sempre 6 casas na resposta.
- **CA-07:** Respostas de erro formatadas estritamente como `application/problem+json` com campos `type`, `title`, `status`, `detail`, `instance`, `errorCode` e `timestamp`.
- **CA-08:** `effectiveAt` preserva o instante recebido e rejeita frações sub-microssegundo.
- **CA-09:** Suíte completa de testes executada via Maven (`mvn test`) e aprovada (`BUILD SUCCESS`) sem nenhuma regressão sobre os testes existentes.

---

## 12. Escopo de Arquivos e Recursos

### 12.1. Tabela de Arquivos

| Ação | Arquivo | Finalidade |
|---|---|---|
| **Criar** | `backendcreditengine/src/main/java/com/claudiocarige/backendcreditengine/config/ClockConfig.java` | Disponibilizar bean gerenciado `java.time.Clock` em UTC |
| **Criar** | `backendcreditengine/src/main/java/com/claudiocarige/backendcreditengine/common/web/GlobalExceptionHandler.java` | `@RestControllerAdvice` padronizando respostas `ProblemDetail` com `errorCode` |
| **Criar** | `backendcreditengine/src/main/java/com/claudiocarige/backendcreditengine/exchangerate/persistence/ExchangeRateRepository.java` | Repositório Spring Data JPA com busca de taxa vigente |
| **Criar** | `backendcreditengine/src/main/java/com/claudiocarige/backendcreditengine/exchangerate/application/ExchangeRateService.java` | Casos de uso de cadastro manual e recuperação de cotação por `operationInstant` |
| **Criar** | `backendcreditengine/src/main/java/com/claudiocarige/backendcreditengine/exchangerate/application/exception/ExchangeRateUnavailableException.java` | Exceção de negócio para cotação indisponível (`422`) |
| **Criar** | `backendcreditengine/src/main/java/com/claudiocarige/backendcreditengine/exchangerate/application/exception/DuplicateExchangeRateException.java` | Exceção de conflito para duplicidade de cotação (`409`) |
| **Criar** | `backendcreditengine/src/main/java/com/claudiocarige/backendcreditengine/exchangerate/web/ExchangeRateController.java` | Endpoints REST `/api/v1/exchange-rates` e `/current` |
| **Criar** | `backendcreditengine/src/main/java/com/claudiocarige/backendcreditengine/exchangerate/web/dto/StrictStringDeserializer.java` | Deserializador Jackson estrito para rejeitar coerção de números JSON |
| **Criar** | `backendcreditengine/src/main/java/com/claudiocarige/backendcreditengine/exchangerate/web/dto/CreateExchangeRateRequest.java` | DTO de entrada com validações estritas de string canônica, USD e precisão temporal |
| **Criar** | `backendcreditengine/src/main/java/com/claudiocarige/backendcreditengine/exchangerate/web/dto/ExchangeRateResponse.java` | DTO de saída com formatação exata de 6 casas decimais |
| **Criar** | `backendcreditengine/src/main/java/com/claudiocarige/backendcreditengine/exchangerate/bootstrap/ExchangeRateDevDataInitializer.java` | Carga inicial condicional para desenvolvimento (`@Profile("dev")`) |
| **Criar** | `backendcreditengine/src/test/java/com/claudiocarige/backendcreditengine/exchangerate/application/ExchangeRateServiceTest.java` | Testes unitários do serviço com Mockito e `Clock` controlado |
| **Criar** | `backendcreditengine/src/test/java/com/claudiocarige/backendcreditengine/exchangerate/web/ExchangeRateControllerTest.java` | Testes `MockMvc` de conformidade OpenAPI, validações e rejeição de números |
| **Criar** | `backendcreditengine/src/test/java/com/claudiocarige/backendcreditengine/exchangerate/ExchangeRateIntegrationTest.java` | Testes end-to-end com Testcontainers PostgreSQL |
| **Alterar**| `backendcreditengine/src/main/resources/application.yaml` | Adicionar configuração `spring.jackson.deserialization.fail-on-unknown-properties: true` |
| **Alterar**| `TASKS.md` | Registrar `IN_PROGRESS` e posteriormente evidências e `VALIDATION` |
| **Alterar**| `docs/task-plans/TASK-006.md` | Atualizar resultados reais de execução e evidências de validação |
| **Preservar**| `docs/task-plans/rascunho-TASK-006.md` | Rascunho mantido no histórico sem exclusão automática |

### 12.2. Arquivos Expressamente Preservados
- `ExchangeRate.java` (mantido intacto; já possui validação de sub-microssegundos aprovada).
- `CurrencyConverter.java` e `CurrencyCode.java` (núcleo matemático puro de precificação intocado).
- `V1__create_assignors_and_receivables.sql` e `V2__create_exchange_rates.sql` (migrations Flyway imutáveis).
- `docs/openapi/srm-credit-engine.yaml` (contrato OpenAPI oficial preservado).
- `DECISIONS.md` (intocado; nenhuma alteração arquitetural de decisões aprovadas).

---

## 13. Subtarefas Sequenciais de Implementação

| Ordem | Subtarefa | Descrição e Validação |
|---|---|---|
| **ST-01** | Registrar `IN_PROGRESS` no `TASKS.md` | Atualizar rastreabilidade oficial (preservando o rascunho anterior). |
| **ST-02** | Configurar Jackson em `application.yaml` e criar `ClockConfig` | Habilitar `fail-on-unknown-properties` nativo e bean `Clock` UTC. |
| **ST-03** | Criar DTOs de entrada e saída em `exchangerate.web.dto` | Implementar `CreateExchangeRateRequest`, `StrictStringDeserializer` e `ExchangeRateResponse`. |
| **ST-04** | Criar `ExchangeRateRepository` em `exchangerate.persistence` | Implementar método de consulta de vigência por moeda e `effectiveAt <= operationInstant`. |
| **ST-05** | Criar exceções de negócio e `ExchangeRateService` | Implementar casos de uso de registro manual (`saveAndFlush` + tradução de 409) e busca por `operationInstant` (`422`). |
| **ST-06** | Criar `GlobalExceptionHandler` em `common.web` | Implementar `@RestControllerAdvice` mapeando `ProblemDetail` com `errorCode`, `fieldErrors` e captura de `HttpMessageNotReadableException`. |
| **ST-07** | Criar `ExchangeRateController` em `exchangerate.web` | Implementar rotas `/api/v1/exchange-rates` (POST) e `/current` (GET com validação prévia de USD rejeitando BRL e EUR com HTTP 400). |
| **ST-08** | Implementar e executar testes unitários (`ExchangeRateServiceTest`) | Validar regras de vigência, ordenação e exceções com `Clock` fixo. |
| **ST-09** | Implementar e executar testes `MockMvc` (`ExchangeRateControllerTest`) | Validar rejeição de campos desconhecidos (400), rejeição de números JSON em `rateBrl`, validação de `currency=BRL` e `currency=EUR` no GET (400), validação de regex/escala e status HTTP 201/409/422. |
| **ST-10** | Implementar e executar testes de integração (`ExchangeRateIntegrationTest`) | Validar round-trip no PostgreSQL real via Testcontainers, unicidade concorrente e histórico append-only. |
| **ST-11** | Criar `ExchangeRateDevDataInitializer` (`@Profile("dev")`) | Implementar seed idempotente exclusivamente para ambiente local. |
| **ST-12** | Executar suíte completa `mvn test` e `git diff --check` | Garantir 100% de sucesso sem regressões e atualizar evidências no `TASK-006.md` e `TASKS.md` para `VALIDATION`. |

---

## 14. Riscos e Mitigações

| Risco | Impacto | Mitigação |
|---|---|---|
| Race condition em cadastros simultâneos com mesmo `effectiveAt` | Inconsistência ou erro genérico 500 | `saveAndFlush` no serviço para disparar a constraint `uk_exchange_rates_currency_effective_at` do PostgreSQL e captura específica da `PSQLException` (23505) traduzida para `409 Conflict`. |
| Vazamento de detalhes de infraestrutura na rejeição de campos JSON desconhecidos | Falha de segurança | `GlobalExceptionHandler` captura `HttpMessageNotReadableException` e formata mensagem genérica padronizada sem expor nomes de classes Java. |
| Coerção silenciosa de números JSON em String | Violação do contrato de transporte decimal | Validação de tipo na desserialização e testes `MockMvc` enviando números crus (ex.: `5.432100`) verificando rejeição. |
| Seed poluir testes de ausência de cotação | Falsos positivos / quebra do cenário 422 | `@Profile("dev")` ativado apenas no perfil de desenvolvimento local; suíte de testes executa no perfil padrão sem o seed. |
| Inconsistência de fuso horário | Cotação vigente considerada antes do tempo | Uso estrito de `Instant` em UTC e controle temporal exclusivo por `Clock` injetável. |

---

## 15. Resultados e Evidências de Execução

- **Estado atual:** `CONCLUÍDO (DONE)`.
- **Aprovação final humana:** Recebida em 2026-11-06T17:44:43-03:00.
- **Autorização humana:** Recebida explicitamente em 2026-11-06T13:23:42-03:00.
- **Subtarefas executadas:** ST-01 a ST-12 concluídas com sucesso.

### 15.1. Evidências dos Testes Automatizados
- **Testes Unitários do Initializer (`ExchangeRateDevDataInitializerTest`):** 2 testes executados e aprovados (`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`). Comprovam que cotações prévias não bloqueiam o seed e que a verificação pontual por par exato `(currency, effectiveAt)` é idempotente.
- **Testes Unitários de Serviço (`ExchangeRateServiceTest`):** 9 testes executados e aprovados (`Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`). Comprovam:
  - Tradução estruturada via Hibernate `ConstraintViolationException` com SQLState `23505` e constraint `uk_exchange_rates_currency_effective_at`.
  - Tradução via `SQLException` com SQLState `23505` e constraint no texto.
  - Repasse estrito de violações com SQLState diferente ou constraint diferente sem mascaramento.
- **Testes Web de Contrato (`ExchangeRateControllerTest`):** 14 testes executados e aprovados (`Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`). Comprovam:
  - `POST /api/v1/exchange-rates` com retorno HTTP 201 e sem header `Location`.
  - Rejeição de propriedades JSON desconhecidas com HTTP 400 e `errorCode: VALIDATION_ERROR`.
  - Rejeição de `rateBrl` enviado como número JSON com HTTP 400 `VALIDATION_ERROR`.
  - Rejeição de formato de `rateBrl` fora da regex com HTTP 400 `VALIDATION_ERROR`.
  - Rejeição de moedas não suportadas (ex.: `BRL`) no POST com HTTP 400 `VALIDATION_ERROR` expondo `field: "currency"`.
  - Rejeição de `effectiveAt` com sub-microssegundos com HTTP 400 `VALIDATION_ERROR` expondo `field: "effectiveAt"`.
  - Tradução de violação de unicidade para HTTP 409 `DUPLICATE_EXCHANGE_RATE_EFFECTIVE_AT`.
  - `GET /api/v1/exchange-rates/current` retornando HTTP 200 com cotação formatada com 6 casas decimais.
  - `GET /api/v1/exchange-rates/current` retornando HTTP 422 `EXCHANGE_RATE_UNAVAILABLE` quando não há cotação vigente.
  - `GET /api/v1/exchange-rates/current?currency=BRL` retornando HTTP 400 `VALIDATION_ERROR` antes de chamar serviço/repositório.
  - `GET /api/v1/exchange-rates/current?currency=EUR` retornando HTTP 400 `VALIDATION_ERROR` antes de chamar serviço/repositório.
  - Rota inexistente retornando HTTP 404 com `errorCode: RESOURCE_NOT_FOUND`.
  - Método HTTP não permitido (PUT) retornando HTTP 405 com `errorCode: METHOD_NOT_ALLOWED`.
  - Exceção interna não tratada retornando HTTP 500 com `errorCode: INTERNAL_ERROR`.
- **Testes de Integração com PostgreSQL Real (`ExchangeRateIntegrationTest`):** 5 testes executados e aprovados via Testcontainers (`Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`). Comprovam:
  - Round-trip completo HTTP POST + GET no PostgreSQL real.
  - Disparo real da constraint `uk_exchange_rates_currency_effective_at` traduzida em HTTP 409.
  - Rejeição de sub-microssegundos na camada HTTP.
  - Consulta de vigência determinística com `Clock` fixo injetado selecionando o maior `effectiveAt <= clock.instant()` e ignorando cotações com vigência futura.
  - Preservação do histórico cambial em inserções retroativas.
- **Suíte Completa do Projeto (`mvn test`):**
  - **121 testes executados, 0 falhas, 0 erros, 0 pulados (`BUILD SUCCESS`).**
  - Nenhuma regressão sobre os 90 testes existentes das tarefas anteriores.
- **Conformidade de Estilo:** `git diff --check` executado sem erros.

### 15.2. Desvios Técnicos Registrados
1. **Jackson 3 (Spring Boot 4.1.2):** O starter do Spring Boot 4.1.2 adota o Jackson 3 (`tools.jackson.core:jackson-databind:3.1.7`). O deserializador estrito `StrictStringDeserializer` estende `tools.jackson.databind.ValueDeserializer<String>` e utiliza a anotação `tools.jackson.databind.annotation.JsonDeserialize`, garantindo compatibilidade nativa com a versão adotada sem dependências concorrentes.
2. **Tratamento Estruturado de Violações:** O `ExchangeRateService` inspeciona prioritariamente `org.hibernate.exception.ConstraintViolationException` e `java.sql.SQLException` exigindo cumulativamente SQLState `23505` e o nome da constraint `uk_exchange_rates_currency_effective_at`.
3. **Resolução de Regressões HTTP no Handler Global:** A precedência `@Order(Ordered.HIGHEST_PRECEDENCE)` foi mantida com o acréscimo de handlers específicos para `NoResourceFoundException` (404) e `HttpRequestMethodNotSupportedException` (405), além do suporte a `ErrorResponse` no handler genérico, impedindo que falhas de roteamento padrão do Spring MVC fossem convertidas em HTTP 500.

