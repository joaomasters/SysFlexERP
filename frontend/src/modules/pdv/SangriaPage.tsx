import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../../shared/api/axios'
import { ArrowDownCircle, ArrowUpCircle, Plus, Wallet, AlertCircle, History, Lock, LockOpen, User } from 'lucide-react'
import { getUsuarioId, getNomeUsuario } from '../../shared/auth'
import { formatBRL } from '@/shared/utils/mask'
import {
  PageHeader, Card, Button, StatusBadge, CurrencyInput, Field, baseInputClass,
  Table, THead, TH, TBody, TR, TD,
} from '@/shared/components/ui'

interface CaixaAberto {
  id: number
  operadorId: number
  dataAbertura: string
  valorAbertura: number
}

interface CaixaHistorico {
  id: number
  operadorId: number
  dataAbertura: string
  dataFechamento: string | null
  valorAbertura: number
  valorFechamentoInformado: number | null
  status: 'ABERTO' | 'FECHADO'
}

interface SangriaItem {
  id: number
  tipo: 'SANGRIA' | 'SUPRIMENTO'
  valor: number
  motivo: string
  createdAt: string
}

interface FechamentoDTO {
  caixaId: number
  valorAbertura: number
  totalSangria: number
  totalSuprimento: number
  totalVendas: number
  totalDinheiro: number
  totalCredito: number
  totalDebito: number
  totalPix: number
  totalFiado: number
  saldoEsperado: number
  quantidadeVendas: number
  movimentos: SangriaItem[]
}

const fmt = formatBRL

export default function SangriaPage() {
  const qc = useQueryClient()
  const [tipo, setTipo] = useState<'SANGRIA' | 'SUPRIMENTO'>('SANGRIA')
  const [valor, setValor] = useState(0)
  const [motivo, setMotivo] = useState('')
  const [showFechamento, setShowFechamento] = useState(false)
  const [fechamento, setFechamento] = useState<FechamentoDTO | null>(null)
  const [showHistorico, setShowHistorico] = useState(false)
  const [valorAbertura, setValorAbertura] = useState(0)

  const operadorId = getUsuarioId()
  const nomeOperador = getNomeUsuario()

  // A tela agora descobre sozinha qual é o caixa aberto DO OPERADOR LOGADO
  // — cada operador tem seu próprio caixa; não existe mais "o caixa aberto
  // do sistema" genérico. Isso evita registrar sangria no caixa errado
  // quando há mais de um operador trabalhando ao mesmo tempo.
  const caixaQuery = useQuery<CaixaAberto>({
    queryKey: ['caixa-aberto', operadorId],
    queryFn: () => api.get('/pdv/caixa/aberto', { params: { operadorId }, silent: true } as any).then(r => r.data),
    enabled: !!operadorId,
    retry: false,
  })
  const caixaId = caixaQuery.data?.id

  // Nomes dos operadores, pra mostrar no histórico de caixas (que pode
  // incluir caixas de outros operadores, não só o do usuário logado).
  const { data: usuarios = [] } = useQuery<{ id: number; nome: string }[]>({
    queryKey: ['usuarios-nomes'],
    queryFn: () => api.get('/usuarios').then(r => r.data),
  })
  const nomeDoOperador = (id: number) => usuarios.find(u => u.id === id)?.nome ?? `Operador #${id}`

  // Histórico de todas as sessões de caixa já abertas — responde "quantos
  // caixas existem" sem precisar ir direto no banco.
  const historicoQuery = useQuery<CaixaHistorico[]>({
    queryKey: ['caixas-historico'],
    queryFn: () => api.get('/pdv/caixa').then(r => r.data),
  })

  const abrirCaixa = useMutation({
    mutationFn: () => api.post('/pdv/caixa/abrir', null, {
      params: { operadorId, valorAbertura },
    }),
    onSuccess: () => {
      setValorAbertura(0)
      qc.invalidateQueries({ queryKey: ['caixa-aberto'] })
      qc.invalidateQueries({ queryKey: ['caixas-historico'] })
    },
  })

  const movQuery = useQuery<SangriaItem[]>({
    queryKey: ['sangria', caixaId],
    queryFn: () => api.get(`/pdv/caixa/${caixaId}/movimentos`).then(r => r.data),
    enabled: !!caixaId,
  })

  const registrar = useMutation({
    mutationFn: () => api.post(`/pdv/caixa/${caixaId}/${tipo.toLowerCase()}`, {
      valor,
      motivo,
      operadorId,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sangria', caixaId] })
      setValor(0)
      setMotivo('')
    },
  })

  const verFechamento = async () => {
    const r = await api.get(`/pdv/caixa/${caixaId}/fechamento`)
    setFechamento(r.data)
    setShowFechamento(true)
  }

  return (
    <div className="p-6 space-y-6">
      <PageHeader title="Sangria / Suprimento" subtitle="Movimentações de numerário no caixa" />

      {/* Identificação do caixa — automática, sem precisar saber o ID */}
      {caixaQuery.isLoading && (
        <Card padding="sm" className="text-sm text-gray-400">Verificando caixa aberto...</Card>
      )}

      {caixaQuery.isError && (
        <div className="bg-warning-50 border border-warning-100 rounded-xl p-4 space-y-3">
          <div className="flex items-start gap-3">
            <AlertCircle size={18} className="text-warning-600 shrink-0 mt-0.5" />
            <div>
              <p className="text-sm font-medium text-warning-700">Nenhum caixa aberto no momento</p>
              <p className="text-xs text-warning-600 mt-0.5">
                Informe o valor inicial (fundo de troco) pra abrir um caixa novo.
              </p>
            </div>
          </div>
          <div className="flex gap-2 pl-8">
            <CurrencyInput value={valorAbertura} onChange={setValorAbertura} className="flex-1" />
            <Button
              variant="warning"
              onClick={() => abrirCaixa.mutate()}
              disabled={!valorAbertura}
              loading={abrirCaixa.isPending}
            >
              <LockOpen size={14} /> Abrir Caixa
            </Button>
          </div>
        </div>
      )}

      {caixaQuery.data && (
        <Card padding="sm" className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-full bg-success-50 flex items-center justify-center shrink-0">
            <Wallet size={18} className="text-success-600" />
          </div>
          <div className="flex-1">
            <p className="text-sm font-medium text-gray-800">
              Caixa #{caixaQuery.data.id} aberto às{' '}
              {new Date(caixaQuery.data.dataAbertura).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
            </p>
            <p className="text-xs text-gray-500 flex items-center gap-1">
              <User size={11} /> {nomeOperador ?? `Operador #${caixaQuery.data.operadorId}`}
              <span className="mx-1">•</span>
              Abertura: {fmt(caixaQuery.data.valorAbertura)}
            </p>
          </div>
          <Button variant="secondary" size="sm" onClick={verFechamento}>
            Ver Fechamento
          </Button>
        </Card>
      )}

      {/* Quantos caixas já existem — histórico de todas as sessões */}
      <Card padding="none" className="overflow-hidden">
        <button
          onClick={() => setShowHistorico(s => !s)}
          className="w-full flex items-center justify-between px-4 py-3 text-sm font-medium text-gray-600 hover:bg-gray-50"
        >
          <span className="flex items-center gap-2">
            <History size={15} />
            Histórico de caixas
            {historicoQuery.data && (
              <span className="text-xs font-normal text-gray-400">
                ({historicoQuery.data.length} no total)
              </span>
            )}
          </span>
          <span className="text-xs text-gray-400">{showHistorico ? 'Ocultar' : 'Ver todos'}</span>
        </button>

        {showHistorico && (
          <Table>
            <THead>
              <tr>
                <TH>Caixa</TH>
                <TH>Operador</TH>
                <TH>Abertura</TH>
                <TH>Fechamento</TH>
                <TH align="right">Valor Abertura</TH>
                <TH align="right">Valor Fechamento</TH>
                <TH align="center">Status</TH>
              </tr>
            </THead>
            <TBody>
              {historicoQuery.data?.map(c => (
                <TR key={c.id}>
                  <TD className="font-medium">#{c.id}</TD>
                  <TD className="text-gray-600">{nomeDoOperador(c.operadorId)}</TD>
                  <TD className="text-gray-500 text-xs">
                    {new Date(c.dataAbertura).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}
                  </TD>
                  <TD className="text-gray-500 text-xs">
                    {c.dataFechamento
                      ? new Date(c.dataFechamento).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })
                      : '—'}
                  </TD>
                  <TD align="right" className="tabular-nums">{fmt(c.valorAbertura)}</TD>
                  <TD align="right" className="tabular-nums">
                    {c.valorFechamentoInformado != null ? fmt(c.valorFechamentoInformado) : '—'}
                  </TD>
                  <TD align="center">
                    <StatusBadge tone={c.status === 'ABERTO' ? 'info' : 'neutral'}>
                      <span className="flex items-center gap-1">
                        {c.status === 'ABERTO' ? <LockOpen size={10} /> : <Lock size={10} />}
                        {c.status}
                      </span>
                    </StatusBadge>
                  </TD>
                </TR>
              ))}
              {historicoQuery.data?.length === 0 && (
                <tr><td colSpan={7} className="px-4 py-6 text-center text-gray-400">Nenhum caixa foi aberto ainda.</td></tr>
              )}
            </TBody>
          </Table>
        )}
      </Card>

      {/* Formulário */}
      {caixaId && (
        <Card className="space-y-4">
          <div className="flex gap-3">
            {(['SANGRIA', 'SUPRIMENTO'] as const).map(t => (
              <button
                key={t}
                onClick={() => setTipo(t)}
                className={`flex-1 py-2.5 rounded-lg text-sm font-medium flex items-center justify-center gap-2 border transition-colors
                  ${tipo === t
                    ? t === 'SANGRIA' ? 'bg-danger-600 text-white border-danger-600' : 'bg-success-600 text-white border-success-600'
                    : 'text-gray-600 border-gray-200 hover:bg-gray-50'}`}
              >
                {t === 'SANGRIA' ? <ArrowDownCircle size={16} /> : <ArrowUpCircle size={16} />}
                {t}
              </button>
            ))}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <Field label="Valor">
              <CurrencyInput value={valor} onChange={setValor} />
            </Field>
            <Field label="Motivo">
              <input
                type="text"
                value={motivo}
                onChange={e => setMotivo(e.target.value)}
                className={baseInputClass}
                placeholder="Opcional"
              />
            </Field>
          </div>

          <Button
            variant={tipo === 'SANGRIA' ? 'danger' : 'success'}
            fullWidth
            disabled={!valor}
            loading={registrar.isPending}
            onClick={() => registrar.mutate()}
          >
            <Plus size={16} />
            Registrar {tipo === 'SANGRIA' ? 'Sangria' : 'Suprimento'}
          </Button>
        </Card>
      )}

      {/* Lista de movimentos */}
      {movQuery.data && movQuery.data.length > 0 && (
        <Card padding="none" className="overflow-hidden">
          <div className="px-5 py-3 border-b bg-gray-50 text-sm font-medium text-gray-600">
            Movimentos do Caixa
          </div>
          <Table>
            <THead>
              <tr>
                <TH>Tipo</TH>
                <TH>Valor</TH>
                <TH>Motivo</TH>
                <TH>Horário</TH>
              </tr>
            </THead>
            <TBody>
              {movQuery.data.map(m => (
                <TR key={m.id}>
                  <TD>
                    <StatusBadge tone={m.tipo === 'SANGRIA' ? 'danger' : 'success'}>
                      <span className="flex items-center gap-1">
                        {m.tipo === 'SANGRIA' ? <ArrowDownCircle size={12} /> : <ArrowUpCircle size={12} />}
                        {m.tipo}
                      </span>
                    </StatusBadge>
                  </TD>
                  <TD className="font-medium">{fmt(m.valor)}</TD>
                  <TD className="text-gray-500">{m.motivo || '—'}</TD>
                  <TD className="text-gray-400">
                    {new Date(m.createdAt).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        </Card>
      )}

      {/* Modal Fechamento */}
      {showFechamento && fechamento && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl w-full max-w-md p-6 space-y-4">
            <h2 className="text-lg font-bold">Fechamento do Caixa #{fechamento.caixaId}</h2>
            <div className="space-y-2 text-sm">
              {[
                ['Abertura', fechamento.valorAbertura],
                ['Vendas', fechamento.totalVendas],
                ['Dinheiro', fechamento.totalDinheiro],
                ['Crédito', fechamento.totalCredito],
                ['Débito', fechamento.totalDebito],
                ['PIX', fechamento.totalPix],
                ['Fiado', fechamento.totalFiado],
                ['Suprimento (+)', fechamento.totalSuprimento],
                ['Sangria (-)', fechamento.totalSangria],
              ].map(([label, val]) => (
                <div key={label as string} className="flex justify-between border-b pb-1">
                  <span className="text-gray-500">{label}</span>
                  <span className="font-medium">{fmt(val as number)}</span>
                </div>
              ))}
              <div className="flex justify-between pt-2 text-base font-bold">
                <span>Saldo Esperado em Caixa</span>
                <span className="text-success-600">{fmt(fechamento.saldoEsperado)}</span>
              </div>
              <p className="text-xs text-gray-400">{fechamento.quantidadeVendas} vendas no período</p>
            </div>
            <Button variant="secondary" fullWidth onClick={() => setShowFechamento(false)}>
              Fechar
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
