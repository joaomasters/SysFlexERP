# Design System — SysFlex ERP (frontend)

Este documento define o padrão visual único do sistema, com a tela **Contas a
Receber** como referência. Toda tela nova ou revisada deve seguir isto.

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
| `Modal` | Modais de formulário/confirmação |

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
`warning`, `danger`, `info` (+ `neutral` = cinza padrão do Tailwind). Cada um
tem shades `50/100/500/600/700` — `100`+`700` para badges (fundo claro/texto
forte), `600`+`700` (hover) para botões sólidos.

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
| Raio de borda | `rounded-lg` (inputs/botões), `rounded-xl` (cards), `rounded-2xl` (modais), `rounded-full` (badges/pílulas) |
| Ícones | `size={16}` em botões/linhas de tabela, `size={20}` em cabeçalhos de modal |

## 6. Escopo desta revisão

Aplicado nesta rodada: fundação completa (tokens, máscaras, kit de UI) +
módulo **Financeiro** e **Estoque** por completo, PDV (campos monetários) e
demais telas em progresso. Ver checklist no PR/changelog para o que falta.
