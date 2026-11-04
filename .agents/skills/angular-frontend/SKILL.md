---
name: angular-frontend
description: >
  Orienta o desenvolvimento autorizado do frontend Angular do SRM Credit Engine.
  Use em tarefas de componentes, telas de simulação, extrato paginado, formulários,
  integração REST, estado de interface, acessibilidade, segurança e testes.
  Respeita AGENTS.md, especificações aprovadas, planejamento SDD e autorizações humanas.
---

# Angular Frontend — SRM Credit Engine

## 1. Objetivo e precedência

Atue como especialista em Angular e TypeScript, priorizando a **solução mais simples, correta, acessível, segura e sustentável**. O frontend permite simular operações e consultar transações; **o backend é a fonte de verdade das regras financeiras**.

Consulte, nesta ordem, o `AGENTS.md`, o enunciado e a `SPEC.md` aprovada, as decisões e contratos aplicáveis, o plano autorizado e, por fim, esta Skill. Na existência de contradição ou lacuna relevante, apresente o problema e aguarde decisão; não invente regra de negócio.

## 2. Governança obrigatória

- **Antes de cada tarefa:** apresente plano com subtarefas sequenciais, arquivos envolvidos, riscos e critérios de aceite.
- **Antes de qualquer escrita:** liste os arquivos a criar, editar, excluir ou formatar, as alterações previstas e seus efeitos; obtenha **autorização explícita para esse escopo**.
- **Antes de cada comando de terminal, inclusive leitura, geração Angular CLI, lint, testes, build, Git ou Docker:** informe comando exato, diretório, finalidade e efeitos possíveis; solicite autorização específica.
- A aprovação do plano **não autoriza automaticamente** comandos nem mudanças fora dos arquivos indicados.
- **G-03 — Execução de terminal:**
  Toda execução de comandos deve respeitar a política
  de segurança, autorização e eficiência definida no AGENTS.md.
  Comandos de baixo risco podem ser executados sem autorização
  individual quando contemplados no escopo previamente aprovado.
  Operações fora desse escopo exigem autorização explícita.
  Esta Skill não pode substituir ou ampliar as permissões
  estabelecidas no AGENTS.md.
- Não altere contratos da API, arquitetura, cálculos, política de arredondamento, autenticação ou testes aprovados sem nova proposta e autorização.
- Não delegue ações proibidas a outros modelos, agentes, Skills ou scripts. A preferência por modelo não implica troca automática nem dispensa governança.
- Se encontrar decisão não especificada, **interrompa a parte afetada e peça esclarecimento**, sem ampliar o escopo por iniciativa própria.

## 3. Stack e convenções

- **Angular**, **TypeScript**, HTML e CSS/SCSS.
- A versão do Angular deve ser definida no planejamento técnico, considerando compatibilidade, estabilidade e requisitos do desafio.
- Após definida, respeitar a versão aprovada e registrada no projeto.
- Priorize **Standalone Components**, templates declarativos, serviços e `HttpClient` para integração com a API.
- Utilize **Signals** e `computed()` para estado local e derivado; **RxJS** para fluxos assíncronos, HTTP e composição de streams quando necessário. Não introduza NgRx sem problema concreto.
- Use **Reactive Forms** para formulários não triviais, com validação e feedback claros.
- Siga a versão efetivamente instalada do Angular; **não atualize dependências ou altere o tooling sem aprovação**. Consulte a API da versão instalada antes de adotar recursos recentes.
- Identificadores de código, componentes, arquivos e propriedades em **inglês**; textos da interface e documentação de negócio em **português brasileiro**, salvo contrato aprovado diferente.
- Componentes: `PascalCase` para classes, `camelCase` para variáveis e métodos, nomes de arquivos coerentes com convenções Angular existentes. Sufixos e caminhos devem respeitar os padrões do projeto, sem renomeações cosméticas.
- TypeScript com tipagem estrita; evite `any`, casts inseguros e `!` não justificados. Use `readonly` e tipos explícitos onde agreguem segurança.
- Prefira `inject()` ou injeção de dependências idiomática à versão instalada; mantenha dependências observáveis e fáceis de testar.

## 4. Arquitetura e organização

**Abordagem padrão: frontend feature-based**, com componentes de apresentação, serviços de acesso à API e modelos tipados próximos da funcionalidade a que pertencem.

Possíveis funcionalidades, a criar **somente quando houver escopo aprovado**:

- `simulation`: entrada e exibição de simulações de recebíveis.
- `transactions` ou `statement`: listagem paginada, filtros e detalhes.
- `shared`: apenas componentes, utilitários e contratos de fato reutilizados.

Responsabilidades:

- **Componentes:** interação, renderização, estado de apresentação e validação de UX; sem cálculos financeiros.
- **Serviços HTTP:** URLs, parâmetros, serialização e interpretação de respostas; sem regras de negócio do servidor.
- **Modelos/DTOs:** contratos explícitos da API, sem depender de formatos implícitos.
- **Estado local:** Signals; serviços de estado somente se houver compartilhamento real entre componentes.

Evite camadas, factories, facades, gerenciadores globais de estado ou bibliotecas internas criadas por antecipação. Prefira componentes pequenos e composição.

## 5. Clean Code, SOLID e padrões de projeto

- **KISS:** privilegie código direto e de fácil leitura.
- **YAGNI:** não implemente recursos hipotéticos.
- **DRY:** elimine duplicação de conhecimento; não abstraia similaridades superficiais.
- **SRP:** cada componente ou serviço deve ter responsabilidade clara.
- **OCP/DIP:** contratos ou estratégias somente para variações reais ou dependências que necessitam substituição.
- **LSP/ISP:** contratos coerentes, pequenos e sem métodos supérfluos.
- Prefira composição a herança de componentes.
- Use **Adapter** para converter formatos de API quando a transformação for relevante; **Facade** apenas quando simplificar um fluxo realmente complexo; **Strategy** somente quando existirem comportamentos de UI substituíveis. Não force padrões para demonstrar conhecimento.
- Não coloque regras de apresentação em interceptors globais. Centralize comportamento apenas quando for transversal e comprovado.

## 6. Fluxos obrigatórios do desafio

### 6.1. Simulação financeira

- A tela envia dados ao endpoint aprovado e **exibe exclusivamente o resultado calculado pelo backend**.
- Não duplique a fórmula de valor presente, spread, deságio, conversão BRL/USD nem arredondamento monetário em TypeScript.
- **Nunca use `number` como fonte de verdade para precisão financeira.** Conforme contrato aprovado, transporte valores monetários e taxas como strings decimais exatas ou outro formato definido formalmente. Não converter silenciosamente valores de precisão arbitrária para `Number`.
- Formate moeda na apresentação preservando o valor recebido, a moeda e a precisão contratadas. Uma biblioteca decimal só pode ser adicionada mediante necessidade e aprovação.
- Validar campos no formulário melhora UX, mas não substitui as validações do servidor.
- Tratar explicitamente loading, sucesso, ausência de cotação, erro de validação e falha de comunicação.

### 6.2. Extrato e transações

- Paginação, filtros por período/vendedor/moeda e ordenação devem ocorrer **no backend**, conforme OpenAPI aprovado.
- Não baixar todos os registros para paginar/filtrar no navegador.
- Preservar critérios de busca ao mudar de página quando definido na UX; tratar página vazia, mudança de filtros e falhas de carregamento.
- Não inferir dados de auditoria nem alterar visualmente o histórico para simular alterações inexistentes.

### 6.3. Operações com efeito financeiro

- Em ações de liquidação, impedir duplo clique por UX, mas **não tratar o bloqueio do botão como garantia de idempotência**.
- Usar mecanismo de idempotência e fluxo de confirmação somente conforme contrato aprovado do backend.
- Não realizar retries automáticos de mutações financeiras sem garantia explícita de segurança.

## 7. HTTP, estado e tratamento de erros

- Concentrar a integração REST em serviços tipados; seguir endpoints, parâmetros, status e DTOs realmente aprovados.
- Interceptors apenas para preocupações transversais justificadas, como correlação ou autenticação **se definida**.
- Tratar respostas `400`, `404`, `409`, `422` (se o contrato utilizar), `5xx`, timeout e indisponibilidade com mensagens claras e proporcionais.
- Não engolir erros, mostrar sucesso falso ou expor stack trace, SQL, segredo ou resposta interna bruta ao usuário.
- Exibir mensagens de validação junto aos campos e mensagens de falha global quando apropriado.
- Em chamadas assíncronas, prevenir estado obsoleto de forma explícita (por exemplo, cancelar buscas anteriores quando novos filtros substituírem os anteriores).
- Não criar mecanismo de retry genérico sobre chamadas com efeitos financeiros.
- Estados de tela devem distinguir `idle`, `loading`, `success`, `empty` e `error` quando aplicáveis, sem framework próprio desnecessário.

## 8. Segurança, privacidade e acessibilidade

- Nunca armazenar segredos, credenciais privadas ou chaves de integração em código frontend ou variáveis publicadas no bundle.
- Não usar `innerHTML` com dados não confiáveis nem contornar sanitização do Angular (`bypassSecurityTrust...`) sem análise e autorização.
- Não concatenar input de usuário em HTML ou URLs inseguras; construir parâmetros HTTP de modo seguro.
- Autorização de ações e proteção real de dados são responsabilidades do servidor; guards de rota são melhoria de UX, **não fronteira de segurança**.
- Política de autenticação, sessão, CORS e CSRF depende da arquitetura aprovada; não inventar fluxo de login.
- Respeitar privacidade: não registrar payload financeiro, tokens ou identificadores sensíveis sem necessidade.
- Usar HTML semântico, rótulos, mensagens acessíveis, foco previsível, navegação por teclado e contraste suficiente.
- Garantir feedback compreensível para leitores de tela em erros de formulário e operações assíncronas, conforme escopo da interface.

## 9. Performance e observabilidade

- Evitar solicitações redundantes, subscriptions sem gerenciamento de ciclo de vida e renderizações custosas.
- Usar estratégias idiomáticas de renderização e listas (`track`, `OnPush` quando fizer sentido), sem micro-otimização precoce.
- Estados de loading e empty devem refletir o resultado real, não mascarar lentidão ou falhas.
- Registrar erros técnicos úteis para diagnóstico **sem dados sigilosos**; um `correlationId` aprovado pode ajudar a relacionar frontend e backend.
- Não introduzir SDKs de analytics, rastreamento ou monitoramento externo sem autorização e análise de privacidade.
- Não declarar observabilidade operacional se houver apenas mensagens no console local.

## 10. Estratégia de testes — comportamento antes de cobertura

**REGRA IMPERATIVA:** testes devem detectar falhas funcionais e proteger os comportamentos e invariantes relevantes, **não apenas aumentar a cobertura de linhas**.

- Elaborar cenários positivos, negativos, de borda e transições de estado para cada funcionalidade aprovada.
- **Componentes:** renderização correta, acessibilidade relevante, validação, loading, erro, empty state e prevenção de duplo envio.
- **Formulários:** campos obrigatórios, formatos inválidos, limites, mensagens e bloqueio de submissão inválida.
- **Integração HTTP:** parâmetros enviados, tipagem/normalização contratada, erros esperados, respostas incompletas e ausência de cotação.
- **Simulação:** comprovar que valores recebidos do backend são exibidos sem recálculo ou perda silenciosa de precisão, especialmente os três golden cases quando apresentados pela API.
- **Extrato:** filtros e paginação no servidor, atualização de dados, estados vazios, resultados fora de ordem e falhas de carregamento.
- **Regressão:** proteger comportamentos previamente aprovados; não remover asserts para adaptar a implementação incorreta.
- Usar o runner de testes **aprovado e já configurado** no projeto. Não presumir Jest, Vitest ou Karma sem verificar a configuração existente.
- Preferir testes determinísticos, legíveis e orientados a efeitos observáveis; mocks de API são adequados para testes de UI, mas não comprovam regras financeiras do backend.
- A cobertura de código é **indicador secundário**; não criar testes artificiais para atingir meta percentual.

### Quality Gates

Antes de considerar uma funcionalidade validada, verificar:

1. Todos os critérios de aceite aplicáveis têm evidência de teste?
2. Casos negativos e limites conhecidos foram exercitados?
3. Erros HTTP e estados de interface são consistentes?
4. Os valores monetários são exibidos sem alteração indevida?
5. Um defeito relevante seria detectado pelos testes existentes?
6. Foram executados os testes necessários, com aprovação prévia de cada comando?

**Execução e governança:** cada comando de teste, lint ou build exige autorização específica. Não omitir falhas, não desabilitar testes e não declarar validação sem evidência. Caso os comandos não sejam autorizados, comunicar: **"Implementação concluída, mas não validada por testes executados."**

## 11. Fluxo operacional da Skill

1. **Compreender:** demanda, contrato OpenAPI e critérios de aceite aprovados.
2. **Planejar:** mudanças mínimas, arquivos, subtarefas, riscos e cenários de testes.
3. **Apresentar proposta de escrita:** identificar o que será criado/alterado e solicitar aprovação explícita.
4. **Executar somente o aprovado**, mantendo as regras de governança.
5. **Solicitar autorização por comando**, inclusive Angular CLI, instalação, testes e build.
6. **Validar:** revisar interface, comportamento, segurança e aderência à API.
7. **Relatar:** alterações feitas, comandos efetivamente executados, evidências, falhas e pendências sem inventar resultados.

**Princípio final: simplicidade primeiro; experiência do usuário correta, contratos preservados e segurança verificável.**
