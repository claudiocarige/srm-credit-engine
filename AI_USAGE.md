# AI_USAGE.md — Uso de Inteligência Artificial

## 1. Objetivo

Documentar o uso de ferramentas de inteligência
artificial durante o desenvolvimento do SRM Credit Engine,
registrando contribuições, validações humanas,
limitações e correções realizadas.

## 2. Ferramentas utilizadas

| Ferramenta | Finalidade |
|---|---|
| Google Antigravity | Ambiente de desenvolvimento assistido por IA |
| ChatGPT | Discussão arquitetural e revisão técnica |

## 3. Política de utilização

- A IA atua como ferramenta de apoio.
- As decisões arquiteturais exigem aprovação humana.
- O código gerado deve ser revisado e testado.
- Nenhuma sugestão da IA é considerada correta
  apenas por ter sido gerada por um modelo.
- Os registros devem representar acontecimentos reais.

## 4. Histórico de utilização

| Data | TASK | Ferramenta | Atividade | Validação humana |
|---|---|---|---|---|
| 2026-11-05 | TASK-003 | Google Antigravity (Gemini) | Implementação inicial do domínio financeiro com as classes concentradas em `pricing.domain`. | Organização identificada para revisão humana, com proposta de separação de responsabilidades. |
| 2026-11-05 | TASK-003 | ChatGPT | Análise arquitetural e proposta de reorganização em `domain.model`, `domain.strategy` e `domain.calculation`. | Estrutura avaliada e selecionada pelo desenvolvedor; refatoração solicitada ao Gemini. Testes pós-refatoração pendentes. |

## 5. Erros identificados na IA

### Caso 001 — PENDENTE

**Contexto:**

**Ferramenta utilizada:**

**O que a IA sugeriu:**

**Por que estava incorreto:**

**Como o problema foi identificado:**

**Correção aplicada:**

**Evidência de validação:**

## 6. Lições aprendidas

### TASK-003 — Revisão humana e organização do domínio financeiro

Durante o desenvolvimento do motor financeiro BRL, a IA organizou inicialmente as classes diretamente no pacote pricing.domain.

A estrutura proposta era funcional e não representava, por si só, uma violação arquitetural. Entretanto, durante a revisão humana, foi identificada uma oportunidade de melhorar a organização das responsabilidades.

**A decisão foi reorganizar o domínio em subpacotes específicos:**

  - domain.model: tipos e objetos imutáveis do domínio.

  - domain.strategy: contrato e implementações das estratégias de precificação.

  - domain.calculation: componentes matemáticos reutilizáveis.

  - domain: motor financeiro e exceção de validação.


**Lição aprendida:** o uso de IA acelera a implementação, mas não substitui a análise crítica do desenvolvedor. Uma solução tecnicamente válida pode ser aprimorada considerando organização, legibilidade e evolução do projeto.

**Decisão humana:** preservar o comportamento financeiro e solicitar uma refatoração exclusivamente estrutural, sem introduzir novas abstrações ou dependências.

