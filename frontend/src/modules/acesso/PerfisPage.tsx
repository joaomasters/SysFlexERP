import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Save, Trash2, Lock } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { usePermissao } from '@/shared/hooks/usePermissao'
import toast from 'react-hot-toast'
import type { Perfil, ModuloInfo } from '@/types/acesso'
import { PageHeader, Card, Button, Field, baseInputClass } from '@/shared/components/ui'

type Acao = 'podeVer' | 'podeCriar' | 'podeEditar' | 'podeExcluir'

const ACOES: { campo: Acao; rotulo: string }[] = [
  { campo: 'podeVer',     rotulo: 'Ver' },
  { campo: 'podeCriar',   rotulo: 'Criar' },
  { campo: 'podeEditar',  rotulo: 'Editar' },
  { campo: 'podeExcluir', rotulo: 'Excluir' },
]

export default function PerfisPage() {
  const qc = useQueryClient()
  const { isSuperAdmin } = usePermissao()
  const [perfilSelId, setPerfilSelId] = useState<number | null>(null)
  const [matriz, setMatriz] = useState<Record<string, Record<Acao, boolean>>>({})
  const [novoNome, setNovoNome] = useState('')
  const [novaDescricao, setNovaDescricao] = useState('')
  const [showNovo, setShowNovo] = useState(false)

  const { data: perfis = [] } = useQuery<Perfil[]>({
    queryKey: ['perfis'],
    queryFn: () => api.get('/perfis').then(r => r.data),
  })

  const { data: modulos = [] } = useQuery<ModuloInfo[]>({
    queryKey: ['modulos'],
    queryFn: () => api.get('/perfis/modulos').then(r => r.data),
  })

  const perfilSel = perfis.find(p => p.id === perfilSelId) ?? null

  // Ao trocar de perfil, carrega a matriz dele no estado local
  useEffect(() => {
    if (!perfilSel) return
    const nova: Record<string, Record<Acao, boolean>> = {}
    modulos.forEach(m => {
      const p = perfilSel.permissoes.find(pp => pp.modulo === m.nome)
      nova[m.nome] = {
        podeVer:     p?.podeVer ?? false,
        podeCriar:   p?.podeCriar ?? false,
        podeEditar:  p?.podeEditar ?? false,
        podeExcluir: p?.podeExcluir ?? false,
      }
    })
    setMatriz(nova)
  }, [perfilSelId, perfis, modulos])

  const criarPerfil = useMutation({
    mutationFn: () => api.post('/perfis', { nome: novoNome.trim().toUpperCase(), descricao: novaDescricao }),
    onSuccess: () => {
      toast.success('Perfil criado!')
      qc.invalidateQueries({ queryKey: ['perfis'] })
      setNovoNome(''); setNovaDescricao(''); setShowNovo(false)
    },
  })

  const salvarPermissoes = useMutation({
    mutationFn: () => api.put(`/perfis/${perfilSelId}/permissoes`, {
      permissoes: modulos.map(m => ({
        modulo:  m.nome,
        ver:     matriz[m.nome]?.podeVer     ?? false,
        criar:   matriz[m.nome]?.podeCriar   ?? false,
        editar:  matriz[m.nome]?.podeEditar  ?? false,
        excluir: matriz[m.nome]?.podeExcluir ?? false,
      })),
    }),
    onSuccess: () => {
      toast.success('Permissões salvas! Os usuários desse perfil verão a mudança no próximo login.')
      qc.invalidateQueries({ queryKey: ['perfis'] })
    },
  })

  const excluirPerfil = useMutation({
    mutationFn: (id: number) => api.delete(`/perfis/${id}`),
    onSuccess: () => {
      toast.success('Perfil excluído.')
      qc.invalidateQueries({ queryKey: ['perfis'] })
      setPerfilSelId(null)
    },
  })

  function alternar(modulo: string, acao: Acao) {
    setMatriz(prev => ({
      ...prev,
      [modulo]: { ...prev[modulo], [acao]: !prev[modulo]?.[acao] },
    }))
  }

  function marcarTodos(modulo: string, valor: boolean) {
    setMatriz(prev => ({
      ...prev,
      [modulo]: { podeVer: valor, podeCriar: valor, podeEditar: valor, podeExcluir: valor },
    }))
  }

  if (!isSuperAdmin) {
    return (
      <div className="p-6">
        <div className="bg-warning-50 border border-warning-100 rounded-xl p-5 max-w-lg">
          <p className="flex items-center gap-2 font-medium text-warning-700">
            <Lock size={16} /> Acesso restrito
          </p>
          <p className="text-sm text-warning-600 mt-1">
            Apenas o SUPER_ADMIN pode gerenciar perfis e permissões.
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="p-6">
      <PageHeader
        title="Perfis de Acesso"
        subtitle="Defina o que cada perfil pode ver e fazer em cada módulo"
        actions={
          <Button variant="primary" onClick={() => setShowNovo(v => !v)}>
            <Plus size={16} /> Novo Perfil
          </Button>
        }
      />

      {showNovo && (
        <Card className="mb-5 flex gap-3 items-end max-w-2xl">
          <Field label="Nome *" className="flex-1">
            <input
              value={novoNome}
              onChange={e => setNovoNome(e.target.value)}
              placeholder="Ex: FISCAL"
              className={`${baseInputClass} uppercase`}
            />
          </Field>
          <Field label="Descrição" className="flex-[2]">
            <input
              value={novaDescricao}
              onChange={e => setNovaDescricao(e.target.value)}
              placeholder="Para que serve esse perfil"
              className={baseInputClass}
            />
          </Field>
          <Button variant="success" disabled={!novoNome.trim()} loading={criarPerfil.isPending} onClick={() => criarPerfil.mutate()}>
            Criar
          </Button>
        </Card>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-5">
        {/* Lista de perfis */}
        <div className="space-y-2">
          {perfis.map(p => (
            <button
              key={p.id}
              onClick={() => setPerfilSelId(p.id)}
              className={`w-full text-left p-3 rounded-xl border-2 transition-colors
                ${perfilSelId === p.id ? 'border-primary-500 bg-primary-50' : 'border-gray-200 bg-white hover:border-gray-300'}`}
            >
              <p className="font-medium text-gray-900 flex items-center gap-1.5">
                {p.nome}
                {p.protegido && <Lock size={11} className="text-gray-400" />}
              </p>
              {p.descricao && <p className="text-xs text-gray-500 mt-0.5">{p.descricao}</p>}
            </button>
          ))}
        </div>

        {/* Matriz de permissões */}
        <div className="lg:col-span-3">
          {!perfilSel && (
            <p className="text-gray-400 text-sm">Selecione um perfil para editar suas permissões.</p>
          )}

          {perfilSel?.superAdmin && (
            <div className="bg-info-50 border border-info-100 rounded-xl p-4 text-sm text-info-700">
              O perfil SUPER_ADMIN tem acesso total e irrestrito a todos os módulos, por definição.
              Suas permissões não podem ser alteradas.
            </div>
          )}

          {perfilSel && !perfilSel.superAdmin && (
            <Card padding="none" className="overflow-hidden">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b">
                  <tr>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide">Módulo</th>
                    {ACOES.map(a => (
                      <th key={a.campo} className="px-3 py-3 text-center text-xs font-semibold text-gray-500 uppercase tracking-wide w-20">
                        {a.rotulo}
                      </th>
                    ))}
                    <th className="px-3 py-3 w-24" />
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {modulos.map(m => (
                    <tr key={m.nome} className="hover:bg-gray-50">
                      <td className="px-4 py-2.5 font-medium text-gray-800">{m.rotulo}</td>
                      {ACOES.map(a => (
                        <td key={a.campo} className="px-3 py-2.5 text-center">
                          <input
                            type="checkbox"
                            checked={matriz[m.nome]?.[a.campo] ?? false}
                            onChange={() => alternar(m.nome, a.campo)}
                            className="w-4 h-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500 cursor-pointer"
                          />
                        </td>
                      ))}
                      <td className="px-3 py-2.5 text-right">
                        <button
                          onClick={() => marcarTodos(m.nome, !(matriz[m.nome]?.podeVer && matriz[m.nome]?.podeCriar
                            && matriz[m.nome]?.podeEditar && matriz[m.nome]?.podeExcluir))}
                          className="text-xs text-gray-400 hover:text-primary-600"
                        >
                          alternar
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              <div className="flex items-center justify-between px-4 py-3 bg-gray-50 border-t">
                <Button
                  variant="outline-danger"
                  size="sm"
                  disabled={perfilSel.protegido}
                  onClick={() => {
                    if (confirm(`Excluir o perfil "${perfilSel.nome}"? Essa ação não pode ser desfeita.`)) {
                      excluirPerfil.mutate(perfilSel.id)
                    }
                  }}
                >
                  <Trash2 size={14} /> Excluir perfil
                </Button>
                <Button variant="primary" loading={salvarPermissoes.isPending} onClick={() => salvarPermissoes.mutate()}>
                  <Save size={15} /> Salvar Permissões
                </Button>
              </div>
            </Card>
          )}
        </div>
      </div>
    </div>
  )
}
