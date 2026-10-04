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

  const abrirEdicao = (c: Cliente) => setForm({
    id: c.id,
    nome: c.nome,
    cpfCnpj: c.cpfCnpj ?? '',
    tipoPessoa: c.tipoPessoa,
    telefone: c.telefone ?? '',
    email: c.email ?? '',
    endereco: c.endereco ?? '',
    tipoCliente: c.tipoCliente,
    limiteCredito: c.limiteCredito ?? 0,
  })

  return (
    <div className="p-6">
      <PageHeader
        title="Clientes"
        subtitle="Cadastro de clientes"
        actions={
          <Button variant="primary" onClick={() => setForm({ ...formVazio })}>
            <Plus size={18} /> Novo Cliente
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
                  <div className="flex items-center justify-end gap-2">
                    <Button variant="ghost" size="sm" onClick={() => abrirEdicao(c)} title="Editar">
                      <Pencil size={15} />
                    </Button>
                    {c.ativo ? (
                      <Button variant="ghost" size="sm" className="hover:!text-danger-600" onClick={() => setConfirmando(c)} title="Inativar">
                        <Ban size={15} />
                      </Button>
                    ) : (
                      <Button variant="ghost" size="sm" className="hover:!text-success-600" onClick={() => reativar.mutate(c.id)} title="Reativar">
                        <RotateCcw size={15} />
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
          onClose={() => setForm(null)}
          maxWidth="lg"
          footer={
            <>
              <Button variant="secondary" fullWidth onClick={() => setForm(null)}>Cancelar</Button>
              <Button
                variant="primary"
                fullWidth
                loading={salvar.isPending}
                disabled={!form.nome.trim()}
                onClick={() => salvar.mutate(form)}
              >
                Salvar
              </Button>
            </>
          }
        >
          <Field label="Nome *">
            <input
              type="text" value={form.nome} autoFocus
              onChange={e => setForm({ ...form, nome: e.target.value })}
              className={baseInputClass}
            />
          </Field>

          <div className="grid grid-cols-2 gap-3">
            <Field label="Tipo de Pessoa">
              <select value={form.tipoPessoa} onChange={e => setForm({ ...form, tipoPessoa: e.target.value as 'PF' | 'PJ' })}
                className={baseInputClass}>
                <option value="PF">Pessoa Física</option>
                <option value="PJ">Pessoa Jurídica</option>
              </select>
            </Field>
            <Field label="CPF/CNPJ">
              <input type="text" value={form.cpfCnpj} onChange={e => setForm({ ...form, cpfCnpj: e.target.value })}
                className={baseInputClass} />
            </Field>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <Field label="Telefone">
              <input type="text" value={form.telefone} onChange={e => setForm({ ...form, telefone: e.target.value })}
                className={baseInputClass} />
            </Field>
            <Field label="E-mail">
              <input type="email" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })}
                className={baseInputClass} />
            </Field>
          </div>

          <Field label="Endereço">
            <textarea value={form.endereco} onChange={e => setForm({ ...form, endereco: e.target.value })}
              rows={2} className={baseInputClass} />
          </Field>

          <div className="grid grid-cols-2 gap-3">
            <Field
              label="Tipo de Cliente"
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
            <Field label="Limite de Crédito">
              <CurrencyInput value={form.limiteCredito} onChange={v => setForm({ ...form, limiteCredito: v })} />
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