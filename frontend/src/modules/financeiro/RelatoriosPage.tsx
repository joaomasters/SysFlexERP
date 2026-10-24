import { useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import { PageHeader, Card, Field, baseInputClass } from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'
import RelatorioEstoqueModal from '../estoque/components/RelatorioEstoqueModal'
import RelatorioValidadeModal from '../estoque/components/RelatorioValidadeModal'
import RelatorioPerdasModal from '../estoque/components/RelatorioPerdasModal'
import RelatorioComissoesModal from '../comissao/components/RelatorioComissoesModal'
import HistoricoPagamentosModal from './components/HistoricoPagamentosModal'
import DesempenhoRelatorio from './relatorios/DesempenhoRelatorio'
import PosicaoEstoqueRelatorio from './relatorios/PosicaoEstoqueRelatorio'
import ContasPagarPosicaoRelatorio from './relatorios/ContasPagarPosicaoRelatorio'
import ComprasFornecedorRelatorio from './relatorios/ComprasFornecedorRelatorio'
import { periodoPadrao } from './relatorios/PainelRelatorio'

type Grupo = 'Vendas e desempenho' | 'Estoque' | 'Compras' | 'Financeiro'

interface Opcao {
  id: string
  grupo: Grupo
  titulo: string
  /** Módulo cujo acesso o relatório exige (as chamadas usam as permissões desse módulo). */
  modulo: string
  /** Relatórios do histórico de pagamentos são restritos a administradores. */
  somenteAdmin?: boolean
  render: (periodo: { inicio: string; fim: string }) => JSX.Element
}

/**
 * Catálogo dos relatórios. Cada item só aparece para quem tem acesso ao módulo de origem —
 * os relatórios que já existem nas telas (Estoque, Validade, Perdas, Comissões, Histórico de
 * pagamentos) são os mesmos, reaproveitados aqui sem o modal.
 */
const OPCOES: Opcao[] = [
  { id: 'desempenho', grupo: 'Vendas e desempenho', titulo: 'Desempenho (vendas, margem e fluxo de caixa)', modulo: 'RELATORIOS',
    render: () => <DesempenhoRelatorio /> },
  { id: 'comissoes', grupo: 'Vendas e desempenho', titulo: 'Comissões', modulo: 'COMISSOES',
    render: p => <RelatorioComissoesModal embedded inicio={p.inicio} fim={p.fim} /> },

  { id: 'estoque', grupo: 'Estoque', titulo: 'Estoque (produtos)', modulo: 'PRODUTOS',
    render: () => <RelatorioEstoqueModal embedded /> },
  { id: 'posicao-estoque', grupo: 'Estoque', titulo: 'Posição de estoque (valor e itens abaixo do mínimo)', modulo: 'RELATORIOS',
    render: () => <PosicaoEstoqueRelatorio /> },
  { id: 'validade', grupo: 'Estoque', titulo: 'Validade dos lotes', modulo: 'PRODUTOS',
    render: () => <RelatorioValidadeModal embedded /> },
  { id: 'perdas', grupo: 'Estoque', titulo: 'Perdas', modulo: 'PERDAS',
    render: p => <RelatorioPerdasModal embedded inicio={p.inicio} fim={p.fim} /> },

  { id: 'compras-fornecedor', grupo: 'Compras', titulo: 'Compras por fornecedor', modulo: 'RECEBIMENTO',
    render: () => <ComprasFornecedorRelatorio /> },

  { id: 'contas-pagar-posicao', grupo: 'Financeiro', titulo: 'Contas a pagar — posição em aberto', modulo: 'RELATORIOS',
    render: () => <ContasPagarPosicaoRelatorio /> },
  { id: 'pagamentos-pagar', grupo: 'Financeiro', titulo: 'Pagamentos realizados (contas a pagar)', modulo: 'CONTAS_PAGAR', somenteAdmin: true,
    render: () => <HistoricoPagamentosModal embedded tipo="pagar" /> },
  { id: 'pagamentos-receber', grupo: 'Financeiro', titulo: 'Recebimentos realizados (contas a receber)', modulo: 'CONTAS_RECEBER', somenteAdmin: true,
    render: () => <HistoricoPagamentosModal embedded tipo="receber" /> },
]

const ORDEM_GRUPOS: Grupo[] = ['Vendas e desempenho', 'Estoque', 'Compras', 'Financeiro']

export default function RelatoriosPage() {
  const { podeVer, podeVerIdentificacao, isSuperAdmin } = usePermissao()
  const [params, setParams] = useSearchParams()
  const periodo = useMemo(() => periodoPadrao(), [])

  const disponiveis = OPCOES.filter(o =>
    (isSuperAdmin || podeVer(o.modulo)) && (!o.somenteAdmin || podeVerIdentificacao)
  )
  const atual = disponiveis.find(o => o.id === params.get('relatorio')) ?? disponiveis[0]

  return (
    <div className="p-6 space-y-6">
      <PageHeader
        title="Relatórios"
        subtitle="Todos os relatórios e indicadores do sistema em um só lugar"
      />

      <Card padding="sm" className="print:hidden">
        <Field label="Relatório">
          <select
            value={atual?.id ?? ''}
            onChange={e => setParams({ relatorio: e.target.value }, { replace: true })}
            className={`${baseInputClass} max-w-lg`}
          >
            {ORDEM_GRUPOS.map(grupo => {
              const itens = disponiveis.filter(o => o.grupo === grupo)
              if (itens.length === 0) return null
              return (
                <optgroup key={grupo} label={grupo}>
                  {itens.map(o => <option key={o.id} value={o.id}>{o.titulo}</option>)}
                </optgroup>
              )
            })}
          </select>
        </Field>
      </Card>

      {atual
        // key: ao trocar de relatório o estado (filtros, busca) recomeça do zero
        ? <div key={atual.id}>{atual.render(periodo)}</div>
        : <Card><p className="text-sm text-gray-500">Você não tem acesso a nenhum relatório.</p></Card>}
    </div>
  )
}