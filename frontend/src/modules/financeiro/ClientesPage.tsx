import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Search, Pencil, Ban, RotateCcw } from 'lucide-react'
import { api } from '@/shared/api/axios'
import toast from 'react-hot-toast'
import type { Cliente } from '@/types/venda'
import { formatBRL } from '@/shared/utils/mask'
import {
  PageHeader, Modal, Button, StatusBadge, CurrencyInput, Field, baseInputClass, ConfirmDialog,
  Table, THead, TH, TBody, TR, TD, EmptyState,
} from '@/shared/components/ui'
import type { BadgeTone } from '@/shared/components/ui'
import { usePermissao } from '@/shared/hooks/usePermissao'

const brl = (v?: number) => formatBRL(v ?? 0)

const tipoClienteLabel: Record<Cliente['tipoCliente'], string> = {
  VAREJO: 'Varejo',
  ATACADO: 'Atacado',
  RESTAURANTE: 'Restaurante',
  CONVENIADO: 'Conveniado',
  FIADO: 'Fiado',
}

// Tipo de cliente é uma categoria (rótulo), não um status — todos usam o tom
// "purple", exceto Varejo (padrão/neutro, é o tipo mais comum, não precisa se destacar).
const tipoClienteTom: Record<Cliente['tipoCliente'], BadgeTone> = {
  VAREJO: 'neutral',
  ATACADO: 'purple',
  RESTAURANTE: 'purple',
  CONVENIADO: 'purple',
  FIADO: 'purple',
}

type FormState = {
  id?: number
  nome: string
  cpfCnpj: string
  tipoPessoa: 'PF' | 'PJ'
  telefone: string
  email: string
  endereco: string
  tipoCliente: Cliente['tipoCliente']
  limiteCredito: number
}

// ── Máscaras e validação do cadastro ─────────────────────────────────
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

/** Erros do formulário. Cadastro novo exige tudo, exceto e-mail; a edição de
 *  cliente já existente só exige o nome (clientes antigos podem não ter
 *  telefone/endereço e não devem ficar impedidos de ser editados). */
function validarCliente(f: FormState) {
  const novo = !f.id
  const nDoc = f.tipoPessoa === 'PF' ? 11 : 14
  const tel = soDigitos(f.telefone).length
  return {
    nome:          f.nome.trim() ? '' : 'Informe o nome.',
    cpfCnpj:       !novo || soDigitos(f.cpfCnpj).length === nDoc
                     ? '' : `${f.tipoPessoa === 'PF' ? 'CPF' : 'CNPJ'} deve ter ${nDoc} dígitos.`,
    telefone:      !novo || tel === 10 || tel === 11 ? '' : 'Informe o telefone com DDD.',
    email:         !f.email.trim() || /^\S+@\S+\.\S+$/.test(f.email.trim()) ? '' : 'E-mail inválido.',
    endereco:      !novo || f.endereco.trim() ? '' : 'Informe o endereço.',
    limiteCredito: !novo || f.limiteCredito > 0 ? '' : 'Informe o limite de crédito.',
  }
}

const formVazio: FormState = {
  nome: '', cpfCnpj: '', tipoPessoa: 'PF', telefone: '', email: '',
  endereco: '', tipoCliente: 'VAREJO', limiteCredito: 0,
}

export default function ClientesPage() {
  const qc = useQueryClient()
  const { podeVerIdentificacao } = usePermissao()
  const [busca, setBusca] = useState('')
  const [form, setForm] = useState<FormState | null>(null)
  const [mostrarInativos, setMostrarInativos] = useState(false)
  const [confirmando, setConfirmando] = useState<Cliente | null>(null)
  // Só mostra os erros depois da primeira tentativa de salvar.
  const [tentouSalvar, setTentouSalvar] = useState(false)

  const { data: clientes = [], isLoading } = useQuery<Cliente[]>({
    queryKey: ['clientes', busca],
    queryFn: () => api.get('/clientes', { params: { nome: busca || undefined } }).then(r => r.data),
  })

  const visiveis = clientes.filter(c => mostrarInativos || c.ativo)

  const salvar = useMutation({
    mutationFn: (f: FormState) => {
      const payload = {
        nome: f.nome.trim(),
        cpfCnpj: f.cpfCnpj.trim() || null,
        tipoPessoa: f.tipoPessoa,
        telefone: f.telefone.trim() || null,
        email: f.email.trim() || null,
        endereco: f.endereco.trim() || null,
        tipoCliente: f.tipoCliente,
        limiteCredito: f.limiteCredito || 0,
      }
      return f.id
        ? api.put(`/clientes/${f.id}`, payload)
        : api.post('/clientes', payload)
    },
    onSuccess: () => {
      toast.success(form?.id ? 'Cliente atualizado!' : 'Cliente cadastrado!')
      qc.invalidateQueries({ queryKey: ['clientes'] })
      setForm(null)
    },
  })

  const inativar = useMutation({
    mutationFn: (id: number) => api.post(`/clientes/${id}/inativar`),
    onSuccess: () => {
      toast.success('Cliente inativado.')
      qc.invalidateQueries({ queryKey: ['clientes'] })
      setConfirmando(null)
    },
  })

  const reativar = useMutation({
    mutationFn: (id: number) => api.post(`/clientes/${id}/reativar`),
    onSuccess: () => {
      toast.success('Cliente reativado.')
      qc.invalidateQueries({ queryKey: ['clientes'] })
    },
  })

  const abrirEdicao = (c: Cliente) => { setTentouSalvar(false); setForm({
    id: c.id,
    nome: c.nome,
    cpfCnpj: c.cpfCnpj ?? '',
    tipoPessoa: c.tipoPessoa,
    telefone: c.telefone ?? '',
    email: c.email ?? '',
    endereco: c.endereco ?? '',
    tipoCliente: c.tipoCliente,
    limiteCredito: c.limiteCredito ?? 0,
  }) }

  const erros = form ? validarCliente(form) : null
  const novo = !!form && !form.id
  const mostrar = (msg?: string) => (tentouSalvar && msg ? msg : undefined)
  const campoErro = (msg?: string) => (tentouSalvar && msg ? '!border-danger-500' : '')

  function tentarSalvar(f: FormState) {
    setTentouSalvar(true)
    if (Object.values(validarCliente(f)).some(Boolean)) {
      toast.error('Preencha todos os campos obrigatórios.')
      return
    }
    salvar.mutate(f)
  }

  return (
    <div className="p-6">
      <PageHeader
        title="Clientes"
        subtitle="Cadastro de clientes"
        actions={
          <Button variant="primary" onClick={() => { setTentouSalvar(false); setForm({ ...formVazio }) }}>
            Novo Cliente
          </Button>
        }
      />

      {/* Filtros */}
      <div className="flex items-center gap-3 mb-4">
        <div className="relative flex-1 max-w-sm">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            type="text" value={busca} onChange={e => setBusca(e.target.value)}
            placeholder="Buscar por nome..."
            className={`${baseInputClass} pl-9`}
          />
        </div>
        <label className="flex items-center gap-2 text-sm text-gray-600">
          <input type="checkbox" checked={mostrarInativos} onChange={e => setMostrarInativos(e.target.checked)} />
          Mostrar inativos
        </label>
      </div>

      {/* Lista */}
      <div className="bg-white rounded-xl shadow border border-gray-100 overflow-hidden">
        <Table>
          <THead>
            <tr>
              <TH>Nome</TH>
              <TH>Tipo</TH>
              <TH>Contato</TH>
              <TH align="right">Limite Crédito</TH>
              <TH align="right">Saldo Fiado</TH>
              {podeVerIdentificacao && <TH>Cadastrado por</TH>}
              <TH />
            </tr>
          </THead>
          <TBody>
            {visiveis.map(c => (
              <TR key={c.id} className={!c.ativo ? 'opacity-50' : ''}>
                <TD className="font-medium">
                  {c.nome}
                  {!c.ativo && <span className="ml-2 text-xs text-gray-400">(inativo)</span>}
                </TD>
                <TD>
                  <StatusBadge tone={tipoClienteTom[c.tipoCliente]}>{tipoClienteLabel[c.tipoCliente]}</StatusBadge>
                </TD>
                <TD className="text-gray-500 text-xs">
                  {c.telefone || c.email || '—'}
                </TD>
                <TD align="right" className="tabular-nums">{brl(c.limiteCredito)}</TD>
                <TD align="right" className={`tabular-nums font-medium ${(c.saldoFiadoAtual ?? 0) > 0 ? 'text-danger-600' : 'text-gray-400'}`}>
                  {brl(c.saldoFiadoAtual)}
                </TD>
                {podeVerIdentificacao && (
                  <TD className="text-gray-500 text-xs">{c.criadoPorNome ?? '—'}</TD>
                )}
                <TD>
                  <div className="flex items-center justify-end gap-2 whitespace-nowrap">
                    <Button variant="secondary" size="sm" onClick={() => abrirEdicao(c)}>
                      Editar 
                    </Button>
                    {c.ativo ? (
                      <Button variant="outline-danger" size="sm" onClick={() => setConfirmando(c)}>
                        Inativar
                      </Button>
                    ) : (
                      <Button variant="success" size="sm" onClick={() => reativar.mutate(c.id)}>
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
          <EmptyState>Nenhum cliente encontrado.</EmptyState>
        )}
      </div>

      {/* Modal de cadastro/edição */}
      {form && (
        <Modal
          title={form.id ? 'Editar Cliente' : 'Novo Cliente'}
          onClose={() => { setForm(null); setTentouSalvar(false) }}
          maxWidth="lg"
          footer={
            <>
              <Button variant="secondary" fullWidth onClick={() => { setForm(null); setTentouSalvar(false) }}>Cancelar</Button>
              <Button
                variant="primary"
                fullWidth
                loading={salvar.isPending}
                onClick={() => tentarSalvar(form)}
              >
                Salvar
              </Button>
            </>
          }
        >
          <Field label="Nome *" error={mostrar(erros?.nome)}>
            <input
              type="text" value={form.nome} autoFocus
              onChange={e => setForm({ ...form, nome: e.target.value })}
              className={`${baseInputClass} ${campoErro(erros?.nome)}`}
            />
          </Field>

          <div className="grid grid-cols-2 gap-3">
            <Field label={novo ? 'Tipo de Pessoa *' : 'Tipo de Pessoa'}>
              <select value={form.tipoPessoa}
                onChange={e => {
                  const tipo = e.target.value as 'PF' | 'PJ'
                  setForm({ ...form, tipoPessoa: tipo, cpfCnpj: formatarCpfCnpj(form.cpfCnpj, tipo) })
                }}
                className={baseInputClass}>
                <option value="PF">Pessoa Física</option>
                <option value="PJ">Pessoa Jurídica</option>
              </select>
            </Field>
            <Field label={`${form.tipoPessoa === 'PF' ? 'CPF' : 'CNPJ'}${novo ? ' *' : ''}`} error={mostrar(erros?.cpfCnpj)}>
              <input type="text" inputMode="numeric" value={form.cpfCnpj}
                onChange={e => setForm({ ...form, cpfCnpj: formatarCpfCnpj(e.target.value, form.tipoPessoa) })}
                placeholder={form.tipoPessoa === 'PF' ? '000.000.000-00' : '00.000.000/0000-00'}
                className={`${baseInputClass} ${campoErro(erros?.cpfCnpj)}`} />
            </Field>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <Field label={novo ? 'Telefone *' : 'Telefone'} error={mostrar(erros?.telefone)}>
              <input type="text" inputMode="tel" value={form.telefone}
                onChange={e => setForm({ ...form, telefone: formatarTelefone(e.target.value) })}
                placeholder="(00) 00000-0000"
                className={`${baseInputClass} ${campoErro(erros?.telefone)}`} />
            </Field>
            <Field label="E-mail (opcional)" error={mostrar(erros?.email)}>
              <input type="email" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })}
                className={`${baseInputClass} ${campoErro(erros?.email)}`} />
            </Field>
          </div>

          <Field label={novo ? 'Endereço *' : 'Endereço'} error={mostrar(erros?.endereco)}>
            <textarea value={form.endereco} onChange={e => setForm({ ...form, endereco: e.target.value })}
              rows={2} className={`${baseInputClass} ${campoErro(erros?.endereco)}`} />
          </Field>

          <div className="grid grid-cols-2 gap-3">
            <Field
              label={novo ? 'Tipo de Cliente *' : 'Tipo de Cliente'}
              hint="Só clientes Atacado/Restaurante/Conveniado/Fiado aparecem pra Faturamento e compra a prazo."
            >
              <select value={form.tipoCliente} onChange={e => setForm({ ...form, tipoCliente: e.target.value as Cliente['tipoCliente'] })}
                className={baseInputClass}>
                <option value="VAREJO">Varejo</option>
                <option value="ATACADO">Atacado</option>
                <option value="RESTAURANTE">Restaurante</option>
                <option value="CONVENIADO">Conveniado</option>
                <option value="FIADO">Fiado</option>
              </select>
            </Field>
            <Field label={novo ? 'Limite de Crédito *' : 'Limite de Crédito'} error={mostrar(erros?.limiteCredito)}>
              <CurrencyInput value={form.limiteCredito} onChange={v => setForm({ ...form, limiteCredito: v })}
                className={tentouSalvar && erros?.limiteCredito ? '[&_input]:!border-danger-500' : ''} />
            </Field>
          </div>
        </Modal>
      )}

      {/* Confirmação de inativação */}
      {confirmando && (
        <ConfirmDialog
          title="Inativar cliente?"
          message={
            (confirmando.saldoFiadoAtual ?? 0) > 0
              ? `"${confirmando.nome}" ainda tem ${brl(confirmando.saldoFiadoAtual)} de saldo fiado em aberto. Ele deixará de aparecer nas buscas de venda mesmo assim.`
              : `"${confirmando.nome}" deixará de aparecer nas buscas de venda e listagens ativas.`
          }
          confirmLabel="Inativar"
          loading={inativar.isPending}
          onConfirm={() => inativar.mutate(confirmando.id)}
          onCancel={() => setConfirmando(null)}
        />
      )}
    </div>
  )
}