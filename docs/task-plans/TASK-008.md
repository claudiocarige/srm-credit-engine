# TASK-008 — Plano Técnico de Implementação

## 1. Identificação
- **Tarefa:** `TASK-008` — Identificação e Persistência Mínima de Cedentes e Recebíveis.
- **Objetivo:** disponibilizar os endpoints REST de consulta de cedentes (`GET /api/v1/assignors`) e de cadastro e consulta de recebíveis (`POST /api/v1/receivables`, `GET /api/v1/receivables` e `GET /api/v1/receivables/{id}`) em conformidade estrita com o contrato oficial (`docs/openapi/srm-credit-engine.yaml`) e `docs/contracts.md`, garantindo a carga inicial (seed) determinística de cedentes, normalização e unicidade de `documentNumber` por cedente (inclusive sob concorrência), validação temporal via `Clock` UTC injetável (`dueDate > operationDate`), rejeição de propriedades JSON desconhecidas e de coerções numéricas em strings decimais, e tratamento padronizado de erros via `ProblemDetail` (`application/problem+json`).
- **Status do planejamento:** `CONCLUÍDA (DONE)`.
- **Referências:**
  - `SPEC.md` (§§1, 3 e 5);
  - `DECISIONS.md`:
    - Decisões arquiteturais e técnicas `APPROVED`: `DEC-001` (Stack Java 21, Spring Boot, JPA/Hibernate, PostgreSQL e Flyway), `DEC-002` (Monólito modular e Package by Feature), `DEC-003` (Transporte monetário em string decimal exata e `BigDecimal` com escala 2), `DEC-005` (Controle temporal via `Clock` UTC injetável), `DEC-007` (Estados de recebíveis `OPEN` $\rightarrow$ `SETTLED` e integridade transacional), `DEC-008` (Persistência PostgreSQL, Flyway, constraints nomeadas e prevenção de `N+1`) e `DEC-010` (Estratégia de testes orientada aos riscos do negócio);
    - Funcionalidades formalmente adiadas (`DEFERRED`, com decisão de adiamento aprovada em `DECISIONS.md`): `DEC-012` (Autenticação e autorização completas adiadas), `DEC-014` (Integração bancária externa adiada), `DEC-015` (Liquidação parcial e estornos financeiros adiados), `DEC-016` (Microsserviços, mensageria e EDA adiados) e `DEC-017` (Cache distribuído e observabilidade avançada adiados);
  - `docs/contracts.md` (§§1.1, 1.2, 2, 4.2, 4.3);
  - `docs/openapi/srm-credit-engine.yaml` (paths `/api/v1/assignors`, `/api/v1/receivables`, `/api/v1/receivables/{id}`; header `ReceivableLocationHeader`; schemas `AssignorResponse`, `CreateReceivableRequest`, `ReceivableResponse`, `ReceivableType`, `ReceivableStatus`, `FaceValueBrlInput`, `MonetaryAmountString`, `ProblemDetail`);
  - `AGENTS.md` (`G-01` a `G-04`, §12 Code Review);
  - `TASKS.md` (`TASK-008`).
- **Dependências:** `TASK-002` (`VALIDATION`), `TASK-005` (`DONE`).

### 1.1. Verificação Documental da Dependência `TASK-002`
- Embora a `TASK-002` esteja registrada com status `VALIDATION` em `TASKS.md` (aguardando apenas a transição administrativa para `DONE`), a inspeção cruzada de `docs/task-plans/TASK-002.md` (§10 e §12), `docs/openapi/srm-credit-engine.yaml` e `docs/contracts.md` confirma que todas as revisões contratuais (`1.0` a `1.6`) foram integralmente concluídas, validadas e aprovadas (`APPROVED`).
- Os endpoints `/api/v1/assignors`, `/api/v1/receivables` e `/api/v1/receivables/{id}`, o header `ReceivableLocationHeader`, os schemas `AssignorResponse`, `CreateReceivableRequest` (com `additionalProperties: false`) e `ReceivableResponse`, bem como os códigos semânticos `ASSIGNOR_NOT_FOUND`, `RECEIVABLE_NOT_FOUND`, `DUPLICATE_RECEIVABLE_DOCUMENT`, `INVALID_DUE_DATE` e `VALIDATION_ERROR`, estão completamente fechados e estáveis.
- **Conclusão de governança:** não existe nenhuma pendência contratual na `TASK-002` que bloqueie a implementação da `TASK-008`. O status da `TASK-002` em `TASKS.md` não será modificado sem autorização humana específica.

---

## 2. Diagnóstico do Estado Atual do Repositório

| Componente | Situação Atual no Código | Ação na `TASK-008` |
|---|---|---|
| Schema PostgreSQL (`assignors` e `receivables`) | Criado em `V1__create_assignors_and_receivables.sql` (`TASK-005`) com constraints `pk_assignors`, `uk_assignors_document`, `ck_assignors_document_digits`, `ck_assignors_name_not_blank`, `pk_receivables`, `fk_receivables_assignor`, `uk_receivables_assignor_document_number`, `ck_receivables_document_number_format`, `ck_receivables_type`, `ck_receivables_face_value_brl_range`, `ck_receivables_status`. | **PRESERVAR `V1` e `V2` INTOCADAS** e criar `V3__seed_assignors.sql` para a carga inicial determinística de cedentes. |
| Entidades JPA (`Assignor`, `Receivable`, `ReceivableStatus`) | Implementadas em `assignor.persistence`, `receivable.persistence` e `receivable.domain` (`TASK-005`) com validações no construtor (`trim().toUpperCase(Locale.ROOT)` para `documentNumber`, `scale() <= 2`, faixa `[0.01, 999999999.99]`, `status = OPEN`, `@Version`). | **REUTILIZAR E PRESERVAR INTOCADAS**, sem modificar as entidades JPA antecipadamente e sem criar modelos paralelos ou mappers externos. |
| Validação estrita de strings JSON (`StrictStringDeserializer`) | Disponível em `common.web.StrictStringDeserializer` (`TASK-007`). | **REUTILIZAR** nos campos `documentNumber` e `faceValueBrl` de `CreateReceivableRequest`. |
| Rejeição de propriedades JSON desconhecidas | `spring.jackson.deserialization.fail-on-unknown-properties: true` ativo em `application.yaml`. | **REUTILIZAR** e comprovar por testes `MockMvc` em `CreateReceivableRequest`. |
| Exceção temporal `InvalidDueDateException` | Criada na `TASK-007` em `simulation.application.exception.InvalidDueDateException` e já tratada no `GlobalExceptionHandler` (`422 INVALID_DUE_DATE`). | **MOVER** para `common.exception.InvalidDueDateException` para compartilhamento limpo entre `simulation`, `receivable` e (futuramente) `settlement`. |
| Repositórios, Services, DTOs e Controllers de `assignor` e `receivable` | Ainda não existem. | **CRIAR** conforme arquitetura modular por feature. |

---

## 3. Especificação Funcional e Contratual (`docs/openapi/srm-credit-engine.yaml`)

### 3.1. `GET /api/v1/assignors` (`listAssignors`)
- **Finalidade:** listar os cedentes disponíveis por carga inicial (seed), ordenados por `name ASC`.
- **Resposta de Sucesso (`HTTP 200 OK` — `application/json`):**
  - Array JSON de `AssignorResponse`:
    - `id` (`UUID`, obrigatório)
    - `document` (`String`, obrigatório, `^[0-9]{11,14}$`)
    - `name` (`String`, obrigatório, `1..150` caracteres)

### 3.2. `POST /api/v1/receivables` (`createReceivable`)
- **Finalidade:** cadastrar explicitamente um recebível elegível em estado inicial `OPEN` vinculado a um cedente existente.
- **Request Body (`CreateReceivableRequest` — `additionalProperties: false`):**
  - `assignorId` (`UUID`, obrigatório)
  - `documentNumber` (`String`, obrigatório, padrão `^\s*[A-Za-z0-9._/-]{1,64}\s*$`; normalizado no backend com `trim().toUpperCase(Locale.ROOT)`)
  - `type` (`ReceivableType`, obrigatório: `DUPLICATA_MERCANTIL` | `CHEQUE_PRE_DATADO`)
  - `faceValueBrl` (`FaceValueBrlInput`, obrigatório: string decimal entre `"0.01"` e `"999999999.99"` com até 2 casas decimais)
  - `dueDate` (`LocalDate`, obrigatório: formato `YYYY-MM-DD`, estritamente posterior a `operationDate` em UTC)
- **Resposta de Sucesso (`HTTP 201 Created` — `application/json`):**
  - **Header obrigatório:** `Location: /api/v1/receivables/{id}` (`ReceivableLocationHeader`).
  - **Body (`ReceivableResponse`):**
    - `id` (`UUID`)
    - `assignorId` (`UUID`)
    - `documentNumber` (`String` normalizada: `^[A-Z0-9._/-]{1,64}$`)
    - `type` (`ReceivableType`)
    - `faceValueBrl` (`MonetaryAmountString`, 2 casas decimais exatas, ex.: `"100000.00"`)
    - `dueDate` (`LocalDate`, `YYYY-MM-DD`)
    - `status` (`ReceivableStatus`: `"OPEN"`)
    - `createdAt` (`Instant`, ISO-8601 UTC)
- **Respostas de Erro (`application/problem+json`):**
  - `400 Bad Request` (`errorCode: VALIDATION_ERROR`): payload malformado, propriedade desconhecida (`additionalProperties: false`), `faceValueBrl` ou `documentNumber` enviado como número JSON sem aspas, campos obrigatórios nulos/ausentes, `faceValueBrl` fora de `[0.01, 999999999.99]` (incluindo `"0.00"`) ou `documentNumber` inválido.
  - `404 Not Found` (`errorCode: ASSIGNOR_NOT_FOUND`): `assignorId` informado não existe na tabela `assignors`.
  - `409 Conflict` (`errorCode: DUPLICATE_RECEIVABLE_DOCUMENT`): já existe recebível cadastrado para o mesmo `(assignorId, documentNumber)` após normalização (`trim` + `UPPERCASE`).
  - `422 Unprocessable Content` (`errorCode: INVALID_DUE_DATE`): `dueDate <= operationDate` (onde `operationDate = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC)`).

### 3.3. `GET /api/v1/receivables` (`listReceivablesByAssignor`)
- **Finalidade:** listar os recebíveis pertencentes ao cedente informado filtrados por estado (padrão `OPEN`), ordenados por `dueDate ASC, documentNumber ASC`.
- **Semântica de retorno e filtro:**
  - Retorna **array JSON direto** (`List<ReceivableResponse>`), sem paginação (conforme contrato OpenAPI aprovado; a paginação obrigatória pertence ao extrato financeiro na `TASK-011`).
  - Quando `status` é omitido na query string, aplica o padrão `OPEN`.
  - Quando `status=SETTLED` é informado, retorna apenas os recebíveis do cedente com `status = SETTLED`.
  - Quando o cedente existe mas não possui recebíveis no `status` solicitado, retorna **`200 OK` com lista vazia `[]`** (não `404`).
  - Quando o `assignorId` não existe na tabela `assignors`, retorna **`404 Not Found` (`errorCode: ASSIGNOR_NOT_FOUND`)**.
- **Query Parameters:**
  - `assignorId` (`UUID`, obrigatório).
  - `status` (`ReceivableStatus`, opcional, valor padrão `OPEN`).
- **Respostas de Erro (`application/problem+json`):**
  - `400 Bad Request` (`errorCode: VALIDATION_ERROR`): `assignorId` ausente ou com formato UUID inválido, ou `status` inválido.
  - `404 Not Found` (`errorCode: ASSIGNOR_NOT_FOUND`): `assignorId` não encontrado na tabela `assignors`.

### 3.4. `GET /api/v1/receivables/{id}` (`getReceivableById`)
- **Finalidade:** consultar os dados e o estado atual (`OPEN` ou `SETTLED`) de um recebível específico pelo identificador retornado no header `Location`.
- **Path Parameter:**
  - `id` (`UUID`, obrigatório).
- **Resposta de Sucesso (`HTTP 200 OK` — `application/json`):**
  - `ReceivableResponse`.
- **Respostas de Erro (`application/problem+json`):**
  - `400 Bad Request` (`errorCode: VALIDATION_ERROR`): `id` com formato UUID inválido.
  - `404 Not Found` (`errorCode: RECEIVABLE_NOT_FOUND`): recebível inexistente para o `id` informado.

---

## 4. Arquitetura, Persistência, Carga Inicial (Seed) e Isolamento de Testes

### 4.1. Organização de Pacotes (`DEC-002` — Package by Feature)
```text
com.claudiocarige.backendcreditengine
├── common
│   ├── exception
│   │   └── InvalidDueDateException.java         (MOVIDA de simulation.application.exception)
│   └── web
│       ├── GlobalExceptionHandler.java          (ALTERADA: +ASSIGNOR_NOT_FOUND, +RECEIVABLE_NOT_FOUND, +DUPLICATE_RECEIVABLE_DOCUMENT, +MissingServletRequestParameterException)
│       └── StrictStringDeserializer.java        (EXISTENTE)
├── assignor
│   ├── persistence
│   │   ├── Assignor.java                        (EXISTENTE — INTOCADA)
│   │   └── AssignorRepository.java              (CRIAR)
│   ├── application
│   │   ├── AssignorService.java                 (CRIAR)
│   │   └── exception
│   │       └── AssignorNotFoundException.java   (CRIAR)
│   └── web
│       ├── AssignorController.java              (CRIAR)
│       └── dto
│           └── AssignorResponse.java            (CRIAR)
└── receivable
    ├── domain
    │   └── ReceivableStatus.java                (EXISTENTE — INTOCADA)
    ├── persistence
    │   ├── Receivable.java                      (EXISTENTE — INTOCADA)
    │   └── ReceivableRepository.java            (CRIAR)
    ├── application
    │   ├── ReceivableService.java               (CRIAR)
    │   └── exception
    │       ├── DuplicateReceivableDocumentException.java (CRIAR)
    │       └── ReceivableNotFoundException.java          (CRIAR)
    └── web
        ├── ReceivableController.java            (CRIAR)
        └── dto
            ├── CreateReceivableRequest.java     (CRIAR)
            └── ReceivableResponse.java          (CRIAR)
```
- **Justificativa de simplicidade (`KISS` / `YAGNI`):**
  - Sem interfaces artificiais `*Service` / `*ServiceImpl` nem mappers externos para conversões triviais (uso de métodos fábrica estáticos `AssignorResponse.from(Assignor)` e `ReceivableResponse.from(Receivable)`).

### 4.2. Carga Inicial Determinística de Cedentes (`V3__seed_assignors.sql`) e Isolamento entre Testes
1. **Migration `V3__seed_assignors.sql`:**
   - Preserva `V1` e `V2` intocadas e insere os dois cedentes canônicos definidos nos exemplos do `docs/openapi/srm-credit-engine.yaml`:
     ```sql
     INSERT INTO assignors (id, document, name, created_at)
     VALUES
         ('8f14e45f-ceea-467a-9a38-1b4c9f8b0001', '12345678000190', 'Industria Alfa Ltda', '2026-11-04T00:00:00Z'),
         ('9a25f560-dffb-478b-8b49-2c5da09c0002', '98765432000110', 'Comercio Beta S.A.', '2026-11-04T00:00:00Z')
     ON CONFLICT (id) DO NOTHING;
     ```
2. **Estratégia Determinística de Isolamento de Dados entre Testes de Integração (Independente da Ordem de Execução):**
   - **Problema identificado na revisão:** As classes existentes `AssignorPersistenceTest` e `ReceivablePersistenceTest` compartilham o container PostgreSQL (`TestcontainersConfiguration`) e executam `DELETE FROM receivables; DELETE FROM assignors;` no `@BeforeEach`, deixando a tabela `assignors` vazia (ou apenas com registros temporários de teste) ao término de sua execução. Se outra classe de teste rodar depois delas assumindo que o seed da migration `V3` permaneceu intacto, haveria dependência da ordem de execução das classes.
   - **Solução simples e determinística (sem criar abstrações complexas):**
     - Nos testes existentes `AssignorPersistenceTest` e `ReceivablePersistenceTest`, adicionar um método `@AfterEach` que limpa os dados temporários criados pelo teste (`DELETE FROM receivables; DELETE FROM assignors;`) e restaura de forma idempotente os dois cedentes canônicos do seed `V3` (`INSERT INTO assignors ... ON CONFLICT (id) DO NOTHING`).
     - Além disso, em `ReceivableIntegrationTest`, o `@BeforeEach` também limpará `receivables` e `assignors` e executará o `INSERT ... ON CONFLICT (id) DO NOTHING` dos dois cedentes canônicos do seed `V3`.
     - Dessa forma:
       1. Logo após o Flyway rodar (`DatabaseSchemaTest`), o banco contém exatamente o seed `V3`.
       2. Mesmo que `AssignorPersistenceTest`, `ReceivablePersistenceTest`, `SimulationIntegrationTest` e `ReceivableIntegrationTest` rodem em qualquer ordem ou isoladamente, cada classe encontra e deixa o banco com o seed `V3` íntegro, eliminando 100% do acoplamento temporal/ordenacional entre testes.

### 4.3. Lazy Loading (`open-in-view=false`) e Verificação de Ausência de `N+1` (`DEC-008`)
1. **Preservação das entidades JPA:**
   - As entidades `Assignor.java` e `Receivable.java` serão **preservadas sem alterações**.
2. **Comportamento com `spring.jpa.open-in-view=false`:**
   - Em `Receivable.java`, `assignor` está mapeado com `@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "assignor_id", nullable = false, updatable = false)`.
   - Para garantir que `ReceivableResponse.from(receivable)` funcione com `open-in-view=false` sem `LazyInitializationException` e sem disparar consultas `N+1`:
     - Em `ReceivableController`, ou `ReceivableService` retorna os objetos já inicializados dentro da transação, ou `ReceivableRepository` utiliza consulta que assegura o acesso seguro ao `assignor.getId()`.
     - **Verificação empírica obrigatória nos testes de integração (`ReceivableIntegrationTest`):**
       - Verificar que `POST /api/v1/receivables`, `GET /api/v1/receivables/{id}` e `GET /api/v1/receivables` respondem com `open-in-view=false` ativo sem `LazyInitializationException`.
       - Utilizar as estatísticas de sessão do Hibernate (`EntityManagerFactory.unwrap(SessionFactory.class).getStatistics()`) durante `GET /api/v1/receivables?assignorId=...` com múltiplos recebíveis (ex.: 5 títulos cadastrados) para comprovar que a quantidade de instruções SQL executadas é **constante** e não cresce proporcionalmente ao número de recebíveis retornados (ausência comprovada de `N+1`).
       - Somente caso o teste com `Hibernate Statistics` evidencie inicialização adicional do proxy `Assignor`, será adicionado `JOIN FETCH r.assignor` na query do `ReceivableRepository` (sem tocar nas entidades JPA).

---

## 5. Ordem de Validação e Tratamento de Concorrência em `ReceivableService`

### 5.1. Criação de Recebível (`createReceivable`) e Tratamento de Concorrência
```java
@Transactional
public Receivable createReceivable(
        UUID assignorId,
        String rawDocumentNumber,
        ReceivableType type,
        BigDecimal faceValueBrl,
        LocalDate dueDate
) {
    Instant createdAt = clock.instant();
    LocalDate operationDate = LocalDate.ofInstant(createdAt, ZoneOffset.UTC);

    // 1. Validação temporal de negócio (422 INVALID_DUE_DATE)
    if (!dueDate.isAfter(operationDate)) {
        throw new InvalidDueDateException(operationDate, dueDate);
    }

    // 2. Verificação de existência do cedente (404 ASSIGNOR_NOT_FOUND)
    Assignor assignor = assignorRepository.findById(assignorId)
            .orElseThrow(() -> new AssignorNotFoundException(assignorId));

    // 3. Normalização canônica (trim + UPPERCASE)
    String normalizedDoc = rawDocumentNumber.trim().toUpperCase(Locale.ROOT);

    // 4. Verificação prévia de unicidade por (assignorId, documentNumber) (409 DUPLICATE_RECEIVABLE_DOCUMENT)
    if (receivableRepository.existsByAssignor_IdAndDocumentNumber(assignorId, normalizedDoc)) {
        throw new DuplicateReceivableDocumentException(assignorId, normalizedDoc);
    }

    Receivable receivable = new Receivable(
            assignor,
            normalizedDoc,
            type,
            faceValueBrl,
            dueDate,
            createdAt
    );

    try {
        return receivableRepository.saveAndFlush(receivable);
    } catch (DataIntegrityViolationException ex) {
        if (isDuplicateReceivableConstraint(ex)) {
            throw new DuplicateReceivableDocumentException(assignorId, normalizedDoc, ex);
        }
        throw ex;
    }
}
```
- **Inspeção estrita da constraint e SQLState (`isDuplicateReceivableConstraint`):**
  - Percorre a cadeia de causas (`Throwable`) procurando `org.postgresql.util.PSQLException`.
  - Verifica simultaneamente:
    1. ` SQLState == "23505"` (`unique_violation` do PostgreSQL); e
    2. `psql.getServerErrorMessage() != null && "uk_receivables_assignor_document_number".equals(psql.getServerErrorMessage().getConstraint())`.
  - Qualquer outra `DataIntegrityViolationException` (de outra constraint ou outro SQLState) não é mascarada como `DuplicateReceivableDocumentException` e é repropagada integralmente.

---

## 6. Modelagem dos DTOs e Tratamento Global de Erros

### 6.1. `AssignorResponse`
```java
public record AssignorResponse(
        UUID id,
        String document,
        String name
) {
    public static AssignorResponse from(Assignor assignor) {
        return new AssignorResponse(
                assignor.getId(),
                assignor.getDocument(),
                assignor.getName()
        );
    }
}
```

### 6.2. `CreateReceivableRequest`
```java
public record CreateReceivableRequest(
        @NotNull(message = "assignorId is required")
        UUID assignorId,

        @NotBlank(message = "documentNumber is required")
        @JsonDeserialize(using = StrictStringDeserializer.class)
        @Pattern(
                regexp = "^\\s*[A-Za-z0-9._/-]{1,64}\\s*$",
                message = "documentNumber must contain 1 to 64 allowed characters (A-Z, a-z, 0-9, ., _, /, -)"
        )
        String documentNumber,

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
        LocalDate dueDate
) {}
```

### 6.3. `ReceivableResponse`
```java
public record ReceivableResponse(
        UUID id,
        UUID assignorId,
        String documentNumber,
        ReceivableType type,
        String faceValueBrl,
        LocalDate dueDate,
        ReceivableStatus status,
        Instant createdAt
) {
    public static ReceivableResponse from(Receivable receivable) {
        return new ReceivableResponse(
                receivable.getId(),
                receivable.getAssignor().getId(),
                receivable.getDocumentNumber(),
                receivable.getType(),
                receivable.getFaceValueBrl().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                receivable.getDueDate(),
                receivable.getStatus(),
                receivable.getCreatedAt()
        );
    }
}
```

### 6.4. Atualizações no `GlobalExceptionHandler`
Adicionar ao `GlobalExceptionHandler` existente:
1. **`AssignorNotFoundException`** $\rightarrow$ `HTTP 404 Not Found` (`HttpStatus.NOT_FOUND`), `title: "Not Found"`, `errorCode: "ASSIGNOR_NOT_FOUND"`.
2. **`ReceivableNotFoundException`** $\rightarrow$ `HTTP 404 Not Found` (`HttpStatus.NOT_FOUND`), `title: "Not Found"`, `errorCode: "RECEIVABLE_NOT_FOUND"`.
3. **`DuplicateReceivableDocumentException`** $\rightarrow$ `HTTP 409 Conflict` (`HttpStatus.CONFLICT`), `title: "Conflict"`, `errorCode: "DUPLICATE_RECEIVABLE_DOCUMENT"`.
4. **`MissingServletRequestParameterException`** $\rightarrow$ `HTTP 400 Bad Request` (`HttpStatus.BAD_REQUEST`), `title: "Validation Error"`, `errorCode: "VALIDATION_ERROR"`, com `fieldErrors` indicando o parâmetro obrigatório ausente (ex.: `assignorId` ausente em `GET /api/v1/receivables`).

---

## 7. Critérios de Aceite

- **CA-01:** `GET /api/v1/assignors` retorna `200 OK` com os cedentes cadastrados via seed inicial (`V3`) ordenados por `name ASC` (`Comercio Beta S.A.` antes de `Industria Alfa Ltda`).
- **CA-02:** `POST /api/v1/receivables` cria recebível em estado `OPEN`, normaliza `documentNumber` (`trim` + `UPPERCASE`), retorna `201 Created`, header `Location: /api/v1/receivables/{id}` e body aderente ao schema `ReceivableResponse` com `faceValueBrl` formatado como string decimal de 2 casas.
- **CA-03:** Rejeição via `MockMvc` de propriedades JSON desconhecidas (`additionalProperties: false`) e de valores numéricos sem aspas em `faceValueBrl` ou `documentNumber` em `CreateReceivableRequest` com `HTTP 400`, `application/problem+json` e `errorCode: VALIDATION_ERROR`.
- **CA-04:** Rejeição de `faceValueBrl` inválido (`"0.00"`, negativos, `> 999999999.99`, `> 2` casas decimais) e `documentNumber` inválido (vazio, `> 64` caracteres após trim, caracteres proibidos) com `HTTP 400 VALIDATION_ERROR` e `fieldErrors`.
- **CA-05:** Retorno de `HTTP 422 Unprocessable Content` (`errorCode: INVALID_DUE_DATE`) quando `dueDate <= operationDate` no cadastro de recebível.
- **CA-06:** Retorno de `HTTP 404 Not Found` (`errorCode: ASSIGNOR_NOT_FOUND`) quando `assignorId` não existe em `POST /api/v1/receivables` ou `GET /api/v1/receivables`.
- **CA-07:** Retorno de `HTTP 409 Conflict` (`errorCode: DUPLICATE_RECEIVABLE_DOCUMENT`) ao tentar cadastrar o mesmo `documentNumber` normalizado para o mesmo `assignorId` — tanto sequencialmente quanto sob **concorrência real** (duas requisições simultâneas com sincronização controlada, resultando em 1 sucesso `201`, 1 conflito `409`, 0 erros `500` e 1 único registro persistido) — permitindo o mesmo `documentNumber` para cedentes distintos.
- **CA-08:** Semântica completa de `GET /api/v1/receivables`:
  - Retorna array JSON ordenado por `dueDate ASC, documentNumber ASC` (sem paginação).
  - Quando `status` é omitido, aplica `OPEN`.
  - Quando `status=SETTLED` é informado, retorna apenas recebíveis `SETTLED` (sem executar liquidação).
  - Quando o cedente existe mas não possui recebíveis no status filtrado, retorna `200 OK` com lista vazia `[]`.
  - Com `open-in-view=false`, `ReceivableResponse.from(receivable)` funciona sem `LazyInitializationException` e a listagem de múltiplos recebíveis não produz consultas `N+1`.
- **CA-09:** `GET /api/v1/receivables/{id}` retorna `200 OK` com o recebível correspondente ao URI do header `Location` emitido no `POST`, ou `404 Not Found` (`errorCode: RECEIVABLE_NOT_FOUND`) quando o `id` não existe.
- **CA-10:** Isolamento determinístico dos testes de integração após a migration `V3` (independente da ordem de execução das classes de teste) e zero regressão sobre os 149 testes existentes (`TASK-001` a `TASK-007`).

---

## 8. Plano de Testes Obrigatórios (`DEC-010`)

### 8.1. Testes Unitários de Aplicação (`ReceivableServiceTest`)
Com `Clock` fixo em `2026-11-04T15:20:00Z` (`operationDate = 2026-11-04`):
1. Criação de recebível com sucesso normalizando `documentNumber` (`"  dup-2026-0042  "` $\rightarrow$ `"DUP-2026-0042"`) e definindo `status = OPEN` e `createdAt = 2026-11-04T15:20:00Z`.
2. Rejeição de `dueDate == operationDate` e `dueDate < operationDate` com `InvalidDueDateException` antes de consultar o banco.
3. Rejeição de `assignorId` inexistente com `AssignorNotFoundException`.
4. Rejeição de `(assignorId, documentNumber)` duplicado com `DuplicateReceivableDocumentException`:
   - Via checagem prévia `existsByAssignor_IdAndDocumentNumber`.
   - Via tradução de `DataIntegrityViolationException` contendo `PSQLException` com `SQLState = "23505"` e constraint `"uk_receivables_assignor_document_number"`.
   - Comprovação de que uma `DataIntegrityViolationException` de outra constraint/SQLState **não** é traduzida indevidamente e é repropagada.
5. Listagem por cedente e status:
   - Cedente existente com recebíveis $\rightarrow$ delega ordenação `dueDate ASC, documentNumber ASC`.
   - Cedente existente sem recebíveis no status $\rightarrow$ retorna lista vazia `[]`.
   - Cedente inexistente $\rightarrow$ lança `AssignorNotFoundException`.
6. Consulta por `id` retornando recebível existente ou lançando `ReceivableNotFoundException`.

### 8.2. Testes de Contrato REST (`AssignorControllerTest` e `ReceivableControllerTest` — `@WebMvcTest`)
1. `GET /api/v1/assignors`: retorna `200 OK` com lista ordenada por nome (`id`, `document`, `name`).
2. `POST /api/v1/receivables`: retorna `201 Created`, header `Location: /api/v1/receivables/{id}` e todos os 8 campos do schema `ReceivableResponse`.
3. `POST /api/v1/receivables`: rejeita propriedade JSON desconhecida (`"status": "SETTLED"`) com `400 VALIDATION_ERROR` (`application/problem+json`).
4. `POST /api/v1/receivables`: rejeita `faceValueBrl` numérico sem aspas (`100000.00`) e `documentNumber` numérico sem aspas (`12345`) com `400 VALIDATION_ERROR`.
5. `POST /api/v1/receivables`: rejeita `"0.00"`, negativos, `> 999999999.99`, `> 2` casas decimais e `documentNumber` inválido com `400 VALIDATION_ERROR` e `fieldErrors`.
6. `POST /api/v1/receivables`: traduz `AssignorNotFoundException` (`404 ASSIGNOR_NOT_FOUND`), `DuplicateReceivableDocumentException` (`409 DUPLICATE_RECEIVABLE_DOCUMENT`) e `InvalidDueDateException` (`422 INVALID_DUE_DATE`).
7. `GET /api/v1/receivables`:
   - Retorna `200 OK` com `status` padrão `OPEN` quando omitido.
   - Retorna `200 OK` quando `status=SETTLED` é informado.
   - Retorna `200 OK` com array vazio `[]` quando o cedente não possui títulos no status filtrado.
   - Retorna `400 VALIDATION_ERROR` quando `assignorId` está ausente ou inválido, ou `status` inválido.
   - Retorna `404 ASSIGNOR_NOT_FOUND` quando cedente não existe.
8. `GET /api/v1/receivables/{id}`: retorna `200 OK` quando encontrado, `400 VALIDATION_ERROR` para UUID inválido e `404 RECEIVABLE_NOT_FOUND` quando inexistente.

### 8.3. Testes de Integração (`ReceivableIntegrationTest` — `@SpringBootTest` + Testcontainers PostgreSQL)
1. **Seed inicial (`V3`) e listagem de cedentes:** `GET /api/v1/assignors` retorna `Comercio Beta S.A.` e `Industria Alfa Ltda` em ordem alfabética com os `UUID`s canônicos do OpenAPI.
2. **Round-trip HTTP completo e header `Location` (`open-in-view=false`):** `POST /api/v1/receivables` (com `documentNumber` contendo espaços e letras minúsculas) $\rightarrow$ captura do header `Location` $\rightarrow$ `GET` no URI do `Location` retornando `200 OK` com `documentNumber` normalizado em caixa alta e estado `OPEN`.
3. **Unicidade sequencial e entre cedentes distintos:** segundo `POST /api/v1/receivables` com o mesmo `documentNumber` (em caixa diferente) para o mesmo cedente retorna `409 DUPLICATE_RECEIVABLE_DOCUMENT`; o mesmo `documentNumber` para o segundo cedente retorna `201 Created`.
4. **Concorrência real no cadastro de recebíveis (sem `Thread.sleep()`):**
   - Duas requisições HTTP `POST /api/v1/receivables` disparadas simultaneamente em threads independentes sincronizadas por `CountDownLatch` (`readyLatch` + `startLatch`), enviando o mesmo `assignorId` e `documentNumber` equivalente após normalização (`"  dup-conc-99  "` e `"DUP-CONC-99"`).
   - Comprova que:
     - Exatamente uma resposta é `201 Created` e a outra é `409 Conflict` (`errorCode: "DUPLICATE_RECEIVABLE_DOCUMENT"`), sem nenhum `500 Internal Server Error`.
     - Apenas `1` registro existe na tabela `receivables` no PostgreSQL para `(assignorId, 'DUP-CONC-99')`.
5. **Semântica completa de listagem e ordenação no PostgreSQL:**
   - Cedente existente sem títulos no status solicitado $\rightarrow$ `200 OK` com `[]`.
   - Omitindo `status` $\rightarrow$ retorna apenas títulos `OPEN` ordenados por `dueDate ASC, documentNumber ASC`.
   - Informando `status=SETTLED` (com registro inserido diretamente via `JdbcTemplate` com `status = 'SETTLED'`, sem executar liquidação) $\rightarrow$ retorna apenas o título `SETTLED`.
   - Cedente inexistente $\rightarrow$ `404 ASSIGNOR_NOT_FOUND`.
6. **Comprovação de ausência de `N+1` e funcionamento com `open-in-view=false`:**
   - Cadastra múltiplos recebíveis (ex.: 5 títulos) para o mesmo cedente, limpa o contexto/estatísticas do Hibernate (`Statistics.clear()`) e executa `GET /api/v1/receivables?assignorId=...`.
   - Comprova que todos os itens são serializados com `assignorId` correto e que a contagem de statements SQL (`getPrepareStatementCount()`) permanece constante ($\le 2$: existência do cedente + busca de recebíveis), sem `SELECT` adicional por item.

---

## 9. Matriz de Arquivos Previstos para Criação ou Modificação

| Arquivo | Operação | Justificativa |
|---|---|---|
| `docs/task-plans/TASK-008.md` | **CRIAR / ATUALIZAR** | Planejamento técnico e registro de evidências da TASK-008. |
| `TASKS.md` | **ALTERAR** | Atualização de status e evidências da TASK-008. |
| `backendcreditengine/src/main/resources/db/migration/V3__seed_assignors.sql` | **CRIAR** | Seed inicial determinístico dos cedentes previstos no OpenAPI. |
| `.../test/.../DatabaseSchemaTest.java` | **ALTERAR** | Atualização do teste de histórico do Flyway para validar `V1`, `V2` e `V3`. |
| `.../test/.../assignor/persistence/AssignorPersistenceTest.java` | **ALTERAR** | Adição de `@AfterEach` restaurando de forma idempotente o seed `V3` após limpeza dos dados de teste, garantindo isolamento independente da ordem de execução. |
| `.../test/.../receivable/persistence/ReceivablePersistenceTest.java` | **ALTERAR** | Adição de `@AfterEach` restaurando de forma idempotente o seed `V3` após limpeza dos dados de teste, garantindo isolamento independente da ordem de execução. |
| `.../common/exception/InvalidDueDateException.java` | **MOVER** | Movida de `simulation.application.exception` para `common.exception` para reuso entre módulos. |
| `.../simulation/application/SimulationService.java` | **ALTERAR** | Atualização do import de `InvalidDueDateException`. |
| `.../test/.../simulation/application/SimulationServiceTest.java` | **ALTERAR** | Atualização do import de `InvalidDueDateException`. |
| `.../test/.../simulation/web/SimulationControllerTest.java` | **ALTERAR** | Atualização do import de `InvalidDueDateException`. |
| `.../common/web/GlobalExceptionHandler.java` | **ALTERAR** | Atualização do import de `InvalidDueDateException` e adição de handlers para `AssignorNotFoundException` (`404`), `ReceivableNotFoundException` (`404`), `DuplicateReceivableDocumentException` (`409`) e `MissingServletRequestParameterException` (`400`). |
| `.../assignor/persistence/AssignorRepository.java` | **CRIAR** | Repositório Spring Data JPA para `Assignor`. |
| `.../assignor/application/exception/AssignorNotFoundException.java` | **CRIAR** | Exceção de domínio/aplicação para `404 ASSIGNOR_NOT_FOUND`. |
| `.../assignor/application/AssignorService.java` | **CRIAR** | Serviço de listagem ordenada de cedentes. |
| `.../assignor/web/dto/AssignorResponse.java` | **CRIAR** | DTO de saída conforme schema `AssignorResponse`. |
| `.../assignor/web/AssignorController.java` | **CRIAR** | Controller REST `GET /api/v1/assignors`. |
| `.../receivable/persistence/ReceivableRepository.java` | **CRIAR** | Repositório Spring Data JPA para `Receivable`. |
| `.../receivable/application/exception/ReceivableNotFoundException.java` | **CRIAR** | Exceção para `404 RECEIVABLE_NOT_FOUND`. |
| `.../receivable/application/exception/DuplicateReceivableDocumentException.java` | **CRIAR** | Exceção para `409 DUPLICATE_RECEIVABLE_DOCUMENT`. |
| `.../receivable/application/ReceivableService.java` | **CRIAR** | Serviço transacional de cadastro e consulta de recebíveis. |
| `.../receivable/web/dto/CreateReceivableRequest.java` | **CRIAR** | DTO de entrada conforme schema `CreateReceivableRequest`. |
| `.../receivable/web/dto/ReceivableResponse.java` | **CRIAR** | DTO de saída conforme schema `ReceivableResponse`. |
| `.../receivable/web/ReceivableController.java` | **CRIAR** | Controller REST para `/api/v1/receivables` e `/api/v1/receivables/{id}`. |
| `.../test/.../assignor/web/AssignorControllerTest.java` | **CRIAR** | Testes `@WebMvcTest` de `AssignorController`. |
| `.../test/.../receivable/application/ReceivableServiceTest.java` | **CRIAR** | Testes unitários de `ReceivableService` (incluindo verificação de constraint e SQLState). |
| `.../test/.../receivable/web/ReceivableControllerTest.java` | **CRIAR** | Testes `@WebMvcTest` de `ReceivableController`. |
| `.../test/.../receivable/ReceivableIntegrationTest.java` | **CRIAR** | Testes de integração com PostgreSQL real via Testcontainers (incluindo concorrência com `CountDownLatch`, ausência de `N+1` e semântica completa de listagem). |

### Arquivos Explicitamente Intocados
- Migrations `V1__create_assignors_and_receivables.sql` e `V2__create_exchange_rates.sql`.
- Entidades `Assignor.java`, `Receivable.java`, `ReceivableStatus.java` e `ExchangeRate.java`.
- Todo o pacote `pricing.domain.*`, `exchangerate.*`, `docs/openapi/srm-credit-engine.yaml`, `SPEC.md` e `DECISIONS.md`.

---

## 10. Subtarefas Sequenciais de Implementação

| Ordem | Subtarefa | Descrição e Verificação |
|---|---|---|
| **ST-01** | Atualizar `TASKS.md` e `TASK-008.md` para `IN_PROGRESS` | Registrar início formal da execução após autorização humana. |
| **ST-02** | Criar `V3__seed_assignors.sql`, atualizar `DatabaseSchemaTest`, ajustar `@AfterEach` em `AssignorPersistenceTest` / `ReceivablePersistenceTest` e mover `InvalidDueDateException` para `common.exception` | Executar imediatamente `DatabaseSchemaTest`, `AssignorPersistenceTest`, `ReceivablePersistenceTest` e testes de `Simulation` para comprovar isolamento e zero regressão. |
| **ST-03** | Implementar módulo `assignor` (`AssignorRepository`, `AssignorNotFoundException`, `AssignorService`, `AssignorResponse`, `AssignorController`) | Disponibilizar consulta ordenada de cedentes (`GET /api/v1/assignors`). |
| **ST-04** | Implementar módulo `receivable` (`ReceivableRepository`, exceções, `ReceivableService`, DTOs, `ReceivableController`) e atualizar `GlobalExceptionHandler` | Disponibilizar cadastro (`201` + `Location`), listagem (`200`) e consulta por ID (`200`), além do mapeamento de `400`, `404`, `409` e `422`. |
| **ST-05** | Implementar e executar `AssignorControllerTest`, `ReceivableServiceTest` e `ReceivableControllerTest` | Comprovar regras de negócio, inspeção de constraint/SQLState e conformidade contratual HTTP (`MockMvc`). |
| **ST-06** | Implementar e executar `ReceivableIntegrationTest` e rodar suíte completa `.\mvnw.cmd test` + `git diff --check` | Comprovar seed `V3`, concorrência com `CountDownLatch`, ausência de `N+1` (`open-in-view=false`), semântica de listagem e zero regressão em toda a suíte; atualizar status para `VALIDATION`. |

---

## 11. Riscos e Medidas Preventivas

| Risco | Impacto | Mitigação |
|---|---|---|
| Limpeza de `assignors` (`DELETE FROM assignors`) em `AssignorPersistenceTest` e `ReceivablePersistenceTest` afetar outras classes de teste no mesmo container PostgreSQL | Dependência da ordem de execução dos testes | Restaurar os 2 cedentes canônicos da migration `V3` (`ON CONFLICT (id) DO NOTHING`) no `@AfterEach` de `AssignorPersistenceTest` e `ReceivablePersistenceTest` e no `@BeforeEach` de `ReceivableIntegrationTest`. |
| Duas requisições concorrentes passarem simultaneamente pelo `existsByAssignor_IdAndDocumentNumber` antes do commit | Retorno de `500` na requisição perdedora se a constraint não for traduzida | Capturar `DataIntegrityViolationException` no `saveAndFlush`, validar explicitamente `PSQLException` (`SQLState == "23505"` e constraint `"uk_receivables_assignor_document_number"`), traduzir para `409 DUPLICATE_RECEIVABLE_DOCUMENT` e comprovar com teste de integração concorrente usando `CountDownLatch`. |
| Inicialização lazy ou `N+1` ao converter `Receivable` para `ReceivableResponse` com `open-in-view=false` | `LazyInitializationException` ou queries extras por item listado | Manter conversão dentro de contexto transacional ou query adequada e comprovar com `Hibernate Statistics` no `ReceivableIntegrationTest` que a contagem de queries não cresce com o número de itens. |
| `MissingServletRequestParameterException` ao omitir `assignorId` em `GET /api/v1/receivables` cair no handler genérico sem `errorCode: VALIDATION_ERROR` | Resposta `400` com `errorCode: HTTP_400` em vez de `VALIDATION_ERROR` | Adicionar `@ExceptionHandler(MissingServletRequestParameterException.class)` explícito no `GlobalExceptionHandler` retornando `errorCode: VALIDATION_ERROR` e `fieldErrors`. |

---

## 12. Resultados e Evidências de Execução

- **Estado atual:** `DONE` (Implementação e ajustes pós-Code Review concluídos, validados e aprovados em 2026-11-09T00:08:00-03:00).
- **Resumo das alterações implementadas:**
  - **Migration `V3` e isolamento determinístico:** Criada `V3__seed_assignors.sql` com os 2 cedentes canônicos do OpenAPI; atualizados `DatabaseSchemaTest`, `AssignorPersistenceTest` (`@AfterEach`) e `ReceivablePersistenceTest` (`@AfterEach`) para restauração idempotente do seed `V3` independente da ordem de execução das classes de teste.
  - **Módulo `assignor`:** Implementados `AssignorRepository`, `AssignorNotFoundException`, `AssignorService`, `AssignorResponse` e `AssignorController` (`GET /api/v1/assignors`).
  - **Módulo `receivable`:** Implementados `ReceivableRepository` (com `@EntityGraph(attributePaths = "assignor")` para compatibilidade com `open-in-view=false` e prevenção de `N+1`), `ReceivableNotFoundException`, `DuplicateReceivableDocumentException`, `ReceivableService`, `CreateReceivableRequest`, `ReceivableResponse` e `ReceivableController` (`POST /api/v1/receivables`, `GET /api/v1/receivables`, `GET /api/v1/receivables/{id}`).
  - **Exceções compartilhadas e tratamento global:** Movida `InvalidDueDateException` para `common.exception` e atualizados `GlobalExceptionHandler` (`404 ASSIGNOR_NOT_FOUND`, `404 RECEIVABLE_NOT_FOUND`, `409 DUPLICATE_RECEIVABLE_DOCUMENT` e `400 VALIDATION_ERROR` em `MissingServletRequestParameterException`).
  - **Ajustes Pós-Code Review:**
    1. Removido `<scope>runtime</scope>` da dependência existente `org.postgresql:postgresql` em `pom.xml` para permitir inspeção tipada de `PSQLException` em `ReceivableService`.
    2. `ReceivableService.isDuplicateReceivableConstraint()` utiliza exclusivamente metadados estruturados (`PSQLException.getSQLState() == "23505"` + `PSQLException.getServerErrorMessage().getConstraint() == "uk_receivables_assignor_document_number"` e `ConstraintViolationException.getSQLState()` + `getConstraintName()`), sem depender de mensagens textuais (`getMessage()`).
    3. Adicionados em `ReceivableControllerTest` os testes explícitos para `GET /api/v1/receivables?assignorId=abc` (`400 VALIDATION_ERROR`) e `GET /api/v1/receivables/abc` (`400 VALIDATION_ERROR`), e em `ReceivableServiceTest` os testes de `PSQLException` + `ServerErrorMessage` para constraint correspondente, constraint divergente (`uk_other_constraint`) e `SQLState` divergente (`23503`).
- **Evidências de Testes Automatizados:**
  - **Suíte da `TASK-008` (`AssignorControllerTest`, `ReceivableServiceTest`, `ReceivableControllerTest`, `ReceivableIntegrationTest`):** `46` testes executados, `0` falhas, `0` erros, `0` ignorados (`AssignorControllerTest`: `1`, `ReceivableServiceTest`: `12`, `ReceivableControllerTest`: `27`, `ReceivableIntegrationTest`: `6`).
  - **Concorrência real com PostgreSQL (`ReceivableIntegrationTest.shouldHandleConcurrentDuplicateReceivableCreationGracefully`):** Duas threads sincronizadas com `CountDownLatch` e `CyclicBarrier` (sem `Thread.sleep()`) dispararam simultaneamente `POST /api/v1/receivables` para o mesmo `assignorId` e `documentNumber` equivalente (`"  dup-conc-99  "` e `"DUP-CONC-99"`), colidindo na constraint `uk_receivables_assignor_document_number` (`SQLState: 23505`) e resultando em `[201, 409]` (`errorCode: "DUPLICATE_RECEIVABLE_DOCUMENT"`), `0` respostas `500` e `1` único registro persistido no banco.
  - **Lazy Loading (`open-in-view=false`) e ausência de `N+1` (`ReceivableIntegrationTest.shouldNotTriggerNPlusOneQueriesWhenListingMultipleReceivables`):** Com 5 recebíveis cadastrados, `Hibernate Statistics` comprovou execução constante de `<= 2` statements SQL na listagem.
  - **Suíte Maven completa (`.\mvnw.cmd test`):** `195` testes executados, `0` falhas, `0` erros, `0` ignorados (`BUILD SUCCESS`).
  - **Verificação de formatação (`git diff --check`):** Sem erros de whitespace/formatação.
