import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Filter, Plus, Pencil, Trash2 } from 'lucide-react'
import { api } from '@/shared/api/axios'
import type { LogAuditoria, ModuloInfo, Usuario } from '@/types/acesso'
import { PageHeader, Card, StatusBadge, Field, EmptyState, LoadingState } from '@/shared/components/ui'
import type { BadgeTone } from '@/shared/components/ui'

const hoje = new Date().toISOString().slice(0, 10)
const seteDiasAtras = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10)

// Criar = positivo; Editar = neutro/informativo; Excluir = negativo/atenção.
const acaoConfig: Record<string, { label: string; tone: BadgeTone; icone: typeof Plus }> = {
  CRIAR:   { label: 'Criou',    tone: 'success', icone: Plus },
  EDITAR:  { label: 'Editou',   tone: 'info',    icone: Pencil },
  EXCLUIR: { label: 'Excluiu',  tone: 'danger',  icone: Trash2 },
}

const selectClass = 'border border-gray-300 rounded-lg px-3 py-1.5 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary-500'

export default function AuditoriaPage() {
  const [filtroInicio, setFiltroInicio]     = useState(seteDiasAtras)
  const [filtroFim, setFiltroFim]           = useState(hoje)
  const [filtroModulo, setFiltroModulo]     = useState('')
  const [filtroAcao, setFiltroAcao]         = useState('')
  const [filtroUsuarioId, setFiltroUsuarioId] = useState('')

  const { data: modulos = [] } = useQuery<ModuloInfo[]>({
    queryKey: ['modulos'],
    queryFn: () => api.get('/perfis/modulos').then(r => r.data),
  })
  const rotuloDoModulo = (nome: string) => modulos.find(m => m.nome === nome)?.rotulo ?? nome

  const { data: usuarios = [] } = useQuery<Usuario[]>({
    queryKey: ['usuarios'],
    queryFn: () => api.get('/usuarios').then(r => r.data),
  })

  const { data: logs = [], isLoading } = useQuery<LogAuditoria[]>({
    queryKey: ['auditoria', filtroInicio, filtroFim, filtroModulo, filtroAcao, filtroUsuarioId],
    queryFn: () => api.get('/auditoria', {
      params: {
        inicio: filtroInicio || undefined,
        fim: filtroFim || undefined,
        modulo: filtroModulo || undefined,
        acao: filtroAcao || undefined,
        usuarioId: filtroUsuarioId || undefined,
      },
    }).then(r => r.data),
  })

  return (
    <div className="p-6">
      <PageHeader title="Auditoria" subtitle="Histórico de ações administrativas" />

      {/* Filtros */}
      <Card padding="sm" className="mb-4 flex flex-wrap items-end gap-3">
        <Filter size={16} className="text-gray-400 mb-2" />
        <Field label="De">
          <input type="date" value={filtroInicio} onChange={e => setFiltroInicio(e.target.value)} className={selectClass} />
        </Field>
        <Field label="Até">
          <input type="date" value={filtroFim} onChange={e => setFiltroFim(e.target.value)} className={selectClass} />
        </Field>
        <Field label="Módulo">
          <select value={filtroModulo} onChange={e => setFiltroModulo(e.target.value)} className={`${selectClass} min-w-[160px]`}>
            <option value="">Todos</option>
            {modulos.map(m => <option key={m.nome} value={m.nome}>{m.rotulo}</option>)}
          </select>
        </Field>
        <Field label="Ação">
          <select value={filtroAcao} onChange={e => setFiltroAcao(e.target.value)} className={selectClass}>
            <option value="">Todas</option>
            <option value="CRIAR">Criou</option>
            <option value="EDITAR">Editou</option>
            <option value="EXCLUIR">Excluiu</option>
          </select>
        </Field>
        <Field label="Usuário">
          <select value={filtroUsuarioId} onChange={e => setFiltroUsuarioId(e.target.value)} className={`${selectClass} min-w-[160px]`}>
            <option value="">Todos</option>
            {usuarios.map(u => <option key={u.id} value={u.id}>{u.nome}</option>)}
          </select>
        </Field>
      </Card>

      {/* Lista */}
      <Card padding="none" className="overflow-hidden">
        {isLoading ? (
          <LoadingState />
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide">Quando</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide">Usuário</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide">Módulo</th>
                <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase tracking-wide">Ação</th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide">O quê</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {logs.map(l => {
                const cfg = acaoConfig[l.acao] ?? acaoConfig.EDITAR
                const Icone = cfg.icone
                return (
                  <tr key={l.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 text-gray-500 text-xs whitespace-nowrap">
                      {new Date(l.criadoEm).toLocaleString('pt-BR', {
                        day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
                      })}
                    </td>
                    <td className="px-4 py-3">
                      <span className="font-medium">{l.nomeUsuario}</span>
                      {l.perfilUsuario && (
                        <span className="text-xs text-gray-400 ml-1.5">({l.perfilUsuario})</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-gray-600">{rotuloDoModulo(l.modulo)}</td>
                    <td className="px-4 py-3 text-center">
                      <StatusBadge tone={cfg.tone}>
                        <span className="flex items-center gap-1"><Icone size={11} /> {cfg.label}</span>
                      </StatusBadge>
                    </td>
                    <td className="px-4 py-3 text-gray-500 font-mono text-xs">{l.descricao ?? '—'}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
        {!isLoading && logs.length === 0 && (
          <EmptyState>Nenhuma ação registrada para os filtros selecionados.</EmptyState>
        )}
      </Card>
    </div>
  )
}
