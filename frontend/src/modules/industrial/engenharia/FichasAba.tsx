import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import {
  Modal, Button, StatusBadge, Field, baseInputClass, BuscaProduto,
  Table, THead, TH, TBody, TR, TD, EmptyState, LoadingState, Paginacao,
} from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { usePaginacao } from '@/shared/hooks/usePaginacao'
import { TIPOS_ITEM, type FichaTecnica, type TipoItem } from '@/types/industrial'
import { num, useProdutosIndustriais } from '../comum'
import { numeroOuNulo, paraTexto } from '../estrutura/CadastroSimples'
import { MODULO_ENG } from './EstruturasAba'

type Form = Record<'tipoItem' | 'codigoDesenho' | 'revisaoDesenho' | 'pesoUnitarioKg' | 'pesoLinearKgM' | 'comprimentoMm'
  | 'loteMinimo' | 'loteMultiplo' | 'prazoProducaoDias' | 'observacao', string> & { produtoId: number; controlaLote: boolean }

const paraForm = (f?: FichaTecnica): Form => ({
  produtoId: f?.produtoId ?? 0, tipoItem: f?.tipoItem ?? '', codigoDesenho: f?.codigoDesenho ?? '', revisaoDesenho: f?.revisaoDesenho ?? '',
  pesoUnitarioKg: paraTexto(f?.pesoUnitarioKg), pesoLinearKgM: paraTexto(f?.pesoLinearKgM), comprimentoMm: paraTexto(f?.comprimentoMm),
  loteMinimo: paraTexto(f?.loteMinimo), loteMultiplo: paraTexto(f?.loteMultiplo), prazoProducaoDias: paraTexto(f?.prazoProducaoDias),
  observacao: f?.observacao ?? '', controlaLote: f?.controlaLote ?? true,
})

/** Ficha técnica industrial: uma por produto, complementa o cadastro de produtos. */
export default function FichasAba() {
  const qc = useQueryClient()
  const { podeEditar } = usePermissao()
  const { data: produtos = [] } = useProdutosIndustriais()
  const { data: fichas = [], isLoading } = useQuery<FichaTecnica[]>({
    queryKey: ['ind-fichas'], queryFn: () => api.get('/industrial/engenharia/fichas').then(r => r.data),
  })
  const [busca, setBusca] = useState('')
  const [form, setForm] = useState<{ dados: Form; nova: boolean } | null>(null)
  const visiveis = fichas.filter(f => !busca.trim()
    || `${f.produtoNome} ${f.produtoCodigo ?? ''} ${f.codigoDesenho ?? ''}`.toLowerCase().includes(busca.trim().toLowerCase()))
  const pag = usePaginacao(visiveis, busca)

  const salvar = useMutation({
    mutationFn: (f: Form) => api.put(`/industrial/engenharia/fichas/${f.produtoId}`, {
      tipoItem: f.tipoItem, codigoDesenho: f.codigoDesenho || null, revisaoDesenho: f.revisaoDesenho || null,
      pesoUnitarioKg: numeroOuNulo(f.pesoUnitarioKg), pesoLinearKgM: numeroOuNulo(f.pesoLinearKgM),
      comprimentoMm: numeroOuNulo(f.comprimentoMm), loteMinimo: numeroOuNulo(f.loteMinimo), loteMultiplo: numeroOuNulo(f.loteMultiplo),
      prazoProducaoDias: numeroOuNulo(f.prazoProducaoDias), controlaLote: f.controlaLote, observacao: f.observacao || null,
    }),
    onSuccess: () => {
      toast.success('Ficha técnica salva.')
      qc.invalidateQueries({ queryKey: ['ind-fichas'] })
      qc.invalidateQueries({ queryKey: ['ind-produtos'] })
      setForm(null)
    },
  })

  function tentarSalvar() {
    const f = form!.dados
    if (!f.produtoId) { toast.error('Escolha o produto.'); return }
    if (!f.tipoItem) { toast.error('Escolha o tipo de item.'); return }
    if (form!.nova && fichas.some(x => x.produtoId === f.produtoId)) { toast.error('Este produto já tem ficha técnica. Edite a existente.'); return }
    salvar.mutate(f)
  }

  const campo = (nome: keyof Form, rotulo: string, extra: { tipo?: 'number' | 'text'; max?: number; dica?: string } = {}) => (
    <Field label={rotulo} htmlFor={`ficha-${nome}`} hint={extra.dica}>
      <input id={`ficha-${nome}`} type={extra.tipo ?? 'text'} step="any" min={extra.tipo === 'number' ? 0 : undefined} maxLength={extra.max}
        value={form!.dados[nome] as string} onChange={e => setForm({ ...form!, dados: { ...form!.dados, [nome]: e.target.value } })}
        className={baseInputClass} />
    </Field>
  )

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <input type="search" value={busca} onChange={e => setBusca(e.target.value)} placeholder="Buscar por produto ou desenho..."
          aria-label="Buscar fichas técnicas" className={`${baseInputClass} sm:w-80`} />
        {podeEditar(MODULO_ENG) && <Button onClick={() => setForm({ dados: paraForm(), nova: true })}>Nova ficha técnica</Button>}
      </div>
      <div className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
        {isLoading ? <LoadingState /> : (
          <Table>
            <THead><tr><TH>Produto</TH><TH>Tipo</TH><TH>Desenho</TH><TH align="right">Peso</TH><TH align="right">Lote mín. / múltiplo</TH><TH align="right">Prazo</TH><TH /></tr></THead>
            <TBody>
              {pag.itens.map(f => (
                <TR key={f.id}>
                  <TD><span className="font-medium">{f.produtoNome}</span> <span className="text-xs text-gray-400 font-mono">{f.produtoCodigo}</span></TD>
                  <TD><StatusBadge tone="info">{TIPOS_ITEM[f.tipoItem]}</StatusBadge></TD>
                  <TD className="font-mono text-sm">{f.codigoDesenho ? `${f.codigoDesenho}${f.revisaoDesenho ? ` rev. ${f.revisaoDesenho}` : ''}` : '—'}</TD>
                  <TD align="right" className="tabular-nums text-sm">
                    {f.pesoLinearKgM ? `${num(f.pesoLinearKgM, 4)} kg/m` : f.pesoUnitarioKg ? `${num(f.pesoUnitarioKg, 4)} kg/un.` : '—'}
                  </TD>
                  <TD align="right" className="tabular-nums text-sm">{num(f.loteMinimo)} / {num(f.loteMultiplo)} {f.unidade}</TD>
                  <TD align="right" className="tabular-nums text-sm">{f.prazoProducaoDias != null ? `${f.prazoProducaoDias} d` : '—'}</TD>
                  <TD align="right">
                    {podeEditar(MODULO_ENG) && <Button size="sm" variant="secondary" onClick={() => setForm({ dados: paraForm(f), nova: false })}>Editar</Button>}
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
        {!isLoading && visiveis.length === 0 && <EmptyState>Nenhuma ficha técnica{busca ? ' encontrada' : ' cadastrada'}.</EmptyState>}
        {!isLoading && <Paginacao paginacao={pag} rotulo="fichas" />}
      </div>

      {form && (
        <Modal title={form.nova ? 'Nova ficha técnica' : `Ficha técnica · ${fichas.find(f => f.produtoId === form.dados.produtoId)?.produtoNome ?? ''}`}
          onClose={() => setForm(null)} maxWidth="xl"
          footer={<>
            <Button variant="secondary" fullWidth onClick={() => setForm(null)}>Cancelar</Button>
            <Button fullWidth loading={salvar.isPending} onClick={tentarSalvar}>Salvar</Button>
          </>}>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {form.nova && (
              <div className="sm:col-span-2">
                <Field label="Produto *">
                  <BuscaProduto produtos={produtos.filter(p => !fichas.some(f => f.produtoId === p.id))} value={form.dados.produtoId}
                    onSelect={p => setForm({ ...form, dados: { ...form.dados, produtoId: p.id } })} />
                </Field>
              </div>
            )}
            <Field label="Tipo de item *" htmlFor="ficha-tipoItem">
              <select id="ficha-tipoItem" value={form.dados.tipoItem} className={baseInputClass}
                onChange={e => setForm({ ...form, dados: { ...form.dados, tipoItem: e.target.value as TipoItem } })}>
                <option value="">Selecione...</option>
                {Object.entries(TIPOS_ITEM).map(([v, r]) => <option key={v} value={v}>{r}</option>)}
              </select>
            </Field>
            <div className="grid grid-cols-2 gap-3">
              {campo('codigoDesenho', 'Desenho', { max: 40 })}
              {campo('revisaoDesenho', 'Revisão', { max: 10 })}
            </div>
            {campo('pesoLinearKgM', 'Peso linear (kg/m)', { tipo: 'number', dica: 'Perfis: referência técnica, não converte unidade sozinho' })}
            {campo('pesoUnitarioKg', 'Peso por unidade (kg)', { tipo: 'number' })}
            {campo('comprimentoMm', 'Comprimento (mm)', { tipo: 'number' })}
            {campo('prazoProducaoDias', 'Prazo de produção (dias)', { tipo: 'number' })}
            {campo('loteMinimo', 'Lote mínimo', { tipo: 'number', dica: 'Na unidade do produto. A OP não aceita menos' })}
            {campo('loteMultiplo', 'Lote múltiplo', { tipo: 'number', dica: 'A quantidade da OP precisa ser múltipla deste valor' })}
            <label className="flex items-center gap-2 text-sm text-gray-700 sm:col-span-2">
              <input type="checkbox" checked={form.dados.controlaLote}
                onChange={e => setForm({ ...form, dados: { ...form.dados, controlaLote: e.target.checked } })} />
              Controla lote (rastreabilidade)
            </label>
            <div className="sm:col-span-2">
              <Field label="Observação" htmlFor="ficha-observacao">
                <textarea id="ficha-observacao" rows={2} value={form.dados.observacao} className={baseInputClass}
                  onChange={e => setForm({ ...form, dados: { ...form.dados, observacao: e.target.value } })} />
              </Field>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
