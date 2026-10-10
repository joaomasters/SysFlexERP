import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Filter, Lock, Eye, Save } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { formatBRL, formatPercent } from '@/shared/utils/mask'
import toast from 'react-hot-toast'
import type { Usuario } from '@/types/acesso'
import type { ComissaoFuncionario, VendaComissao } from '@/types/comissao'
import {
  PageHeader, Card, Modal, Button, StatusBadge, Field, FilterTabs,
  Table, THead, TH, TBody, TR, TD, EmptyState, LoadingState,
} from '@/shared/components/ui'

type Aba = 'apuracao' | 'percentuais'

const hoje = new Date().toISOString().slice(0, 10)
const inicioDoMes = hoje.slice(0, 8) + '01'

const dateClass = 'border border-gray-300 rounded-lg px-3 py-1.5 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary-500'

export default function ComissoesPage() {
  const { podeVer } = usePermissao()
  const [aba, setAba] = useState<Aba>('apuracao')

  if (!podeVer('COMISSOES')) {
    return (
      <div className="p-6">
        <div className="bg-warning-50 border border-warning-100 rounded-xl p-5 max-w-lg">
          <p className="flex items-center gap-2 font-medium text-warning-700">
            <Lock size={16} /> Acesso restrito
          </p>
          <p className="text-sm text-warning-600 mt-1">
            Seu perfil não tem permissão para visualizar as comissões dos funcionários.
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="p-6">
      <PageHeader title="Comissões" subtitle="Percentual de comissão por funcionário e apuração sobre as vendas do PDV" />

      <div className="flex flex-wrap gap-2 mb-4">
        <FilterTabs<Aba>
          value={aba}
          onChange={setAba}
          options={[
            { value: 'apuracao', label: 'Apuração' },
            { value: 'percentuais', label: 'Percentuais por funcionário' },
          ]}
        />
      </div>

      {aba === 'apuracao' ? <Apuracao /> : <Percentuais />}
    </div>
  )
}

// ─── Apuração ───────────────────────────────────────────────────────────────

function Apuracao() {
  const [inicio, setInicio] = useState(inicioDoMes)
  const [fim, setFim]       = useState(hoje)
  const [detalhando, setDetalhando] = useState<ComissaoFuncionario | null>(null)

  const periodoValido = !!inicio && !!fim && inicio <= fim

  const { data: linhas = [], isLoading } = useQuery<ComissaoFuncionario[]>({
    queryKey: ['comissoes-apuracao', inicio, fim],
    queryFn: () => api.get('/comissoes/apuracao', { params: { inicio, fim } }).then(r => r.data),
    enabled: periodoValido,
  })

  const totalVendido  = linhas.reduce((s, l) => s + l.totalVendido, 0)
  const totalComissao = linhas.reduce((s, l) => s + l.totalComissao, 0)

  return (
    <>
      <Card padding="sm" className="mb-4 flex flex-wrap items-end gap-3">
        <Filter size={16} className="text-gray-400 mb-2" />
        <Field label="De">
          <input type="date" value={inicio} onChange={e => setInicio(e.target.value)} className={dateClass} />
        </Field>
        <Field label="Até">
          <input type="date" value={fim} onChange={e => setFim(e.target.value)} className={dateClass} />
        </Field>
        {!periodoValido && (
          <p className="text-xs text-danger-600 mb-2">A data inicial não pode ser depois da data final.</p>
        )}
      </Card>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
        <Card padding="sm">
          <p className="text-xs text-gray-500">Total vendido pelos funcionários</p>
          <p className="text-xl font-bold text-gray-900 tabular-nums mt-1">{formatBRL(totalVendido)}</p>
        </Card>
        <Card padding="sm">
          <p className="text-xs text-gray-500">Total de comissões no período</p>
          <p className="text-xl font-bold text-success-700 tabular-nums mt-1">{formatBRL(totalComissao)}</p>
        </Card>
        <Card padding="sm">
          <p className="text-xs text-gray-500">Funcionários listados</p>
          <p className="text-xl font-bold text-gray-900 tabular-nums mt-1">{linhas.length}</p>
        </Card>
      </div>

      <Card padding="none" className="overflow-hidden">
        {isLoading ? (
          <LoadingState />
        ) : (
          <Table>
            <THead>
              <tr>
                <TH>Funcionário</TH>
                <TH align="right">Vendas</TH>
                <TH align="right">Total vendido</TH>
                <TH align="right">% atual</TH>
                <TH align="right">Comissão</TH>
                <TH />
              </tr>
            </THead>
            <TBody>
              {linhas.map(l => (
                <TR key={l.usuarioId}>
                  <TD>
                    <span className="font-medium text-gray-900">{l.nome}</span>
                    <span className="text-xs text-gray-400 font-mono ml-1.5">{l.login}</span>
                    {!l.ativo && <StatusBadge tone="neutral" className="ml-2">Inativo</StatusBadge>}
                  </TD>
                  <TD align="right" className="tabular-nums">{l.quantidadeVendas}</TD>
                  <TD align="right" className="tabular-nums">{formatBRL(l.totalVendido)}</TD>
                  <TD align="right" className="tabular-nums text-gray-500">{formatPercent(l.percentualAtual, 2)}</TD>
                  <TD align="right" className="tabular-nums font-semibold text-success-700">{formatBRL(l.totalComissao)}</TD>
                  <TD align="right">
                    <Button
                      variant="ghost" size="sm"
                      className="hover:!text-info-600"
                      disabled={l.quantidadeVendas === 0}
                      onClick={() => setDetalhando(l)}
                      title="Ver vendas"
                    >
                      <Eye size={15} />
                    </Button>
                  </TD>
                </TR>
              ))}
              {linhas.length === 0 && (
                <tr><td colSpan={6}>
                  <EmptyState>Nenhuma venda de funcionário comissionado no período.</EmptyState>
                </td></tr>
              )}
            </TBody>
          </Table>
        )}
      </Card>

      <p className="text-xs text-gray-400 mt-3">
        A comissão é calculada sobre o total de cada venda fechada no PDV, com o percentual vigente
        no momento do fechamento. Alterar o percentual não muda vendas já fechadas.
      </p>

      {detalhando && (
        <VendasDoFuncionarioModal
          funcionario={detalhando}
          inicio={inicio}
          fim={fim}
          onClose={() => setDetalhando(null)}
        />
      )}
    </>
  )
}

function VendasDoFuncionarioModal({ funcionario, inicio, fim, onClose }: {
  funcionario: ComissaoFuncionario
  inicio: string
  fim: string
  onClose: () => void
}) {
  const { data: vendas = [], isLoading } = useQuery<VendaComissao[]>({
    queryKey: ['comissoes-vendas', funcionario.usuarioId, inicio, fim],
    queryFn: () => api.get(`/comissoes/apuracao/${funcionario.usuarioId}/vendas`, { params: { inicio, fim } })
      .then(r => r.data),
  })

  return (
    <Modal title={`Vendas de ${funcionario.nome}`} maxWidth="xl" onClose={onClose}>
      {isLoading ? (
        <LoadingState />
      ) : (
        <div className="max-h-[60vh] overflow-y-auto -mx-6">
          <Table>
            <THead>
              <tr>
                <TH>Data</TH>
                <TH>Venda</TH>
                <TH align="right">Total</TH>
                <TH align="right">%</TH>
                <TH align="right">Comissão</TH>
              </tr>
            </THead>
            <TBody>
              {vendas.map(v => (
                <TR key={v.vendaId}>
                  <TD className="text-gray-500 text-xs whitespace-nowrap">
                    {new Date(v.dataVenda).toLocaleString('pt-BR', {
                      day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit',
                    })}
                  </TD>
                  <TD className="font-mono text-xs">{v.numeroCupom ?? `#${v.vendaId}`}</TD>
                  <TD align="right" className="tabular-nums">{formatBRL(v.total)}</TD>
                  <TD align="right" className="tabular-nums text-gray-500">
                    {v.percentualComissao === null ? '—' : formatPercent(v.percentualComissao, 2)}
                  </TD>
                  <TD align="right" className="tabular-nums font-medium">{formatBRL(v.valorComissao)}</TD>
                </TR>
              ))}
              {vendas.length === 0 && (
                <tr><td colSpan={5}><EmptyState>Nenhuma venda no período.</EmptyState></td></tr>
              )}
            </TBody>
          </Table>
        </div>
      )}
      <div className="flex justify-between text-sm border-t pt-3">
        <span className="text-gray-500">{funcionario.quantidadeVendas} venda(s) · {formatBRL(funcionario.totalVendido)}</span>
        <span className="font-semibold text-success-700">Comissão: {formatBRL(funcionario.totalComissao)}</span>
      </div>
    </Modal>
  )
}

// ─── Percentuais por funcionário ────────────────────────────────────────────

function Percentuais() {
  const qc = useQueryClient()
  const { podeEditar } = usePermissao()
  const editavel = podeEditar('COMISSOES')

  // Valor digitado por usuário ainda não salvo (id -> texto do input)
  const [rascunhos, setRascunhos] = useState<Record<number, string>>({})

  const { data: funcionarios = [], isLoading } = useQuery<Usuario[]>({
    queryKey: ['comissoes-funcionarios'],
    queryFn: () => api.get('/comissoes/funcionarios').then(r => r.data),
  })

  const salvar = useMutation({
    mutationFn: ({ id, percentual }: { id: number; percentual: number }) =>
      api.put(`/comissoes/funcionarios/${id}`, { percentual }),
    onSuccess: (_, { id }) => {
      toast.success('Percentual de comissão atualizado.')
      setRascunhos(r => { const resto = { ...r }; delete resto[id]; return resto })
      qc.invalidateQueries({ queryKey: ['comissoes-funcionarios'] })
      qc.invalidateQueries({ queryKey: ['comissoes-apuracao'] })
    },
  })

  const valorValido = (texto: string) => {
    if (texto.trim() === '') return false
    const n = Number(texto.replace(',', '.'))
    return Number.isFinite(n) && n >= 0 && n <= 100
  }

  return (
    <Card padding="none" className="overflow-hidden">
      {isLoading ? (
        <LoadingState />
      ) : (
        <Table>
          <THead>
            <tr>
              <TH>Funcionário</TH>
              <TH>Login</TH>
              <TH>Perfil</TH>
              <TH>Status</TH>
              <TH align="right">Comissão (%)</TH>
            </tr>
          </THead>
          <TBody>
            {funcionarios.map(u => {
              const rascunho = rascunhos[u.id]
              const alterado = rascunho !== undefined && Number(rascunho.replace(',', '.')) !== u.percentualComissao
              const valido = rascunho === undefined || valorValido(rascunho)
              return (
                <TR key={u.id}>
                  <TD className="font-medium text-gray-900">{u.nome}</TD>
                  <TD className="text-gray-500 font-mono text-xs">{u.login}</TD>
                  <TD className="text-gray-600">{u.perfil.nome}</TD>
                  <TD>
                    <StatusBadge tone={u.ativo ? 'success' : 'neutral'}>{u.ativo ? 'Ativo' : 'Inativo'}</StatusBadge>
                  </TD>
                  <TD align="right">
                    {editavel ? (
                      <div className="flex items-center justify-end gap-2">
                        <div className="relative">
                          <input
                            type="text"
                            inputMode="decimal"
                            value={rascunho ?? String(u.percentualComissao).replace('.', ',')}
                            onChange={e => setRascunhos(r => ({ ...r, [u.id]: e.target.value.replace(/[^\d,.]/g, '') }))}
                            onKeyDown={e => {
                              if (e.key === 'Enter' && alterado && valido) {
                                salvar.mutate({ id: u.id, percentual: Number(rascunho!.replace(',', '.')) })
                              }
                            }}
                            className={`w-24 border rounded px-2 py-1 pr-6 text-right text-sm tabular-nums
                              ${valido ? 'border-gray-300' : 'border-danger-500 bg-danger-50'}`}
                          />
                          <span className="absolute right-2 top-1/2 -translate-y-1/2 text-xs text-gray-400">%</span>
                        </div>
                        <Button
                          variant="primary" size="sm"
                          disabled={!alterado || !valido}
                          loading={salvar.isPending && salvar.variables?.id === u.id}
                          onClick={() => salvar.mutate({ id: u.id, percentual: Number(rascunho!.replace(',', '.')) })}
                          title="Salvar percentual"
                        >
                          <Save size={14} />
                        </Button>
                      </div>
                    ) : (
                      <span className="tabular-nums">{formatPercent(u.percentualComissao, 2)}</span>
                    )}
                  </TD>
                </TR>
              )
            })}
            {funcionarios.length === 0 && (
              <tr><td colSpan={5}><EmptyState>Nenhum usuário cadastrado.</EmptyState></td></tr>
            )}
          </TBody>
        </Table>
      )}
    </Card>
  )
}
