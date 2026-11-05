# TASK-001 — Plano Técnico de Implementação

## 1. Identificação
- **Tarefa:** `TASK-001` — Consolidar backend existente e base de testes
- **Objetivo:** validar e consolidar o scaffold Spring Boot existente em `backendcreditengine/` (Java 21, Maven Wrapper, configuração segura sem segredos e base de testes com PostgreSQL via Testcontainers), deixando o projeto pronto para evolução modular por funcionalidade sem implementar regras financeiras nesta etapa.
- **Status do planejamento:** `APROVADO PELO RESPONSÁVEL`
- **Referências:** `SPEC.md` (§§1, 5); `DECISIONS.md` (`DEC-001`, `DEC-002`, `DEC-010`); `AGENTS.md` (`G-01` a `G-04`); `TASKS.md` (`TASK-001`).
- **Dependências:** nenhuma.
- **Ferramenta e modelo de IA elaborador:** Google Antigravity — Gemini 4 Argon.

## 2. Diagnóstico do Estado Atual
- **Estrutura existente:** diretório `backendcreditengine/` já criado com pacote raiz `com.claudiocarige.backendcreditengine`, contendo `BackendcreditengineApplication.java`, `BackendcreditengineApplicationTests.java`, `src/main/resources/application.yaml` e `.gitignore`.
- **Componentes disponíveis:**
  - Java `21` configurado em `<java.version>21</java.version>` no `pom.xml`.
  - `spring-boot-starter-parent` na versão `4.1.2`.
  - Maven Wrapper (`mvnw`, `mvnw.cmd` e `.mvn/wrapper/maven-wrapper.properties` apontando para `wrapperVersion=3.3.4` e `apache-maven-3.10.0`).
  - Dependências presentes no `pom.xml`: `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `flyway-database-postgresql`, `postgresql` (`runtime`), `spring-boot-devtools` (`runtime`/`optional`) e starters modulares `*-test`.
- **Lacunas identificadas:**
  1. `application.yaml` possuía apenas `spring.application.name: backendcreditengine`; como JPA e Flyway já estão no classpath, a subida do contexto e o teste `@SpringBootTest` falhariam por ausência de `DataSource` configurado.
  2. Faltavam no `pom.xml` as dependências de teste do Testcontainers para PostgreSQL (`spring-boot-testcontainers` e módulo PostgreSQL do Testcontainers gerenciados pelo BOM do Spring Boot `4.1.2`) exigidas pela `DEC-001` e `DEC-010`.
  3. Faltavam diretivas seguras padrão em `application.yaml`: `spring.jpa.hibernate.ddl-auto=validate` (`DEC-001`), `spring.jpa.open-in-view=false`, timezone UTC, parametrização de banco por variáveis de ambiente sem credenciais reais e exposição mínima do Actuator (`health` apenas, sem detalhes internos).
  4. Ausência do diretório `src/main/resources/db/migration/` esperado pelo Flyway.
  5. Necessidade de comprovar em execução real a resolução do Maven `3.10.0` no Wrapper e o `effective-pom` do Spring Boot `4.1.2`.
- **Evidências da inspeção:** leitura estática verificada em `backendcreditengine/pom.xml` (linhas 1–108), `.mvn/wrapper/maven-wrapper.properties` (linhas 1–3), `src/main/resources/application.yaml` (linhas 1–3), `BackendcreditengineApplication.java` (linhas 1–13) e `BackendcreditengineApplicationTests.java` (linhas 1–13).

## 3. Abordagem Técnica Proposta
- **Solução recomendada:**
  1. Preservar integralmente o diretório `backendcreditengine/`, o pacote `com.claudiocarige.backendcreditengine` (futuras funcionalidades entrarão como subpacotes por feature sob esse pacote raiz conforme `DEC-002`, sem criar pacotes vazios agora) e o parent `spring-boot-starter-parent:4.1.2`.
  2. Verificar o funcionamento do Maven Wrapper (`./mvnw -version`) e inspecionar o gerenciamento de dependências do Spring Boot `4.1.2` (`./mvnw help:effective-pom`) antes de editar o `pom.xml`.
  3. Adicionar ao `pom.xml` exclusivamente as dependências de `test` necessárias para Testcontainers com PostgreSQL (gerenciadas pelo BOM do Spring Boot), sem introduzir bibliotecas ainda sem uso na tarefa (`springdoc-openapi` ficará para a primeira tarefa de API REST após validar versão compatível com Boot `4.1.2`; `MapStruct` permanecerá fora enquanto não houver necessidade concreta).
  4. Consolidar `src/main/resources/application.yaml` com variáveis de ambiente (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) sem segredos reais versionados, `ddl-auto: validate`, `open-in-view: false`, UTC, Flyway habilitado com diretório `src/main/resources/db/migration/.gitkeep` e Actuator restrito ao endpoint `health`.
  5. Configurar suporte reutilizável de teste com Testcontainers PostgreSQL (`@TestConfiguration` + `@ServiceConnection`) e integrá-lo ao `BackendcreditengineApplicationTests` para validar a inicialização completa do contexto Spring + DataSource + Flyway + Hibernate contra PostgreSQL real.
- **Justificativa:** resolve a falha imediata de inicialização de contexto com a menor mudança possível (KISS/YAGNI), valida desde a primeira tarefa a integração real com PostgreSQL exigida por `DEC-001` e `DEC-010`, evita configurações descartáveis e prepara a base exata para as tarefas seguintes (`TASK-003` e `TASK-005`).
- **Alternativas consideradas:**
  - *Alternativa A — Recriar o projeto ou renomear `groupId`/`artifactId`/pacote base:* rejeitada por gerar diff cosmético sem valor funcional e violar as restrições da `TASK-001`.
  - *Alternativa B — Desabilitar autoconfiguração de DataSource/JPA/Flyway em perfil de teste ou usar H2 até a `TASK-005`:* rejeitada como abordagem principal porque `DEC-010` veda H2 para validação de infraestrutura PostgreSQL e a exclusão temporária ocultaria falhas reais de configuração dos starters já presentes (mantida apenas como contingência documentada caso o daemon Docker esteja indisponível).
  - *Alternativa C — Incluir `springdoc-openapi` e `mapstruct` já na `TASK-001`:* rejeitada por YAGNI; não há endpoints nem mapeamentos nesta tarefa, e bibliotecas fora do BOM oficial exigem validação específica quando forem efetivamente utilizadas.

## 4. Escopo e Restrições
- **O que será implementado:**
  - Verificação do Maven Wrapper e do BOM do Spring Boot `4.1.2`.
  - Adição restrita das dependências de teste Testcontainers PostgreSQL compatíveis no `pom.xml`.
  - Consolidação segura do `application.yaml` e criação de `src/main/resources/db/migration/.gitkeep`.
  - Configuração `TestcontainersConfiguration` no escopo de teste e ajuste de `BackendcreditengineApplicationTests` para validar subida real com PostgreSQL.
- **O que não será implementado:**
  - Regras financeiras, `PricingStrategy`, entidades JPA, scripts SQL de negócio, endpoints REST, OpenAPI, código Angular, Dockerfiles da aplicação ou pacotes vazios antecipados.
- **Decisões arquiteturais aplicáveis:** `DEC-001` (stack Java 21, Spring Boot, JPA/Hibernate com `ddl-auto=validate`, Flyway, Testcontainers PostgreSQL), `DEC-002` (monólito modular *package by feature* sem camadas artificiais) e `DEC-010` (validação de infraestrutura em PostgreSQL real).

## 5. Arquivos Previstos
| Arquivo | Ação | Justificativa |
|---|---|---|
| `backendcreditengine/.mvn/wrapper/maven-wrapper.properties` | Manter (ou ajustar somente se `3.10.0` falhar) | Garantir build reproduzível via `./mvnw` caso a URL configurada exija ajuste pontual comprovado |
| `backendcreditengine/pom.xml` | Modificar | Adicionar exclusivamente dependências `test` de Testcontainers PostgreSQL gerenciadas pelo BOM do Spring Boot `4.1.2` |
| `backendcreditengine/src/main/resources/application.yaml` | Modificar | Parametrizar `datasource` por variáveis de ambiente sem segredos, definir `ddl-auto=validate`, `open-in-view=false`, UTC e restringir Actuator a `health` |
| `backendcreditengine/src/main/resources/db/migration/.gitkeep` | Criar | Garantir existência do diretório padrão do Flyway antes das migrations da `TASK-005` |
| `backendcreditengine/src/test/java/com/claudiocarige/backendcreditengine/TestcontainersConfiguration.java` | Criar | Prover container `PostgreSQLContainer` via `@ServiceConnection` reutilizável nos testes de integração |
| `backendcreditengine/src/test/java/com/claudiocarige/backendcreditengine/BackendcreditengineApplicationTests.java` | Modificar | Importar `TestcontainersConfiguration` para comprovar inicialização real do contexto Spring Boot com PostgreSQL |

## 6. Subtarefas de Implementação
| Ordem | Subtarefa | Resultado esperado |
|---|---|---|
| 1 | Criar branch da tarefa (mediante autorização G-03) e validar `./mvnw -version` e JDK 21 | Ambiente Java 21 e Maven Wrapper comprovadamente operacionais |
| 2 | Inspecionar gerenciamento de versões do BOM `4.1.2` para Testcontainers PostgreSQL | Coordenadas exatas compatíveis com Spring Boot `4.1.2` confirmadas sem versões arbitrárias |
| 3 | Atualizar `pom.xml` apenas com as dependências `test` de Testcontainers PostgreSQL | Dependências de teste resolvidas sem conflitos |
| 4 | Consolidar `application.yaml` seguro e criar `src/main/resources/db/migration/.gitkeep` | Configuração limpa, sem credenciais reais, com `ddl-auto=validate`, `open-in-view=false` e Actuator restrito |
| 5 | Implementar `TestcontainersConfiguration` e integrar em `BackendcreditengineApplicationTests` | Teste de inicialização conectado a PostgreSQL efêmero via `@ServiceConnection` |
| 6 | Executar `./mvnw clean test` em `backendcreditengine/` e registrar evidências no plano | Compilação e teste de contexto aprovados em PostgreSQL real |

## 7. Critérios de Aceite
- **CA-01:** Projeto compila sem erros em Java 21 utilizando o Maven Wrapper (`./mvnw`).
- **CA-02:** Dependências do `pom.xml` verificadas e resolvidas sem adição de bibliotecas desnecessárias para esta fase.
- **CA-03:** `application.yaml` parametrizado por variáveis de ambiente, sem credenciais ou segredos reais versionados, com `ddl-auto=validate`, `open-in-view=false` e Actuator expondo apenas `health`.
- **CA-04:** Estrutura existente preservada (`backendcreditengine/` e pacote raiz `com.claudiocarige.backendcreditengine`), sem pacotes ou classes vazias especulativas (`DEC-002`).
- **CA-05:** `BackendcreditengineApplicationTests.contextLoads()` executa e passa contra PostgreSQL via Testcontainers (`DEC-010`).

## 8. Estratégia de Testes
| Critério | Teste ou verificação | Resultado esperado |
|---|---|---|
| CA-01 / CA-02 | Execução de `./mvnw -B -V test-compile` (e conferência de dependências) em `backendcreditengine/` | Build de compilação reproduzível em Java 21 com dependências íntegras |
| CA-03 | Revisão estática de `application.yaml` e `git diff` | Zero segredos hardcoded; propriedades de JPA, Flyway e Actuator corretas |
| CA-04 | Inspeção da árvore `src/main/java` | Ausência de pacotes/camadas vazias ou renomeações indevidas |
| CA-05 | Execução de `./mvnw -B clean test` com Docker ativo | `BackendcreditengineApplicationTests.contextLoads()` verde subindo PostgreSQL no Testcontainers |

## 9. Riscos e Mitigações
| Risco | Impacto | Mitigação |
|---|---|---|
| Mudança de coordenadas de artefatos Testcontainers no BOM do Spring Boot `4.1.2` | Erro de resolução Maven ao adicionar dependência | Consultar `./mvnw help:effective-pom` na Subtarefa 2 antes de editar o `pom.xml` |
| Distribuição `apache-maven-3.10.0` em `maven-wrapper.properties` indisponível ou instável | Falha na primeira execução do `./mvnw` | Testar `./mvnw -version` na Subtarefa 1; apenas se falhar, propor ajuste pontual para versão Maven 3.9.x estável |
| Daemon Docker parado no ambiente local ao rodar `@SpringBootTest` | Falha de inicialização do Testcontainers | Verificar `docker info`/`docker compose ps` antes da execução dos testes |
| Exposição excessiva de endpoints do `spring-boot-starter-actuator` já presente | Vazamento de metadados de ambiente | Restringir explicitamente `management.endpoints.web.exposure.include=health` e `show-details=never` |

## 10. Revisão e Aprovação Humana
- **Plano apresentado:** 2026-11-04
- **Ajustes solicitados:** nenhum
- **Situação:** APROVADO PARA EXECUÇÃO
- **Aprovação:** aprovado pelo responsável em 2026-11-04 (`inicie a TASK-001` / `sim execute`)

## 11. Histórico de Revisões
| Revisão | Alteração | Justificativa | Aprovação |
|---|---|---|---|
| 1.0 | Elaboração inicial do plano técnico da `TASK-001` | Estabelecer diagnóstico real do scaffold e estratégia segura de consolidação e testes | Aprovado (2026-11-04) |

## 12. Resultado da Execução
- **Status:** `DONE` (concluída e aprovada pelo responsável).
- **Arquivos realmente alterados:**
  - `backendcreditengine/pom.xml` (modificado — adicionados `spring-boot-testcontainers`, `testcontainers-junit-jupiter` e `testcontainers-postgresql` com `scope=test`).
  - `backendcreditengine/src/main/resources/application.yaml` (modificado — `datasource` parametrizado por `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `ddl-auto: validate`, `open-in-view: false`, `hibernate.jdbc.time_zone: UTC`, Flyway em `classpath:db/migration` e Actuator restrito a `health` com `show-details: never`).
  - `backendcreditengine/src/main/resources/db/migration/.gitkeep` (criado).
  - `backendcreditengine/src/test/java/com/claudiocarige/backendcreditengine/TestcontainersConfiguration.java` (criado — `org.testcontainers.postgresql.PostgreSQLContainer` 2.0.5 com `@ServiceConnection`).
  - `backendcreditengine/src/test/java/com/claudiocarige/backendcreditengine/BackendcreditengineApplicationTests.java` (modificado — `@Import(TestcontainersConfiguration.class)`).
  - `backendcreditengine/.mvn/wrapper/maven-wrapper.properties` (mantido sem alterações; `apache-maven-3.10.0` operou normalmente).
- **Comandos executados:**
  - `java -version`, `docker info`, `.\mvnw.cmd -version`
  - `.\mvnw.cmd -q help:effective-pom "-Doutput=target/eff-pom.xml"` (com `JAVA_HOME=C:\Program Files\Zulu\zulu-21`)
  - `.\mvnw.cmd -B -q test-compile` e inspeção `javap` de `org.testcontainers.postgresql.PostgreSQLContainer`
  - `.\mvnw.cmd -B clean test` (com `JAVA_HOME=C:\Program Files\Zulu\zulu-21` e daemon Docker ativo)
- **Testes e resultados:**
  - **CA-01 (Compilação Java 21 + Wrapper):** APROVADO (`javac [debug parameters release 21]` com `Apache Maven 3.10.0` e `Zulu 21.0.11`).
  - **CA-02 (Dependências verificadas):** APROVADO (coordenadas Testcontainers `2.0.5` confirmadas no BOM do Spring Boot `4.1.2` e resolvidas com sucesso).
  - **CA-03 (Configuração segura sem segredos):** APROVADO (`application.yaml` sem credenciais reais; log de inicialização confirmou `Exposing 1 endpoint beneath base path '/actuator'`).
  - **CA-04 (Preservação estrutural):** APROVADO (nenhum pacote vazio ou renomeação cosmética).
  - **CA-05 (`BackendcreditengineApplicationTests.contextLoads()` com PostgreSQL):** APROVADO (`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS`; container `postgres:16-alpine` iniciado em PostgreSQL `16.15`, `flyway_schema_history` criada e Hibernate `7.4.13.Final` inicializado com `ddl-auto=validate` e isolamento `READ_COMMITTED`).
- **Desvios do planejamento:**
  - O `JAVA_HOME`/`PATH` padrão da sessão Windows apontava para `Zulu 17` (`C:\Program Files\Zulu\zulu-17`); para compilar e testar em Java 21, foi utilizado `C:\Program Files\Zulu\zulu-21` (`21.0.11`) já instalado na máquina.
  - A inspeção do `effective-pom` confirmou que o Spring Boot `4.1.2` gerencia Testcontainers `2.0.5`, cujas coordenadas modulares são `testcontainers-postgresql` e `testcontainers-junit-jupiter` (pacote `org.testcontainers.postgresql.PostgreSQLContainer`), prevenindo o uso incorreto das coordenadas legadas 1.x.
  - Não foi criada branch Git automaticamente por não ter sido solicitada na autorização.
- **Riscos remanescentes:**
  1. Recomenda-se configurar `JAVA_HOME` global do sistema/IDE para `C:\Program Files\Zulu\zulu-21` e incluir o CLI do Docker no `PATH` do Windows (o Testcontainers conectou com sucesso ao daemon Docker via pipe nativo do Windows, mas comandos diretos `docker compose` na futura `TASK-016` exigirão o binário `docker` no `PATH`).
- **Aprovação final:** Aprovado pelo responsável em 2026-11-04 (`ok`).
