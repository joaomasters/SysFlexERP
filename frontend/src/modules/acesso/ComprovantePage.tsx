import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Lock, Printer, Save } from 'lucide-react'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { PageHeader, Card, Button, Field, baseInputClass } from '@/shared/components/ui'
import ReciboCupom, { areaImpressaoMm } from '@/modules/pdv/components/ReciboCupom'
import { imprimirComprovante } from '@/modules/pdv/imprimirComprovante'
import type { ConfiguracaoComprovante } from '@/types/comprovante'
import type { Venda } from '@/types/venda'

/** Venda de exemplo só para a prévia e o "Imprimir teste" (não existe no banco). */
const VENDA_EXEMPLO = {
  id: 1234,
  numeroCupom: '1234',
  tipoVenda: 'PDV',
  status: 'FECHADA',
  dataVenda: new Date().toISOString(),
  subtotal: 84.4,
  desconto: 0,
  total: 84.4,
  troco: 15.6,
  cliente: undefined,
  itens: [
    { id: 1, produto: { nome: 'Picanha', unidadeMedida: 'KG' }, quantidade: 0.85, precoUnitario: 69.9, descontoItem: 0, totalItem: 59.42, tipoEntrada: 'BARCODE' },
    { id: 2, produto: { nome: 'Kurupi Menta e Limão 500g', unidadeMedida: 'UN' }, quantidade: 1, precoUnitario: 24.98, descontoItem: 0, totalItem: 24.98, tipoEntrada: 'MANUAL' },
  ],
  pagamentos: [{ id: 1, formaPagamento: 'DINHEIRO', valor: 100 }],
} as unknown as Venda

const PADRAO: ConfiguracaoComprovante = {
  nomeFantasia: '', cnpj: '', endereco: '', bairro: '', cidade: '', telefone: '', mensagemRodape: '',
  larguraPapelMm: 80, vias: 1, imprimirAutomatico: false,
  mostrarAvisoNaoFiscal: true, mostrarOperador: true, mostrarCliente: true,
}

export default function ComprovantePage() {
  const qc = useQueryClient()
  const { podeVer, podeEditar, nome } = usePermissao()
  const [form, setForm] = useState<ConfiguracaoComprovante>(PADRAO)

  const { data } = useQuery<ConfiguracaoComprovante>({
    queryKey: ['comprovante-config'],
    queryFn: () => api.get('/comprovante/configuracao').then(r => r.data),
    enabled: podeVer('COMPROVANTE'),
  })
  useEffect(() => { if (data) setForm({ ...PADRAO, ...data }) }, [data])

  const salvar = useMutation({
    mutationFn: () => api.put('/comprovante/configuracao', form),
    onSuccess: () => {
      toast.success('Comprovante salvo. Vale para os próximos cupons.')
      qc.invalidateQueries({ queryKey: ['comprovante-config'] })
    },
  })

  if (!podeVer('COMPROVANTE')) {
    return (
      <div className="p-6">
        <div className="bg-warning-50 border border-warning-100 rounded-xl p-5 max-w-lg">
          <p className="flex items-center gap-2 font-medium text-warning-700"><Lock size={16} /> Acesso restrito</p>
          <p className="text-sm text-warning-600 mt-1">Seu perfil não tem permissão para configurar o comprovante de venda.</p>
        </div>
      </div>
    )
  }

  const set = <K extends keyof ConfiguracaoComprovante>(campo: K, valor: ConfiguracaoComprovante[K]) =>
    setForm(f => ({ ...f, [campo]: valor }))
  const editavel = podeEditar('COMPROVANTE')
  const texto = (campo: keyof ConfiguracaoComprovante, label: string, placeholder = '', max = 150) => (
    <Field label={label}>
      <input value={(form[campo] as string) ?? ''} maxLength={max} disabled={!editavel} placeholder={placeholder}
             onChange={e => set(campo, e.target.value as never)} className={baseInputClass} />
    </Field>
  )
  const opcao = (campo: keyof ConfiguracaoComprovante, label: string) => (
    <label className="flex items-center gap-2 text-sm text-gray-700">
      <input type="checkbox" checked={Boolean(form[campo])} disabled={!editavel}
             onChange={e => set(campo, e.target.checked as never)} />
      {label}
    </label>
  )

  function imprimirTeste() {
    try {
      imprimirComprovante(VENDA_EXEMPLO, nome ?? 'Operador', form, undefined, 1)
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Não foi possível imprimir.')
    }
  }

  return (
    <div className="p-6">
      <PageHeader
        title="Comprovante de Venda"
        subtitle="O que sai no cupom impresso no PDV (cupom não fiscal)"
        icon={<Printer size={22} className="text-gray-400" />}
        actions={
          <>
            <Button variant="secondary" onClick={imprimirTeste} disabled={!form.nomeFantasia.trim()}>
              <Printer size={15} /> Imprimir teste
            </Button>
            {editavel && (
              <Button variant="primary" onClick={() => salvar.mutate()} loading={salvar.isPending}
                      disabled={!form.nomeFantasia.trim()}>
                <Save size={15} /> Salvar
              </Button>
            )}
          </>
        }
      />

      <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_auto] gap-6 items-start">
        <div className="space-y-5 min-w-0">
          <Card padding="md" className="space-y-4">
            <h2 className="text-sm font-semibold text-gray-700">Cabeçalho</h2>
            {texto('nomeFantasia', 'Nome que sai no cupom *', 'Ex.: Açougue e Mercearia do Zé', 120)}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {texto('cnpj', 'CNPJ', '00.000.000/0000-00', 20)}
              {texto('telefone', 'Telefone', '(43) 99999-9999', 30)}
            </div>
            {texto('endereco', 'Endereço', 'Rua, número', 150)}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {texto('bairro', 'Bairro', '', 80)}
              {texto('cidade', 'Cidade', 'Ex.: Londrina - PR', 80)}
            </div>
          </Card>

          <Card padding="md" className="space-y-4">
            <h2 className="text-sm font-semibold text-gray-700">Rodapé</h2>
            <Field label="Mensagem no fim do cupom" hint="Ex.: Obrigado pela preferência! Trocas em até 7 dias com este cupom.">
              <textarea rows={2} maxLength={300} disabled={!editavel} value={form.mensagemRodape ?? ''}
                        onChange={e => set('mensagemRodape', e.target.value)} className={baseInputClass} />
            </Field>
          </Card>

          <Card padding="md" className="space-y-4">
            <h2 className="text-sm font-semibold text-gray-700">Impressão</h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Field label="Largura da bobina" hint="Knup KP-IM602 e a maioria das térmicas de balcão: 80 mm">
                <select value={form.larguraPapelMm} disabled={!editavel}
                        onChange={e => set('larguraPapelMm', Number(e.target.value) as 58 | 80)} className={baseInputClass}>
                  <option value={80}>80 mm</option>
                  <option value={58}>58 mm</option>
                </select>
              </Field>
              <Field label="Vias por venda">
                <select value={form.vias} disabled={!editavel}
                        onChange={e => set('vias', Number(e.target.value))} className={baseInputClass}>
                  <option value={1}>1 via</option>
                  <option value={2}>2 vias</option>
                  <option value={3}>3 vias</option>
                </select>
              </Field>
            </div>
            <div className="space-y-2">
              <label className="flex items-center gap-2 text-sm text-gray-700">
                <input type="radio" name="modo" checked={!form.imprimirAutomatico} disabled={!editavel}
                       onChange={() => set('imprimirAutomatico', false)} />
                Perguntar ao caixa se imprime, ao finalizar cada venda
              </label>
              <label className="flex items-center gap-2 text-sm text-gray-700">
                <input type="radio" name="modo" checked={form.imprimirAutomatico} disabled={!editavel}
                       onChange={() => set('imprimirAutomatico', true)} />
                Imprimir sempre, automaticamente
              </label>
            </div>
            <div className="border-t pt-3 space-y-2">
              {opcao('mostrarAvisoNaoFiscal', 'Mostrar "Cupom para simples conferência / Não é documento fiscal"')}
              {opcao('mostrarOperador', 'Mostrar o nome do operador')}
              {opcao('mostrarCliente', 'Mostrar o cliente da comanda')}
            </div>
          </Card>
        </div>

        <div className="lg:sticky lg:top-4">
          <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">Prévia (bobina {form.larguraPapelMm} mm, impressão {areaImpressaoMm(form.larguraPapelMm)} mm)</p>
          <div className="bg-white border border-gray-200 shadow-md rounded-sm overflow-x-auto max-w-full">
            <ReciboCupom venda={VENDA_EXEMPLO} operador={nome ?? 'Operador'} config={{ ...form, nomeFantasia: form.nomeFantasia || 'NOME DA LOJA' }} />
          </div>
          <p className="text-xs text-gray-400 mt-2 max-w-[80mm]">Venda de exemplo. O cupom real usa os itens e pagamentos da venda.</p>
        </div>
      </div>
    </div>
  )
}
