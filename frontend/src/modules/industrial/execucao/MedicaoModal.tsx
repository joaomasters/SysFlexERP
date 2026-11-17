import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import { Modal, Button, Field, baseInputClass, LoadingState } from '@/shared/components/ui'
import { ETAPAS_RECEITA, dentroDaFaixa, faixaTexto, novaChave, type Medicao, type Receita } from '@/types/industrial'

/**
 * Medição de processo contra a receita da liga e da máquina. O operador digita o valor lido no
 * equipamento (origem manual); fora da faixa, a tela avisa na hora e pede a ação tomada.
 */
export default function MedicaoModal({ ordemId, apontamentoId, maquinaId, onClose }:
  { ordemId: number; apontamentoId?: number; maquinaId?: number | null; onClose: () => void }) {
  const qc = useQueryClient()
  const [chave, setChave] = useState(novaChave)
  const [receitaId, setReceitaId] = useState(0)
  const [valores, setValores] = useState<Record<string, string>>({})
  const [observacao, setObservacao] = useState('')
  const { data: receitas, isLoading } = useQuery<Receita[]>({
    queryKey: ['ind-receitas-op', ordemId, maquinaId ?? 0],
    queryFn: () => api.get('/industrial/extrusao/receitas-da-op', { params: { ordemId, maquinaId: maquinaId ?? undefined } }).then(r => r.data),
  })
  const receita = receitas?.find(r => r.id === receitaId) ?? (receitas?.length === 1 ? receitas[0] : undefined)
  const fora = receita?.parametros.some(p => valores[p.codigo] !== undefined && valores[p.codigo] !== ''
    && !dentroDaFaixa(Number(valores[p.codigo]), p.minimo, p.maximo)) ?? false

  const registrar = useMutation({
    mutationFn: () => api.post('/industrial/medicoes', {
      chave, ordemId, apontamentoId: apontamentoId ?? null, maquinaId: maquinaId ?? null, receitaId: receita!.id,
      origem: 'MANUAL', observacao: observacao || null,
      valores: Object.entries(valores).filter(([, v]) => v !== '').map(([codigo, v]) => ({ codigo, valor: Number(v) })),
    }).then(r => r.data as Medicao),
    onSuccess: m => {
      if (m.foraEspecificacao) toast.error('Medição registrada FORA da faixa da receita.')
      else toast.success('Medição registrada dentro da faixa.')
      qc.invalidateQueries({ queryKey: ['ind-metalurgia', ordemId] })
      setChave(novaChave())
      setValores({})
      setObservacao('')
    },
  })

  function tentar() {
    if (!receita) return toast.error('Escolha a receita.')
    const faltando = receita.parametros.find(p => p.obrigatorio && !valores[p.codigo])
    if (faltando) return toast.error(`Informe ${faltando.nome}.`)
    if (fora && !observacao.trim()) return toast.error('Valor fora da faixa: descreva a ação tomada.')
    registrar.mutate()
  }

  return (
    <Modal title="Medição de processo" onClose={onClose} maxWidth="lg"
      footer={<>
        <Button variant="secondary" fullWidth onClick={onClose}>Fechar</Button>
        <Button fullWidth variant={fora ? 'warning' : 'primary'} loading={registrar.isPending} disabled={!receita} onClick={tentar}>Registrar medição</Button>
      </>}>
      {isLoading ? <LoadingState /> : !receitas?.length ? (
        <p className="text-sm text-gray-600">
          Nenhuma receita para esta OP nesta máquina. É preciso a liga do produto na ficha técnica e uma receita ativa da liga para o tipo da máquina.
        </p>
      ) : (
        <div className="space-y-4">
          {receitas.length > 1 && (
            <div className="grid gap-2">
              {receitas.map(r => (
                <button key={r.id} type="button" onClick={() => { setReceitaId(r.id); setValores({}) }}
                  className={`text-left rounded-xl border-2 p-3 ${receita?.id === r.id ? 'border-primary-500 bg-primary-50' : 'border-gray-200'}`}>
                  <span className="font-semibold">{ETAPAS_RECEITA[r.etapa]}</span> · <span className="font-mono">{r.codigo}</span> rev. {r.revisao}
                </button>
              ))}
            </div>
          )}
          {receita && (
            <>
              {receitas.length === 1 && <p className="text-sm text-gray-600">{ETAPAS_RECEITA[receita.etapa]} · <span className="font-mono">{receita.codigo}</span> rev. {receita.revisao}</p>}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {receita.parametros.map(p => {
                  const v = valores[p.codigo] ?? ''
                  const ruim = v !== '' && !dentroDaFaixa(Number(v), p.minimo, p.maximo)
                  return (
                    <Field key={p.codigo} label={`${p.nome}${p.unidade ? ` (${p.unidade})` : ''}${p.obrigatorio ? ' *' : ''}`} htmlFor={`med-${p.codigo}`}
                      hint={`Faixa: ${faixaTexto(p.minimo, p.maximo, p.unidade)}${p.alvo != null ? ` · alvo ${p.alvo.toLocaleString('pt-BR')}` : ''}`}>
                      <input id={`med-${p.codigo}`} type="number" inputMode="decimal" step="any" value={v}
                        onChange={e => setValores({ ...valores, [p.codigo]: e.target.value })}
                        className={`${baseInputClass} text-xl py-3 tabular-nums ${ruim ? 'border-danger-500 bg-danger-50 text-danger-700' : ''}`} />
                    </Field>
                  )
                })}
              </div>
              {fora && <p className="text-sm font-semibold text-danger-700">Fora da faixa da receita. Siga o procedimento da fábrica e registre a ação tomada.</p>}
              <Field label={fora ? 'Ação tomada *' : 'Observação'} htmlFor="med-obs">
                <textarea id="med-obs" rows={2} maxLength={500} value={observacao} onChange={e => setObservacao(e.target.value)} className={baseInputClass} />
              </Field>
            </>
          )}
        </div>
      )}
    </Modal>
  )
}
