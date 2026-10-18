import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import { Modal, Button, Field, baseInputClass } from '@/shared/components/ui'
import {
  ROTULO_MODO, ROTULO_PROVEDOR,
  type ModoIntegracao, type ProvedorConfig, type ProvedorPagamento, type TerminalPagamento,
} from '@/types/pagamento'

interface Props {
  /** {} = nova maquininha; com id = edição */
  terminal: Partial<TerminalPagamento>
  provedores: ProvedorConfig[]
  onClose: () => void
}

const AJUDA_MODO: Record<ModoIntegracao, string> = {
  API_NUVEM: 'O sistema envia a cobrança pela internet direto para a maquininha. Não precisa instalar nada no computador.',
  AGENTE_LOCAL: 'O navegador do caixa conversa com o software TEF da adquirente instalado no computador (contrato em docs/MAQUININHAS.md).',
  MANUAL: 'Maquininha não integrada: o operador digita o valor nela e depois informa NSU/autorização no PDV.',
}

export default function TerminalModal({ terminal, provedores, onClose }: Props) {
  const qc = useQueryClient()
  const editando = terminal.id != null

  const [nome, setNome] = useState(terminal.nome ?? '')
  const [provedor, setProvedor] = useState<ProvedorPagamento>(terminal.provedor ?? 'MERCADO_PAGO')
  const [modo, setModo] = useState<ModoIntegracao>(terminal.modoIntegracao ?? 'API_NUVEM')
  const [identificador, setIdentificador] = useState(terminal.identificadorDispositivo ?? '')
  const [agenteUrl, setAgenteUrl] = useState(terminal.agenteUrl ?? 'http://localhost:12345')
  const [aceitaDebito, setAceitaDebito] = useState(terminal.aceitaDebito ?? true)
  const [aceitaCredito, setAceitaCredito] = useState(terminal.aceitaCredito ?? true)
  const [maxParcelas, setMaxParcelas] = useState(terminal.maxParcelas ?? 12)
  const [ativo, setAtivo] = useState(terminal.ativo ?? true)

  const suportaNuvem = provedores.find(p => p.provedor === provedor)?.suportaApiNuvem ?? false
  const modosDisponiveis: ModoIntegracao[] = suportaNuvem
    ? ['API_NUVEM', 'AGENTE_LOCAL', 'MANUAL']
    : ['AGENTE_LOCAL', 'MANUAL']

  const trocarProvedor = (p: ProvedorPagamento) => {
    setProvedor(p)
    const nuvem = provedores.find(x => x.provedor === p)?.suportaApiNuvem ?? false
    if (!nuvem && modo === 'API_NUVEM') setModo('AGENTE_LOCAL')
  }

  const salvar = useMutation({
    mutationFn: () => {
      const corpo = {
        nome, provedor, modoIntegracao: modo,
        identificadorDispositivo: identificador || null,
        agenteUrl: agenteUrl || null,
        aceitaDebito, aceitaCredito, maxParcelas, ativo,
      }
      return editando
        ? api.put(`/maquininhas/terminais/${terminal.id}`, corpo)
        : api.post('/maquininhas/terminais', corpo)
    },
    onSuccess: () => {
      toast.success(editando ? 'Maquininha atualizada.' : 'Maquininha cadastrada.')
      qc.invalidateQueries({ queryKey: ['maquininhas-terminais'] })
      onClose()
    },
  })

  const valido = nome.trim() !== ''
    && (aceitaDebito || aceitaCredito)
    && (modo !== 'API_NUVEM' || identificador.trim() !== '')
    && (modo !== 'AGENTE_LOCAL' || /^https?:\/\/.+/.test(agenteUrl.trim()))

  return (
    <Modal
      title={editando ? 'Editar maquininha' : 'Nova maquininha'}
      maxWidth="lg"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" fullWidth onClick={onClose}>Cancelar</Button>
          <Button variant="primary" fullWidth disabled={!valido} loading={salvar.isPending} onClick={() => salvar.mutate()}>
            Salvar
          </Button>
        </>
      }
    >
      <Field label="Nome *" hint="Como aparece para o operador no PDV, ex: “Caixa 1 — Stone”">
        <input value={nome} onChange={e => setNome(e.target.value)} className={baseInputClass} autoFocus />
      </Field>

      <div className="grid grid-cols-2 gap-3">
        <Field label="Adquirente *">
          <select value={provedor} onChange={e => trocarProvedor(e.target.value as ProvedorPagamento)} className={baseInputClass}>
            {(Object.keys(ROTULO_PROVEDOR) as ProvedorPagamento[]).map(p => (
              <option key={p} value={p}>{ROTULO_PROVEDOR[p]}</option>
            ))}
          </select>
        </Field>
        <Field label="Integração *">
          <select value={modo} onChange={e => setModo(e.target.value as ModoIntegracao)} className={baseInputClass}>
            {modosDisponiveis.map(m => <option key={m} value={m}>{ROTULO_MODO[m]}</option>)}
          </select>
        </Field>
      </div>
      <p className="text-xs text-gray-500 -mt-2">{AJUDA_MODO[modo]}</p>

      {modo === 'API_NUVEM' && (
        <Field
          label="Identificador da maquininha *"
          hint={provedor === 'MERCADO_PAGO'
            ? 'device_id da Point, ex: PAX_A910__SMARTPOS1234567 (use o botão “Dispositivos” para listar).'
            : 'Número de série da POS Stone (etiqueta atrás da maquininha).'}
        >
          <input value={identificador} onChange={e => setIdentificador(e.target.value)} className={`${baseInputClass} font-mono`} />
        </Field>
      )}

      {modo === 'AGENTE_LOCAL' && (
        <Field label="URL do agente local *" hint="Endereço do agente TEF no computador do caixa.">
          <input value={agenteUrl} onChange={e => setAgenteUrl(e.target.value)} className={`${baseInputClass} font-mono`} />
        </Field>
      )}

      <div className="flex flex-wrap items-end gap-4">
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={aceitaDebito} onChange={e => setAceitaDebito(e.target.checked)} /> Débito
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={aceitaCredito} onChange={e => setAceitaCredito(e.target.checked)} /> Crédito
        </label>
        {aceitaCredito && (
          <Field label="Máx. parcelas">
            <select value={maxParcelas} onChange={e => setMaxParcelas(Number(e.target.value))} className={baseInputClass}>
              {Array.from({ length: 18 }, (_, i) => i + 1).map(n => <option key={n} value={n}>{n}x</option>)}
            </select>
          </Field>
        )}
        <label className="flex items-center gap-2 text-sm ml-auto">
          <input type="checkbox" checked={ativo} onChange={e => setAtivo(e.target.checked)} /> Ativa
        </label>
      </div>
    </Modal>
  )
}
