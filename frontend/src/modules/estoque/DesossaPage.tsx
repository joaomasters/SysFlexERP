import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ChevronRight, Link2, History, AlertTriangle, Filter } from 'lucide-react'
import { api } from '@/shared/api/axios'
import toast from 'react-hot-toast'
import type { FichaDesossa, ExecutarDesossaDTO, ProcessoDesossa } from '@/types/produto'
import { formatWeightDisplay } from '@/shared/utils/mask'
import { PageHeader, Card, Button, CurrencyInput, WeightInput, Field, baseInputClass } from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'

interface Recebimento {
  id: number
  numeroNf: string | null
  fornecedor: string
  dataRecebimento: string
  itens: { produto: { id: number; nome: string }; quantidade: number; custoUnitario: number }[]
}

const kg3 = formatWeightDisplay

export default function DesossaPage() {
  const qc = useQueryClient()
  const { podeVerIdentificacao } = usePermissao()
  const [fichaSel, setFichaSel]         = useState<FichaDesossa | null>(null)
  const [qtdEntrada, setQtdEntrada]     = useState(0)
  const [custoPorKg, setCustoPorKg]     = useState(0)
  const [qtdsReais, setQtdsReais]       = useState<Record<number, number>>({})
  const [recebimentoId, setRecebimentoId] = useState('')
  const [preenchidoPelaNf, setPreenchidoPelaNf] = useState(false)
  const [confirmouPerdaAnormal, setConfirmouPerdaAnormal] = useState(false)

  // Filtro de período do histórico — sem período escolhido, o histórico
  // não é carregado nem exibido (evita listar todas as execuções de uma vez).
  const [historicoInicio, setHistoricoInicio] = useState('')
  const [historicoFim, setHistoricoFim]       = useState('')

  const { data: saldoNf } = useQuery<number>({
    queryKey: ['saldo-nf', recebimentoId, fichaSel?.produtoPai.id],
    queryFn: () => api.get('/estoque/desossa/saldo-nf', {
      params: { recebimentoId, produtoPaiId: fichaSel!.produtoPai.id },
    }).then(r => r.data),
    enabled: !!recebimentoId && !!fichaSel,
  })

  const { data: fichas = [] } = useQuery<FichaDesossa[]>({
    queryKey: ['fichas-desossa'],
    queryFn: () => api.get('/estoque/fichas-desossa').then(r => r.data),
  })

  const { data: recebimentos = [] } = useQuery<Recebimento[]>({
    queryKey: ['recebimentos'],
    queryFn: () => api.get('/estoque/recebimentos').then(r => r.data),
  })

  const { data: historico = [] } = useQuery<ProcessoDesossa[]>({
    queryKey: ['desossa-historico', fichaSel?.id, historicoInicio, historicoFim],
    queryFn: () => api.get(`/estoque/desossa/historico/${fichaSel!.id}`, {
      params: { inicio: historicoInicio, fim: historicoFim },
    }).then(r => r.data),
    enabled: !!fichaSel && !!historicoInicio && !!historicoFim,
  })

  const executar = useMutation({
    mutationFn: (dto: ExecutarDesossaDTO) =>
      api.post('/estoque/desossa/executar', dto),
    onSuccess: () => {
      toast.success('Desossa executada e estoque atualizado!')
      qc.invalidateQueries({ queryKey: ['produtos'] })
      qc.invalidateQueries({ queryKey: ['desossa-historico'] })
      setFichaSel(null)
      setQtdEntrada(0)
      setCustoPorKg(0)
      setQtdsReais({})
      setRecebimentoId('')
      setPreenchidoPelaNf(false)
      setConfirmouPerdaAnormal(false)
    },
  })

  function handleExecutar() {
    if (!fichaSel || !qtdEntrada) return
    const notaPerdaAnormal = rendimentoAbaixoDoEsperado
      ? `[Perda acima do esperado: previsto ${kg3(somaPrevista)} kg, real ${kg3(somaReal)} kg — confirmado pelo operador]`
      : ''
    const dto: ExecutarDesossaDTO = {
      fichaDesossaId: fichaSel.id,
      quantidadeKgEntrada: qtdEntrada,
      custoPorKg: custoPorKg || undefined,
      quantidadesReais: qtdsReais,
      recebimentoId: recebimentoId ? parseInt(recebimentoId) : null,
      observacao: notaPerdaAnormal || undefined,
    }
    executar.mutate(dto)
  }

  const qtdNum = qtdEntrada || 0

  // Soma prevista (pela ficha) x soma real (o que o operador está digitando).
  // Usados para alertar quando o rendimento real fica muito abaixo do esperado.
  const somaPrevista = fichaSel
    ? fichaSel.itens.reduce((acc, item) => acc + qtdNum * (item.percentualRendimento / 100), 0)
    : 0
  const somaReal = fichaSel
    ? fichaSel.itens.reduce((acc, item) => {
        const prevista = qtdNum * (item.percentualRendimento / 100)
        const real = qtdsReais[item.produtoFilho.id]
        return acc + (real !== undefined ? real : prevista)
      }, 0)
    : 0
  // A própria ficha já define o rendimento esperado (ex: 92%, com 8% de perda cadastrada).
  // Qualquer soma real abaixo disso já é pior do que o previsto, sem margem extra inventada.
  // O 0.001 é só pra evitar ruído de arredondamento de ponto flutuante do JS, não é tolerância de negócio.
  const rendimentoAbaixoDoEsperado = somaPrevista > 0 && somaReal < somaPrevista - 0.001

  const recebSel = recebimentos.find(r => r.id === parseInt(recebimentoId))

  return (
    <div className="p-6">
      <PageHeader
        title="Desossa / Rendimento"
        subtitle="Processa peças inteiras em cortes e filhos com rateio automático"
      />

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">

        {/* Seleção de ficha */}
        <div>
          <h2 className="text-sm font-semibold text-gray-700 mb-3">Ficha de Desossa</h2>
          <div className="space-y-2">
            {fichas.map(f => (
              <button
                key={f.id}
                onClick={() => setFichaSel(f)}
                className={`w-full flex items-center justify-between p-4 rounded-xl border-2 text-left transition-all
                  ${fichaSel?.id === f.id
                    ? 'border-primary-500 bg-primary-50'
                    : 'border-gray-200 bg-white hover:border-gray-300'}`}
              >
                <div>
                  <p className="font-medium text-gray-900">{f.nome}</p>
                  <p className="text-xs text-gray-500 mt-0.5">
                    Produto pai: {f.produtoPai.nome} • {f.itens.length} cortes
                  </p>
                </div>
                <ChevronRight size={16} className="text-gray-400 shrink-0" />
              </button>
            ))}
            {fichas.length === 0 && (
              <p className="text-gray-400 text-sm">Nenhuma ficha cadastrada.</p>
            )}
          </div>

          {/* Histórico de execuções — escondido por padrão; só aparece depois
              que o usuário escolhe um período, com destaque para perdas anormais */}
          {fichaSel && (
            <div className="mt-6">
              <h2 className="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-1.5">
                <History size={14} /> Histórico de Execuções
              </h2>

              <div className="flex items-end gap-2 mb-3">
                <Filter size={14} className="text-gray-400 mb-2" />
                <div>
                  <label className="text-[11px] text-gray-500 block mb-1">De</label>
                  <input type="date" value={historicoInicio} onChange={e => setHistoricoInicio(e.target.value)}
                    className="border border-gray-300 rounded-lg px-2 py-1 text-xs" />
                </div>
                <div>
                  <label className="text-[11px] text-gray-500 block mb-1">Até</label>
                  <input type="date" value={historicoFim} onChange={e => setHistoricoFim(e.target.value)}
                    className="border border-gray-300 rounded-lg px-2 py-1 text-xs" />
                </div>
              </div>

              {!historicoInicio || !historicoFim ? (
                <p className="text-gray-400 text-sm">Escolha um período para ver as execuções anteriores.</p>
              ) : (
                <div className="space-y-2 max-h-96 overflow-y-auto">
                  {historico.map(p => {
                    const anormal = !!p.observacao
                    return (
                      <div
                        key={p.id}
                        className={`rounded-lg border px-3 py-2 text-sm ${
                          anormal ? 'border-warning-200 bg-warning-50' : 'border-gray-200 bg-white'
                        }`}
                      >
                        <div className="flex items-center justify-between">
                          <span className="font-medium text-gray-800">
                            {new Date(p.dataProcesso).toLocaleDateString('pt-BR')} — {kg3(p.quantidadeEntrada)} kg
                          </span>
                          {anormal && (
                            <span className="flex items-center gap-1 text-warning-700 text-xs font-semibold">
                              <AlertTriangle size={12} /> Perda anormal
                            </span>
                          )}
                        </div>
                        {podeVerIdentificacao && (
                          <p className="text-xs text-gray-500 mt-0.5">
                            Rateio por: <span className="font-medium">{p.usuarioNome ?? '—'}</span>
                          </p>
                        )}
                        {p.recebimento && (
                          <p className="text-xs text-gray-400 mt-0.5">
                            NF {p.recebimento.numeroNf ?? 'S/N'} — {p.recebimento.fornecedor}
                          </p>
                        )}
                        {anormal && (
                          <p className="text-xs text-warning-700 mt-1">{p.observacao}</p>
                        )}
                      </div>
                    )
                  })}
                  {historico.length === 0 && (
                    <p className="text-gray-400 text-sm">Nenhuma execução nesse período.</p>
                  )}
                </div>
              )}
            </div>
          )}
        </div>

        {/* Formulário de execução */}
        {fichaSel && (
          <Card className="space-y-4">
            <h2 className="font-semibold text-gray-900">
              Executar: <span className="text-primary-600">{fichaSel.nome}</span>
            </h2>

            <div className="grid grid-cols-2 gap-3">
              <Field
                label={<>Quantidade (kg) *{preenchidoPelaNf && <span className="text-info-600 font-normal"> (da NF)</span>}</>}
                hint={preenchidoPelaNf ? 'Vem da NF — desvincule para editar manualmente.' : undefined}
              >
                <WeightInput
                  value={qtdEntrada}
                  onChange={setQtdEntrada}
                  disabled={preenchidoPelaNf}
                  placeholder="100,000"
                />
              </Field>
              <Field label={<>Custo / kg (R$){preenchidoPelaNf && <span className="text-info-600 font-normal"> (da NF)</span>}</>}>
                <CurrencyInput value={custoPorKg} onChange={setCustoPorKg} disabled={preenchidoPelaNf} />
              </Field>
            </div>

            {/* Casamento com NF de recebimento */}
            <div>
              <label className="text-xs font-medium text-gray-600 flex items-center gap-1 mb-1">
                <Link2 size={12} /> Vincular NF de Recebimento
              </label>
              <select
                value={recebimentoId}
                onChange={e => {
                  setRecebimentoId(e.target.value)
                  if (e.target.value) {
                    const r = recebimentos.find(r => r.id === parseInt(e.target.value))
                    const itemPai = r?.itens.find(i => i.produto.id === fichaSel.produtoPai.id)
                    if (itemPai) {
                      setQtdEntrada(itemPai.quantidade)
                      setCustoPorKg(itemPai.custoUnitario)
                      setPreenchidoPelaNf(true)
                    } else if (r) {
                      toast.error(
                        `A NF ${r.numeroNf ?? 'S/N'} não tem item de "${fichaSel.produtoPai.nome}". Preencha manualmente.`
                      )
                    }
                  } else {
                    setPreenchidoPelaNf(false)
                  }
                }}
                className={baseInputClass}
              >
                <option value="">Sem vínculo</option>
                {recebimentos.map(r => (
                  <option key={r.id} value={r.id}>
                    NF {r.numeroNf ?? 'S/N'} — {r.fornecedor}
                  </option>
                ))}
              </select>
              {recebSel && (
                <p className="mt-1 text-xs text-info-600 flex items-center gap-1">
                  <Link2 size={10} />
                  Vinculada à NF {recebSel.numeroNf ?? 'S/N'} de {recebSel.fornecedor}
                </p>
              )}
              {recebimentoId && saldoNf !== undefined && (
                <p className={`mt-1 text-xs font-medium ${saldoNf > 0 ? 'text-success-600' : 'text-danger-600'}`}>
                  Saldo disponível nessa NF: {kg3(saldoNf)} kg
                </p>
              )}
            </div>

            {/* Itens da ficha com quantidades reais */}
            <div>
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">
                Quantidades Reais (opcional)
              </p>
              <div className="space-y-2 max-h-48 overflow-y-auto">
                {fichaSel.itens.map(item => {
                  const prevista = qtdNum * (item.percentualRendimento / 100)
                  const valorAtual = qtdsReais[item.produtoFilho.id] ?? prevista
                  return (
                    <div key={item.id} className="flex items-center gap-3 bg-gray-50 rounded-lg px-3 py-2">
                      <div className="flex-1">
                        <p className="text-sm font-medium">{item.produtoFilho.nome}</p>
                        <p className="text-xs text-gray-400">
                          {item.percentualRendimento}% — Previsto: {kg3(prevista)} kg
                        </p>
                      </div>
                      <WeightInput
                        value={valorAtual}
                        onChange={v => {
                          setQtdsReais(prev => ({ ...prev, [item.produtoFilho.id]: v }))
                          setConfirmouPerdaAnormal(false)
                        }}
                        size="sm"
                        className="w-32"
                      />
                    </div>
                  )
                })}
              </div>
            </div>

            {recebimentoId && saldoNf !== undefined && qtdNum > saldoNf && (
              <p className="text-xs text-danger-600 font-medium bg-danger-50 border border-danger-100 rounded-lg px-3 py-2">
                Quantidade acima do saldo disponível na NF ({kg3(saldoNf)} kg).
              </p>
            )}

            {rendimentoAbaixoDoEsperado && (
              <div className="text-xs bg-warning-50 border border-warning-100 rounded-lg px-3 py-2 space-y-2">
                <p className="text-warning-700 font-medium">
                  ⚠️ Rendimento real ({kg3(somaReal)} kg) está bem abaixo do previsto pela ficha ({kg3(somaPrevista)} kg).
                </p>
                <label className="flex items-center gap-2 text-warning-800 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={confirmouPerdaAnormal}
                    onChange={e => setConfirmouPerdaAnormal(e.target.checked)}
                    className="rounded border-warning-400"
                  />
                  Confirmo que revisei os pesos e a perda está correta.
                </label>
              </div>
            )}

            <Button
              variant="primary"
              fullWidth
              size="md"
              loading={executar.isPending}
              disabled={
                !qtdEntrada ||
                (recebimentoId !== '' && saldoNf !== undefined && qtdNum > saldoNf) ||
                (rendimentoAbaixoDoEsperado && !confirmouPerdaAnormal)
              }
              onClick={handleExecutar}
              className="!py-3"
            >
              Executar Desossa
            </Button>
          </Card>
        )}
      </div>
    </div>
  )
}
