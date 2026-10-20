import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../../shared/api/axios'
import toast from 'react-hot-toast'
import { Plus, History, FileText } from 'lucide-react'
import { formatBRL } from '@/shared/utils/mask'
import {
  PageHeader, Card, FilterTabs, Modal, Button, StatusBadge, CurrencyInput, Field, baseInputClass,
  Table, THead, TH, TBody, TR, TD, EmptyState,
} from '@/shared/components/ui'
import type { BadgeTone } from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'
import HistoricoPagamentosModal from './components/HistoricoPagamentosModal'

interface ContaPagar {
  id: number; descricao: string; fornecedor: string; valor: number
  valorPago: number; dataVencimento: string; dataPagamento: string | null
  categoria: string; status: string; observacao: string
}

const fmt = formatBRL

// ABERTO/PARCIAL = pendente, aguardando pagamento (atenção); PAGO = concluído;
// CANCELADO = neutro.
const statusTom: Record<string, BadgeTone> = {
  ABERTO: 'warning',
  PAGO: 'success',
  PARCIAL: 'warning',
  CANCELADO: 'neutral',
}

// Categorias de conta a pagar. Os valores gravados seguem o que já existe no
// banco (sem acento), para os relatórios agruparem tudo junto; o rótulo é só
// o que aparece na tela. Para incluir uma categoria nova, acrescente aqui.
const CATEGORIAS_CONTA = [
  { valor: 'Materia-prima',  rotulo: 'Matéria-prima' },
  { valor: 'Insumos',        rotulo: 'Insumos' },
  { valor: 'Utilidades',     rotulo: 'Utilidades (água, luz, gás)' },
  { valor: 'Infraestrutura', rotulo: 'Infraestrutura (aluguel)' },
  { valor: 'Manutencao',     rotulo: 'Manutenção' },
  { valor: 'Pessoal',        rotulo: 'Pessoal' },
  { valor: 'Seguros',        rotulo: 'Seguros' },
  { valor: 'Impostos',       rotulo: 'Impostos e taxas' },
  { valor: 'Outros',         rotulo: 'Outros' },
]

const NOVO_FORNECEDOR = '__novo__'

const isoDia = (deslocamentoDias: number) =>
  new Date(Date.now() + deslocamentoDias * 86_400_000).toISOString().slice(0, 10)

export default function ContasPagarPage() {
  const qc = useQueryClient()
  const { podeVerIdentificacao } = usePermissao()
  const [statusFiltro, setStatusFiltro] = useState('ABERTO')
  const [showForm, setShowForm] = useState(false)
  const [pagandoId, setPagandoId] = useState<number | null>(null)
  const [valorPag, setValorPag] = useState(0)
  // Histórico de pagamentos (somente administradores): de uma conta, ou o relatório geral
  const [historicoContaId, setHistoricoContaId] = useState<number | null>(null)
  const [mostrarRelatorio, setMostrarRelatorio] = useState(false)
  // Só mostra os erros depois da primeira tentativa de criar a conta.
  const [tentouSalvar, setTentouSalvar] = useState(false)
  // true quando o fornecedor não está na lista e será digitado.
  const [novoFornecedor, setNovoFornecedor] = useState(false)
  const [form, setForm] = useState({
    descricao: '', fornecedor: '', valor: 0, dataVencimento: '', categoria: '', observacao: ''
  })

  const contas = useQuery<ContaPagar[]>({
    queryKey: ['contas-pagar', statusFiltro],
    queryFn: () => api.get(`/financeiro/contas-pagar?status=${statusFiltro}`).then(r => r.data),
  })

  // Fornecedores já usados em contas anteriores (mesma permissão da tela, não
  // depende de outro módulo). Só busca quando o modal de nova conta é aberto.
  const fornecedoresQuery = useQuery<ContaPagar[]>({
    queryKey: ['contas-pagar', 'fornecedores'],
    queryFn: () => api
      .get('/financeiro/contas-pagar/periodo', { params: { inicio: isoDia(-365 * 3), fim: isoDia(365 * 2) } })
      .then(r => r.data),
    enabled: showForm,
  })
  const fornecedores = useMemo(() => {
    const nomes = new Set<string>()
    ;(fornecedoresQuery.data ?? []).forEach(c => { if (c.fornecedor?.trim()) nomes.add(c.fornecedor.trim()) })
    return Array.from(nomes).sort((a, b) => a.localeCompare(b, 'pt-BR'))
  }, [fornecedoresQuery.data])

  const criar = useMutation({
    mutationFn: () => api.post('/financeiro/contas-pagar', { ...form }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['contas-pagar'] })
      fecharForm()
    },
  })

  const pagar = useMutation({
    mutationFn: () => api.post(`/financeiro/contas-pagar/${pagandoId}/pagar`, { valor: valorPag }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['contas-pagar'] })
      qc.invalidateQueries({ queryKey: ['historico-pagamentos'] })
      setPagandoId(null)
      setValorPag(0)
    },
  })

  const cancelar = useMutation({
    mutationFn: (id: number) => api.post(`/financeiro/contas-pagar/${id}/cancelar`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['contas-pagar'] }),
  })

  const contaDoPagamento = pagandoId !== null ? contas.data?.find(c => c.id === pagandoId) : undefined

  const totalAberto = contas.data?.reduce((s, c) => s + (c.valor - c.valorPago), 0) ?? 0

  function fecharForm() {
    setShowForm(false)
    setTentouSalvar(false)
    setNovoFornecedor(false)
    setForm({ descricao: '', fornecedor: '', valor: 0, dataVencimento: '', categoria: '', observacao: '' })
  }

  // Todos os campos da conta são obrigatórios.
  const erros = {
    descricao:      form.descricao.trim() ? '' : 'Informe a descrição.',
    fornecedor:     form.fornecedor.trim() ? '' : (novoFornecedor ? 'Informe o nome do fornecedor.' : 'Selecione o fornecedor.'),
    valor:          form.valor > 0 ? '' : 'Informe o valor.',
    dataVencimento: form.dataVencimento ? '' : 'Informe o vencimento.',
    categoria:      form.categoria ? '' : 'Selecione a categoria.',
  }
  const mostrar = (msg: string) => (tentouSalvar && msg ? msg : undefined)
  const campoErro = (msg: string) => (tentouSalvar && msg ? '!border-danger-500' : '')

  function tentarCriar() {
    setTentouSalvar(true)
    if (Object.values(erros).some(Boolean)) {
      toast.error('Preencha todos os campos obrigatórios.')
      return
    }
    criar.mutate()
  }

  return (
    <div className="p-6 space-y-5">
      <PageHeader
        title="Contas a Pagar"
        subtitle="Gestão de pagamentos e fornecedores"
        actions={
          <div className="flex gap-2">
            {podeVerIdentificacao && (
              <Button variant="secondary" onClick={() => setMostrarRelatorio(true)}>
                <FileText size={16} /> Histórico de pagamentos
              </Button>
            )}
            <Button variant="primary" onClick={() => setShowForm(true)}>
              Nova Conta
            </Button>
          </div>
        }
      />

      {/* Filtros + total */}
      <Card padding="sm" className="flex items-center gap-3 flex-wrap">
        <FilterTabs
          options={[
            { value: 'ABERTO', label: 'ABERTO' },
            { value: 'PARCIAL', label: 'PARCIAL' },
            { value: 'PAGO', label: 'PAGO' },
            { value: 'CANCELADO', label: 'CANCELADO' },
          ]}
          value={statusFiltro}
          onChange={setStatusFiltro}
        />
        <div className="ml-auto text-right">
          <p className="text-xs text-gray-400">Saldo devedor</p>
          <p className="text-lg font-bold text-danger-600 tabular-nums">{fmt(totalAberto)}</p>
        </div>
      </Card>

      {/* Tabela */}
      <Card padding="none" className="overflow-hidden">
        <Table>
          <THead>
            <tr>
              <TH>Descrição</TH>
              <TH>Fornecedor</TH>
              <TH>Vencimento</TH>
              <TH align="right">Valor</TH>
              <TH align="right">Pago</TH>
              <TH>Status</TH>
              <TH />
            </tr>
          </THead>
          <TBody>
            {contas.data?.length === 0 && (
              <tr><td colSpan={7}><EmptyState>Nenhuma conta encontrada</EmptyState></td></tr>
            )}
            {contas.data?.map(c => (
              <TR key={c.id}>
                <TD className="font-medium">{c.descricao}</TD>
                <TD className="text-gray-500">{c.fornecedor || '—'}</TD>
                <TD>
                  <span className={new Date(c.dataVencimento) < new Date() && c.status === 'ABERTO'
                    ? 'text-danger-600 font-medium' : 'text-gray-600'}>
                    {new Date(c.dataVencimento).toLocaleDateString('pt-BR')}
                  </span>
                </TD>
                <TD align="right" className="font-medium tabular-nums">{fmt(c.valor)}</TD>
                <TD align="right" className="text-success-600 tabular-nums">{fmt(c.valorPago)}</TD>
                <TD>
                  <StatusBadge tone={statusTom[c.status] ?? 'neutral'}>{c.status}</StatusBadge>
                </TD>
                <TD>
                  <div className="flex items-center gap-2">
                    {podeVerIdentificacao && c.valorPago > 0 && (
                      <Button variant="ghost" size="sm" onClick={() => setHistoricoContaId(c.id)}
                        title="Histórico de pagamentos">
                        <History size={16} />
                      </Button>
                    )}
                    {(c.status === 'ABERTO' || c.status === 'PARCIAL') && (
                      <div className="flex gap-2">
                        <Button variant="success" size="sm" onClick={() => { setPagandoId(c.id); setValorPag(0) }}>
                          Pagar
                        </Button>
                        <Button variant="outline-danger" size="sm" onClick={() => cancelar.mutate(c.id)}>
                          Cancelar
                        </Button>
                      </div>
                    )}
                  </div>
                </TD>
              </TR>
            ))}
          </TBody>
        </Table>
      </Card>

      {/* Histórico de pagamentos (administradores) */}
      {podeVerIdentificacao && historicoContaId !== null && (
        <HistoricoPagamentosModal tipo="pagar" contaId={historicoContaId} onClose={() => setHistoricoContaId(null)} />
      )}
      {podeVerIdentificacao && mostrarRelatorio && (
        <HistoricoPagamentosModal tipo="pagar" onClose={() => setMostrarRelatorio(false)} />
      )}

      {/* Modal nova conta */}
      {showForm && (
        <Modal
          title="Nova Conta a Pagar"
          onClose={fecharForm}
          footer={
            <>
              <Button variant="secondary" fullWidth onClick={fecharForm}>Cancelar</Button>
              <Button variant="primary" fullWidth loading={criar.isPending} onClick={tentarCriar}>
                Criar
              </Button>
            </>
          }
        >
          <Field label="Descrição *" error={mostrar(erros.descricao)}>
            <input
              value={form.descricao}
              onChange={e => setForm(p => ({ ...p, descricao: e.target.value }))}
              placeholder="Ex: Compra de bovino"
              className={`${baseInputClass} ${campoErro(erros.descricao)}`}
            />
          </Field>
          <Field label="Fornecedor *" error={mostrar(erros.fornecedor)}>
            <select
              value={novoFornecedor ? NOVO_FORNECEDOR : form.fornecedor}
              onChange={e => {
                if (e.target.value === NOVO_FORNECEDOR) {
                  setNovoFornecedor(true)
                  setForm(p => ({ ...p, fornecedor: '' }))
                } else {
                  setNovoFornecedor(false)
                  setForm(p => ({ ...p, fornecedor: e.target.value }))
                }
              }}
              className={`${baseInputClass} ${novoFornecedor ? '' : campoErro(erros.fornecedor)}`}
            >
              <option value="">Selecione o fornecedor...</option>
              {fornecedores.map(f => <option key={f} value={f}>{f}</option>)}
              <option value={NOVO_FORNECEDOR}>+ Outro (digitar novo fornecedor)</option>
            </select>
            {novoFornecedor && (
              <input
                autoFocus
                value={form.fornecedor}
                onChange={e => setForm(p => ({ ...p, fornecedor: e.target.value }))}
                placeholder="Nome do novo fornecedor"
                className={`${baseInputClass} mt-2 ${campoErro(erros.fornecedor)}`}
              />
            )}
          </Field>
          <Field label="Valor *" error={mostrar(erros.valor)}>
            <CurrencyInput
              value={form.valor}
              onChange={v => setForm(p => ({ ...p, valor: v }))}
              className={tentouSalvar && erros.valor ? '[&_input]:!border-danger-500' : ''}
            />
          </Field>
          <Field label="Vencimento *" error={mostrar(erros.dataVencimento)}>
            <input
              type="date"
              value={form.dataVencimento}
              onChange={e => setForm(p => ({ ...p, dataVencimento: e.target.value }))}
              className={`${baseInputClass} ${campoErro(erros.dataVencimento)}`}
            />
          </Field>
          <Field label="Categoria *" error={mostrar(erros.categoria)}>
            <select
              value={form.categoria}
              onChange={e => setForm(p => ({ ...p, categoria: e.target.value }))}
              className={`${baseInputClass} ${campoErro(erros.categoria)}`}
            >
              <option value="">Selecione a categoria...</option>
              {CATEGORIAS_CONTA.map(c => <option key={c.valor} value={c.valor}>{c.rotulo}</option>)}
            </select>
          </Field>
        </Modal>
      )}

      {/* Modal pagar */}
      {pagandoId !== null && (
        <Modal
          title="Registrar Pagamento"
          onClose={() => setPagandoId(null)}
          maxWidth="sm"
          footer={
            <>
              <Button variant="secondary" fullWidth onClick={() => setPagandoId(null)}>Cancelar</Button>
              <Button variant="success" fullWidth disabled={!valorPag} loading={pagar.isPending} onClick={() => pagar.mutate()}>
                Confirmar
              </Button>
            </>
          }
        >
          {contaDoPagamento && (
            <p className="text-xs text-gray-500">
              Saldo da conta: <span className="font-semibold text-danger-600 tabular-nums">
                {fmt(contaDoPagamento.valor - contaDoPagamento.valorPago)}
              </span>
            </p>
          )}
          <Field label="Valor Pago">
            <CurrencyInput value={valorPag} onChange={setValorPag} autoFocus />
          </Field>
        </Modal>
      )}
    </div>
  )
}