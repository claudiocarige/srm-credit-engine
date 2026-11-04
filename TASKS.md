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
- Registrar aprendizados e eventuais erros reais de IA para subsidiar `AI_USAGE.md`, sem inventar ocorrências.

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

**Nota:** cobertura de regras conhecidas não é sinônimo de 100% de linhas de código nem de exaustividade sobre todas as falhas imagináveis.

## 4. Visão geral — backlog inicial

> IDs estáveis para rastreio. A ordem orienta a execução, mas dependências e prioridades prevalecem. Nenhuma tarefa foi iniciada.

| ID | Entrega | Prioridade | Depende de | Status |
|---|---|---|---|---|
| TASK-001 | Estrutura mínima do backend Java e testes | P0 | — | `PENDING` |
| TASK-002 | Contratos mínimos de dados/API e fluxo dos recebíveis | P0 | 001 | `PENDING` |
| TASK-003 | Motor financeiro BRL + Strategy + C1/C2 | P0 | 001, 002 | `PENDING` |
| TASK-004 | Prazo comercial, conversão BRL→USD + C3 | P0 | 003 | `PENDING` |
| TASK-005 | PostgreSQL, Flyway e modelo persistente inicial | P0 | 002 | `PENDING` |
| TASK-006 | Currency Engine: histórico, vigência e API manual | P0 | 004, 005 | `PENDING` |
| TASK-007 | API de simulação, validação, erros e OpenAPI | P0 | 003, 004, 006 | `PENDING` |
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
  AGENTS G-01 a G-04.

- **Aceite/testes:** compilação executada, dependências
  verificadas, configuração local segura, ausência de
  credenciais reais e estratégia definida para testes
  de inicialização que dependam do PostgreSQL.

- **Evidências:** PENDENTE.

### TASK-002 — Definir contrato mínimo de dados e API
- **Objetivo:** resolver *antes das migrations* o fluxo de criação/identificação de cedente, recebível, simulação e lote de liquidação.
- **Escopo:** apresentar proposta enxuta de payloads, IDs, endpoints, estados, modelo mínimo e tratamento dos erros; definir onde nasce o recebível persistido e como o frontend referencia uma operação.
- **Referências:** SPEC §§1, 3 e 5; DEC-002, DEC-007, DEC-008, DEC-009.
- **Aceite/testes:**
  - Definir onde e como o recebível é criado.
  - Definir os estados mínimos do recebível.
  - Definir a identificação do cedente e do título.
  - Definir payloads de simulação e liquidação.
  - Definir endpoints e contratos HTTP essenciais.
  - Definir respostas e códigos de erros de negócio.
  - Definir o comportamento HTTP para timeout de
    concorrência na liquidação.
  - Definir o tamanho máximo da paginação do extrato.
  - Garantir compatibilidade com o SPEC.md e as
    decisões arquiteturais aprovadas.
  - Obter aprovação humana dos contratos antes da
    implementação das migrations correspondentes.
- **Evidências:** `PENDENTE`.

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
- **Evidências:** `PENDENTE`.

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
- **Evidências:** `PENDENTE`.

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
- **Evidências:** `PENDENTE`.

### TASK-006 — Implementar Currency Engine
- **Objetivo:** cadastrar e recuperar a cotação vigente para uma operação.
- **Escopo:** endpoint de atualização manual, histórico append-only, `effectiveAt`, seleção da maior vigência `<= instante da operação`, índice apropriado e massa inicial apenas de desenvolvimento.
- **Referências:** SPEC §§1 e 3; DEC-006, DEC-008, DEC-013.
- **Aceite/testes:** cotação positiva; vigências anteriores/futuras, retroatividade e ausência de taxa (`422`); novas cotações não mudam históricos já concluídos; teste de API e integração no PostgreSQL.
- **Evidências:** `PENDENTE`.

### TASK-007 — API REST de simulação
- **Objetivo:** disponibilizar simulação segura, sem registrar liquidação.
- **Escopo:** endpoint REST/OpenAPI, validação Bean Validation + domínio, cálculo financeiro, conversão por moeda, resposta decimal como string e tratamento global de erros.
- **Referências:** SPEC §§1–3 e 5; DEC-003 a DEC-006, DEC-010.
- **Aceite/testes:** entradas válidas; valores, tipos e datas inválidos; ausência de câmbio; formatos JSON decimais exatos; simulação **sem persistir liquidação ou modificar situação do título**; erros sem stack trace.
- **Evidências:** `PENDENTE`.

### TASK-008 — Identificação e persistência mínima dos recebíveis
- **Objetivo:** disponibilizar títulos e cedentes identificáveis e elegíveis para a liquidação, conforme o fluxo aprovado na TASK-002.
- **Escopo:** somente criação/consulta e estado mínimo indispensáveis; validar vínculo com cedente, integridade e elegibilidade, sem workflow bancário ou autorização avançada.
- **Referências:** SPEC §§1 e 3; DEC-007, DEC-008, DEC-014.
- **Aceite/testes:** IDs/vínculos consistentes, valores e datas válidos, rejeição de dados inválidos ou duplicados conforme contrato, constraints comprovadas em PostgreSQL; sem criar regras não especificadas.
- **Evidências:** `PENDENTE`.

### TASK-009 — Liquidação ACID e snapshots auditáveis
- **Objetivo:** liquidar lotes válidos integralmente, persistindo os parâmetros financeiros efetivamente utilizados.
- **Escopo:** transação Spring única, lote de `1..N` itens do mesmo cedente/moeda, recálculo no servidor, itens e snapshot imutável, migrations complementares, sem integração bancária externa.
    - Definir desde a migration inicial de liquidação
    as restrições estruturais de integridade.
    - Garantir UNIQUE(receivable_id) na associação
    entre recebíveis e itens de liquidação.
    - Garantir unicidade persistente de Idempotency-Key
    no modelo definido para a operação.
    - Preservar uma única transação financeira.
    - Não disponibilizar a funcionalidade de liquidação
  como pronta antes da conclusão da TASK-010.
- **Referências:** SPEC §§1 e 3; DEC-003, DEC-006, DEC-007, DEC-015.
- **Aceite/testes:** lote válido com valores corretos; item inválido no meio causa rollback integral; cotação e taxas gravadas no snapshot; histórico não editável/excluível pela aplicação. **Esta etapa não libera o endpoint como concluído para entrega antes da TASK-010.**
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
- **Escopo:** `Idempotency-Key`, hash canônico SHA-256, registro definitivo da chave na **mesma transação** que os efeitos financeiros, unicidade de chave e de `settlement_items.receivable_id`, proteção de estado, timeout/transação e tradução de conflitos.
- **Referências:** SPEC §3.5; DEC-007, DEC-008, DEC-010.
- **Aceite/testes:** mesma chave/payload recupera resultado; payload diferente `409`; concorrência com mesma chave e com chaves distintas sobre o mesmo título; primeira transação abortada permite retry seguro; timeout sem duplicação; rollback completo; **não** criar reserva `IN_PROGRESS` em transação separada, Inbox/Outbox ou Redis.
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
- **Escopo:** endpoint REST, filtros opcionais conforme contrato, `Pageable` com limite, projeções JPA e ordenação determinística; SQL nativo apenas se justificado.
- **Referências:** SPEC §§1 e 5; DEC-008, DEC-010.
- **Aceite/testes:** combinações de filtros, limites e páginas vazias/inválidas; ordenação por instante + ID único; nenhuma paginação em memória; sem N+1 relevante; índices do extrato definidos por consultas reais, não antecipados sem evidência.
- **Evidências:** `PENDENTE`.

### TASK-012 — Inicializar frontend Angular
- **Objetivo:** estabelecer frontend mínimo, tipado e organizado por funcionalidade.
- **Escopo:** versão Angular/Node compatível aprovada no plano, Standalone Components, TypeScript estrito, serviços HttpClient, Reactive Forms e infraestrutura de testes, sem NgRx.
- **Referências:** DEC-001, DEC-002, DEC-009, DEC-010.
- **Aceite/testes:** projeto compila e executa teste mínimo; organização por features; sem cálculo financeiro ou conversão insegura para `number`.
- **Evidências:** `PENDENTE`.

### TASK-013 — Painel de simulação Angular
- **Objetivo:** permitir ao operador simular recebíveis em tempo real.
- **Escopo:** formulário com valor de face, vencimento, tipo e moeda, validações, `debounceTime`/`switchMap`, apresentação precisa e estados de carregamento/vazio/erro.
- **Referências:** SPEC §§1 e 5; DEC-003, DEC-009, DEC-010.
- **Aceite/testes:** entradas inválidas não chamam API, mudanças rápidas não exibem respostas antigas, resultado exibido preserva centavos, ausência de câmbio tratada, sem fórmula financeira no navegador.
- **Evidências:** `PENDENTE`.

### TASK-014 — Interface Angular de liquidação
- **Objetivo:** executar a operação de liquidação aprovada sem duplicidade induzida pela interface.
- **Escopo:** seleção/confirmação de título ou lote conforme TASK-002, envio com `Idempotency-Key` por intenção, bloqueio durante envio e retry manual seguro da mesma intenção.
- **Referências:** SPEC §§1 e 3; DEC-007, DEC-009, DEC-010.
- **Aceite/testes:** duplo clique bloqueado; mesmo retry usa a chave original e payload equivalente; nova intenção usa nova chave; erro/confirmação claros; **não** usar `switchMap` como cancelamento financeiro nem retry automático não aprovado.
- **Evidências:** `PENDENTE`.

### TASK-015 — Grid de extrato Angular
- **Objetivo:** oferecer histórico operacional pesquisável e paginado.
- **Escopo:** filtros por período/cedente/moeda, página/tamanho controlados pelo backend, ordenação contratada e estados de lista vazia/carregamento/erro.
- **Referências:** SPEC §§1 e 5; DEC-008, DEC-009, DEC-010.
- **Aceite/testes:** filtros e páginas enviados corretamente, troca de filtros reinicia paginação quando aplicável, não baixa todo o extrato e não faz agregações financeiras no cliente.
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
- **Objetivo:** automatizar compilação e suíte obrigatória sem prejudicar os requisitos P0.
- **Escopo:** um workflow em `.github/workflows/ci.yml` com jobs backend/frontend, Java 21, Maven `verify` incluindo integração Testcontainers, Angular testes/build, permissões mínimas e lint se já configurado.
- **Referências:** DEC-010, DEC-011; enunciado Sênior (diferencial para Pleno).
- **Aceite/testes:** execução em PR/push para `main`, workflow falha quando teste obrigatório falha, sem `skipTests` para mascarar falhas, sem deploy/segredos reais. **Não confundir CI verde com cobertura integral das regras.**
- **Evidências:** `PENDENTE`.

### TASK-019 — Documentação final e práticas de Git
- **Objetivo:** entregar documentação verificável e histórico de desenvolvimento justificável.
- **Escopo:** `README.md` (setup, execução, arquitetura, regras, API, testes e escolhas), `AI_USAGE.md` (prompts/specs estratégicos, **erro real da IA** e como foi detectado, decisões não delegadas), revisão de `SPEC.md`/`DECISIONS.md` apenas se houver mudança aprovada; commits convencionais e PR(s) descritivos mediante autorização.
- **Referências:** enunciado §§3, 7, 8, 10 e 12; DEC-001 a DEC-017; AGENTS.
- **Aceite/validação:** instruções reproduzíveis e conferidas; `AI_USAGE.md` com evidências autênticas (se o caso de erro ainda não existir, registrar pendência — **não fabricar**); repositório privado e convite aos avaliadores conforme orientações recebidas, mediante ação/autorização humana.
- **Evidências:** `PENDENTE`.

### TASK-020 — Homologação e preparação da defesa
- **Objetivo:** verificar a entrega integral e garantir capacidade de explicar/modificar o próprio código.
- **Escopo:** auditoria da matriz de rastreabilidade, suíte completa, smoke test via Compose, revisão de segurança/integridade, conferência dos entregáveis e ensaio de walkthrough + mudança local guiada.
- **Referências:** SPEC §§2 e 5; DEC-010; enunciado §§9–11.
- **Aceite/validação:** C1/C2/C3 exatos; todas as regras conhecidas e erros aplicáveis testados; fluxo completo, idempotência e rollback comprovados; todos os entregáveis existentes; demonstração de arquitetura em ~10 min e exercício de alteração em 30–40 min **sem comprometer o repositório principal**; riscos residuais documentados.
- **Evidências:** `PENDENTE`.

## 6. Matriz inicial de rastreabilidade (SPEC → TASK → TESTE)

> IDs `R-xx` são **marcadores de planejamento** extraídos do `SPEC.md` e do enunciado, não novas regras de negócio. Substituir `A DEFINIR` por nomes reais dos testes **após implementá-los**. Revisar se todas as regras e erros conhecidos estão representados antes da entrega.

| ID | Requisito / invariante | Tarefas responsáveis | Testes / evidências |
|---|---|---|---|
| R-01 | Duplicata: spread 1,5%, VP e deságio exatos (C1) | 003 | `A DEFINIR` |
| R-02 | Cheque: spread 2,5%, VP e deságio exatos (C2) | 003 | `A DEFINIR` |
| R-03 | Câmbio BRL→USD somente ao final, C3 e arredondamento | 004, 006, 007 | `A DEFINIR` |
| R-04 | `BigDecimal`, DECIMAL128, HALF_EVEN e valores JSON como strings | 003, 004, 005, 007, 013 | `A DEFINIR` |
| R-05 | Prazo inteiro e dueDate→ceil(dias/30); vencimento futuro | 004, 007 | `A DEFINIR` |
| R-06 | Taxa base configurável, snapshot financeiro | 003, 009 | `A DEFINIR` |
| R-07 | Câmbio vigente `effectiveAt`, sem fallback, histórico append-only | 006, 009 | `A DEFINIR` |
| R-08 | Simulação sem efeitos persistentes de liquidação | 007, 013 | `A DEFINIR` |
| R-09 | Lote de 1..N, mesmo cedente/moeda, ACID all-or-nothing | 008, 009 | `A DEFINIR` |
| R-10 | Idempotência: chave+payload igual ou divergente | 010, 014 | `A DEFINIR` |
| R-11 | Concorrência: mesma chave e chaves distintas; título uma vez | 010 | `A DEFINIR` |
| R-12 | Auditoria imutável de todos os parâmetros e timestamps | 009, 010 | `A DEFINIR` |
| R-13 | API: validação, erros semânticos/seguros e OpenAPI | 006, 007, 009, 010, 011 | `A DEFINIR` |
| R-14 | Extrato: período, cedente, moeda e paginação server-side | 011, 015 | `A DEFINIR` |
| R-15 | Angular: campos obrigatórios, simulação em tempo real, sem cálculo | 013 | `A DEFINIR` |
| R-16 | Execução local com Docker/Compose e PostgreSQL | 016 | `A DEFINIR` |
| R-17 | Code review do Anexo A (`REVIEW.md`) | 017 | `A DEFINIR` |
| R-18 | Uso de IA documentado com erro real (`AI_USAGE.md`) | 019 | `A DEFINIR` |
| R-19 |  README com justificativa da stack, Git convencional, PRs e entrega privada | 001, 019, 020 | `A DEFINIR` |
| R-20 | Testes de regras/erros aplicáveis e validação final | 003–020 | `A DEFINIR` |
| R-21 | CI automatizada (DEC-011, diferencial) | 018 | `A DEFINIR` |
| R-22 | Diagrama ER e modelo relacional normalizado | 005, 009 | `A DEFINIR` |
| R-23 | Extensibilidade da Strategy com nova fórmula | 003 | `A DEFINIR` |
| R-24 | Data da operação controlada pelo backend | 004, 007 | `A DEFINIR` |
| R-25 | Novas cotações não alteram liquidações concluídas | 006, 009 | `A DEFINIR` |

## 7. Pendências de definição antes da implementação

1. **Ciclo de vida dos recebíveis (TASK-002):** como são criados/identificados pelo operador ou API antes da liquidação? O enunciado não determina endpoints de cadastro nem se o lote informa IDs ou dados completos. Propor o caminho mais simples, sem assumir como requisito uma solução ainda não aprovada.
2. **Contrato HTTP (TASK-002):** fechar payloads/IDs, representação de erros, forma de criar/repetir uma intenção de liquidação, resposta à espera concorrente que exceda o timeout e limites de paginação, respeitando o `SPEC.md` e a DEC-007.
3. **Detalhes de infraestrutura:** versões compatíveis de Spring Boot, Angular e Node, convenções de nomes, localização física das migrations, forma de servir o frontend e variáveis de ambiente serão propostas dentro das tarefas pertinentes — não decididas automaticamente por este backlog.
4. **Evolução documental:** manter o DECISIONS.md enxuto como registro oficial e acrescentar progressivamente as decisões necessárias às tarefas, mediante aprovação humana. As referências a decisões futuras no TASKS.md devem ser preservadas.

## 8. Registro de execução (preencher ao longo do projeto)

| Data | Tarefa | Mudança autorizada | Testes/comandos | Resultado | Erro de IA detectado / validação | Aprovação |
|---|---|---|---|---|---|---|
| — | — | — | — | — | — | — |

> **Próxima ação:** revisar/aprovar este backlog como **plano inicial**. Depois, solicitar ao Antigravity **somente o plano detalhado da TASK-001**; execução e modificações continuam sujeitas à autorização G-01/G-03.
