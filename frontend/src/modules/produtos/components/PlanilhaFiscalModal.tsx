import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Download, Upload } from 'lucide-react'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import type { Produto } from '@/types/produto'
import { Button, Modal, StatusBadge } from '@/shared/components/ui'
import { baixarCsv, gerarCsv, lerArquivo, lerCsv, semFiscal } from '../planilhaFiscal'
import type { LinhaPlanilha } from '../planilhaFiscal'

interface ResultadoLinha {
  linha: number
  codigoInterno: string | null
  produto: string | null
  status: 'ALTERAR' | 'SEM_ALTERACAO' | 'ERRO'
  mensagem: string
}

interface Resultado {
  aplicado: boolean
  total: number
  alterados: number
  semAlteracao: number
  erros: number
  aindaIncompletos: number
  linhas: ResultadoLinha[]
}

const TOM = { ALTERAR: 'info', SEM_ALTERACAO: 'neutral', ERRO: 'danger' } as const
const ROTULO = { ALTERAR: 'Vai mudar', SEM_ALTERACAO: 'Sem mudança', ERRO: 'Erro' } as const
const MAX_MOSTRADAS = 300

/**
 * Atualiza NCM, CEST, CFOP, origem, CSOSN e descrição de vários produtos de uma vez (só SUPER_ADMIN).
 * 1) baixa a planilha com os produtos; 2) preenche no Excel; 3) envia: primeiro uma prévia, depois aplica.
 */
export default function PlanilhaFiscalModal({ produtos, onClose }: { produtos: Produto[]; onClose: () => void }) {
  const qc = useQueryClient()
  const [soIncompletos, setSoIncompletos] = useState(true)
  const [linhas, setLinhas] = useState<LinhaPlanilha[] | null>(null)
  const [arquivo, setArquivo] = useState('')
  const [previa, setPrevia] = useState<Resultado | null>(null)
  const [filtro, setFiltro] = useState<'TODAS' | ResultadoLinha['status']>('TODAS')

  const ativos = produtos.filter(p => p.ativo)
  const incompletos = ativos.filter(semFiscal)
  const paraBaixar = soIncompletos ? incompletos : ativos

  const enviar = useMutation({
    mutationFn: ({ dados, aplicar }: { dados: LinhaPlanilha[]; aplicar: boolean }) =>
      api.post<Resultado>('/estoque/produtos/fiscal/importar', { linhas: dados, aplicar }).then(r => r.data),
    onSuccess: r => {
      if (!r.aplicado) {
        setPrevia(r)
        return
      }
      toast.success(`${r.alterados} produto(s) atualizado(s).`)
      qc.invalidateQueries({ queryKey: ['produtos'] })
      qc.invalidateQueries({ queryKey: ['pdv-produtos'] })
      onClose()
    },
  })

  function baixar() {
    baixarCsv(gerarCsv(paraBaixar), `produtos-fiscal-${new Date().toISOString().slice(0, 10)}.csv`)
  }

  async function escolher(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files?.[0]
    e.target.value = ''
    if (!f) return
    setPrevia(null)
    try {
      const lidas = lerCsv(await lerArquivo(f))
      if (lidas.length === 0) throw new Error('A planilha não tem linhas de produto.')
      setLinhas(lidas)
      setArquivo(f.name)
      enviar.mutate({ dados: lidas, aplicar: false })
    } catch (err) {
      setLinhas(null)
      toast.error((err as Error).message)
    }
  }

  const mostradas = (previa?.linhas ?? []).filter(l => filtro === 'TODAS' || l.status === filtro)

  return (
    <Modal
      title="Planilha fiscal de produtos"
      onClose={onClose}
      maxWidth="2xl"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} className="flex-1">Fechar</Button>
          <Button
            variant="primary"
            className="flex-1"
            disabled={!previa || previa.alterados === 0 || enviar.isPending}
            onClick={() => linhas && enviar.mutate({ dados: linhas, aplicar: true })}
          >
            {enviar.isPending && previa ? 'Aplicando...' : `Aplicar ${previa?.alterados ?? 0} alteração(ões)`}
          </Button>
        </>
      }
    >
      <section className="space-y-2">
        <h3 className="text-sm font-semibold text-gray-800">1. Baixe a planilha</h3>
        <p className="text-xs text-gray-500">
          {incompletos.length} de {ativos.length} produto(s) ativo(s) ainda sem NCM, CFOP ou CSOSN — esses não saem em nota fiscal.
        </p>
        <label className="flex items-center gap-2 text-sm text-gray-700">
          <input type="checkbox" checked={soIncompletos} onChange={e => setSoIncompletos(e.target.checked)} />
          Só os produtos com informação fiscal faltando
        </label>
        <Button variant="secondary" onClick={baixar} disabled={paraBaixar.length === 0}>
          <Download size={16} /> Baixar planilha ({paraBaixar.length} produto{paraBaixar.length === 1 ? '' : 's'})
        </Button>
      </section>

      <section className="space-y-1">
        <h3 className="text-sm font-semibold text-gray-800">2. Preencha no Excel e salve como CSV</h3>
        <ul className="text-xs text-gray-500 list-disc pl-5 space-y-0.5">
          <li>Não mude a coluna <b>codigo_interno</b>: é por ela que o produto é encontrado.</li>
          <li>Célula em branco mantém o que já está no cadastro (nada é apagado).</li>
          <li>NCM com 8 dígitos (pode ter pontos), CFOP com 4 (ex.: 5102), origem de 0 a 8, CSOSN (ex.: 102, 500).</li>
          <li>A <b>descricao</b> também pode ser corrigida; ela fica em MAIÚSCULAS.</li>
          <li>Confirme os códigos com a contabilidade antes de aplicar.</li>
        </ul>
      </section>

      <section className="space-y-2">
        <h3 className="text-sm font-semibold text-gray-800">3. Envie a planilha preenchida</h3>
        <label className="inline-flex items-center gap-2 px-4 py-2 rounded-lg border border-gray-300 text-sm font-medium text-gray-700 cursor-pointer hover:bg-gray-50">
          <Upload size={16} /> Escolher arquivo CSV
          <input type="file" accept=".csv,text/csv" onChange={escolher} className="sr-only" data-testid="planilha-fiscal-arquivo" />
        </label>
        {arquivo && <span className="ml-3 text-xs text-gray-500">{arquivo}</span>}
        {enviar.isPending && !previa && <p className="text-sm text-gray-500">Conferindo a planilha...</p>}
      </section>

      {previa && (
        <section className="space-y-3" aria-live="polite">
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-center">
            {([
              ['Vai mudar', previa.alterados, 'ALTERAR'],
              ['Sem mudança', previa.semAlteracao, 'SEM_ALTERACAO'],
              ['Com erro', previa.erros, 'ERRO'],
              ['Linhas', previa.total, 'TODAS'],
            ] as const).map(([rotulo, n, f]) => (
              <button key={rotulo} type="button" onClick={() => setFiltro(f)}
                      className={`rounded-lg border p-2 ${filtro === f ? 'border-primary-500 bg-primary-50' : 'border-gray-200'}`}>
                <div className="text-lg font-semibold text-gray-900 tabular-nums">{n}</div>
                <div className="text-xs text-gray-500">{rotulo}</div>
              </button>
            ))}
          </div>
          {previa.aindaIncompletos > 0 && (
            <p className="text-xs text-warning-700">
              {previa.aindaIncompletos} produto(s) continuarão sem tudo o que a nota exige (veja "Ainda falta" em cada linha).
            </p>
          )}
          <div className="border rounded-lg max-h-72 overflow-y-auto">
            <table className="w-full text-xs">
              <thead className="bg-gray-50 sticky top-0">
                <tr className="text-left text-gray-500">
                  <th className="px-2 py-1.5">Linha</th>
                  <th className="px-2 py-1.5">Produto</th>
                  <th className="px-2 py-1.5">Situação</th>
                  <th className="px-2 py-1.5">Detalhe</th>
                </tr>
              </thead>
              <tbody>
                {mostradas.slice(0, MAX_MOSTRADAS).map(l => (
                  <tr key={l.linha} className="border-t">
                    <td className="px-2 py-1.5 tabular-nums text-gray-500">{l.linha}</td>
                    <td className="px-2 py-1.5">
                      <div className="font-mono text-gray-500">{l.codigoInterno ?? '—'}</div>
                      <div className="text-gray-800">{l.produto ?? ''}</div>
                    </td>
                    <td className="px-2 py-1.5"><StatusBadge tone={TOM[l.status]}>{ROTULO[l.status]}</StatusBadge></td>
                    <td className="px-2 py-1.5 text-gray-600">{l.mensagem}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {mostradas.length > MAX_MOSTRADAS && (
              <p className="p-2 text-xs text-gray-500">Mostrando {MAX_MOSTRADAS} de {mostradas.length} linhas.</p>
            )}
          </div>
          {previa.erros > 0 && previa.alterados > 0 && (
            <p className="text-xs text-gray-500">As linhas com erro ficam de fora; as demais são aplicadas.</p>
          )}
        </section>
      )}
    </Modal>
  )
}
