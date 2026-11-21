import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, RefreshCw } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { Button, Card, LoadingState, StatusBadge } from '@/shared/components/ui'

/*
 Saneamento cadastral fiscal: contagens reais (feitas no banco na hora da consulta) do que impede
 nota hoje (BLOQUEIA) ou vai faltar na escrituração e apuração (ATENCAO). Nada é estimado.
*/

interface Registro { id: number | null; nome: string; detalhe: string | null }
interface Grupo {
  codigo: string
  titulo: string
  severidade: 'BLOQUEIA' | 'ATENCAO'
  impacto: string
  ondeCorrigir: string
  quantidade: number
  exemplos: Registro[]
}
interface Saneamento { geradoEm: string; totalPendencias: number; grupos: Grupo[] }

export default function SaneamentoCadastral() {
  const { data, isLoading, refetch, isFetching } = useQuery<Saneamento>({
    queryKey: ['fiscal-saneamento'],
    queryFn: () => api.get('/fiscal/saneamento').then(r => r.data),
  })

  if (isLoading || !data) return <LoadingState />

  return (
    <div className="space-y-4">
      <Card padding="md" className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-sm font-semibold text-gray-800">
            {data.totalPendencias === 0 ? 'Cadastro fiscal sem pendências' : `${data.totalPendencias} pendência(s) no cadastro fiscal`}
          </h2>
          <p className="text-xs text-gray-500">
            Contado agora, direto do banco ({new Date(data.geradoEm).toLocaleString('pt-BR')}). Corrija na tela indicada e atualize.
          </p>
        </div>
        <Button size="sm" variant="secondary" onClick={() => refetch()} loading={isFetching}>
          <RefreshCw size={14} /> Atualizar
        </Button>
      </Card>

      {data.grupos.map(g => (
        <section key={g.codigo} aria-label={g.titulo}>
          <Card padding="md" className="space-y-2">
            <div className="flex flex-wrap items-center gap-2">
              {g.quantidade === 0
                ? <CheckCircle2 size={16} className="text-success-600" />
                : <AlertTriangle size={16} className={g.severidade === 'BLOQUEIA' ? 'text-danger-600' : 'text-warning-600'} />}
              <h3 className="text-sm font-semibold text-gray-800">{g.titulo}</h3>
              <span className="tabular-nums text-sm text-gray-700">{g.quantidade}</span>
              {g.quantidade > 0 && (
                <StatusBadge tone={g.severidade === 'BLOQUEIA' ? 'danger' : 'warning'}>
                  {g.severidade === 'BLOQUEIA' ? 'Bloqueia emissão' : 'Atenção'}
                </StatusBadge>
              )}
            </div>
            {g.quantidade > 0 && (
              <>
                <p className="text-xs text-gray-600">{g.impacto} Corrija em: <strong>{g.ondeCorrigir}</strong>.</p>
                <ul className="divide-y divide-gray-100 rounded-lg border border-gray-100 text-sm">
                  {g.exemplos.map((r, i) => (
                    <li key={`${r.id ?? 'x'}-${i}`} className="flex flex-wrap justify-between gap-2 px-3 py-1.5">
                      <span className="text-gray-800">{r.nome}</span>
                      {r.detalhe && <span className="text-xs text-gray-500">{r.detalhe}</span>}
                    </li>
                  ))}
                </ul>
                {g.quantidade > g.exemplos.length && (
                  <p className="text-xs text-gray-500">Mostrando {g.exemplos.length} de {g.quantidade}.</p>
                )}
              </>
            )}
          </Card>
        </section>
      ))}
    </div>
  )
}
