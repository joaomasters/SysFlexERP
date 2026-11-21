# Design System — SysFlex ERP (frontend)

Este documento define o padrão visual único do sistema, com a tela **Contas a
Receber** como referência. Toda tela nova ou revisada deve seguir isto.

## 0. Identidade visual ("Dynamics")

Azul de marca sóbrio, neutros frios, cantos discretos e barra lateral azul-marinho.
Tudo é definido em **dois lugares**: `tailwind.config.js` (escalas) e `src/index.css`
(variáveis CSS das superfícies). Trocar a identidade no futuro = editar só esses dois.

| O quê | Como usar |
|---|---|
| Fundo da página | `bg-background` (nunca `bg-gray-50` solto na raiz da tela) |
| Painéis / cards / modais | `bg-card` (use o componente `Card`) |
| Bordas | `border-border` (a borda padrão do Tailwind já usa esse token) |
| Texto base | `text-foreground`; secundário `text-gray-500` |
| Barra lateral, login, Jarvis | `bg-topbar` (e `bg-topbar-soft` para cartões sobre ele) |
| Neutros | escala `gray` (cinzas frios, não os do Tailwind padrão) |
| Marca / ação principal / foco | `primary` (azul `#0f6cbd` em `-600`) |

**Regra:** nunca usar cores cruas do Tailwind para significado (`red-600`,
`emerald-500`, `amber-100`...). Use as escalas semânticas da seção 4. Cores decorativas
sem significado (ex.: ícones coloridos da tela inicial) podem usar `blue`, `violet`, `teal`
etc., mas com parcimônia.

**Ícones da tela inicial** seguem a área do sistema: PDV em `primary`, estoque em `info`
(perdas e validade em `warning`), financeiro em `success` (saídas de dinheiro em `danger`),
fiscal em `primary-800` e acesso/configurações em `gray`. Variar só o shade dentro da área.

**Tela de login:** ocupa a tela inteira, dividida em duas metades (a partir de 768px). À esquerda, painel
`primary-50` com uma ilustração plana em SVG (`LoginIlustracao.tsx`: barras crescentes e seta de
tendência nos azuis da marca) sobre ondas suaves; à direita, o formulário com o logo. Sem brilhos,
vidro, gradientes ou animação, de propósito. A ilustração é decorativa (`aria-hidden`). Abaixo de
768px só o formulário aparece, centralizado na tela. O logo usa `sysflex-logo-escuro.png` (a versão clara,
`sysflex-logo.png`, serve para fundos escuros como a sidebar): "Sys" em azul-marinho, "Flex" no azul
da marca e "| ERP" em cinza. A classe `login-claro` (index.css) neutraliza a cor de preenchimento
automático do navegador nos campos.

**Logo:** há duas versões em `src/assets`. `sysflex-logo-escuro.png` (colorido: "Sys" azul-marinho, "Flex" azul da
marca, "| ERP" cinza) serve para **fundos claros** e é a usada no login, no topo do menu lateral e no cabeçalho
mobile. `sysflex-logo.png` (branco) serve para **fundos escuros**, e hoje não está em uso. No desktop, o topo do
menu lateral é um bloco claro (`bg-card`) da mesma altura do cabeçalho (`h-14`), formando uma faixa branca contínua.
As faixas de ambiente e do Jarvis ocupam a largura toda, acima do menu e do cabeçalho, para manter esse alinhamento.

**Cores fora do Tailwind** (Recharts, SVG): importar de `@/config/tema` (`COR_GRAFICO`).
Esse arquivo espelha os tokens; ao mudar a paleta, atualizar os três (`tailwind.config.js`,
`index.css`, `tema.ts`). Cupons e impressões (`ReciboCupom`, impressão de NF/expedição)
usam preto e branco fixos de propósito.

## 1. Componentes compartilhados (`src/shared/components/ui`)

Sempre importar de `@/shared/components/ui` em vez de recriar HTML/classes soltas:

| Componente | Uso |
|---|---|
| `CurrencyInput` | Todo campo de valor em R$ (preço, custo, pagamento, limite de crédito, sangria, etc.) |
| `WeightInput` | Todo campo de peso em kg/g (estoque, desossa, balança, PDV) |
| `Field` | Wrapper de label + campo + erro em formulários |
| `Button` | Todo botão de ação (ver seção 3 — variantes semânticas) |
| `StatusBadge` | Toda pílula de status (ver seção 4 — tons semânticos) |
| `PageHeader` | Cabeçalho de página (título + subtítulo + ações) |
| `Card` | Container branco arredondado (painel, tabela) |
| `Table`, `THead`, `TH`, `TBody`, `TR`, `TD`, `EmptyState`, `LoadingState` | Tabelas de listagem |
| `FilterTabs` | Filtros em pílula (abas de status) |
| `FormularioPagina` | **Cadastros e lançamentos**: formulário em tela cheia (cabeçalho com título, subtítulo e ações; formulário num `Card`). Padrão da tela de Produto |
| `Modal` | Só para confirmações e ações rápidas (inativar, cancelar com motivo, registrar pagamento em Contas a Receber) |

### Cadastro em tela cheia × modal

Telas de cadastro e lançamento **não abrem modal**: usam `FormularioPagina` (ou uma rota
própria, como `ProdutoCadastroPage`). O padrão é o da tela de Produto: título à esquerda,
`← Cancelar` (com a seta) e a ação principal à direita, e o conteúdo num `Card` logo abaixo. O card ocupa a largura toda da área de conteúdo, como em Produto, e os botões alinham com a borda dele. O Cancelar é desenhado pelo próprio componente (prop `onClose`); a página só informa as ações principais em `actions`.

Como aplicar numa tela que já tem o estado do formulário na própria página: depois de todos
os hooks, antes do `return` principal, `if (form) return <FormularioPagina ...>`. Os handlers
dos botões não mudam, só o lugar onde aparecem. Não há opção de largura: todos os formulários usam a largura total. Dê sempre um subtítulo e rotule a ação como em Produto ("Salvar Fornecedor", não só "Salvar").

Telas já neste padrão: Produto, Fornecedores, Clientes, Recebimento, Fichas de Desossa,
Perdas, NF de Saída, Faturamento e Contas a Pagar. Permanecem como diálogo: confirmações
(`ConfirmDialog`), cancelamento com motivo, histórico de pagamentos, visualizador de XML e
os modais do PDV (operação de balcão com atalhos de teclado).

## 2. Máscaras de preenchimento (`src/shared/utils/mask.ts`)

- **Moeda**: `CurrencyInput` já aplica a máscara — nunca usar `<input type="number">`
  para dinheiro. Para exibição (não-input), usar `formatBRL(valor)`.
- **Peso**: `WeightInput` já aplica a máscara — nunca usar `<input type="number"
  step="0.001">` para peso. Para exibição, usar `formatWeight(valor)`.
- Estratégia: máscara "caixa eletrônico" (dígitos preenchem da direita pra
  esquerda), igual em toda tela — elimina estados de digitação inválidos.

## 3. Botões — variantes semânticas (`Button`)

A variante representa o **significado** da ação, não uma escolha estética:

| Variante | Quando usar | Exemplos reais no sistema |
|---|---|---|
| `primary` | Ação principal da tela | Salvar Produto, Gerar carga agora, Abrir Inventário, Novo Cliente |
| `success` | Confirmação positiva | Pagar, Confirmar Pagamento, Finalizar Inventário |
| `danger` | Ação destrutiva/negativa | Excluir, Cancelar Venda, Estornar |
| `warning` | Atenção, não destrutiva | Reabrir Caixa, Ajustar Estoque |
| `secondary` | Ação neutra alternativa | Cancelar (modal), Fechar, Voltar |
| `ghost` | Ação terciária discreta | Ícones de ação em linha de tabela |
| `outline-danger` | Destrutiva de baixa ênfase | Cancelar item numa lista |

## 4. Cores de status (`StatusBadge`, tokens Tailwind)

Tokens semânticos definidos em `tailwind.config.js`: `primary`, `success`,
`warning`, `danger`, `info` (+ `neutral` = escala `gray`). Cada um tem shades
`50` a `950` — `100`+`700` para badges (fundo claro/texto forte), `600`+`700`
(hover) para botões sólidos, `300`/`400` para texto sobre fundo escuro (PDV).

| Token | Cor base (`-600`) | Observação |
|---|---|---|
| `primary` | `#0f6cbd` azul | marca, ação principal, foco |
| `success` | `#107c10` verde | concluído / positivo |
| `warning` | `#9e6000` âmbar | pendente / atenção (distinto do vermelho) |
| `danger` | `#c50f1f` vermelho | problema / destrutivo |
| `info` | `#0a7285` azul-petróleo | em andamento (distinto do azul da marca) |

Todos os pares usados nos componentes (texto branco em `-600`/`-700`, badge `700` sobre
`100`) têm contraste mínimo de 4,5:1 (WCAG AA).

**Importante:** o mesmo texto de status (ex. "ABERTO") pode ter tons
diferentes dependendo do que significa em cada módulo — o tom é sobre
**semântica**, não sobre a palavra:

| Tom | Significado | Exemplos |
|---|---|---|
| `success` | Concluído / positivo | PAGO, QUITADO, FINALIZADO, EMITIDA, Ativo |
| `warning` | Pendente, aguardando ação, ainda sem problema | PARCIAL, PENDENTE, conta a pagar/receber em aberto |
| `danger` | Problema / negativo | VENCIDO, FURTO, AVARIA, Excluído |
| `info` | Processo em andamento | Caixa aberto, Inventário em contagem, Faturamento aberto, Agrupado |
| `neutral` | Inativo / cancelado sem urgência | CANCELADO, Inativo |
| `purple` | Rótulo de categoria (não é status) | Tipo de cliente (Atacado, Restaurante...) |

> Observação de auditoria: antes desta revisão, "ABERTO" aparecia com 4 cores
> diferentes (vermelho em Contas a Receber, amarelo em Contas a Pagar, azul em
> Faturamento, verde em Inventário) porque cada tela definia sua própria
> paleta solta. Isso foi mantido *semanticamente* (cada um significa uma coisa
> diferente — dívida pendente vs. processo em andamento), mas agora usa os
> tons oficiais (`warning`/`info`) em vez de cores cruas do Tailwind.

## 5. Tipografia e espaçamento

| Elemento | Classe padrão |
|---|---|
| Título de página (h1) | `text-2xl font-bold text-gray-900` |
| Subtítulo de página | `text-gray-500 text-sm` |
| Cabeçalho de coluna de tabela | `text-xs font-semibold text-gray-500 uppercase tracking-wide` |
| Label de campo de formulário | `text-xs font-medium text-gray-600` |
| Texto de célula de tabela | `text-sm` (valores numéricos: `tabular-nums`) |
| Erro de campo | `text-danger-600 text-xs` |
| Texto de ajuda | `text-[11px] text-gray-400` |
| Padding de página | `p-6` |
| Padding de card | `p-4` (painéis compactos) ou `p-6` (formulários) |
| Espaçamento entre seções | `mb-6` (header → conteúdo), `mb-4` (filtros → tabela), `gap-3`/`gap-4` (grids) |
| Raio de borda | `rounded-lg` 4px (inputs/botões), `rounded-xl` 6px (cards), `rounded-2xl` 8px (modais), `rounded-full` (badges/pílulas). Os valores seguem `--radius` em `index.css` |
| Fonte | Segoe UI Variable / Segoe UI / Inter (Inter carregada pelo `index.html`). Algarismos tabulares ligados no `body`; `font-mono` só para códigos |
| Sombras | `shadow-card` (cards), `shadow-flutuante` (modais e popovers) |
| Ícones | `size={16}` em botões/linhas de tabela, `size={20}` em cabeçalhos de modal |

## 6. Escopo desta revisão

Aplicado nesta rodada: identidade "Dynamics" (paleta, neutros frios, raios, sidebar azul-marinho), fundação completa (tokens, máscaras, kit de UI) +
módulo **Financeiro** e **Estoque** por completo, PDV (campos monetários) e
demais telas em progresso. Ver checklist no PR/changelog para o que falta.
