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
import type { ConfiguracaoFiscal, Nfce } from '@/types/fiscal'

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
    { id: 1, produto: { codigoInterno: 'P000012', nome: 'PICANHA', unidadeMedida: 'KG' }, quantidade: 0.85, precoUnitario: 69.9, descontoItem: 0, totalItem: 59.42, tipoEntrada: 'BARCODE' },
    { id: 2, produto: { codigoInterno: 'P000345', nome: 'KURUPI MENTA E LIMÃO 500G', unidadeMedida: 'UN' }, quantidade: 1, precoUnitario: 24.98, descontoItem: 0, totalItem: 24.98, tipoEntrada: 'MANUAL' },
  ],
  pagamentos: [{ id: 1, formaPagamento: 'DINHEIRO', valor: 100 }],
} as unknown as Venda

/** Só desenha o lugar do QR Code na prévia: o de verdade vem da autorização da SEFAZ. */
const QR_EXEMPLO = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 29 29"><rect width="29" height="29" fill="#fff"/>'
  + '<path d="M1 1h7v7H1zM21 1h7v7h-7zM1 21h7v7H1z" fill="none" stroke="#000" stroke-width="1.6"/>'
  + '<path d="M3 3h3v3H3zM23 3h3v3h-3zM3 23h3v3H3zM11 2h2v2h-2zM14 5h2v2h-2zM10 10h3v2h-3zM15 11h3v3h-3zM20 12h2v2h-2zM11 15h2v4h-2zM17 17h4v2h-4zM23 15h3v3h-3zM12 22h3v2h-3zM18 22h2v4h-2zM23 21h4v2h-4zM22 25h3v2h-3z"/>'
  + '<text x="14.5" y="16" font-size="3" text-anchor="middle" font-family="Arial" fill="#000">EXEMPLO</text></svg>'

/** NFC-e de exemplo para a prévia do DANFE: usa o emitente da configuração fiscal, em "homologação" (sem valor). */
function nfceExemplo(fiscal?: ConfiguracaoFiscal): Nfce {
  return {
    id: 0, vendaId: VENDA_EXEMPLO.id, status: 'AUTORIZADA', mensagem: null, ambiente: 'HOMOLOGACAO',
    serie: fiscal?.nfceSerie ?? 1, numero: fiscal?.nfceProximoNumero ?? 1,
    chave: '41000000000000000000650000000000011000000000', protocolo: '000000000000000',
    emitidaEm: new Date().toISOString(), destinatarioDocumento: null,
    urlConsulta: 'http://www.fazenda.pr.gov.br/nfce/consulta', qrCodeSvg: QR_EXEMPLO,
    emitente: {
      razaoSocial: fiscal?.razaoSocial || 'RAZÃO SOCIAL DA EMPRESA', nomeFantasia: fiscal?.nomeFantasia ?? null,
      cnpj: fiscal?.cnpj ?? null, inscricaoEstadual: fiscal?.inscricaoEstadual ?? null,
      endereco: [fiscal?.logradouro, fiscal?.numeroEndereco, fiscal?.complemento, fiscal?.bairro].filter(Boolean).join(', '),
      municipio: [fiscal?.municipio, fiscal?.uf].filter(Boolean).join(' - '),
    },
    percentualTributos: fiscal?.percentualTributos ?? null,
  }
}

const PADRAO: ConfiguracaoComprovante = {
  nomeFantasia: '', cnpj: '', endereco: '', bairro: '', cidade: '', telefone: '', mensagemRodape: '',
  larguraPapelMm: 80, margemDireitaMm: 5, vias: 1, imprimirAutomatico: false,
  mostrarAvisoNaoFiscal: true, mostrarOperador: true, mostrarCliente: true,
}

export default function ComprovantePage() {
  const qc = useQueryClient()
  const { podeVer, podeEditar, nome } = usePermissao()
  const [form, setForm] = useState<ConfiguracaoComprovante>(PADRAO)
  const [modelo, setModelo] = useState<'cupom' | 'danfe'>('cupom')
  const { data: fiscal } = useQuery<ConfiguracaoFiscal>({
    queryKey: ['fiscal-configuracao'],
    queryFn: () => api.get('/fiscal/configuracao').then(r => r.data),
    enabled: podeVer('FISCAL'),
  })
  const nfce = modelo === 'danfe' ? nfceExemplo(fiscal) : null

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
      <div className="p-4 sm:p-6">
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
      imprimirComprovante(VENDA_EXEMPLO, nome ?? 'Operador', form, undefined, 1, nfce)
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Não foi possível imprimir.')
    }
  }

  return (
    <div className="p-4 sm:p-6">
      <PageHeader
        title="Comprovante de Venda"
        subtitle="O que sai impresso no PDV: o cupom não fiscal e o DANFE da NFC-e"
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
              <Field label="Margem de segurança à direita (mm)"
                     hint="Se os valores da direita saírem cortados, aumente (1 em 1 mm) e use Imprimir teste">
                <input type="number" min={0} max={20} step={1} value={form.margemDireitaMm} disabled={!editavel}
                       onChange={e => set('margemDireitaMm', Math.min(20, Math.max(0, Math.round(Number(e.target.value) || 0))))}
                       className={baseInputClass} />
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
          <div className="flex gap-1 mb-2" role="group" aria-label="Modelo da prévia">
            {([['cupom', 'Cupom não fiscal'], ['danfe', 'DANFE NFC-e']] as const).map(([v, rot]) => (
              <button key={v} type="button" aria-pressed={modelo === v} onClick={() => setModelo(v)}
                      className={`text-xs px-2.5 py-1 rounded-full border ${modelo === v
                        ? 'bg-primary-600 border-primary-600 text-white' : 'border-gray-300 text-gray-600 hover:bg-gray-50'}`}>
                {rot}
              </button>
            ))}
          </div>
          <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">Prévia (bobina {form.larguraPapelMm} mm, conteúdo {areaImpressaoMm(form)} mm)</p>
          <div className="bg-white border border-gray-200 shadow-md rounded-sm overflow-x-auto max-w-full" data-testid="previa-comprovante">
            <ReciboCupom venda={VENDA_EXEMPLO} operador={nome ?? 'Operador'} nfce={nfce}
                         config={{ ...form, nomeFantasia: form.nomeFantasia || 'NOME DA LOJA' }} />
          </div>
          <p className="text-xs text-gray-400 mt-2 max-w-[80mm]">
            {modelo === 'danfe'
              ? 'Exemplo com o emitente de Emissão de Notas. Chave, protocolo e QR Code de verdade vêm da autorização da SEFAZ. O cabeçalho do DANFE não usa os campos desta tela.'
              : 'Venda de exemplo. O cupom real usa os itens e pagamentos da venda.'}
          </p>
        </div>
      </div>
    </div>
  )
}
