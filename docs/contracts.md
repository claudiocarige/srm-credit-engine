# SRM Credit Engine — Contratos Mínimos de Dados e API (`docs/contracts.md`)

> **Origem:** entregável normativo da [`TASK-002`](task-plans/TASK-002.md) (Revisão `1.3`).  
> **Fontes superiores:** [`SPEC.md`](../SPEC.md) e [`DECISIONS.md`](../DECISIONS.md).  
> **Finalidade:** fixar, antes das migrations (`TASK-005`) e dos endpoints/componentes (`TASK-006` a `TASK-015`), os identificadores, estados, limites numéricos, payloads REST, precedência de idempotência, snapshot imutável e semântica de erros.

---

## 1. Convenções Transversais e Limites Numéricos

### 1.1. Tipos, Datas, Tempo e Serialização
- **Identificadores primários:** `UUID` (string canônica lowercase de 36 caracteres na API; tipo nativo `UUID` no PostgreSQL).
- **Valores monetários, taxas e câmbio em JSON:** serializados **sempre como `String` decimal canônica** com ponto como separador decimal (ex.: `"92859.94"`, `"0.015000"`, `"5.432100"`), sem símbolo monetário nem agrupador de milhar (`SPEC.md` §2.1, `DEC-003`).
- **Fonte única de tempo (`Clock` UTC):** o backend controla integralmente o instante da operação via `java.time.Clock` em **UTC** (clientes não enviam timestamp da operação):
  - `operationInstant` = `Instant.now(clock)` (serializado em ISO-8601 UTC, ex.: `"2026-11-04T15:30:00Z"`).
  - `operationDate` = `LocalDate.ofInstant(operationInstant, ZoneOffset.UTC)` (`YYYY-MM-DD`), usada para validar `dueDate > operationDate` e converter dias corridos em meses comerciais inteiros: `days = DAYS.between(operationDate, dueDate)` e `termMonths = (days + 29) / 30` (equivalente inteiro exato a `ceil(days / 30)` para `days >= 1`, sem ponto flutuante).
- **Enums públicos:**
  - `ReceivableType`: `"DUPLICATA_MERCANTIL"` | `"CHEQUE_PRE_DATADO"`
  - `ReceivableStatus`: `"OPEN"` | `"SETTLED"`
  - `CurrencyCode`: `"BRL"` | `"USD"` (para cotações em `exchange_rates`, apenas `"USD"` cotado em `BRL` por `1 USD`).

### 1.2. Política de Limites Financeiros e Prevenção de Overflow
Mantendo intactas as escalas do [`SPEC.md`](../SPEC.md) (`NUMERIC(19,2)` para valores monetários — até `17` dígitos inteiros, máx. `99999999999999999.99` —, `NUMERIC(19,6)` para câmbio e `NUMERIC(10,6)` para taxas mensais), aplica-se validação declarativa na entrada (`@DecimalMin`, `@DecimalMax`, `@Digits` → `400 Bad Request`), reforçada no domínio e em constraints `CHECK` do PostgreSQL:

| Parâmetro / Campo | Mínimo | Máximo | Casas decimais | Coluna PostgreSQL |
|---|---:|---:|---:|---|
| `faceValueBrl` (por título / simulação) | `"0.01"` | `"999999999.99"` (`9` dígitos inteiros) | `2` | `NUMERIC(19,2)` |
| `rateBrl` (`BRL` por `1 USD`) | `"0.000001"` | `"9999999.999999"` (`7` dígitos inteiros) | `6` | `NUMERIC(19,6)` |
| `baseRateMonthly` / `spreadMonthly` | `"0.000000"` (`> 0` no spread) | `"9.999999"` | `6` | `NUMERIC(10,6)` |
| `receivableIds` (tamanho do lote) | `1` item | `100` itens | — | — |

**Invariante contra overflow em lotes de até 100 recebíveis:**
- Em `BRL`, `100 × 999_999_999.99 = 99_999_999_999.00` (`11` dígitos inteiros `<< 17`).
- Em `USD`, mesmo no limite extremo (`100` títulos de `"999999999.99"` convertidos pela menor cotação positiva `"0.000001"`), `totalPaidAmount <= 99_999_999_999_000_000.00`, permanecendo estritamente dentro dos `17` dígitos inteiros de `NUMERIC(19,2)`.

### 1.3. Moeda Explícita de Cada Atributo Financeiro

| Campo JSON | Moeda / Unidade | Definição |
|---|---|---|
| `faceValueBrl`, `totalFaceValueBrl` | **Sempre `BRL`** | Valor nominal do título / soma exata dos itens do lote |
| `presentValueBrl`, `totalPresentValueBrl` | **Sempre `BRL`** | Valor presente em `BRL` arredondado (`HALF_EVEN`, 2 casas) / soma exata dos itens |
| `discountBrl`, `totalDiscountBrl` | **Sempre `BRL`** | `faceValueBrl - presentValueBrl` / soma exata dos itens |
| `paidAmount`, `totalPaidAmount` | **`paymentCurrency` (`BRL` ou `USD`)** | Em `BRL`: `presentValueBrl`; em `USD`: `round_half_even(presentValueBrl / exchangeRateApplied, 2)` / soma exata dos itens |
| `baseRateMonthly`, `spreadMonthly` | Razão decimal a.m. | Ex.: `"0.010000"` (`1,00% a.m.`), `"0.015000"` (`1,50% a.m.`), `"0.025000"` (`2,50% a.m.`) |
| `rateBrl`, `exchangeRateApplied` | **`BRL` por `1 USD`** | Cotação cambial em `USD`; `null` em `exchangeRateApplied` quando `paymentCurrency = "BRL"` |

---

## 2. Ciclo de Vida, Modelo Mínimo de Dados e Unicidade

### 2.1. Entidades Persistentes e Estados
1. **`assignors` (Cedentes):**
   - Campos mínimos: `id` (`UUID` PK), `document` (`VARCHAR(14)` `UNIQUE`, CNPJ/CPF somente dígitos), `name` (`VARCHAR(150)`), `created_at` (`TIMESTAMPTZ`).
   - Origem: **carga inicial de demonstração (seed)** + consulta somente leitura (`GET /api/v1/assignors`), sem CRUD.
2. **`receivables` (Recebíveis):**
   - Campos mínimos: `id` (`UUID` PK), `assignor_id` (`UUID` FK → `assignors.id`), `document_number` (`VARCHAR(64)`), `type` (`VARCHAR(32)`), `face_value_brl` (`NUMERIC(19,2)`), `due_date` (`DATE`), `status` (`VARCHAR(16)`), `version` (`BIGINT` para locking otimista complementar), `created_at` (`TIMESTAMPTZ`).
   - Estados mínimos: nasce exclusivamente como **`OPEN`**; transita de forma irreversível para **`SETTLED`** apenas dentro da transação ACID de liquidação (sem `PUT`, `PATCH`, `DELETE` ou estorno — `DEC-015`).
   - Origem: carga inicial de demonstração + cadastro explícito via `POST /api/v1/receivables`.
   - **Política de normalização e unicidade de `documentNumber` por cedente:**
     - Na entrada, aplica-se `trim()` + `toUpperCase(Locale.ROOT)`.
     - Deve obedecer ao padrão `^[A-Za-z0-9._/-]{1,64}$` após `trim()` (`400 Bad Request` se vazio ou com espaços/caracteres inválidos).
     - Constraint relacional **`UNIQUE (assignor_id, document_number)`**: mesmo cedente + mesmo número normalizado (estando `OPEN` ou `SETTLED`) retorna `409 Conflict` (`DUPLICATE_RECEIVABLE_DOCUMENT`); cedentes distintos podem cadastrar o mesmo número comercial (ex.: `"NF-0001"`).
3. **`exchange_rates` (Histórico Cambial):**
   - Campos mínimos: `id` (`UUID` PK), `currency` (`VARCHAR(3)`, `'USD'`), `rate_brl` (`NUMERIC(19,6)`), `effective_at` (`TIMESTAMPTZ`), `created_at` (`TIMESTAMPTZ`).
   - Estreitamente *append-only* (sem `UPDATE`/`DELETE`), com **`UNIQUE (currency, effective_at)`** para eliminar ambiguidade na seleção da vigência máxima `effective_at <= operationInstant`.
4. **`settlements` (Cabeçalho de Lote Liquidado — Imutável):**
   - Campos mínimos: `id` (`UUID` PK), `idempotency_key` (`VARCHAR(128)` `UNIQUE NOT NULL`), `request_hash` (`CHAR(64)` `NOT NULL`), `assignor_id` (`UUID` FK → `assignors.id`), `payment_currency` (`VARCHAR(3)`), `item_count` (`INTEGER`), `total_face_value_brl` (`NUMERIC(19,2)`), `total_present_value_brl` (`NUMERIC(19,2)`), `total_discount_brl` (`NUMERIC(19,2)`), `exchange_rate_applied` (`NUMERIC(19,6)` nullable), `exchange_rate_effective_at` (`TIMESTAMPTZ` nullable), `total_paid_amount` (`NUMERIC(19,2)`), `operation_date` (`DATE`), `settled_at` (`TIMESTAMPTZ`).
   - `idempotency_key` e `request_hash` são de uso interno de protocolo/auditoria de banco e **não são expostos nos DTOs públicos nem nas consultas de liquidações**.
5. **`settlement_items` (Snapshot Imutável por Recebível Liquidado):**
   - Campos mínimos: `id` (`UUID` PK), `settlement_id` (`UUID` FK → `settlements.id`), `receivable_id` (`UUID` FK → `receivables.id` com **`UNIQUE (receivable_id)`**), `document_number` (`VARCHAR(64)`), `type` (`VARCHAR(32)`), `due_date` (`DATE`), `term_months` (`INTEGER`), `face_value_brl` (`NUMERIC(19,2)`), `base_rate_monthly` (`NUMERIC(10,6)`), `spread_monthly` (`NUMERIC(10,6)`), `present_value_brl` (`NUMERIC(19,2)`), `discount_brl` (`NUMERIC(19,2)`), `payment_currency` (`VARCHAR(3)`), `exchange_rate_applied` (`NUMERIC(19,6)` nullable), `exchange_rate_effective_at` (`TIMESTAMPTZ` nullable), `paid_amount` (`NUMERIC(19,2)`), `settled_at` (`TIMESTAMPTZ`).

---

## 3. Contratos dos Endpoints REST (`/api/v1`)

### 3.1. Cedentes
#### `GET /api/v1/assignors` → `200 OK`
Retorna a lista de cedentes ordenada por `name ASC`:
```json
[
  {
    "id": "8f14e45f-ceea-467a-9a38-1b4c9f8b0001",
    "document": "12345678000190",
    "name": "Industria Alfa Ltda"
  }
]
```

### 3.2. Recebíveis
#### `POST /api/v1/receivables` → `201 Created`
Header de resposta: `Location: /api/v1/receivables/{id}`.
- **Request:**
  ```json
  {
    "assignorId": "8f14e45f-ceea-467a-9a38-1b4c9f8b0001",
    "documentNumber": "DUP-2026-0042",
    "type": "DUPLICATA_MERCANTIL",
    "faceValueBrl": "100000.00",
    "dueDate": "2027-02-02"
  }
  ```
- **Response (`201 Created` — `ReceivableResponse`):**
  ```json
  {
    "id": "c7b3a110-5312-4e7e-9b19-08d1e4a00101",
    "assignorId": "8f14e45f-ceea-467a-9a38-1b4c9f8b0001",
    "documentNumber": "DUP-2026-0042",
    "type": "DUPLICATA_MERCANTIL",
    "faceValueBrl": "100000.00",
    "dueDate": "2027-02-02",
    "status": "OPEN",
    "createdAt": "2026-11-04T15:20:00Z"
  }
  ```
- **Erros:** `400 VALIDATION_ERROR` (formato/limites), `404 ASSIGNOR_NOT_FOUND`, `409 DUPLICATE_RECEIVABLE_DOCUMENT`, `422 INVALID_DUE_DATE` (`dueDate <= operationDate`).

#### `GET /api/v1/receivables/{id}` → `200 OK` | `404 Not Found`
Permite consultar diretamente o recurso referenciado pelo header `Location` do cadastro.
- **`200 OK`:** retorna `ReceivableResponse` (com `status` atual `OPEN` ou `SETTLED`).
- **`404 Not Found`:** código `RECEIVABLE_NOT_FOUND` (se `id` não for `UUID` válido → `400 VALIDATION_ERROR`).

#### `GET /api/v1/receivables?assignorId={uuid}&status=OPEN` → `200 OK`
- `assignorId` obrigatório (`UUID`); `status` opcional (padrão `OPEN`). Ordenação: `due_date ASC, document_number ASC`.
- Retorna `ReceivableResponse[]` (`404 ASSIGNOR_NOT_FOUND` caso o cedente informado não exista).

### 3.3. Gestão Cambial Manual (`Currency Engine`)
#### `POST /api/v1/exchange-rates` → `201 Created`
- **Request:**
  ```json
  {
    "currency": "USD",
    "rateBrl": "5.432100",
    "effectiveAt": "2026-11-04T12:00:00Z"
  }
  ```
- **Response (`201 Created`):**
  ```json
  {
    "id": "3c2a9b10-12ab-4d3e-8f01-9a8b7c6d5e01",
    "currency": "USD",
    "rateBrl": "5.432100",
    "effectiveAt": "2026-11-04T12:00:00Z",
    "createdAt": "2026-11-04T12:00:05Z"
  }
  ```
- **Erros:** `400 VALIDATION_ERROR` (`currency != "USD"`, escala/limites de `rateBrl` fora de `0.000001..9999999.999999` ou `effectiveAt` ausente/inválido), `409 DUPLICATE_EXCHANGE_RATE_EFFECTIVE_AT`.

#### `GET /api/v1/exchange-rates/current?currency=USD` → `200 OK` | `422 Unprocessable Entity`
- `currency` opcional (padrão `"USD"`). Seleciona a cotação com maior `effective_at <= Instant.now(clock)`.
- **`200 OK`:** mesmo formato do retorno do `POST`.
- **`422 Unprocessable Entity`:** código `EXCHANGE_RATE_UNAVAILABLE` quando não houver cotação vigente (`effective_at <= now`).

### 3.4. Simulação Financeira (Sem Efeito Persistente)
#### `POST /api/v1/simulations` → `200 OK`
Recebe exclusivamente os **4 campos obrigatórios** do painel do operador (`SPEC.md` §1.5). **Não persiste registros, não altera títulos e não trava cotação (*sem quote lock*).**
- **Request:**
  ```json
  {
    "type": "DUPLICATA_MERCANTIL",
    "faceValueBrl": "100000.00",
    "dueDate": "2027-02-02",
    "paymentCurrency": "USD"
  }
  ```
- **Response (`200 OK`):**
  ```json
  {
    "simulatedAt": "2026-11-04T15:25:00Z",
    "operationDate": "2026-11-04",
    "type": "DUPLICATA_MERCANTIL",
    "dueDate": "2027-02-02",
    "termMonths": 3,
    "faceValueBrl": "100000.00",
    "baseRateMonthly": "0.010000",
    "spreadMonthly": "0.015000",
    "presentValueBrl": "92859.94",
    "discountBrl": "7140.06",
    "paymentCurrency": "USD",
    "exchangeRateApplied": "5.432100",
    "exchangeRateEffectiveAt": "2026-11-04T12:00:00Z",
    "paidAmount": "17094.67"
  }
  ```
  *(Quando `paymentCurrency = "BRL"`, `exchangeRateApplied` e `exchangeRateEffectiveAt` são `null`, e `paidAmount == presentValueBrl`.)*
- **Erros:** `400 VALIDATION_ERROR`, `422 INVALID_DUE_DATE`, `422 EXCHANGE_RATE_UNAVAILABLE`.

### 3.5. Liquidação em Lote ACID e Idempotente
#### `POST /api/v1/settlements` → `201 Created` (nova) | `200 OK` (replay idempotente)
- **Header obrigatório:** `Idempotency-Key: <1..128 caracteres ASCII visíveis [\x21-\x7E]>` (ausente, em branco ou fora do padrão → `400 INVALID_IDEMPOTENCY_KEY`).
- **Header de resposta na criação inédita (`201 Created`):** `Location: /api/v1/settlements/{settlementId}`.
- **Request:**
  ```json
  {
    "assignorId": "8f14e45f-ceea-467a-9a38-1b4c9f8b0001",
    "paymentCurrency": "USD",
    "receivableIds": [
      "c7b3a110-5312-4e7e-9b19-08d1e4a00101",
      "d9e4b220-6423-4f8f-8c20-19e2f5b00202"
    ]
  }
  ```

#### Canonização determinística do payload (`request_hash` SHA-256)
1. Rejeita-se com `400 DUPLICATE_RECEIVABLE_IN_BATCH` qualquer repetição do mesmo `UUID` dentro de `receivableIds`.
2. Normalizam-se `assignorId` e todos os elementos de `receivableIds` para `UUID` lowercase, ordenando `receivableIds` lexicograficamente (`ASC`), de modo que os mesmos recebíveis enviados em ordens distintas representem a mesma intenção e produzam o mesmo hash.
3. Computa-se `SHA-256` (hex lowercase de 64 caracteres) em `UTF-8` sobre:
   ```text
   assignorId=<assignor_uuid_lower>|paymentCurrency=<BRL|USD>|receivableIds=<uuid1_lower,...,uuidN_lower>
   ```

#### Ordem obrigatória de avaliação e precedência da idempotência
1. **Validação estrutural e canonização (`400 Bad Request`):** header `Idempotency-Key`, limites `1..100` itens, ausência de IDs duplicados no lote e geração de `request_hash`.
2. **Precedência estrita da `Idempotency-Key` (antes de validar estado `OPEN`/`SETTLED`, datas ou câmbio):**
   - Se a `Idempotency-Key` já pertence a uma liquidação concluída:
     - `request_hash` **idêntico** → retorna imediatamente **`200 OK`** com o `SettlementResponse` do snapshot original imutável (**jamais** retorna `409 RECEIVABLE_ALREADY_SETTLED`, pois não reavalia a elegibilidade atual dos títulos).
     - `request_hash` **divergente** → retorna **`409 Conflict` (`IDEMPOTENCY_PAYLOAD_MISMATCH`)**.
   - Se aguardar lock concorrente no PostgreSQL exceder `srm.settlement.lock-timeout-ms` (padrão `3000` ms) → rollback integral sem efeitos persistentes e retorno **`503 Service Unavailable` (`CONCURRENT_SETTLEMENT_TIMEOUT`)** com header **`Retry-After: <segundos>`** (configurável via `srm.settlement.retry-after-seconds`, padrão `2`).
     - **Nota semântica obrigatória:** o valor de `Retry-After` é **exclusivamente uma orientação (*hint*) de intervalo mínimo antes de nova tentativa** (RFC 9110 §10.2.3), **não uma garantia** de que a transação concorrente já terá terminado. O cliente deve preservar a **mesma `Idempotency-Key` e o mesmo payload** ao repetir a tentativa.
3. **Validação de negócio, estado e gravação ACID (executada unicamente para `Idempotency-Key` inédita):**
   - Na mesma transação PostgreSQL (`DEC-007`), processando os recebíveis em ordem crescente de `UUID` para evitar deadlocks:
   - `404 ASSIGNOR_NOT_FOUND` ou `404 RECEIVABLE_NOT_FOUND`.
   - `422 RECEIVABLE_ASSIGNOR_MISMATCH` (algum recebível pertence a outro cedente).
   - **`409 RECEIVABLE_ALREADY_SETTLED`:** ocorre **somente** quando **outra intenção distinta (outra `Idempotency-Key`)** já liquidou algum recebível do lote (`status == SETTLED` ou violação de `UNIQUE(receivable_id)`), causando rollback integral do lote.
   - `422 INVALID_DUE_DATE` (`dueDate <= operationDate`) ou `422 EXCHANGE_RATE_UNAVAILABLE` (em `USD` sem cotação com `effective_at <= settledAt`).
   - Totalização *bottom-up*: cada item é calculado e arredondado (`HALF_EVEN`, 2 casas); os totais do cabeçalho (`totalFaceValueBrl`, `totalPresentValueBrl`, `totalDiscountBrl`, `totalPaidAmount`) são a **soma exata dos valores arredondados dos itens**.
   - Commit único gravando `settlements`, `settlement_items` e transição `receivables.status = SETTLED` → **`201 Created`**.

- **Response (`201 Created` ou `200 OK` — `SettlementResponse`, sem expor `idempotencyKey`):**
  ```json
  {
    "settlementId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "settledAt": "2026-11-04T15:30:00Z",
    "operationDate": "2026-11-04",
    "assignorId": "8f14e45f-ceea-467a-9a38-1b4c9f8b0001",
    "assignorDocument": "12345678000190",
    "assignorName": "Industria Alfa Ltda",
    "itemCount": 2,
    "totalFaceValueBrl": "125000.00",
    "totalPresentValueBrl": "116197.71",
    "totalDiscountBrl": "8802.29",
    "paymentCurrency": "USD",
    "exchangeRateApplied": "5.432100",
    "exchangeRateEffectiveAt": "2026-11-04T12:00:00Z",
    "totalPaidAmount": "21390.94",
    "items": [
      {
        "settlementItemId": "91a0d111-1111-4111-8111-111111111111",
        "receivableId": "c7b3a110-5312-4e7e-9b19-08d1e4a00101",
        "documentNumber": "DUP-2026-0042",
        "type": "DUPLICATA_MERCANTIL",
        "dueDate": "2027-02-02",
        "termMonths": 3,
        "faceValueBrl": "100000.00",
        "baseRateMonthly": "0.010000",
        "spreadMonthly": "0.015000",
        "presentValueBrl": "92859.94",
        "discountBrl": "7140.06",
        "paymentCurrency": "USD",
        "exchangeRateApplied": "5.432100",
        "exchangeRateEffectiveAt": "2026-11-04T12:00:00Z",
        "paidAmount": "17094.67",
        "settledAt": "2026-11-04T15:30:00Z"
      },
      {
        "settlementItemId": "91a0d222-2222-4222-8222-222222222222",
        "receivableId": "d9e4b220-6423-4f8f-8c20-19e2f5b00202",
        "documentNumber": "CHQ-2026-0099",
        "type": "CHEQUE_PRE_DATADO",
        "dueDate": "2027-01-03",
        "termMonths": 2,
        "faceValueBrl": "25000.00",
        "baseRateMonthly": "0.010000",
        "spreadMonthly": "0.025000",
        "presentValueBrl": "23337.77",
        "discountBrl": "1662.23",
        "paymentCurrency": "USD",
        "exchangeRateApplied": "5.432100",
        "exchangeRateEffectiveAt": "2026-11-04T12:00:00Z",
        "paidAmount": "4296.27",
        "settledAt": "2026-11-04T15:30:00Z"
      }
    ]
  }
  ```

### 3.6. Extrato Paginado por Lote e Detalhamento Individual
#### `GET /api/v1/settlements` → `200 OK`
Paginação *server-side* **por lote liquidado**, sem carregar a coleção `items[]` na listagem (evitando consultas `N+1` e paginação em memória) e **sem expor `Idempotency-Key`**.
- **Query params opcionais:**
  - `from` (`LocalDate` `YYYY-MM-DD`): `settled_at >= fromT00:00:00Z`.
  - `to` (`LocalDate` `YYYY-MM-DD`): `settled_at < (to + 1 dia)T00:00:00Z` (`from > to` → `400 INVALID_DATE_RANGE`).
  - `assignorId` (`UUID`).
  - `paymentCurrency` (`BRL` | `USD`).
  - `page` (`int >= 0`, padrão `0`).
  - `size` (`int` entre `1` e **`100`**, padrão `20`; `size < 1` ou `size > 100` ou `page < 0` → `400 VALIDATION_ERROR`).
- **Ordenação determinística fixa:** `settled_at DESC, id DESC`.
- **Response (`PageResponse<SettlementSummaryResponse>`):**
  ```json
  {
    "content": [
      {
        "settlementId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
        "settledAt": "2026-11-04T15:30:00Z",
        "operationDate": "2026-11-04",
        "assignorId": "8f14e45f-ceea-467a-9a38-1b4c9f8b0001",
        "assignorDocument": "12345678000190",
        "assignorName": "Industria Alfa Ltda",
        "itemCount": 2,
        "totalFaceValueBrl": "125000.00",
        "totalPresentValueBrl": "116197.71",
        "totalDiscountBrl": "8802.29",
        "paymentCurrency": "USD",
        "exchangeRateApplied": "5.432100",
        "exchangeRateEffectiveAt": "2026-11-04T12:00:00Z",
        "totalPaidAmount": "21390.94"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
  ```

#### `GET /api/v1/settlements/{settlementId}` → `200 OK` | `404 Not Found`
- **`200 OK`:** retorna `SettlementResponse` completo (cabeçalho + `items[]` ordenado por `documentNumber ASC, receivableId ASC`, **sem expor `Idempotency-Key`**).
- **`404 Not Found`:** código `SETTLEMENT_NOT_FOUND`.

---

## 4. Fluxo Operacional no Frontend Angular

1. **Painel de Simulação (`TASK-013`):** formulário reativo com os **4 campos obrigatórios** (`type`, `faceValueBrl`, `dueDate`, `paymentCurrency`), disparando `POST /api/v1/simulations` com `debounceTime` + `switchMap`. Não executa cálculos locais em `number` nem presume cotação travada.
2. **Cadastro Explícito de Recebível (`TASK-014` / `TASK-008`):** o operador pode reaproveitar `type`, `faceValueBrl` e `dueDate` informados, selecionar o cedente (`GET /api/v1/assignors`), informar `documentNumber` e enviar `POST /api/v1/receivables`, criando o recebível como `OPEN`.
3. **Seleção e Liquidação em Lote (`TASK-014`):** o operador seleciona um cedente, carrega seus títulos elegíveis via `GET /api/v1/receivables?assignorId={uuid}&status=OPEN`, marca `1..N` recebíveis, escolhe `paymentCurrency` e aciona "Liquidar":
   - O Angular gera um `UUID v4` para `Idempotency-Key` **uma única vez por intenção operacional**, desabilita o botão enquanto a requisição estiver em voo e preserva **exatamente a mesma chave e payload** caso o operador realize retry manual após erro de rede ou `503 CONCURRENT_SETTLEMENT_TIMEOUT`.
   - Alterar cedente, moeda ou conjunto de títulos invalida a intenção anterior e gera nova `Idempotency-Key`.
4. **Grid de Extrato (`TASK-015`):** consome `GET /api/v1/settlements` com filtros `from`, `to`, `assignorId`, `paymentCurrency` e paginação `page`/`size` (`<= 100`), permitindo expandir/consultar os itens auditáveis do lote via `GET /api/v1/settlements/{settlementId}`.

---

## 5. Contrato Padronizado de Erros HTTP

Todas as respostas de erro utilizam um envelope único (sem stack trace, nomes de tabelas/constraints internas ou detalhes sensíveis de infraestrutura):

```json
{
  "timestamp": "2026-11-04T15:30:02Z",
  "status": 409,
  "code": "RECEIVABLE_ALREADY_SETTLED",
  "message": "One or more receivables in the batch have already been settled.",
  "path": "/api/v1/settlements",
  "fieldErrors": []
}
```
Para erros `400 VALIDATION_ERROR`, `fieldErrors` lista `{ "field": "faceValueBrl", "message": "must be between 0.01 and 999999999.99" }`.

| HTTP Status | `code` | Situação |
|---:|---|---|
| `400` | `VALIDATION_ERROR` | Payload malformado, tipo/UUID inválido, limites numéricos violados ou `size` fora de `1..100` |
| `400` | `INVALID_IDEMPOTENCY_KEY` | Header `Idempotency-Key` ausente, vazio ou fora do padrão ASCII `1..128` |
| `400` | `DUPLICATE_RECEIVABLE_IN_BATCH` | `receivableIds` contém o mesmo `UUID` repetido no mesmo lote |
| `400` | `INVALID_DATE_RANGE` | Filtro de período do extrato com `from > to` |
| `404` | `ASSIGNOR_NOT_FOUND` | Cedente informado não existe |
| `404` | `RECEIVABLE_NOT_FOUND` | Recebível consultado ou incluído no lote não existe |
| `404` | `SETTLEMENT_NOT_FOUND` | Liquidação consultada em `/settlements/{settlementId}` não existe |
| `409` | `DUPLICATE_RECEIVABLE_DOCUMENT` | Já existe recebível com o mesmo `(assignorId, documentNumber)` normalizado |
| `409` | `DUPLICATE_EXCHANGE_RATE_EFFECTIVE_AT` | Já existe cotação para a mesma `(currency, effectiveAt)` |
| `409` | `IDEMPOTENCY_PAYLOAD_MISMATCH` | Mesma `Idempotency-Key` reutilizada com `request_hash` divergente |
| `409` | `RECEIVABLE_ALREADY_SETTLED` | Outra intenção distinta já liquidou pelo menos um recebível do lote |
| `422` | `INVALID_DUE_DATE` | `dueDate <= operationDate` controlada pelo backend |
| `422` | `RECEIVABLE_ASSIGNOR_MISMATCH` | Recebível do lote não pertence ao `assignorId` informado |
| `422` | `EXCHANGE_RATE_UNAVAILABLE` | Operação/consulta em `USD` sem cotação com `effectiveAt <= operationInstant` |
| `503` | `CONCURRENT_SETTLEMENT_TIMEOUT` | Espera por lock concorrente excedeu `lock-timeout-ms`; inclui header orientativo `Retry-After` |
| `500` | `INTERNAL_ERROR` | Falha inesperada genérica, registrada em log interno sem expor detalhes ao cliente |

