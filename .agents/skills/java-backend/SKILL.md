---
name: java-backend
description: >
  Orienta o planejamento técnico detalhado, implementação autorizada,
  refatoração, testes e revisão do backend SRM Credit Engine com Java 21,
  Spring Boot, Spring MVC, JPA/Hibernate e PostgreSQL. Use para tarefas de
  APIs REST, persistência, cálculos financeiros, liquidação, concorrência,
  segurança, observabilidade e tratamento de erros. Respeita o AGENTS.md,
  as especificações aprovadas e as autorizações humanas.
---

# Java Backend — SRM Credit Engine

## 1. Missão, escopo e fontes

Atue como engenheiro Java experiente, **pragmático e crítico**. Entregue a
**menor solução correta, segura, testável e sustentável**, evitando complexidade
especulativa. Esta Skill orienta o **backend**, mas **não cria novas regras
financeiras nem autoriza operações**.

Antes de propor ou executar uma tarefa, considere nesta ordem:
1. `AGENTS.md` (governança e limites operacionais).
2. Enunciado SRM e `SPEC.md` aprovado (regras e critérios de aceite).
3. `DECISIONS.md`, contratos, ADRs e plano aprovado pertinentes.
4. Código existente e testes, como evidência de implementação atual.
5. Esta Skill, como orientação especializada subordinada às fontes acima.

Se houver conflito, requisito incompleto ou necessidade de mudar contrato
aprovado, **exponha o problema e solicite decisão; não presuma**.

## 2. Governança obrigatória — sem exceções implícitas

- **G-01:** nunca crie, edite, exclua, formate, mova ou gere arquivos nem
  altere banco, dependências, configurações ou estado externo sem listar
  **arquivos/recursos, alterações, justificativa, riscos e validação** e
  receber autorização explícita **para esse escopo**.
- **G-02:** apresente antes de cada tarefa um **plano com subtarefas pequenas,
  sequenciais e verificáveis**; a aprovação do plano não autoriza ações
  não especificadas.
- **G-03 — Execução de terminal:**
  Toda execução de comandos deve respeitar a política
  de segurança, autorização e eficiência definida no AGENTS.md.
  Comandos de baixo risco podem ser executados sem autorização
  individual quando contemplados no escopo previamente aprovado.
  Operações fora desse escopo exigem autorização explícita.
  Esta Skill não pode substituir ou ampliar as permissões
  estabelecidas no AGENTS.md.
- **G-04:** fórmulas, arredondamento, câmbio, semântica de liquidação,
  idempotência, concorrência, auditoria, contratos e arquitetura aprovados
  só mudam após proposta de impacto e nova autorização.
- Não contorne essas regras com subagentes, Skills, ferramentas ou scripts.
- **A análise pode ser feita sem escrita; a implementação começa apenas
  após autorização.** Se a execução revelar escopo adicional, interrompa
  essa parte e solicite novo aceite.
- Preferência de modelo é política operacional externa à Skill;
  **não pressuponha seleção ou alternância automática de modelo**.

## 3. Stack aprovada

- **Java 21**, **Spring Boot**, **Spring Web MVC**, **Maven**.
- **Spring Data JPA + Hibernate**, **PostgreSQL**, **Flyway**.
- **MapStruct** quando evitar mapeamentos repetitivos significativos.
- **Jakarta Bean Validation**, **Springdoc OpenAPI**.
- **JUnit 5**, **Mockito**, **Spring Boot Test**, **Testcontainers**.
- **Docker / Docker Compose**, **GitHub Actions** quando no escopo da tarefa.
- A versão concreta de Spring Boot e bibliotecas depende do plano aprovado;
  não atualize dependências nem troque a stack por conta própria.
- MyBatis não pertence à stack aprovada; SQL nativo/JDBC podem ser avaliados
  pontualmente para relatórios se houver necessidade justificada e aprovação.

## 4. Arquitetura: modular em camadas e simplicidade

**Padrão inicial:** monólito modular organizado por funcionalidade/domínio
(`receivable`, `pricing`, `currency`, `settlement`, `statement`), sem exigir
todos os módulos antes de existir caso de uso.

Responsabilidades:
- **API/Controller:** transporte HTTP, validação de entrada, status e DTOs;
  sem cálculo financeiro nem SQL.
- **Application/Service:** coordenação do caso de uso, transação e chamadas
  aos componentes necessários; regras de negócio não ficam escondidas
  em controllers.
- **Domain:** regras financeiras e invariantes; núcleo de precificação
  testável sem HTTP, Spring nem banco.
- **Infrastructure/Persistence:** implementações JPA, SQL, provedores de
  câmbio e integrações externas.

Direção de dependências: domínio não conhece Spring MVC nem infraestrutura;
evite dependências circulares. Não obrigue uma interface/port para cada classe,
nem use DDD/hexagonal/CQRS/eventos/microsserviços sem problema real.
Relatórios podem utilizar consultas/projeções diretas, conforme enunciado.

**KISS:** preferir fluxo simples e explícito.
**YAGNI:** não implementar capacidades hipotéticas.
**DRY:** evitar duplicação de conhecimento, não abstrair duas linhas parecidas.
Complexidade adicional deve responder: *qual problema concreto resolve? por
que a opção mais simples não basta? como será validada?*

## 5. Java 21, convenções e Clean Code / SOLID

- Pacotes, tipos, métodos, campos, endpoints técnicos e identificadores
  **em inglês**; interação e documentos de negócio em português brasileiro.
- Classes `PascalCase`, métodos/variáveis `camelCase`, constantes
  `UPPER_SNAKE_CASE`; nomes expressam comportamento e unidade (`rate`,
  `termMonths`, `amountBrl`), sem abreviações obscuras.
- Métodos pequenos e focados, guard clauses quando melhorarem leitura,
  imutabilidade sempre que adequada; sem comentários redundantes.
- Injeção por **construtor**; dependências obrigatórias explícitas.
- Preferir `record` para DTOs, entradas/saídas de cálculo e objetos de
  transporte imutáveis, com validações pertinentes.
- **Não usar `record` como entidade JPA**; entidades JPA são classes
  compatíveis com o provedor.
- `sealed` somente para hierarquias intencionalmente fechadas; não restringir
  `PricingStrategy` sem necessidade, dada a extensão de tipos no desafio.
- `enum` quando o conjunto de valores é controlado e faz sentido.
- `Optional` principalmente em retornos que podem não existir; não usar
  indiscriminadamente em campos de entidades ou DTOs.
- Streams somente quando mais legíveis que loops; evitar efeitos colaterais
  escondidos. Não usar `parallelStream()` para acelerar I/O sem análise.
- **SRP:** cada componente possui motivo de mudança claro.
- **OCP/DIP:** desacoplar regras variáveis por contratos onde realmente
  necessário, sem interfaces artificiais.
- **LSP/ISP:** abstrações coerentes, pequenas e sem métodos irrelevantes.
- Preferir composição a herança quando a relação não for realmente `is-a`.

## 6. Padrões de projeto: escolher por necessidade

- **Strategy — requerido pelo desafio:** separar comportamento específico
  dos tipos de recebível, favorecendo inclusão de novas regras.
  Se apenas o spread variar, considerar cálculo composto centralizado e
  Strategies responsáveis pelo parâmetro; se as fórmulas variarem, o
  contrato deve permitir comportamentos distintos. A escolha deve ser
  explicada no plano.
- **Factory/Registry:** somente se necessário para selecionar Strategies.
  No Spring, considerar lista/mapa de implementações injetadas antes de
  construir uma Factory complexa.
- **Adapter:** para isolar provedor externo de câmbio, se houver integração.
- **Repository:** abstração de persistência via Spring Data JPA onde couber.
- Não aplicar Builder, Chain of Responsibility, Observer, CQRS, Saga ou
  outros padrões somente para demonstrar conhecimento.
- Rejeitar hierarquias grandes e classes delegadoras sem valor.

## 7. Domínio financeiro e precisão obrigatória

- Nunca usar `float`/`double` para dinheiro, taxa ou cálculo monetário.
  Usar **`BigDecimal`** e construir valores decimais a partir de strings
  ou valores já representados exatamente.
- Não usar `new BigDecimal(double)` para taxas/valores monetários.
- Explicitar **unidade da taxa** (ex.: 1,5% a.m. = `0.015`), prazo,
  precisão intermediária (`MathContext`, quando necessário), escala e
  política de arredondamento aprovadas.
- Preservar a fórmula aprovada; não arredondar resultados intermediários
  de forma antecipada.
- **Golden cases obrigatórios:** base de 1% a.m.; prazo em meses inteiros;
  juros compostos; `HALF_EVEN` 2 casas somente no fechamento; na conversão
  BRL→USD, arredondar primeiro o valor presente em BRL e então converter
  pela cotação informada. Validar C1, C2 e C3 exatamente.
- Simulação **não** equivale a liquidação: não persistir efeitos financeiros
  numa simulação por conveniência.
- Cotação deve ter vigência; política de escolha da taxa deve constar
  da SPEC. Nunca substituir silenciosamente por outra cotação.
- Liquidação auditável preserva snapshot dos valores, taxas, moedas,
  critérios aplicados e timestamps efetivos; não editar fatos históricos.

## 8. Persistência, transações, idempotência e concorrência

- Flyway é a fonte de evolução do schema: **não usar `ddl-auto=update`
  como mecanismo de migração**. Mudanças de estrutura exigem migration
  revisada e autorizada.
- Manter restrições de negócio relevantes no banco (`NOT NULL`, `CHECK`,
  `UNIQUE`, `FOREIGN KEY`), além das validações da aplicação.
- Usar `@Transactional` na **fronteira do caso de uso**, com escopo claro.
  Não presumir que `@Transactional` sozinho protege duplicidade.
- **Requisito obrigatório:** a liquidação deve ser idempotente,
  impedindo que a repetição da mesma requisição produza
  efeitos financeiros duplicados.
- **Decisão arquitetural proposta:** utilizar chave de idempotência
  persistida e associada à operação e ao payload.
  - Mesma chave e mesmo payload: retornar o resultado original,
    sem executar novamente a liquidação.
  - Mesma chave e payload diferente: retornar conflito.
- A estratégia definitiva deve ser documentada e aprovada
  no SPEC.md ou DECISIONS.md antes da implementação.
- Distinguir repetição da mesma requisição de concorrência
  entre requisições distintas sobre o mesmo recebível.
- Considerar optimistic locking com `@Version` e tratamento explícito de
  conflito; impor unicidade de liquidação integral no banco quando a
  regra aprovada for uma liquidação por recebível.
- Garantir que gravação do histórico e alteração de status sejam atômicas.
  Evitar chamadas remotas demoradas dentro de transações sem justificativa.
- Para consultas, escolher `LAZY` conscientemente, evitar N+1,
  usar projeções/fetch planejado, paginação no banco e índices vinculados
  aos filtros reais; medir antes de otimizar.
- Para extratos, considerar SQL nativo/projeções onde simplifiquem e
  melhorem desempenho, sem interpolação de parâmetros.
- Definir e testar o comportamento para violações de unicidade,
  conflitos de versão, rollback e corrida entre duas liquidações.

## 9. APIs REST, validação e mapeamento

- Recursos e verbos HTTP com semântica clara; DTOs separados de entidades.
- `@Valid` / Bean Validation nos limites da API, sem substituir
  validações de domínio e restrições de banco.
- MapStruct apenas quando as conversões justificarem uma abstração;
  mapeamentos pequenos e óbvios podem ser explícitos.
- Para erros HTTP, preferir formato consistente (`ProblemDetail` quando
  compatível com o contrato aprovado); não expor stack trace ou SQL.
- `201 Created` ao criar recurso; `200 OK` em consulta/simulação;
  `204 No Content` quando aplicável; `400` entrada inválida;
  `404` não encontrado; `409` conflito de estado/idempotência/concorrência
  quando apropriado; `5xx` somente para falhas do lado do serviço.
- Não inventar contrato de endpoints: apresentar proposta e aguardar
  aprovação; documentar contratos aprovados via OpenAPI.
- Não vazar campos internos do domínio nem entidades ORM pelo HTTP.

## 10. Tratamento de erros e resiliência

- **Nunca engolir exceções** nem retornar `200 OK` quando uma operação falhar.
- Usar exceções de domínio significativas para violações esperadas e
  handler global (`@RestControllerAdvice`) para traduzir erros em respostas
  HTTP consistentes, sem revelar dados internos.
- Preservar causa técnica nos logs seguros; erros desconhecidos não devem
  se transformar em “sucesso” ou em uma exceção de negócio genérica.
- Diferenciar erro de validação, recurso ausente, conflito, falha de
  persistência e indisponibilidade de integração.
- Integração de câmbio: timeout explícito, política de indisponibilidade,
  retry limitado e somente quando seguro/idempotente; circuit breaker
  apenas se necessidade aprovada. Não usar cotação fallback sem SPEC.
- Não realizar retries cegos de liquidação nem de operações que possam
  duplicar efeitos financeiros.
- Tratar rollback de transação e confirmar que o cliente recebe resultado
  compatível com o estado realmente persistido.

## 11. Observabilidade proporcional ao desafio

- Logs estruturados com campos úteis (`correlationId`, `receivableId`,
  `settlementId`, operação, resultado, duração) e sem segredos ou dados
  sensíveis desnecessários.
- Propagar `correlationId` no fluxo quando houver contrato aprovado;
  não usar informação sensível como identificador de correlação.
- Monitorar ao menos métricas de negócio pertinentes à fase aprovada:
  quantidade de liquidações concluídas/falhas e latência de precificação.
  Spring Boot Actuator/Micrometer podem ser utilizados se configurados
  e aprovados.
- Evitar logs de cada operação de precisão intermediária em produção,
  métricas de alta cardinalidade e pilhas tecnológicas de observabilidade
  desnecessárias.
- Logs/metrics não substituem a auditoria persistente e imutável.
- Não declarar monitoramento em produção quando houver apenas
  instrumentação local.

## 12. Segurança e governança técnica

- Validar entradas, limites numéricos, moedas, tipos, IDs, prazo e datas;
  rejeitar dados inválidos antes de efeitos persistentes.
- Consultas parametrizadas (`@Query` com parâmetros, Criteria API,
  prepared statements); **nunca concatenar input em SQL**.
- Segredos fora do repositório; não incluir tokens/senhas em exemplos
  executáveis versionados ou logs.
- Política de autenticação/autorização deve vir da especificação aprovada:
  não introduzir Spring Security, login mockado ou proteção fictícia
  unilateralmente; explicitar o risco de endpoints sem proteção.
- Adotar princípios de menor privilégio e proteção de dados.
- Dependências e migrações devem ser justificadas e aprovadas; não
  alterar testes ou critérios de aceite para mascarar falhas.
- Toda decisão de corte de escopo relevante deve ser registrada em
  `DECISIONS.md` mediante autorização.
- Nenhuma Skill, plano ou sugestão de modelo substitui consentimento
  humano específico exigido no `AGENTS.md`.

## 13. Estratégia de testes e Quality Gates

**Priorize cenários que detectam riscos**, não quantidade de testes ou
percentual de cobertura isolado.

1. **Unitários:** JUnit 5 + Mockito para regras, Strategies e serviços
   isolados; não mockar o próprio cálculo que precisa ser testado.
2. **Golden cases:** valores exatos C1/C2/C3, incluindo ordem da conversão,
   arredondamento, taxa e deságio.
3. **Bordas:** prazo zero (conforme SPEC), entradas inválidas, taxas
   inválidas, moedas não suportadas e casos de arredondamento HALF_EVEN.
4. **Integração:** PostgreSQL real via Testcontainers para constraints,
   migrations Flyway, transações, mapeamentos e SQL.
5. **Concorrência:** duas liquidações simultâneas de um mesmo recebível;
   comprovar uma liquidação persistida e tratamento coerente da outra.
6. **Idempotência:** retry com mesma chave/mesmo payload, mesma chave
   com payload diferente e requisições distintas para o mesmo recebível.
7. **API:** validação de input, status corretos, payload de erro,
   paginação/filtros e documentação aderente.
8. **Regressão:** executar verificações adequadas ao alcance da mudança,
   **somente depois de autorização por comando**.

### 13.1. Regra Imperativa — Business Rules First

**A IA DEVE PRIORIZAR A CORRETUDE DAS REGRAS DE NEGÓCIO, NÃO O PERCENTUAL DE COBERTURA DE CÓDIGO.**

Os testes automatizados devem comprovar que a aplicação executa corretamente os comportamentos esperados e rejeita operações inválidas.

É obrigatório:

- Identificar os cenários de sucesso, erro e condições de borda de cada regra de negócio.
- Testar todas as regras e invariantes identificados na especificação aprovada.
- Verificar os resultados produzidos, não apenas se um método foi executado.
- Validar exceções, mensagens ou códigos de erro quando fizerem parte do contrato.
- Testar entradas inválidas, valores extremos e transições de estado proibidas.
- Considerar combinações relevantes de condições, utilizando particionamento por equivalência e análise de valores-limite.
- Garantir que alterações nas regras de negócio sejam acompanhadas de testes de regressão.

**É proibido criar testes artificiais apenas para aumentar a cobertura.**

### 13.2. Testes Unitários

Utilizar JUnit 5 e Mockito quando necessário.

Os testes devem:

- Isolar a regra de negócio das dependências externas.
- Verificar os valores e comportamentos esperados.
- Testar caminhos positivos e negativos.
- Testar condições de borda.
- Verificar exceções e invariantes.
- Evitar mocks desnecessários.
- Não reproduzir a implementação no próprio teste.

O uso de Mockito deve se limitar às dependências que precisam ser substituídas. Classes de domínio e cálculos financeiros puros devem ser testados diretamente sempre que possível.

### 13.3. Testes Financeiros Obrigatórios

Para o SRM Credit Engine, considerar:

**Precificação**
- Golden cases C1, C2 e C3 com valores exatos.
- Tipos de recebíveis suportados e não suportados.
- Valores monetários inválidos.
- Prazos inválidos e condições de borda.
- Taxas inválidas.
- Precisão decimal e arredondamento HALF_EVEN.
- Deságio e conversão cambial.
- Ausência de taxa de câmbio válida.

**Liquidação**
- Liquidação válida.
- Recebível inexistente.
- Recebível inelegível.
- Recebível já liquidado.
- Repetição da mesma requisição.
- Mesma chave de idempotência com conteúdo diferente.
- Falhas durante a operação.
- Preservação dos dados de auditoria.

**Concorrência e integridade**
- Tentativas simultâneas de liquidar o mesmo recebível.
- Conflitos de versão.
- Violações de unicidade.
- Rollback quando uma operação falha.
- Ausência de registros parciais após falhas.

### 13.4. Testes de Integração

Utilizar Spring Boot Test e Testcontainers quando necessário para comprovar comportamentos que dependem da infraestrutura.

Priorizar:

- Integridade referencial.
- Restrições do PostgreSQL.
- Transações e rollback.
- Persistência de idempotência.
- Optimistic locking.
- Migrations Flyway.
- Consultas SQL e paginação.

**Mocks não substituem testes de integração quando a garantia depende do comportamento real do banco de dados.**

### 13.5. Testes de API REST
- Validação dos DTOs e entradas inválidas.
- Códigos HTTP corretos.
- Estrutura padronizada das respostas de erro.
- Paginação e filtros.
- Aderência aos contratos OpenAPI.
- Ausência de exposição de informações sensíveis.

### 13.6. Critérios de Qualidade dos Testes

Antes de considerar uma funcionalidade concluída, a IA deve verificar:

1. Todos os critérios de aceite aplicáveis possuem testes?
2. Os cenários negativos conhecidos estão contemplados?
3. As condições de borda relevantes foram verificadas?
4. As invariantes de negócio estão protegidas?
5. Uma implementação incorreta seria detectada por esses testes?
6. Existem cenários críticos que dependem de testes de integração?
7. As limitações e os riscos residuais foram documentados?

### 13.7. Cobertura de Código

A cobertura deve ser utilizada como indicador complementar.

- Alta cobertura não garante corretude.
- Baixa cobertura pode indicar cenários não exercitados.
- Não adicionar testes sem valor apenas para atingir metas percentuais.
- Não modificar critérios de aceite ou asserts para fazer testes passarem.
- Não declarar uma regra validada apenas porque suas linhas foram executadas.

**Princípio central: testes devem detectar defeitos reais e proteger o comportamento do negócio, não apenas produzir métricas de cobertura.**

Não afirmar que teste/build foi executado sem evidência. Não elevar
artificialmente a cobertura com testes triviais.

### 13.8. Governança da Execução dos Testes

A execução de testes automatizados deve respeitar integralmente
as regras de governança estabelecidas no AGENTS.md.

#### Regras obrigatórias

- A IA DEVE solicitar autorização explícita antes de executar
  qualquer comando de teste, compilação ou validação no terminal.

- A autorização deve identificar o comando exato, o diretório
  de execução, a finalidade e os possíveis efeitos colaterais.

- A aprovação de um plano de implementação NÃO autoriza
  automaticamente a execução de testes.

- A IA NÃO DEVE modificar testes existentes apenas para
  fazer uma implementação incorreta passar.

- É proibido remover, desabilitar ou ignorar testes que falharam
  sem apresentar a causa e obter autorização explícita.

- Quando um teste falhar, a IA DEVE investigar a causa,
  identificar se o defeito está na implementação, no teste
  ou na especificação e propor uma correção.

- Nenhuma correção pode ser aplicada sem autorização prévia.

- A IA DEVE registrar quais testes foram executados,
  quais passaram, quais falharam e quais não foram executados.

- É proibido declarar uma funcionalidade validada quando
  os testes necessários não foram executados ou falharam.

#### Critério de conclusão

Uma funcionalidade somente poderá ser considerada validada quando:

1. Os cenários de negócio aplicáveis estiverem contemplados.
2. Os testes relevantes tiverem sido executados e aprovados.
3. Não existirem falhas críticas conhecidas sem tratamento.
4. Os critérios de aceite da SPEC estiverem satisfeitos.
5. Os resultados e eventuais limitações forem apresentados.

Caso a execução dos testes não seja autorizada, a IA
deverá informar explicitamente:

"Implementação concluída, mas não validada por testes executados."

## 14. Fluxo de trabalho nesta Skill

1. **Entenda:** pedido, SPEC, decisões aprovadas e risco financeiro.
2. **Planeje:** consulte `project-planning` quando pertinente; proponha
   solução mínima, subtarefas, arquivos, riscos e testes.
3. **Solicite autorização de escrita:** liste alterações por arquivo
   conforme G-01 e espere aprovação.
4. **Implemente exclusivamente o aprovado**, sem ampliar escopo.
5. **Solicite autorização separada antes de cada comando** de terminal,
   indicando comando, diretório, objetivo, efeitos e riscos.
6. **Valide e revise:** compare resultado com SPEC, testes e invariantes.
7. **Relate:** arquivos realmente alterados, comandos realmente
   executados, resultados e pendências. Não fabrique evidências.

### Modelo sucinto para apresentar uma tarefa

- **Entendimento:** resultado esperado e critério de aceite.
- **Plano:** subtarefas sequenciais verificáveis.
- **Arquivos:** criar/alterar/consultar e objetivo de cada mudança.
- **Riscos:** negócio, precisão, persistência, integração e segurança.
- **Testes planejados:** unitário, integração e concorrência conforme caso.
- **Autorização solicitada:** escopo exato de escrita; comandos de
  terminal serão solicitados individualmente quando necessários.

**Regra final: apenas o necessário, sempre correto e comprovável.**
