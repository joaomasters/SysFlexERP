import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import { Modal, Button, Field, baseInputClass } from '@/shared/components/ui'
import type { ProvedorConfig } from '@/types/pagamento'

interface Props {
  provedor: ProvedorConfig
  onClose: () => void
}

// O que cada adquirente chama de "credencial" — o campo é o mesmo no backend.
const CREDENCIAL: Record<string, { rotulo: string; dica: string }> = {
  MERCADO_PAGO: {
    rotulo: 'Access Token',
    dica: 'Mercado Pago → Suas integrações → sua aplicação → Credenciais de produção (APP_USR-...). Teste: TEST-...',
  },
  STONE: {
    rotulo: 'Secret Key (Stone Connect)',
    dica: 'Fornecida pela Stone ao habilitar o Stone Connect (sk_... produção / sk_test_... sandbox).',
  },
}

export default function ProvedorConfigModal({ provedor, onClose }: Props) {
  const qc = useQueryClient()
  const [ativo, setAtivo] = useState(provedor.ativo)
  const [ambiente, setAmbiente] = useState(provedor.ambiente)
  const [codigoEstabelecimento, setCodigo] = useState(provedor.codigoEstabelecimento ?? '')
  const [observacoes, setObservacoes] = useState(provedor.observacoes ?? '')
  const [accessToken, setAccessToken] = useState('')
  const [webhookSecret, setWebhookSecret] = useState('')
  const [limparAccessToken, setLimparAccessToken] = useState(false)
  const [limparWebhookSecret, setLimparWebhookSecret] = useState(false)

  const credencial = CREDENCIAL[provedor.provedor]
  const podeSalvarSegredo = provedor.criptoConfigurada

  const salvar = useMutation({
    mutationFn: () => api.put(`/maquininhas/provedores/${provedor.provedor}`, {
      ativo, ambiente, codigoEstabelecimento, observacoes,
      accessToken: accessToken || null,
      webhookSecret: webhookSecret || null,
      limparAccessToken, limparWebhookSecret,
    }),
    onSuccess: () => {
      toast.success(`${provedor.rotulo} atualizado.`)
      qc.invalidateQueries({ queryKey: ['maquininhas-provedores'] })
      onClose()
    },
  })

  const placeholderSegredo = (configurado: boolean) =>
    configurado ? '•••••••• cadastrado — deixe em branco para manter' : 'Cole aqui'

  return (
    <Modal
      title={`Configurar ${provedor.rotulo}`}
      maxWidth="lg"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" fullWidth onClick={onClose}>Cancelar</Button>
          <Button variant="primary" fullWidth loading={salvar.isPending} onClick={() => salvar.mutate()}>
            Salvar
          </Button>
        </>
      }
    >
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" checked={ativo} onChange={e => setAtivo(e.target.checked)} />
        Adquirente ativa nesta empresa
      </label>

      {!provedor.suportaApiNuvem && (
        <p className="text-xs bg-info-50 text-info-700 rounded-lg p-3">
          A {provedor.rotulo} não tem API em nuvem para maquininhas. Cadastre as maquininhas em modo
          <b> Agente local (TEF)</b> — com o software TEF da adquirente instalado no computador do caixa —
          ou em modo <b>Manual</b>.
        </p>
      )}

      <div className="grid grid-cols-2 gap-3">
        {provedor.suportaApiNuvem && (
          <Field label="Ambiente">
            <select value={ambiente} onChange={e => setAmbiente(e.target.value as typeof ambiente)} className={baseInputClass}>
              <option value="SANDBOX">Sandbox (testes)</option>
              <option value="PRODUCAO">Produção</option>
            </select>
          </Field>
        )}
        <Field label="Código do estabelecimento" hint="Stone Code, nº do estabelecimento Sicredi etc. (informativo)">
          <input value={codigoEstabelecimento} onChange={e => setCodigo(e.target.value)} className={baseInputClass} />
        </Field>
      </div>

      {credencial && (
        <Field label={credencial.rotulo} hint={credencial.dica}>
          <input
            type="password"
            autoComplete="off"
            value={accessToken}
            disabled={!podeSalvarSegredo || limparAccessToken}
            onChange={e => setAccessToken(e.target.value)}
            placeholder={placeholderSegredo(provedor.credencialConfigurada)}
            className={baseInputClass}
          />
          {provedor.credencialConfigurada && (
            <label className="flex items-center gap-2 text-xs text-gray-500 mt-1.5">
              <input type="checkbox" checked={limparAccessToken} onChange={e => setLimparAccessToken(e.target.checked)} />
              Remover credencial cadastrada
            </label>
          )}
        </Field>
      )}

      {provedor.provedor === 'MERCADO_PAGO' && (
        <Field
          label="Assinatura secreta do webhook"
          hint="Mercado Pago → Suas integrações → Webhooks → Assinatura secreta. Com ela, notificações falsas são recusadas."
        >
          <input
            type="password"
            autoComplete="off"
            value={webhookSecret}
            disabled={!podeSalvarSegredo || limparWebhookSecret}
            onChange={e => setWebhookSecret(e.target.value)}
            placeholder={placeholderSegredo(provedor.webhookSecretConfigurado)}
            className={baseInputClass}
          />
          {provedor.webhookSecretConfigurado && (
            <label className="flex items-center gap-2 text-xs text-gray-500 mt-1.5">
              <input type="checkbox" checked={limparWebhookSecret} onChange={e => setLimparWebhookSecret(e.target.checked)} />
              Remover assinatura cadastrada
            </label>
          )}
        </Field>
      )}

      {credencial && !podeSalvarSegredo && (
        <p className="text-xs text-danger-600">
          O servidor está sem PAGAMENTO_CRIPTO_CHAVE — não é possível salvar segredos até configurá-la.
        </p>
      )}

      {provedor.provedor === 'MERCADO_PAGO' && (
        <p className="text-xs text-gray-500">
          O mesmo Access Token passa a ser usado também no PIX do PDV desta empresa.
        </p>
      )}

      <Field label="Observações">
        <textarea
          rows={2}
          value={observacoes}
          onChange={e => setObservacoes(e.target.value)}
          className={baseInputClass}
          placeholder="Ex: contato do gerente da conta, nº do contrato..."
        />
      </Field>
    </Modal>
  )
}
