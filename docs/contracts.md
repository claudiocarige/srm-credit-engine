# SRM Credit Engine — Regras Contratuais e Operacionais Complementares (`docs/contracts.md`)

> - **Origem:** entregável complementar da [`TASK-002`](task-plans/TASK-002.md) (Revisão `1.6` — *Contract First* + segurança de validação e nota de `Clock`).
> - **Fonte oficial dos contratos HTTP:** [`docs/openapi/srm-credit-engine.yaml`](openapi/srm-credit-engine.yaml) (**10 operações em 8 paths sob `/api/v1`**).
> - **Fontes superiores:** [`SPEC.md`](../SPEC.md) e [`DECISIONS.md`](../DECISIONS.md).
> - **Finalidade:** registrar apenas as invariantes relacionais, numéricas, transacionais e operacionais que complementam os schemas do OpenAPI oficial, sem duplicar payloads ou definições de endpoints.

---

## 1. Convenções Temporais, Limites Numéricos e Totalização

### 1.1. Fonte Única de Tempo (`Clock` UTC)
- O backend determina integralmente o instante da operação via `java.time.Clock` em **UTC** (clientes não enviam timestamp da operação):
  - `operationInstant` = `Instant.now(clock)` (`simulatedAt` na simulação; `settledAt` na liquidação).
  - `operationDate` = `LocalDate.ofInstant(operationInstant, ZoneOffset.UTC)` (`YYYY-MM-DD`).
  - Quando a entrada fornece `dueDate`, exige-se `dueDate > operationDate` (caso contrário `422 INVALID_DUE_DATE`), convertendo dias corridos em meses comerciais inteiros sem ponto flutuante: `days = DAYS.between(operationDate, dueDate)` e `termMonths = (days + 29) / 30` (`ceil(days / 30)`).
  - **Nota sobre os exemplos dos *Golden Cases* no OpenAPI:** os exemplos de simulação e liquidação em [`docs/openapi/srm-credit-engine.yaml`](openapi/srm-credit-engine.yaml) utilizam uma data de operação fixa (`2026-11-04`) para fins de demonstração e testes determinísticos. Na execução real, a data de referência será obtida pelo `Clock` do backend (podendo resultar em prazos e valores distintos em outras datas); nos testes automatizados (a partir da `TASK-004`), será utilizado `Clock` controlado.

### 1.2. Limites Financeiros e Invariante contra Overflow
Preservando as colunas do [`SPEC.md`](../SPEC.md) (`NUMERIC(19,2)` para valores monetários — até `17` dígitos inteiros —, `NUMERIC(19,6)` para câmbio e `NUMERIC(10,6)` para taxas mensais), aplicam-se na entrada os limites definidos no OpenAPI, no domínio e em `CHECK` do PostgreSQL:

| Parâmetro / Campo | Mínimo | Máximo | Casas decimais | Coluna PostgreSQL |
|---|---:|---:|---:|---|
| `faceValueBrl` (por título / simulação) | `"0.01"` | `"999999999.99"` (`9` dígitos inteiros) | `2` | `NUMERIC(19,2)` |
| `rateBrl` (`BRL` por `1 USD`) | `"0.000001"` | `"9999999.999999"` (`7` dígitos inteiros) | `6` | `NUMERIC(19,6)` |
| `baseRateMonthly` / `spreadMonthly` | `"0.000000"` (`> 0` no spread) | `"9.999999"` | `6` | `NUMERIC(10,6)` |
| `receivableIds` (tamanho do lote) | `1` item | `100` itens | — | — |

- **Garantia contra overflow em lotes de até 100 itens:** em `BRL`, os totais atingem no máximo `99_999_999_999.00` (`11` dígitos inteiros `<< 17`); em `USD`, mesmo no pior caso matemático extremo (`100` itens no teto divididos por `"0.000001"`), `totalPaidAmount <= 99_999_999_999_000_000.00`, cabendo integralmente nos `17` dígitos inteiros de `NUMERIC(19,2)`.

### 1.3. Moeda de Cada Valor e Regra de Totalização *Bottom-Up*
- **Sempre em `BRL`:** `faceValueBrl`, `presentValueBrl`, `discountBrl`, `totalFaceValueBrl`, `totalPresentValueBrl`, `totalDiscountBrl`.
- **Em `paymentCurrency` (`BRL` ou `USD`):** `paidAmount` e `totalPaidAmount` (em `BRL`, `paidAmount == presentValueBrl`; em `USD`, `paidAmount = round_half_even(presentValueBrl / exchangeRateApplied, 2)`).
- **Cotação cambial (`BRL` por `1 USD`):** `rateBrl` e `exchangeRateApplied` (`null` quando `paymentCurrency = "BRL"`).
- **Totalização do lote (*bottom-up*):** cada item é calculado e arredondado individualmente (`HALF_EVEN`, 2 casas); os totais do cabeçalho (`totalFaceValueBrl`, `totalPresentValueBrl`, `totalDiscountBrl`, `totalPaidAmount`) correspondem **sempre à soma exata dos snapshots arredondados dos itens**, nunca convertendo `totalPresentValueBrl / exchangeRateApplied` em separado.

---

## 2. Modelo Mínimo de Dados, Estados e Unicidade

1. **`assignors` (Cedentes):** `id` (`UUID` PK), `document` (`VARCHAR(14)` `UNIQUE`, apenas dígitos), `name` (`VARCHAR(150)`), `created_at` (`TIMESTAMPTZ`). Providos por **seed inicial** + leitura `GET /api/v1/assignors` (sem escrita via API).
2. **`receivables` (Recebíveis):** `id` (`UUID` PK), `assignor_id` (`UUID` FK → `assignors.id`), `document_number` (`VARCHAR(64)`), `type` (`VARCHAR(32)`), `face_value_brl` (`NUMERIC(19,2)`), `due_date` (`DATE`), `status` (`VARCHAR(16)`), `version` (`BIGINT`), `created_at` (`TIMESTAMPTZ`).
   - **Estados:** nasce exclusivamente em `OPEN` (via seed ou `POST /api/v1/receivables`) e transita de forma irreversível para `SETTLED` apenas na transação ACID de liquidação (sem `PUT`, `PATCH`, `DELETE` ou estorno).
   - **Normalização e unicidade por cedente:** `documentNumber` sofre `trim()` + `toUpperCase(Locale.ROOT)` antes da validação `^[A-Z0-9._/-]{1,64}$` e obedece a **`UNIQUE (assignor_id, document_number)`** (`409 DUPLICATE_RECEIVABLE_DOCUMENT` para o mesmo cedente; permitido entre cedentes distintos).
3. **`exchange_rates` (Câmbio Manual *Append-Only*):** `id` (`UUID` PK), `currency` (`VARCHAR(3)`, `'USD'`), `rate_brl` (`NUMERIC(19,6)`), `effective_at` (`TIMESTAMPTZ`), `created_at` (`TIMESTAMPTZ`), com **`UNIQUE (currency, effective_at)`** (`409 DUPLICATE_EXCHANGE_RATE_EFFECTIVE_AT`). Seleciona-se para todo o lote a maior vigência `effective_at <= operationInstant` (sem registro elegível → `422 EXCHANGE_RATE_UNAVAILABLE`, zero fallback). Novas cotações jamais alteram liquidações já gravadas.
4. **`settlements` (Cabeçalho Imutável do Lote):** `id` (`UUID` PK), `idempotency_key` (`VARCHAR(128)` `UNIQUE NOT NULL`), `request_hash` (`CHAR(64)` `NOT NULL`), `assignor_id` (`UUID` FK → `assignors.id`), `payment_currency`, `item_count`, `total_face_value_brl`, `total_present_value_brl`, `total_discount_brl`, `exchange_rate_applied` (nullable), `exchange_rate_effective_at` (nullable), `total_paid_amount`, `operation_date` (`DATE`), `settled_at` (`TIMESTAMPTZ`).
   - **Encapsulamento:** `idempotency_key` e `request_hash` existem apenas na persistência interna e **nunca são expostos** nos DTOs públicos nem nas consultas `GET /api/v1/settlements` e `GET /api/v1/settlements/{settlementId}`.
5. **`settlement_items` (Snapshot Imutável por Item):** persiste os 16 campos obrigatórios definidos no schema `SettlementItemSnapshot` do OpenAPI, com **`UNIQUE (receivable_id)`** impedindo estruturalmente dupla liquidação do mesmo título. `settlements` e `settlement_items` não admitem `UPDATE` nem `DELETE`.

---

## 3. Protocolo de Idempotência, Canonização e Concorrência

### 3.1. Canonização Determinística (`request_hash` SHA-256)
1. IDs repetidos no mesmo array `receivableIds` são rejeitados na entrada com `400 DUPLICATE_RECEIVABLE_IN_BATCH`.
2. `assignorId` e todos os elementos de `receivableIds` são normalizados em lowercase, ordenando `receivableIds` em ordem lexicográfica crescente (`ASC`) para que lotes com os mesmos títulos em ordens distintas gerem o mesmo hash.
3. Calcula-se `SHA-256` (hex lowercase, 64 caracteres) em `UTF-8` sobre:
   ```text
   assignorId=<assignor_uuid_lower>|paymentCurrency=<BRL|USD>|receivableIds=<uuid1_lower,...,uuidN_lower>
   ```

### 3.2. Ordem Estrita de Avaliação em `POST /api/v1/settlements`
1. **Validação estrutural e canonização (`400 Bad Request`):** header `Idempotency-Key` (`1..128` ASCII visíveis), estrutura do body, ausência de IDs repetidos no lote e cálculo de `request_hash`.
2. **Precedência absoluta da `Idempotency-Key` (antes de checar `OPEN`/`SETTLED`, vencimento ou câmbio):**
   - Chave já associada a liquidação efetivada com **mesmo `request_hash`** → retorna **`200 OK`** com o snapshot original imutável (**jamais** retorna `409 RECEIVABLE_ALREADY_SETTLED`).
   - Chave já associada com **`request_hash` divergente** → retorna **`409 Conflict` (`IDEMPOTENCY_PAYLOAD_MISMATCH`)**.
   - Espera por lock concorrente acima de `srm.settlement.lock-timeout-ms` (padrão `3000` ms) → rollback integral e retorno **`503 Service Unavailable` (`CONCURRENT_SETTLEMENT_TIMEOUT`)** com header **`Retry-After`** configurável via `srm.settlement.retry-after-seconds` (padrão `2` s).
     - **Semântica do `Retry-After`:** trata-se exclusivamente de uma **orientação (*hint*) de intervalo mínimo antes de nova tentativa** (RFC 9110 §10.2.3), **não constituindo garantia** de que a transação concorrente já tenha concluído. O cliente deve repetir usando a **mesma `Idempotency-Key` e o mesmo payload**.
3. **Validação negocial e gravação ACID única (somente para `Idempotency-Key` inédita):**
   - Processa os recebíveis em ordem crescente de `UUID` (prevenção de deadlocks) na mesma transação PostgreSQL (`DEC-007`):
   - `404 ASSIGNOR_NOT_FOUND` | `404 RECEIVABLE_NOT_FOUND`;
   - `422 RECEIVABLE_ASSIGNOR_MISMATCH` | `422 INVALID_DUE_DATE` | `422 EXCHANGE_RATE_UNAVAILABLE`;
   - **`409 RECEIVABLE_ALREADY_SETTLED`** exclusivamente quando **outra intenção distinta (outra `Idempotency-Key`)** já liquidou algum título do lote, revertendo todo o lote;
   - Efetivação inédita concluída → **`201 Created`** (`Location: /api/v1/settlements/{settlementId}`).

---

## 4. Extrato Paginado, Semântica `ProblemDetail` e Fluxo Angular

### 4.1. Paginação por Lote e Detalhamento sem `N+1`
- `GET /api/v1/settlements` pagina **por lote** (`page >= 0` padrão `0`, `size` `1..100` padrão `20`, ordenação `settled_at DESC, id DESC`), aplicando filtros `from`/`to` como intervalo semiaberto indexável em UTC (`settled_at >= fromT00:00:00Z AND settled_at < (to + 1 dia)T00:00:00Z`; `from > to` → `400 INVALID_DATE_RANGE`), sem carregar `items[]` na listagem.
- `GET /api/v1/settlements/{settlementId}` retorna o lote e seus `items[]` ordenados por `documentNumber ASC, receivableId ASC`.

### 4.2. Mapeamento Semântico de Erros (`ProblemDetail` — `application/problem+json`) e Rejeição de Propriedades Desconhecidas
Conforme schema `ProblemDetail` do OpenAPI oficial (`org.springframework.http.ProblemDetail` RFC 9457, estendido com `errorCode`, `timestamp` e `fieldErrors` opcionais, sem stack trace ou metadados internos de banco):

- **Política estrita de rejeição de propriedades JSON desconhecidas (`additionalProperties: false`):** os quatro schemas de entrada (`CreateReceivableRequest`, `CreateExchangeRateRequest`, `SimulationRequest` e `SettlementRequest`) aceitam exclusivamente os atributos definidos no contrato oficial. Qualquer campo desconhecido (ex.: `"manualDiscount": "999.99"` em `SettlementRequest`) deve ser rejeitado durante a desserialização com **`HTTP 400 Bad Request`**, **`Content-Type: application/problem+json`**, **`errorCode: VALIDATION_ERROR`** e mensagem indicando a propriedade desconhecida, sem expor nomes de classes/pacotes Java ou detalhes internos. A configuração do Jackson no Spring Boot deverá garantir esse comportamento em runtime e comprová-lo via testes automatizados com `MockMvc`.

| HTTP Status | `ErrorCode` (`errorCode`) | Regra disparadora |
|---:|---|---|
| `400` | `VALIDATION_ERROR` | Formato/tipo inválido, propriedade JSON desconhecida (`additionalProperties: false`), zero ou limites numéricos violados (`faceValueBrl < 0.01`, `rateBrl < 0.000001`) ou `size` fora de `1..100` |
| `400` | `INVALID_IDEMPOTENCY_KEY` | Header `Idempotency-Key` ausente, vazio ou fora de `^[\x21-\x7E]{1,128}$` |
| `400` | `DUPLICATE_RECEIVABLE_IN_BATCH` | `receivableIds` contém `UUID` repetido no mesmo lote |
| `400` | `INVALID_DATE_RANGE` | Período do extrato com `from > to` |
| `404` | `ASSIGNOR_NOT_FOUND` / `RECEIVABLE_NOT_FOUND` / `SETTLEMENT_NOT_FOUND` | Recurso inexistente |
| `409` | `DUPLICATE_RECEIVABLE_DOCUMENT` | Violação de `UNIQUE (assignor_id, document_number)` normalizado |
| `409` | `DUPLICATE_EXCHANGE_RATE_EFFECTIVE_AT` | Violação de `UNIQUE (currency, effective_at)` |
| `409` | `IDEMPOTENCY_PAYLOAD_MISMATCH` | Mesma `Idempotency-Key` com `request_hash` divergente |
| `409` | `RECEIVABLE_ALREADY_SETTLED` | Recebível já liquidado por outra `Idempotency-Key` distinta |
| `422` | `INVALID_DUE_DATE` / `RECEIVABLE_ASSIGNOR_MISMATCH` / `EXCHANGE_RATE_UNAVAILABLE` | Inelegibilidade de negócio ou ausência de cotação vigente |
| `503` | `CONCURRENT_SETTLEMENT_TIMEOUT` | Timeout de espera por lock concorrente (acompanha header orientativo `Retry-After`) |
| `500` | `INTERNAL_ERROR` | Falha interna inesperada tratada genericamente |

### 4.3. Fluxo Operacional no Angular
1. **Simulação (`POST /api/v1/simulations`):** exige apenas os 4 campos obrigatórios (`type`, `faceValueBrl`, `dueDate`, `paymentCurrency`), com `debounceTime` + `switchMap`, sem cálculo local em `number` e sem *quote lock*.
2. **Cadastro explícito (`POST /api/v1/receivables`):** operador seleciona cedente, informa `documentNumber` + dados do título e registra-o como `OPEN` (consultável em `GET /api/v1/receivables/{id}` via `Location`).
3. **Liquidação (`POST /api/v1/settlements`):** operador lista títulos `OPEN` do cedente (`GET /api/v1/receivables`), seleciona `1..100` itens, gera um `UUID v4` de `Idempotency-Key` por intenção, bloqueia duplo envio na UI e reutiliza **exatamente a mesma chave e payload** em retry manual após erro de rede ou `503`. Nova seleção/cedente/moeda gera nova chave.
