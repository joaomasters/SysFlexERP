import { useState } from 'react'
import { FileText } from 'lucide-react'
import type { ModoNfce } from '@/types/fiscal'

/** O que o caixa decidiu: NFC-e (com ou sem CPF/CNPJ) ou só o cupom não fiscal. */
export interface EscolhaNota {
  emitir: boolean
  documento?: string
}

interface Props {
  modo: ModoNfce
  /** CPF/CNPJ do cliente da venda, quando houver */
  documentoInicial?: string
  onEscolher: (escolha: EscolhaNota) => void
  onVoltar: () => void
}

const digitos = (v: string) => v.replace(/\D/g, '').slice(0, 14)

/** 000.000.000-00 ou 00.000.000/0000-00 conforme a quantidade de dígitos. */
function mascarar(d: string) {
  if (d.length <= 11) {
    return d.replace(/(\d{3})(\d)/, '$1.$2').replace(/(\d{3})(\d)/, '$1.$2').replace(/(\d{3})(\d{1,2})$/, '$1-$2')
  }
  return d.replace(/(\d{2})(\d)/, '$1.$2').replace(/(\d{3})(\d)/, '$1.$2').replace(/(\d{3})(\d)/, '$1/$2')
    .replace(/(\d{4})(\d{1,2})$/, '$1-$2')
}

function dvValidos(d: string, pesos: (i: number) => number, tamanhoBase: number) {
  for (let t = tamanhoBase; t < d.length; t++) {
    let soma = 0
    for (let i = 0; i < t; i++) soma += Number(d[i]) * pesos(t - i)
    const resto = soma % 11
    if ((resto < 2 ? 0 : 11 - resto) !== Number(d[t])) return false
  }
  return true
}

export function documentoValido(d: string) {
  if (/^(\d)\1+$/.test(d)) return false
  if (d.length === 11) return dvValidos(d, p => p + 1, 9)
  if (d.length === 14) return dvValidos(d, p => ((p - 1) % 8) + 2, 12)
  return false
}

/**
 * "CPF na nota?" ao finalizar a venda (tema escuro do PDV). Enter confirma, Esc volta ao pagamento.
 * Sob demanda, Enter sem documento segue no cupom não fiscal; em "todas", emite sem identificar.
 */
export default function CpfNaNotaModal({ modo, documentoInicial, onEscolher, onVoltar }: Props) {
  const [doc, setDoc] = useState(digitos(documentoInicial ?? ''))
  const [erro, setErro] = useState('')
  const sobDemanda = modo === 'SOB_DEMANDA'

  function confirmar(semDocumento = false) {
    if (semDocumento || !doc) {
      onEscolher(sobDemanda && !semDocumento ? { emitir: false } : { emitir: true })
      return
    }
    if (!documentoValido(doc)) {
      setErro(doc.length === 11 || doc.length === 14 ? 'CPF ou CNPJ inválido: confira os números.' : 'Digite os 11 dígitos do CPF ou os 14 do CNPJ.')
      return
    }
    onEscolher({ emitir: true, documento: doc })
  }

  return (
    <div className="fixed inset-0 bg-black/70 flex items-center justify-center z-[60] p-4">
      <div role="dialog" aria-label="CPF na nota" className="bg-gray-900 border border-gray-700 rounded-2xl w-full max-w-sm p-6 space-y-4 text-white">
        <div className="text-center space-y-1">
          <FileText size={30} className="mx-auto text-emerald-400" />
          <h2 className="text-lg font-bold">CPF na nota?</h2>
          <p className="text-xs text-gray-400">
            {sobDemanda
              ? 'Digite o CPF para emitir a NFC-e (Nota Paraná). Sem CPF, sai o cupom não fiscal.'
              : 'Digite o CPF ou CNPJ do cliente, ou deixe em branco.'}
          </p>
        </div>
        <input
          autoFocus
          aria-label="CPF ou CNPJ do cliente"
          inputMode="numeric"
          placeholder="000.000.000-00"
          value={mascarar(doc)}
          onChange={e => { setDoc(digitos(e.target.value)); setErro('') }}
          onKeyDown={e => {
            if (e.key === 'Enter') { e.preventDefault(); confirmar() }
            if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); onVoltar() }
          }}
          className="w-full bg-gray-800 border border-gray-600 rounded-xl px-4 py-3 text-xl text-center font-bold tabular-nums tracking-wide focus:outline-none focus:border-emerald-500"
        />
        {erro && <p className="text-sm text-red-400 text-center">{erro}</p>}
        <div className="grid grid-cols-2 gap-2">
          <button onClick={onVoltar} className="py-3 rounded-xl bg-gray-700 hover:bg-gray-600 text-sm font-medium">
            Voltar <span className="opacity-60 text-xs">[Esc]</span>
          </button>
          <button onClick={() => confirmar()} className="py-3 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-sm font-bold">
            {doc ? 'Emitir NFC-e' : sobDemanda ? 'Sem nota' : 'NFC-e sem CPF'} <span className="opacity-70 text-xs font-normal">[Enter]</span>
          </button>
        </div>
        {sobDemanda && (
          <button onClick={() => confirmar(true)} className="w-full text-xs text-gray-400 hover:text-white">
            Emitir NFC-e sem CPF
          </button>
        )}
      </div>
    </div>
  )
}
