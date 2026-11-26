import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, Download, FileKey, Lock, Save, Trash2 } from 'lucide-react'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import { usePermissao } from '@/shared/hooks/usePermissao'
import { formatBRL } from '@/shared/utils/mask'
import {
  PageHeader, Card, Button, Field, FilterTabs, StatusBadge, ConfirmDialog, baseInputClass,
  Table, THead, TH, TBody, TR, TD, EmptyState, LoadingState, Paginacao,
} from '@/shared/components/ui'
import { usePaginacao } from '@/shared/hooks/usePaginacao'
import type { BadgeTone } from '@/shared/components/ui'
import {
  REGIMES_TRIBUTARIOS, ROTULO_STATUS_DOCUMENTO, type ConfiguracaoFiscal, type DocumentoFiscal, type StatusDocumentoFiscal,
} from '@/types/fiscal'
import SaneamentoCadastral from './components/SaneamentoCadastral'
import VigenciasRegime from './components/VigenciasRegime'
import NumeracaoInutilizar from './components/NumeracaoInutilizar'
import FaturamentoMes from './components/FaturamentoMes'

/*
 Emissão de NFC-e (modelo 65) e NF-e (modelo 55), Paraná primeiro.
 O cupom não fiscal do PDV continua sendo o padrão: cada empresa liga a emissão quando a
 configuração estiver completa (o backend recusa ligar com pendência — ver PendenciasFiscais).
*/

type Aba = 'configuracao' | 'documentos' | 'faturamento' | 'saneamento'

// Campos editáveis: o resto (pendências, certificado...) vem pronto do backend
type Form = Pick<ConfiguracaoFiscal,
  'emitirNfce' | 'emitirNfe' | 'ambiente' | 'provedor' | 'razaoSocial' | 'nomeFantasia' | 'cnpj'
  | 'inscricaoEstadual' | 'regimeTributario' | 'logradouro' | 'numeroEndereco' | 'complemento' | 'bairro'
  | 'codigoMunicipio' | 'municipio' | 'cep' | 'telefone' | 'nfceSerie' | 'nfceProximoNumero' | 'nfeSerie'
  | 'nfeProximoNumero' | 'nfceModo' | 'cscId'> & {
    cscToken: string; limparCsc: boolean
    tokenHomologacao: string; tokenProducao: string; limparTokens: boolean; pisCofinsCst: string
  }

/** Provedores que guardam certificado e CSC do lado deles: aqui só vão o token e o CST de PIS/COFINS. */
const PROVEDORES_API = ['FOCUS_NFE']

const TOM_STATUS: Record<StatusDocumentoFiscal, BadgeTone> = {
  AUTORIZADA: 'success', PENDENTE: 'info', CONTINGENCIA: 'warning', REJEITADA: 'danger', DENEGADA: 'danger',
  ERRO: 'danger', CANCELADA: 'neutral', INUTILIZADA: 'neutral', DESCONHECIDO: 'warning',
}

function paraForm(c: ConfiguracaoFiscal): Form {
  return {
    emitirNfce: c.emitirNfce, emitirNfe: c.emitirNfe, ambiente: c.ambiente, provedor: c.provedor,
    razaoSocial: c.razaoSocial, nomeFantasia: c.nomeFantasia, cnpj: c.cnpj, inscricaoEstadual: c.inscricaoEstadual,
    regimeTributario: c.regimeTributario, logradouro: c.logradouro, numeroEndereco: c.numeroEndereco,
    complemento: c.complemento, bairro: c.bairro, codigoMunicipio: c.codigoMunicipio, municipio: c.municipio,
    cep: c.cep, telefone: c.telefone, nfceSerie: c.nfceSerie, nfceProximoNumero: c.nfceProximoNumero,
    nfeSerie: c.nfeSerie, nfeProximoNumero: c.nfeProximoNumero, nfceModo: c.nfceModo ?? 'SOB_DEMANDA',
    cscId: c.cscId, cscToken: '', limparCsc: false,
    tokenHomologacao: '', tokenProducao: '', limparTokens: false, pisCofinsCst: c.pisCofinsCst ?? '',
  }
}

export default function EmissaoFiscalPage() {
  const { podeVer } = usePermissao()
  const [aba, setAba] = useState<Aba>('configuracao')

  if (!podeVer('FISCAL')) {
    return (
      <div className="p-4 sm:p-6">
        <div className="bg-warning-50 border border-warning-100 rounded-xl p-5 max-w-lg">
          <p className="flex items-center gap-2 font-medium text-warning-700"><Lock size={16} /> Acesso restrito</p>
          <p className="text-sm text-warning-600 mt-1">Seu perfil não tem permissão para a emissão de notas fiscais.</p>
        </div>
      </div>
    )
  }

  return (
    <div className="p-4 sm:p-6 space-y-5">
      <PageHeader title="Emissão de Notas" subtitle="NFC-e (modelo 65) e NF-e (modelo 55) com a SEFAZ do Paraná" />
      <FilterTabs<Aba>
        options={[
          { value: 'configuracao', label: 'Configuração' },
          { value: 'documentos', label: 'Documentos emitidos' },
          { value: 'faturamento', label: 'Faturamento do mês' },
          { value: 'saneamento', label: 'Saneamento cadastral' },
        ]}
        value={aba}
        onChange={setAba}
      />
      {aba === 'configuracao' && <><Configuracao /><VigenciasRegime /></>}
      {aba === 'documentos' && <><Documentos /><NumeracaoInutilizar /></>}
      {aba === 'faturamento' && <FaturamentoMes />}
      {aba === 'saneamento' && <SaneamentoCadastral />}
    </div>
  )
}

function Configuracao() {
  const qc = useQueryClient()
  const { podeEditar, podeExcluir } = usePermissao()
  const editavel = podeEditar('FISCAL')
  const [form, setForm] = useState<Form | null>(null)
  const [senhaCert, setSenhaCert] = useState('')
  const [removendoCert, setRemovendoCert] = useState(false)
  const arquivoRef = useRef<HTMLInputElement>(null)

  const { data: config } = useQuery<ConfiguracaoFiscal>({
    queryKey: ['fiscal-configuracao'],
    queryFn: () => api.get('/fiscal/configuracao').then(r => r.data),
  })
  useEffect(() => { if (config) setForm(paraForm(config)) }, [config])

  const atualizar = (c: ConfiguracaoFiscal) => qc.setQueryData(['fiscal-configuracao'], c)

  const salvar = useMutation({
    mutationFn: () => api.put<ConfiguracaoFiscal>('/fiscal/configuracao', form).then(r => r.data),
    onSuccess: c => { atualizar(c); toast.success('Configuração fiscal salva.') },
  })

  const enviarCert = useMutation({
    mutationFn: async (arquivo: File) => {
      const base64 = await new Promise<string>((ok, falha) => {
        const leitor = new FileReader()
        leitor.onload = () => ok(String(leitor.result))
        leitor.onerror = () => falha(new Error('Não foi possível ler o arquivo.'))
        leitor.readAsDataURL(arquivo)
      })
      return api.post<ConfiguracaoFiscal>('/fiscal/configuracao/certificado', { arquivoBase64: base64, senha: senhaCert })
        .then(r => r.data)
    },
    onSuccess: c => { atualizar(c); setSenhaCert(''); toast.success('Certificado digital salvo.') },
    onSettled: () => { if (arquivoRef.current) arquivoRef.current.value = '' },
  })

  const removerCert = useMutation({
    mutationFn: () => api.delete<ConfiguracaoFiscal>('/fiscal/configuracao/certificado').then(r => r.data),
    onSuccess: c => { atualizar(c); setRemovendoCert(false); toast.success('Certificado removido.') },
  })

  if (!config || !form) return <LoadingState />

  const set = <K extends keyof Form>(campo: K, valor: Form[K]) => setForm(f => (f ? { ...f, [campo]: valor } : f))
  const texto = (campo: keyof Form, label: string, opcoes: { placeholder?: string; max?: number; hint?: string; className?: string } = {}) => (
    <Field label={label} hint={opcoes.hint} className={opcoes.className} htmlFor={`fiscal-${campo}`}>
      <input id={`fiscal-${campo}`} value={(form[campo] as string | null) ?? ''} maxLength={opcoes.max ?? 150} disabled={!editavel}
             placeholder={opcoes.placeholder} onChange={e => set(campo, e.target.value as never)} className={baseInputClass} />
    </Field>
  )
  const numero = (campo: keyof Form, label: string, max: number) => (
    <Field label={label} htmlFor={`fiscal-${campo}`}>
      <input id={`fiscal-${campo}`} type="number" min={campo.includes('Serie') ? 0 : 1} max={max} value={form[campo] as number} disabled={!editavel}
             onChange={e => set(campo, Number(e.target.value) as never)} className={`${baseInputClass} text-right tabular-nums`} />
    </Field>
  )

  const certVencido = config.certificadoValidade ? new Date(config.certificadoValidade) < new Date() : false

  return (
    <div className="space-y-5">
      {/* Situação: o que o PDV faz hoje e o que falta para ligar cada modelo */}
      <Card padding="md" className="space-y-4">
        <div>
          <h2 className="text-sm font-semibold text-gray-800">Situação da emissão</h2>
          <p className="text-sm text-gray-600 mt-1">
            {form.emitirNfce
              ? 'O PDV emite NFC-e nas vendas ao consumidor.'
              : 'O PDV continua no cupom não fiscal. Ligue a NFC-e quando a configuração estiver completa.'}
          </p>
        </div>
        <div className="grid md:grid-cols-2 gap-4">
          {([
            ['emitirNfce', 'NFC-e (modelo 65)', 'Venda ao consumidor, no PDV', config.pendenciasNfce],
            ['emitirNfe', 'NF-e (modelo 55)', 'Venda para empresas, devoluções e transferências', config.pendenciasNfe],
          ] as const).map(([campo, titulo, descricao, pendencias]) => (
            <div key={campo} className="border border-gray-200 rounded-lg p-4 space-y-3">
              <label className="flex items-start gap-3">
                <input type="checkbox" className="mt-1" checked={form[campo]} aria-label={`Emitir ${titulo}`}
                       disabled={!editavel || (pendencias.length > 0 && !form[campo])}
                       onChange={e => set(campo, e.target.checked)} />
                <span>
                  <span className="block text-sm font-medium text-gray-800">Emitir {titulo}</span>
                  <span className="block text-xs text-gray-500">{descricao}</span>
                </span>
              </label>
              {pendencias.length === 0 ? (
                <p className="flex items-center gap-1.5 text-xs text-success-700"><CheckCircle2 size={14} /> Pronta para emitir</p>
              ) : (
                <ul className="space-y-1">
                  {pendencias.map(p => (
                    <li key={p} className="flex items-start gap-1.5 text-xs text-warning-700">
                      <AlertTriangle size={13} className="shrink-0 mt-0.5" /> {p}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          ))}
        </div>
        {config.produtosSemNcm > 0 && (
          <p className="text-xs text-warning-700 bg-warning-50 rounded-lg px-3 py-2">
            {config.produtosSemNcm} produto(s) ativo(s) sem NCM. Eles não poderão sair em nota: complete a aba
            Informações Fiscais no cadastro de cada um.
          </p>
        )}
        <div className="grid md:grid-cols-3 gap-3">
          <Field label="Ambiente" hint="Teste em homologação antes de emitir notas com valor fiscal." htmlFor="fiscal-ambiente">
            <select id="fiscal-ambiente" value={form.ambiente} disabled={!editavel} onChange={e => set('ambiente', e.target.value as Form['ambiente'])}
                    className={baseInputClass}>
              <option value="HOMOLOGACAO">Homologação (testes, sem valor fiscal)</option>
              <option value="PRODUCAO">Produção</option>
            </select>
          </Field>
          <Field label="Comunicação com a SEFAZ" hint={config.provedores.length === 0 ? 'Nenhum provedor disponível ainda.' : undefined}
                 htmlFor="fiscal-provedor">
            <select id="fiscal-provedor" value={form.provedor} disabled={!editavel} onChange={e => set('provedor', e.target.value)} className={baseInputClass}>
              <option value="NENHUM">Nenhum</option>
              {config.provedores.map(p => <option key={p.codigo} value={p.codigo}>{p.rotulo}</option>)}
            </select>
          </Field>
          <Field label="Estado (UF)">
            <input value="PR — Paraná" disabled className={baseInputClass} />
          </Field>
          <Field label="Quando emitir NFC-e" htmlFor="fiscal-nfce-modo"
                 hint="No PDV, ao finalizar, o caixa pergunta &quot;CPF na nota?&quot;.">
            <select id="fiscal-nfce-modo" value={form.nfceModo} disabled={!editavel}
                    onChange={e => set('nfceModo', e.target.value as Form['nfceModo'])} className={baseInputClass}>
              <option value="SOB_DEMANDA">Só quando o cliente pedir (as outras vendas saem no cupom não fiscal)</option>
              <option value="TODAS">Em todas as vendas (com ou sem CPF)</option>
            </select>
          </Field>
        </div>
      </Card>

      {/* Emitente */}
      <Card padding="md" className="space-y-4">
        <h2 className="text-sm font-semibold text-gray-800">Emitente</h2>
        <div className="grid md:grid-cols-2 gap-3">
          {texto('razaoSocial', 'Razão social *')}
          {texto('nomeFantasia', 'Nome fantasia', { max: 120 })}
        </div>
        <div className="grid md:grid-cols-3 gap-3">
          {texto('cnpj', 'CNPJ *', { placeholder: '00.000.000/0000-00', max: 20, hint: 'Aceita o CNPJ com letras (a partir de jul/2026).' })}
          {texto('inscricaoEstadual', 'Inscrição Estadual (PR) *', { placeholder: '000.00000-00', max: 20 })}
          <Field label="Regime tributário *" htmlFor="fiscal-regime" hint="Mudar aqui abre uma vigência a partir de hoje (histórico abaixo).">
            <select id="fiscal-regime" value={form.regimeTributario} disabled={!editavel}
                    onChange={e => set('regimeTributario', Number(e.target.value))} className={baseInputClass}>
              {REGIMES_TRIBUTARIOS.map(r => <option key={r.value} value={r.value}>{r.label}</option>)}
            </select>
          </Field>
        </div>
        <div className="grid md:grid-cols-6 gap-3">
          {texto('logradouro', 'Logradouro *', { max: 120, className: 'md:col-span-3' })}
          {texto('numeroEndereco', 'Número *', { max: 10 })}
          {texto('complemento', 'Complemento', { max: 60, className: 'md:col-span-2' })}
          {texto('bairro', 'Bairro *', { max: 60, className: 'md:col-span-2' })}
          {texto('municipio', 'Município *', { max: 60, className: 'md:col-span-2' })}
          {texto('codigoMunicipio', 'Código IBGE *', { placeholder: '41xxxxx', max: 7, hint: '7 dígitos.' })}
          {texto('cep', 'CEP *', { placeholder: '86600-000', max: 10 })}
        </div>
        <div className="grid md:grid-cols-3 gap-3">
          {texto('telefone', 'Telefone', { max: 20 })}
        </div>
      </Card>

      {/* Numeração e CSC */}
      <div className="grid md:grid-cols-2 gap-5">
        <Card padding="md" className="space-y-4">
          <h2 className="text-sm font-semibold text-gray-800">Numeração</h2>
          <p className="text-xs text-gray-500">
            O próximo número de cada modelo. Se a loja já emitia notas em outro sistema, continue de onde parou.
          </p>
          <div className="grid grid-cols-2 gap-3">
            {numero('nfceSerie', 'Série NFC-e', 999)}
            {numero('nfceProximoNumero', 'Próximo número NFC-e', 999999999)}
            {numero('nfeSerie', 'Série NF-e', 999)}
            {numero('nfeProximoNumero', 'Próximo número NF-e', 999999999)}
          </div>
        </Card>

        {PROVEDORES_API.includes(form.provedor) ? (
        <Card padding="md" className="space-y-4">
          <h2 className="text-sm font-semibold text-gray-800">Acesso ao provedor fiscal</h2>
          <p className="text-xs text-gray-500">
            Com este provedor, o certificado A1 e o CSC ficam cadastrados no painel dele. Aqui vai só o token de acesso da
            empresa (aba Tokens da empresa no painel) e o CST de PIS/COFINS. Os tokens ficam cifrados e não são mostrados de novo.
          </p>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Token de homologação" htmlFor="fiscal-tokenHomologacao">
              <input id="fiscal-tokenHomologacao" type="password" autoComplete="off" value={form.tokenHomologacao}
                     disabled={!editavel || form.limparTokens}
                     placeholder={config.tokenHomologacaoConfigurado ? '•••••••• cadastrado — deixe em branco para manter' : 'Cole aqui'}
                     onChange={e => set('tokenHomologacao', e.target.value)} className={baseInputClass} />
            </Field>
            <Field label="Token de produção" htmlFor="fiscal-tokenProducao">
              <input id="fiscal-tokenProducao" type="password" autoComplete="off" value={form.tokenProducao}
                     disabled={!editavel || form.limparTokens}
                     placeholder={config.tokenProducaoConfigurado ? '•••••••• cadastrado — deixe em branco para manter' : 'Cole aqui'}
                     onChange={e => set('tokenProducao', e.target.value)} className={baseInputClass} />
            </Field>
            <Field label="CST de PIS/COFINS" htmlFor="fiscal-pisCofinsCst" hint="Definido pela contabilidade (Simples costuma usar 49).">
              <input id="fiscal-pisCofinsCst" value={form.pisCofinsCst} maxLength={2} disabled={!editavel} inputMode="numeric"
                     placeholder="49" onChange={e => set('pisCofinsCst', e.target.value.replace(/\D/g, ''))} className={baseInputClass} />
            </Field>
          </div>
          {(config.tokenHomologacaoConfigurado || config.tokenProducaoConfigurado) && (
            <label className="flex items-center gap-2 text-xs text-gray-500">
              <input type="checkbox" checked={form.limparTokens} disabled={!editavel} onChange={e => set('limparTokens', e.target.checked)} />
              Remover tokens cadastrados
            </label>
          )}
        </Card>
        ) : (
        <Card padding="md" className="space-y-4">
          <h2 className="text-sm font-semibold text-gray-800">CSC da NFC-e</h2>
          <p className="text-xs text-gray-500">
            O Código de Segurança do Contribuinte gera o QR Code da NFC-e. Ele é obtido no portal da Receita Estadual do Paraná.
          </p>
          <div className="grid grid-cols-3 gap-3">
            {texto('cscId', 'Identificador', { placeholder: '000001', max: 6 })}
            <Field label="Código (CSC)" className="col-span-2" htmlFor="fiscal-cscToken">
              <input id="fiscal-cscToken" type="password" autoComplete="off" value={form.cscToken} disabled={!editavel || form.limparCsc}
                     placeholder={config.cscConfigurado ? '•••••••• cadastrado — deixe em branco para manter' : 'Cole aqui'}
                     onChange={e => set('cscToken', e.target.value)} className={baseInputClass} />
            </Field>
          </div>
          {config.cscConfigurado && (
            <label className="flex items-center gap-2 text-xs text-gray-500">
              <input type="checkbox" checked={form.limparCsc} disabled={!editavel} onChange={e => set('limparCsc', e.target.checked)} />
              Remover CSC cadastrado
            </label>
          )}
        </Card>
        )}
      </div>

      {/* Certificado */}
      <Card padding="md" className="space-y-4">
        <h2 className="text-sm font-semibold text-gray-800 flex items-center gap-2"><FileKey size={16} /> Certificado digital A1</h2>
        {config.certificadoConfigurado ? (
          <div className="flex flex-wrap items-center justify-between gap-3 bg-gray-50 rounded-lg p-3">
            <div className="text-sm">
              <p className="font-medium text-gray-800">{config.certificadoTitular}</p>
              <p className={certVencido ? 'text-danger-600' : 'text-gray-500'}>
                {certVencido ? 'Venceu em ' : 'Válido até '}
                {config.certificadoValidade ? new Date(config.certificadoValidade).toLocaleDateString('pt-BR') : '—'}
              </p>
            </div>
            {podeExcluir('FISCAL') && (
              <Button variant="outline-danger" size="sm" onClick={() => setRemovendoCert(true)}>
                <Trash2 size={14} /> Remover
              </Button>
            )}
          </div>
        ) : (
          <p className="text-sm text-gray-600">Nenhum certificado cadastrado.</p>
        )}
        {editavel && (
          <div className="grid md:grid-cols-3 gap-3 items-end">
            <Field label="Senha do certificado" htmlFor="fiscal-senhaCert">
              <input id="fiscal-senhaCert" type="password" autoComplete="new-password" value={senhaCert}
                     onChange={e => setSenhaCert(e.target.value)} className={baseInputClass} />
            </Field>
            <Field label={config.certificadoConfigurado ? 'Trocar pelo arquivo (.pfx)' : 'Arquivo (.pfx ou .p12)'} className="md:col-span-2"
                   htmlFor="fiscal-arquivoCert">
              <input id="fiscal-arquivoCert" ref={arquivoRef} type="file" accept=".pfx,.p12" disabled={!senhaCert || enviarCert.isPending}
                     onChange={e => { const f = e.target.files?.[0]; if (f) enviarCert.mutate(f) }}
                     className="block w-full text-sm text-gray-600 file:mr-3 file:rounded-lg file:border-0 file:bg-gray-100 file:px-3 file:py-2 file:text-sm disabled:opacity-50" />
            </Field>
          </div>
        )}
        <p className="text-xs text-gray-500">
          Digite a senha e depois escolha o arquivo. O certificado e a senha ficam guardados criptografados e não são mostrados de novo.
        </p>
        {!config.criptoConfigurada && (
          <p className="text-xs text-danger-600">
            O servidor está sem PAGAMENTO_CRIPTO_CHAVE: não é possível guardar o certificado até configurá-la.
          </p>
        )}
      </Card>

      {editavel && (
        <div className="flex justify-end">
          <Button variant="primary" onClick={() => salvar.mutate()} loading={salvar.isPending}>
            <Save size={15} /> Salvar configuração
          </Button>
        </div>
      )}

      {removendoCert && (
        <ConfirmDialog
          title="Remover o certificado?"
          message="Sem certificado a emissão de NFC-e e NF-e é desligada e o PDV volta ao cupom não fiscal."
          confirmLabel="Remover"
          loading={removerCert.isPending}
          onConfirm={() => removerCert.mutate()}
          onCancel={() => setRemovendoCert(false)}
        />
      )}
    </div>
  )
}

/** "2026-11" do mês atual, no horário local. */
function mesAtual() {
  const h = new Date()
  return `${h.getFullYear()}-${String(h.getMonth() + 1).padStart(2, '0')}`
}

/** Baixa um arquivo da API (XML ou .zip) com o nome que o backend mandou. */
async function baixarArquivo(url: string, params: Record<string, string>, nomePadrao: string) {
  try {
    const r = await api.get(url, { params, responseType: 'blob', silent: true } as never) as { data: Blob; headers: Record<string, string> }
    const nome = /filename="?([^";]+)"?/.exec(r.headers['content-disposition'] ?? '')?.[1] ?? nomePadrao
    const href = URL.createObjectURL(r.data)
    const a = document.createElement('a')
    a.href = href
    a.download = nome
    a.click()
    URL.revokeObjectURL(href)
  } catch (e) {
    const corpo = (e as { response?: { data?: Blob } }).response?.data
    let msg = 'Não foi possível baixar o arquivo.'
    try { if (corpo) msg = JSON.parse(await corpo.text()).message ?? msg } catch { /* mantém a genérica */ }
    toast.error(msg)
  }
}

function Documentos() {
  const qc = useQueryClient()
  const [modelo, setModelo] = useState<'' | '65' | '55'>('')
  const [mes, setMes] = useState(mesAtual)
  const [baixandoZip, setBaixandoZip] = useState(false)
  const { data: docs = [], isLoading } = useQuery<DocumentoFiscal[]>({
    queryKey: ['fiscal-documentos', modelo, mes],
    queryFn: () => api.get('/fiscal/documentos', { params: { modelo: modelo || undefined, mes } }).then(r => r.data),
    enabled: /^\d{4}-\d{2}$/.test(mes),
  })
  const pag = usePaginacao(docs, `${modelo}|${mes}`)
  const emAberto = docs.filter(d => d.status === 'DESCONHECIDO' || d.status === 'PENDENTE').length
  const producao = docs.filter(d => d.ambiente === 'PRODUCAO')
  const autorizadas = producao.filter(d => d.status === 'AUTORIZADA')
  const comXml = producao.filter(d => d.temXml).length
  const totalAutorizado = autorizadas.reduce((s, d) => s + Number(d.valorTotal), 0)

  async function baixarZip() {
    setBaixandoZip(true)
    await baixarArquivo('/fiscal/documentos/xml-do-mes', { mes }, `XML-notas-${mes}.zip`)
    setBaixandoZip(false)
  }
  const consultar = useMutation({
    mutationFn: (id: number) => api.post<{ status: StatusDocumentoFiscal }>(`/fiscal/nfce/documentos/${id}/consultar`).then(r => r.data),
    onSuccess: r => {
      qc.invalidateQueries({ queryKey: ['fiscal-documentos'] })
      if (r.status === 'DESCONHECIDO') toast.error('A SEFAZ não deu resposta definitiva: a nota continua com resultado desconhecido.')
      else toast.success(`Situação na SEFAZ: ${ROTULO_STATUS_DOCUMENTO[r.status]}`)
    },
  })

  return (
    <Card padding="none">
      <div className="p-4 flex flex-wrap items-end gap-3 border-b border-gray-100">
        <Field label="Mês" htmlFor="fiscal-docs-mes">
          <input id="fiscal-docs-mes" type="month" value={mes} max={mesAtual()} onChange={e => setMes(e.target.value)}
                 className={`${baseInputClass} w-44`} />
        </Field>
        <Field label="Modelo" htmlFor="fiscal-docs-modelo">
          <select id="fiscal-docs-modelo" value={modelo} onChange={e => setModelo(e.target.value as typeof modelo)}
                  className={`${baseInputClass} w-48`}>
            <option value="">NFC-e e NF-e</option>
            <option value="65">Só NFC-e (65)</option>
            <option value="55">Só NF-e (55)</option>
          </select>
        </Field>
        <div className="ml-auto flex flex-col items-end gap-1">
          <Button variant="primary" onClick={baixarZip} loading={baixandoZip} disabled={comXml === 0}>
            <Download size={16} /> Baixar XMLs do mês (.zip)
          </Button>
          <span className="text-xs text-gray-500" aria-live="polite">
            {autorizadas.length} autorizada(s) em produção · {formatBRL(totalAutorizado)} · {comXml} XML(s)
          </span>
        </div>
      </div>
      {emAberto > 0 && (
        <p className="mx-4 mt-4 flex items-start gap-2 rounded-lg border border-warning-100 bg-warning-50 p-3 text-sm text-warning-700">
          <AlertTriangle size={16} className="mt-0.5 shrink-0" />
          {emAberto} nota(s) com resultado desconhecido: foram transmitidas e ficaram sem resposta, então podem ter sido
          autorizadas. Consulte a situação antes de emitir de novo para a mesma venda.
        </p>
      )}
      {isLoading ? <LoadingState /> : docs.length === 0 ? (
        <EmptyState>Nenhuma nota neste mês. Com a emissão desligada, o PDV usa o cupom não fiscal.</EmptyState>
      ) : (
        <Table>
          <THead>
            <TH>Modelo</TH><TH>Série / Número</TH><TH>Data</TH><TH>Destinatário</TH>
            <TH align="right">Valor</TH><TH>Status</TH><TH />
          </THead>
          <TBody>
            {pag.itens.map(d => (
              <TR key={d.id}>
                <TD>{d.modelo === 65 ? 'NFC-e' : 'NF-e'}</TD>
                <TD className="tabular-nums">{d.serie} / {d.numero}</TD>
                <TD>{new Date(d.emitidaEm ?? d.createdAt).toLocaleString('pt-BR')}</TD>
                <TD>{d.destinatarioNome ?? 'Consumidor'}</TD>
                <TD align="right" className="tabular-nums">{formatBRL(d.valorTotal)}</TD>
                <TD>
                  <StatusBadge tone={TOM_STATUS[d.status]}>{ROTULO_STATUS_DOCUMENTO[d.status] ?? d.status}</StatusBadge>
                  {d.ambiente === 'HOMOLOGACAO' && <span className="ml-2 text-xs text-gray-400">homologação</span>}
                  {d.mensagem && d.status !== 'AUTORIZADA' && <p className="mt-1 max-w-xs text-xs text-gray-500">{d.mensagem}</p>}
                </TD>
                <TD align="right">
                  {d.temXml && (
                    <Button size="sm" variant="secondary" aria-label={`Baixar XML da nota ${d.serie}/${d.numero}`}
                            onClick={() => baixarArquivo(`/fiscal/documentos/${d.id}/xml`, {}, `${d.chave ?? `serie${d.serie}-numero${d.numero}`}-${d.modelo === 55 ? 'nfe' : 'nfce'}.xml`)}>
                      <Download size={14} /> XML
                    </Button>
                  )}
                  {d.modelo === 65 && (d.status === 'DESCONHECIDO' || d.status === 'PENDENTE') && (
                    <Button size="sm" variant="secondary" loading={consultar.isPending && consultar.variables === d.id}
                            onClick={() => consultar.mutate(d.id)}>
                      Consultar situação
                    </Button>
                  )}
                </TD>
              </TR>
            ))}
          </TBody>
        </Table>
      )}
      {!isLoading && <Paginacao paginacao={pag} rotulo="notas" />}
    </Card>
  )
}