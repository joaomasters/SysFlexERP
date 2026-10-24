import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Search, RotateCcw } from 'lucide-react'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import type { Fornecedor } from '@/types/fornecedor'
import {
  PageHeader, Modal, Button, StatusBadge, Field, baseInputClass, ConfirmDialog,
  Table, THead, TH, TBody, TR, TD, EmptyState,
} from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'

type FormState = {
  id?: number
  nome: string
  tipoPessoa: 'PF' | 'PJ'
  cpfCnpj: string
  telefone: string
  email: string
  contato: string
  endereco: string
}

// ── Máscaras e validação (mesmo padrão do cadastro de Clientes) ───────
const soDigitos = (v: string) => v.replace(/\D/g, '')

function formatarCpfCnpj(v: string, tipo: 'PF' | 'PJ'): string {
  const d = soDigitos(v).slice(0, tipo === 'PF' ? 11 : 14)
  if (tipo === 'PF') {
    return d
      .replace(/^(\d{3})(\d)/, '$1.$2')
      .replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
      .replace(/\.(\d{3})(\d)/, '.$1-$2')
  }
  return d
    .replace(/^(\d{2})(\d)/, '$1.$2')
    .replace(/^(\d{2})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d)/, '.$1/$2')
    .replace(/(\d{4})(\d)/, '$1-$2')
}

function formatarTelefone(v: string): string {
  const d = soDigitos(v).slice(0, 11)
  if (d.length <= 2) return d.length ? `(${d}` : ''
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`
  if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`
}

/** Cadastro novo exige tudo, exceto e-mail e contato. Fornecedor já existente (inclusive os
 *  importados automaticamente dos produtos antigos, que só têm nome) só exige o nome — assim
 *  dá para abrir e completar os dados aos poucos. */
function validarFornecedor(f: FormState) {
  const novo = !f.id
  const nDoc = f.tipoPessoa === 'PF' ? 11 : 14
  const tel = soDigitos(f.telefone).length
  const docPreenchido = soDigitos(f.cpfCnpj).length > 0
  return {
    nome:     f.nome.trim() ? '' : 'Informe o nome.',
    cpfCnpj:  (novo || docPreenchido) && soDigitos(f.cpfCnpj).length !== nDoc
                ? `${f.tipoPessoa === 'PF' ? 'CPF' : 'CNPJ'} deve ter ${nDoc} dígitos.` : '',
    telefone: (novo || tel > 0) && tel !== 10 && tel !== 11 ? 'Informe o telefone com DDD.' : '',
    email:    !f.email.trim() || /^\S+@\S+\.\S+$/.test(f.email.trim()) ? '' : 'E-mail inválido.',
    endereco: !novo || f.endereco.trim() ? '' : 'Informe o endereço.',
  }
}

const formVazio: FormState = {
  nome: '', tipoPessoa: 'PJ', cpfCnpj: '', telefone: '', email: '', contato: '', endereco: '',
}

export default function FornecedoresPage() {
  const qc = useQueryClient()
  const { podeVerIdentificacao, podeCriar, podeEditar, podeExcluir } = usePermissao()
  const [busca, setBusca] = useState('')
  const [mostrarInativos, setMostrarInativos] = useState(false)
  const [form, setForm] = useState<FormState | null>(null)
  const [confirmando, setConfirmando] = useState<Fornecedor | null>(null)
  const [tentouSalvar, setTentouSalvar] = useState(false)

  const { data: fornecedores = [], isLoading } = useQuery<Fornecedor[]>({
    queryKey: ['fornecedores', mostrarInativos],
    queryFn: () => api.get('/estoque/fornecedores', { params: { incluirInativos: mostrarInativos } }).then(r => r.data),
  })

  const termo = busca.trim().toLowerCase()
  const visiveis = fornecedores.filter(f =>
    !termo ||
    f.nome.toLowerCase().includes(termo) ||
    (f.cpfCnpj ?? '').replace(/\D/g, '').includes(termo.replace(/\D/g, '') || '\u0000') ||
    (f.contato ?? '').toLowerCase().includes(termo)
  )

  // O dropdown do Novo Produto e as listagens de produto leem estes dados.
  const atualizarListas = () => {
    qc.invalidateQueries({ queryKey: ['fornecedores'] })
    qc.invalidateQueries({ queryKey: ['fornecedores-opcoes'] })
    qc.invalidateQueries({ queryKey: ['produtos'] })
    qc.invalidateQueries({ queryKey: ['produtos-relatorio'] })
  }

  const salvar = useMutation({
    mutationFn: (f: FormState) => {
      const payload = {
        nome: f.nome.trim(),
        tipoPessoa: f.tipoPessoa,
        cpfCnpj: f.cpfCnpj.trim() || null,
        telefone: f.telefone.trim() || null,
        email: f.email.trim() || null,
        contato: f.contato.trim() || null,
        endereco: f.endereco.trim() || null,
      }
      return f.id ? api.put(`/estoque/fornecedores/${f.id}`, payload) : api.post('/estoque/fornecedores', payload)
    },
    onSuccess: () => {
      toast.success(form?.id ? 'Fornecedor atualizado!' : 'Fornecedor cadastrado!')
      atualizarListas()
      setForm(null)
    },
  })

  const inativar = useMutation({
    mutationFn: (id: number) => api.post(`/estoque/fornecedores/${id}/inativar`),
    onSuccess: () => {
      toast.success('Fornecedor inativado.')
      atualizarListas()
      setConfirmando(null)
    },
  })

  const reativar = useMutation({
    mutationFn: (id: number) => api.post(`/estoque/fornecedores/${id}/reativar`),
    onSuccess: () => {
      toast.success('Fornecedor reativado.')
      atualizarListas()
    },
  })

  const abrirNovo = () => { setTentouSalvar(false); setForm({ ...formVazio }) }
  const abrirEdicao = (f: Fornecedor) => {
    setTentouSalvar(false)
    setForm({
      id: f.id, nome: f.nome, tipoPessoa: f.tipoPessoa ?? 'PJ',
      cpfCnpj: f.cpfCnpj ?? '', telefone: f.telefone ?? '', email: f.email ?? '',
      contato: f.contato ?? '', endereco: f.endereco ?? '',
    })
  }
  const fechar = () => { setForm(null); setTentouSalvar(false) }

  const erros = form ? validarFornecedor(form) : null
  const novo = !!form && !form.id
  const mostrar = (msg?: string) => (tentouSalvar && msg ? msg : undefined)
  const campoErro = (msg?: string) => (tentouSalvar && msg ? '!border-danger-500' : '')

  function tentarSalvar(f: FormState) {
    setTentouSalvar(true)
    if (Object.values(validarFornecedor(f)).some(Boolean)) {
      toast.error('Preencha todos os campos obrigatórios.')
      return
    }
    salvar.mutate(f)
  }

  return (
    <div className="p-6">
      <PageHeader
        title="Fornecedores"
        subtitle="Cadastro de fornecedores — usado no cadastro de produtos"
        actions={podeCriar('FORNECEDORES') && (
          <Button variant="primary" onClick={abrirNovo}>Novo Fornecedor</Button>
        )}
      />

      <div className="flex items-center gap-3 mb-4">
        <div className="relative flex-1 max-w-sm">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            type="text" value={busca} onChange={e => setBusca(e.target.value)}
            placeholder="Buscar por nome, CNPJ/CPF ou contato..."
            className={`${baseInputClass} pl-9`}
          />
        </div>
        <label className="flex items-center gap-2 text-sm text-gray-600">
          <input type="checkbox" checked={mostrarInativos} onChange={e => setMostrarInativos(e.target.checked)} />
          Mostrar inativos
        </label>
      </div>

      <div className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
        <Table>
          <THead>
            <tr>
              <TH>Nome</TH>
              <TH>CNPJ / CPF</TH>
              <TH>Telefone</TH>
              <TH>Contato</TH>
              <TH>Situação</TH>
              {podeVerIdentificacao && <TH>Cadastrado por</TH>}
              <TH />
            </tr>
          </THead>
          <TBody>
            {visiveis.map(f => (
              <TR key={f.id} className={!f.ativo ? 'opacity-50' : ''}>
                <TD className="font-medium">{f.nome}</TD>
                <TD className="text-gray-600 tabular-nums">{f.cpfCnpj || '—'}</TD>
                <TD className="text-gray-600 tabular-nums">{f.telefone || '—'}</TD>
                <TD className="text-gray-500 text-xs">{f.contato || f.email || '—'}</TD>
                <TD>
                  <StatusBadge tone={f.ativo ? 'success' : 'neutral'}>{f.ativo ? 'Ativo' : 'Inativo'}</StatusBadge>
                </TD>
                {podeVerIdentificacao && <TD className="text-gray-500 text-xs">{f.criadoPorNome ?? '—'}</TD>}
                <TD>
                  <div className="flex items-center justify-end gap-2 whitespace-nowrap">
                    {podeEditar('FORNECEDORES') && (
                      <Button variant="secondary" size="sm" onClick={() => abrirEdicao(f)}>Editar</Button>
                    )}
                    {f.ativo && podeExcluir('FORNECEDORES') && (
                      <Button variant="outline-danger" size="sm" onClick={() => setConfirmando(f)}>Inativar</Button>
                    )}
                    {!f.ativo && podeEditar('FORNECEDORES') && (
                      <Button variant="success" size="sm" onClick={() => reativar.mutate(f.id)}>
                        <RotateCcw size={14} /> Reativar
                      </Button>
                    )}
                  </div>
                </TD>
              </TR>
            ))}
          </TBody>
        </Table>
        {!isLoading && visiveis.length === 0 && (
          <EmptyState>{fornecedores.length === 0 ? 'Nenhum fornecedor cadastrado.' : 'Nenhum fornecedor encontrado.'}</EmptyState>
        )}
      </div>

      {form && (
        <Modal
          title={form.id ? 'Editar Fornecedor' : 'Novo Fornecedor'}
          onClose={fechar}
          maxWidth="lg"
          footer={
            <>
              <Button variant="secondary" fullWidth onClick={fechar}>Cancelar</Button>
              <Button variant="primary" fullWidth loading={salvar.isPending} onClick={() => tentarSalvar(form)}>Salvar</Button>
            </>
          }
        >
          <Field label="Nome / Razão social *" error={mostrar(erros?.nome)}>
            <input
              type="text" value={form.nome} autoFocus
              onChange={e => setForm({ ...form, nome: e.target.value })}
              className={`${baseInputClass} ${campoErro(erros?.nome)}`}
            />
          </Field>

          <div className="grid grid-cols-2 gap-3">
            <Field label={novo ? 'Tipo de Pessoa *' : 'Tipo de Pessoa'}>
              <select
                value={form.tipoPessoa}
                onChange={e => {
                  const tipo = e.target.value as 'PF' | 'PJ'
                  setForm({ ...form, tipoPessoa: tipo, cpfCnpj: formatarCpfCnpj(form.cpfCnpj, tipo) })
                }}
                className={baseInputClass}
              >
                <option value="PJ">Pessoa Jurídica</option>
                <option value="PF">Pessoa Física</option>
              </select>
            </Field>
            <Field label={`${form.tipoPessoa === 'PF' ? 'CPF' : 'CNPJ'}${novo ? ' *' : ''}`} error={mostrar(erros?.cpfCnpj)}>
              <input
                type="text" inputMode="numeric" value={form.cpfCnpj}
                onChange={e => setForm({ ...form, cpfCnpj: formatarCpfCnpj(e.target.value, form.tipoPessoa) })}
                placeholder={form.tipoPessoa === 'PF' ? '000.000.000-00' : '00.000.000/0000-00'}
                className={`${baseInputClass} ${campoErro(erros?.cpfCnpj)}`}
              />
            </Field>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <Field label={novo ? 'Telefone *' : 'Telefone'} error={mostrar(erros?.telefone)}>
              <input
                type="text" inputMode="tel" value={form.telefone}
                onChange={e => setForm({ ...form, telefone: formatarTelefone(e.target.value) })}
                placeholder="(00) 00000-0000"
                className={`${baseInputClass} ${campoErro(erros?.telefone)}`}
              />
            </Field>
            <Field label="E-mail (opcional)" error={mostrar(erros?.email)}>
              <input
                type="email" value={form.email}
                onChange={e => setForm({ ...form, email: e.target.value })}
                className={`${baseInputClass} ${campoErro(erros?.email)}`}
              />
            </Field>
          </div>

          <Field label="Contato / Representante (opcional)">
            <input
              type="text" value={form.contato}
              onChange={e => setForm({ ...form, contato: e.target.value })}
              placeholder="Ex: João — vendedor"
              className={baseInputClass}
            />
          </Field>

          <Field label={novo ? 'Endereço *' : 'Endereço'} error={mostrar(erros?.endereco)}>
            <textarea
              value={form.endereco} rows={2}
              onChange={e => setForm({ ...form, endereco: e.target.value })}
              className={`${baseInputClass} ${campoErro(erros?.endereco)}`}
            />
          </Field>
        </Modal>
      )}

      {confirmando && (
        <ConfirmDialog
          title="Inativar fornecedor?"
          message={`"${confirmando.nome}" deixará de aparecer no dropdown do cadastro de produtos. Os produtos já vinculados a ele continuam como estão.`}
          confirmLabel="Inativar"
          loading={inativar.isPending}
          onConfirm={() => inativar.mutate(confirmando.id)}
          onCancel={() => setConfirmando(null)}
        />
      )}
    </div>
  )
}