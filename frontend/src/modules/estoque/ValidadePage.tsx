import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '@/shared/api/axios'
import { Printer } from 'lucide-react'
import { formatWeightDisplay } from '@/shared/utils/mask'
import {
  PageHeader, Card, Button, StatusBadge, FilterTabs,
  Table, THead, TH, TBody, TR, TD, EmptyState, LoadingState,
} from '@/shared/components/ui'
import RelatorioValidadeModal from './components/RelatorioValidadeModal'
import { situacaoTom, situacaoRotulo, textoPrazo, formatarDataValidade } from './validadeUtils'
import type { LoteValidade, SituacaoValidade } from '@/types/validade'

type Filtro = 'ATENCAO' | 'TODOS' | SituacaoValidade

const kg3 = (v: number) => formatWeightDisplay(v, 3)

/**
 * Controle de Validade: lotes em estoque ordenados do que vence primeiro para o que vence depois.
 * Os mesmos lotes "vencidos / vencendo em breve" alimentam o sino de notificações.
 *
 * Só aparecem lotes com validade conhecida (informada no recebimento ou gerada pela validade
 * padrão do produto). Estoque sem validade conhecida não está aqui — ver Produtos.
 */
export default function ValidadePage() {
  const [filtro, setFiltro] = useState<Filtro>('ATENCAO')
  const [showRelatorio, setShowRelatorio] = useState(false)

  const { data: lotes = [], isLoading } = useQuery<LoteValidade[]>({
    queryKey: ['validade-lotes'],
    queryFn: () => api.get('/estoque/validade').then(r => r.data),
    refetchOnWindowFocus: true,
  })

  const qtdVencidos = lotes.filter(l => l.situacao === 'VENCIDO').length
  const qtdEmBreve  = lotes.filter(l => l.situacao === 'VENCE_EM_BREVE').length

  const filtrados = lotes.filter(l => {
    if (filtro === 'TODOS') return true
    if (filtro === 'ATENCAO') return l.situacao !== 'OK'
    return l.situacao === filtro
  })

  return (
    <div className="p-6">
      <PageHeader
        title="Controle de Validade"
        subtitle="Lotes em estoque, do que vence primeiro para o que vence depois"
        actions={
          <>
            <Button variant="secondary" onClick={() => setShowRelatorio(true)}>
              <Printer size={16} /> Relatório de Validade
            </Button>
            {qtdVencidos > 0 && (
              <Link to="/estoque/perdas">
                <Button variant="warning">Lançar perda por vencimento</Button>
              </Link>
            )}
          </>
        }
      />

      {/* Filtros + resumo — mesmo esquema de Contas a Receber/Pagar: abas à esquerda,
          totais à direita */}
      <Card padding="sm" className="flex items-center gap-3 mb-4 flex-wrap">
        <FilterTabs<Filtro>
          value={filtro}
          onChange={setFiltro}
          options={[
            { value: 'ATENCAO',        label: 'REQUER ATENÇÃO' },
            { value: 'VENCIDO',        label: 'VENCIDOS' },
            { value: 'VENCE_EM_BREVE', label: 'VENCENDO EM BREVE' },
            { value: 'TODOS',          label: 'TODOS' },
          ]}
        />
        <div className="ml-auto flex items-center gap-6 text-right">
          <div>
            <p className="text-xs text-gray-400">Vencidos</p>
            <p className="text-lg font-bold text-danger-600 tabular-nums">{qtdVencidos}</p>
          </div>
          <div>
            <p className="text-xs text-gray-400">Vencendo em breve</p>
            <p className="text-lg font-bold text-warning-600 tabular-nums">{qtdEmBreve}</p>
          </div>
          <div>
            <p className="text-xs text-gray-400">Lotes rastreados</p>
            <p className="text-lg font-bold text-gray-900 tabular-nums">{lotes.length}</p>
          </div>
        </div>
      </Card>

      <Card padding="none" className="overflow-hidden">
        {isLoading ? (
          <LoadingState />
        ) : (
          <Table>
            <THead>
              <tr>
                <TH>Produto</TH>
                <TH align="right">Saldo do lote</TH>
                <TH>Validade</TH>
                <TH>Prazo</TH>
                <TH align="center">Situação</TH>
                <TH>Origem</TH>
              </tr>
            </THead>
            <TBody>
              {filtrados.map(l => (
                <TR key={l.loteId}>
                  <TD className="font-medium">{l.produtoNome}</TD>
                  <TD align="right" className="tabular-nums">{kg3(l.quantidadeAtual)} {l.unidadeMedida}</TD>
                  <TD className="text-xs text-gray-500">{formatarDataValidade(l.dataValidade)}</TD>
                  <TD className={l.situacao === 'VENCIDO' ? 'text-danger-600 font-medium' : 'text-gray-600'}>
                    {textoPrazo(l.diasRestantes)}
                  </TD>
                  <TD align="center">
                    <StatusBadge tone={situacaoTom[l.situacao]}>{situacaoRotulo[l.situacao]}</StatusBadge>
                  </TD>
                  <TD className="text-xs text-gray-500">{l.documentoRef ?? '—'}</TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
        {!isLoading && filtrados.length === 0 && (
          <EmptyState>
            {lotes.length === 0
              ? 'Nenhum lote com validade registrado. Informe a validade ao receber mercadoria, ou defina a validade padrão no cadastro do produto.'
              : 'Nenhum lote nesta situação.'}
          </EmptyState>
        )}
      </Card>

      {showRelatorio && <RelatorioValidadeModal onClose={() => setShowRelatorio(false)} />}
    </div>
  )
}