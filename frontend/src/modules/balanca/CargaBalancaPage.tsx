import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Download, Eye, Clock, X, Send } from 'lucide-react'
import { api } from '@/shared/api/axios'
import toast from 'react-hot-toast'
import type { Produto, ItemPendenteBalanca } from '@/types/produto'
import { formatBRL } from '@/shared/utils/mask'
import { PageHeader, Card, Button, StatusBadge } from '@/shared/components/ui'

type TipoBalanca = 'TOLEDO_MGV7' | 'FILIZOLA_SMART'

const BALANÇAS = [
  { value: 'TOLEDO_MGV7'    as TipoBalanca, label: 'Toledo MGV6/MGV7',  desc: 'Formato PLU com cabeçalho 99|CARGA|1|1' },
  { value: 'FILIZOLA_SMART' as TipoBalanca, label: 'Filizola Smart',    desc: 'Formato CSV posicional 1;PLU;NOME;PRECO;VALIDADE' },
]

const brl = formatBRL

function PendenciasBalanca() {
  const qc = useQueryClient()

  const { data: pendentes = [], isLoading } = useQuery<ItemPendenteBalanca[]>({
    queryKey: ['balanca-pendentes'],
    queryFn: () => api.get('/balanca/pendentes').then(r => r.data),
    refetchInterval: 30_000,
  })

  const cancelar = useMutation({
    mutationFn: (id: number) => api.post(`/balanca/pendentes/${id}/cancelar`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['balanca-pendentes'] })
      toast.success('Item removido da fila.')
    },
  })

  const gerarCarga = useMutation({
    mutationFn: () => api.post('/balanca/pendentes/gerar-carga'),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['balanca-pendentes'] })
      toast.success('Carga gerada! Aguardando o agente local aplicar na balança.')
    },
  })

  return (
    <Card className="mb-6">
      <div className="flex items-center justify-between mb-3">
        <p className="font-medium text-gray-900 flex items-center gap-2">
          <Clock size={16} className="text-warning-600" />
          Preços pendentes de carga na balança
          {pendentes.length > 0 && (
            <StatusBadge tone="warning">{pendentes.length}</StatusBadge>
          )}
        </p>
        <Button
          variant="warning"
          size="sm"
          loading={gerarCarga.isPending}
          disabled={pendentes.length === 0}
          onClick={() => gerarCarga.mutate()}
        >
          <Send size={14} /> Gerar carga agora
        </Button>
      </div>

      {isLoading && <p className="text-sm text-gray-400 py-4">Carregando...</p>}

      {!isLoading && pendentes.length === 0 && (
        <p className="text-sm text-gray-400 py-4 text-center">
          Nenhum preço pendente — a balança está com os valores em dia.
        </p>
      )}

      {pendentes.length > 0 && (
        <div className="max-h-64 overflow-y-auto">
          <table className="w-full text-xs">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-3 py-2 text-left text-gray-500">PLU</th>
                <th className="px-3 py-2 text-left text-gray-500">Produto</th>
                <th className="px-3 py-2 text-right text-gray-500">Preço anterior</th>
                <th className="px-3 py-2 text-right text-gray-500">Preço novo</th>
                <th className="px-3 py-2 text-left text-gray-500">Desde</th>
                <th className="px-3 py-2 text-center text-gray-500">Ações</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {pendentes.map(item => (
                <tr key={item.id}>
                  <td className="px-3 py-2 font-mono">{String(item.codigoBalanca).padStart(5, '0')}</td>
                  <td className="px-3 py-2">{item.produtoNome}</td>
                  <td className="px-3 py-2 text-right tabular-nums text-gray-400 line-through">
                    {brl(item.precoAnterior)}
                  </td>
                  <td className="px-3 py-2 text-right tabular-nums font-medium text-warning-700">
                    {brl(item.precoNovo)}
                  </td>
                  <td className="px-3 py-2 text-gray-500">
                    {new Date(item.criadoEm).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}
                  </td>
                  <td className="px-3 py-2 text-center">
                    <button
                      onClick={() => cancelar.mutate(item.id)}
                      disabled={cancelar.isPending}
                      title="Remover da fila (não altera o preço no sistema, só cancela a sincronização com a balança)"
                      className="text-gray-400 hover:text-danger-600 disabled:opacity-50"
                    >
                      <X size={14} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <p className="text-[11px] text-gray-400 mt-3">
        Esses preços foram alterados no sistema mas ainda não chegaram fisicamente na balança.
        A carga é gerada automaticamente todo dia às 7h, ou você pode forçar agora com o botão acima.
        Depois que a carga é gerada, o agente local instalado perto da balança faz o envio.
      </p>
    </Card>
  )
}

export default function CargaBalancaPage() {
  const [tipo, setTipo]         = useState<TipoBalanca>('TOLEDO_MGV7')
  const [preview, setPreview]   = useState<string | null>(null)
  const [loadPreview, setLoadP] = useState(false)
  const [downloading, setDown]  = useState(false)

  const { data: produtos = [] } = useQuery<Produto[]>({
    queryKey: ['produtos-balanca'],
    queryFn: () => api.get('/estoque/produtos', { params: { balanca: true } }).then(r => r.data),
  })

  const prodComPlu = produtos.filter(p => p.codigoBalanca)

  async function verPreview() {
    setLoadP(true)
    try {
      const path = tipo === 'TOLEDO_MGV7' ? 'toledo-mgv7' : 'filizola-smart'
      const { data } = await api.get<string>(`/balanca/carga/preview/${path}`)
      setPreview(data)
    } finally {
      setLoadP(false)
    }
  }

  async function baixarArquivo() {
    setDown(true)
    try {
      const path = tipo === 'TOLEDO_MGV7' ? 'toledo-mgv7' : 'filizola-smart'
      const resp = await api.get(`/balanca/carga/${path}`, { responseType: 'blob' })
      const url  = URL.createObjectURL(new Blob([resp.data]))
      const a    = document.createElement('a')
      a.href     = url
      a.download = tipo === 'TOLEDO_MGV7' ? 'carga_toledo.txt' : 'carga_filizola.txt'
      a.click()
      URL.revokeObjectURL(url)
      toast.success('Arquivo gerado e registrado!')
    } finally {
      setDown(false)
    }
  }

  return (
    <div className="p-6">
      <PageHeader
        title="Carga de Balança"
        subtitle="Gera arquivo de atualização de preços e cadastro de PLUs para balanças"
      />

      {/* Fila de preços pendentes de carga */}
      <PendenciasBalanca />

      {/* Seletor de balança */}
      <div className="grid grid-cols-2 gap-4 mb-6">
        {BALANÇAS.map(b => (
          <button
            key={b.value}
            onClick={() => { setTipo(b.value); setPreview(null) }}
            className={`p-4 rounded-xl border-2 text-left transition-colors
              ${tipo === b.value
                ? 'border-primary-500 bg-primary-50'
                : 'border-gray-200 bg-white hover:border-gray-300'}`}
          >
            <p className="font-semibold text-gray-900">{b.label}</p>
            <p className="text-xs text-gray-500 mt-1">{b.desc}</p>
          </button>
        ))}
      </div>

      {/* Resumo de produtos com PLU */}
      <Card className="mb-6">
        <div className="flex items-center justify-between mb-3">
          <p className="font-medium text-gray-900">
            Produtos com PLU cadastrado: <span className="text-primary-600">{prodComPlu.length}</span>
          </p>
          <div className="flex gap-2">
            <Button variant="secondary" size="sm" loading={loadPreview} onClick={verPreview}>
              <Eye size={14} /> Pré-visualizar
            </Button>
            <Button variant="primary" size="sm" loading={downloading} disabled={prodComPlu.length === 0} onClick={baixarArquivo}>
              <Download size={14} /> Baixar Arquivo
            </Button>
          </div>
        </div>

        <div className="max-h-48 overflow-y-auto">
          <table className="w-full text-xs">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-3 py-2 text-left text-gray-500">PLU</th>
                <th className="px-3 py-2 text-left text-gray-500">Nome</th>
                <th className="px-3 py-2 text-right text-gray-500">Preço/KG</th>
                <th className="px-3 py-2 text-center text-gray-500">EAN-13</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {prodComPlu.map(p => (
                <tr key={p.id}>
                  <td className="px-3 py-2 font-mono">{String(p.codigoBalanca).padStart(5, '0')}</td>
                  <td className="px-3 py-2">{p.nome}</td>
                  <td className="px-3 py-2 text-right tabular-nums font-medium">
                    {brl(p.precoVenda)}
                  </td>
                  <td className="px-3 py-2 text-center font-mono text-gray-400">
                    {p.codigoBalanca ? `2${String(p.codigoBalanca).padStart(5, '0')}XXXXX` : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      {/* Pré-visualização do arquivo — tema "console" proposital, ver nota em RecebimentoPage.tsx */}
      {preview && (
        <div className="bg-gray-900 rounded-xl p-4">
          <div className="flex items-center justify-between mb-2">
            <p className="text-xs font-medium text-gray-400 uppercase tracking-wide">
              Pré-visualização — {tipo}
            </p>
            <button onClick={() => setPreview(null)} className="text-gray-500 hover:text-gray-300 text-xs">
              Fechar
            </button>
          </div>
          <pre className="text-success-400 text-xs font-mono whitespace-pre overflow-x-auto max-h-64">
            {preview}
          </pre>
        </div>
      )}
    </div>
  )
}
