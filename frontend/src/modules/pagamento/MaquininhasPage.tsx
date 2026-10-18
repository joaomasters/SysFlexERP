import { useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { Plus, Pencil, Lock, KeyRound, AlertTriangle, Plug, Copy, Smartphone, BookOpen } from 'lucide-react'
import toast from 'react-hot-toast'
import { api } from '@/shared/api/axios'
import { urlPublicaApi } from '@/shared/api/ambiente'
import { usePermissao } from '@/shared/hooks/usePermissao'
import {
  PageHeader, Card, Button, StatusBadge,
  Table, THead, TH, TBody, TR, TD, EmptyState, LoadingState,
} from '@/shared/components/ui'
import {
  ROTULO_MODO, ROTULO_PROVEDOR,
  type ProvedorConfig, type TerminalPagamento, type TesteConexao,
} from '@/types/pagamento'
import ProvedorConfigModal from './components/ProvedorConfigModal'
import TerminalModal from './components/TerminalModal'
import DispositivosModal from './components/DispositivosModal'

export default function MaquininhasPage() {
  const { podeVer, podeCriar, podeEditar } = usePermissao()

  const [editandoProvedor, setEditandoProvedor] = useState<ProvedorConfig | null>(null)
  const [editandoTerminal, setEditandoTerminal] = useState<Partial<TerminalPagamento> | null>(null)
  const [dispositivosDe, setDispositivosDe] = useState<ProvedorConfig | null>(null)
  const [testes, setTestes] = useState<Record<string, TesteConexao>>({})

  const { data: provedores = [], isLoading: carregandoProvedores } = useQuery<ProvedorConfig[]>({
    queryKey: ['maquininhas-provedores'],
    queryFn: () => api.get('/maquininhas/provedores').then(r => r.data),
    enabled: podeVer('MAQUININHAS'),
  })

  const { data: terminais = [], isLoading: carregandoTerminais } = useQuery<TerminalPagamento[]>({
    queryKey: ['maquininhas-terminais'],
    queryFn: () => api.get('/maquininhas/terminais').then(r => r.data),
    enabled: podeVer('MAQUININHAS'),
  })

  const { data: webhooks } = useQuery<{ codigoEmpresa: string; mercadoPago: string; stone: string }>({
    queryKey: ['maquininhas-webhooks'],
    queryFn: () => api.get('/maquininhas/webhooks').then(r => r.data),
    enabled: podeVer('MAQUININHAS'),
  })

  const testar = useMutation({
    mutationFn: (p: ProvedorConfig) =>
      api.post<TesteConexao>(`/maquininhas/provedores/${p.provedor}/testar`).then(r => r.data),
    onSuccess: (resultado, p) => {
      setTestes(t => ({ ...t, [p.provedor]: resultado }))
      if (resultado.ok) toast.success('Conexão OK')
      else toast.error('Falha na conexão')
    },
  })

  if (!podeVer('MAQUININHAS')) {
    return (
      <div className="p-6">
        <div className="bg-warning-50 border border-warning-100 rounded-xl p-5 max-w-lg">
          <p className="flex items-center gap-2 font-medium text-warning-700">
            <Lock size={16} /> Acesso restrito
          </p>
          <p className="text-sm text-warning-600 mt-1">
            Seu perfil não tem permissão para configurar as maquininhas de cartão.
          </p>
        </div>
      </div>
    )
  }

  const criptoAusente = provedores.length > 0 && !provedores[0].criptoConfigurada
  const urlCompleta = (caminho?: string) => (caminho ? urlPublicaApi(caminho) : '')
  const copiar = (texto: string) =>
    navigator.clipboard.writeText(texto).then(() => toast.success('Copiado!'))

  return (
    <div className="p-6">
      <PageHeader
        title="Maquininhas de Cartão"
        subtitle="Credenciais das adquirentes e maquininhas usadas no PDV"
        actions={
          podeCriar('MAQUININHAS') && (
            <Button variant="primary" onClick={() => setEditandoTerminal({})}>
              <Plus size={16} /> Nova maquininha
            </Button>
          )
        }
      />

      {criptoAusente && (
        <div className="bg-danger-50 border border-danger-100 rounded-xl p-4 mb-5 flex gap-3">
          <AlertTriangle size={18} className="text-danger-600 shrink-0 mt-0.5" />
          <div className="text-sm">
            <p className="font-medium text-danger-700">Chave de criptografia não configurada no servidor</p>
            <p className="text-danger-600 mt-0.5">
              Defina a variável de ambiente <code className="font-mono">PAGAMENTO_CRIPTO_CHAVE</code> no
              backend (Railway) para poder salvar tokens e chaves das adquirentes.
              Maquininhas em modo Manual funcionam sem ela.
            </p>
          </div>
        </div>
      )}

      {/* ── Credenciais ─────────────────────────────────────────────── */}
      <h2 className="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-2">
        <KeyRound size={15} /> Credenciais das adquirentes
      </h2>
      {carregandoProvedores ? (
        <LoadingState />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
          {provedores.map(p => {
            const teste = testes[p.provedor]
            return (
              <Card key={p.provedor} padding="sm" className="flex flex-col gap-3">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="font-semibold text-gray-900">{p.rotulo}</p>
                    <p className="text-xs text-gray-500">
                      {p.suportaApiNuvem ? 'API em nuvem, agente local ou manual' : 'Agente local (TEF) ou manual'}
                    </p>
                  </div>
                  <StatusBadge tone={p.ativo ? 'success' : 'neutral'}>{p.ativo ? 'Ativo' : 'Inativo'}</StatusBadge>
                </div>

                {p.suportaApiNuvem && (
                  <div className="flex flex-wrap gap-1.5">
                    <StatusBadge tone={p.credencialConfigurada ? 'info' : 'warning'}>
                      {p.credencialConfigurada ? 'Credencial cadastrada' : 'Sem credencial'}
                    </StatusBadge>
                    <StatusBadge tone="purple">{p.ambiente === 'PRODUCAO' ? 'Produção' : 'Sandbox'}</StatusBadge>
                    {p.provedor === 'MERCADO_PAGO' && (
                      <StatusBadge tone={p.webhookSecretConfigurado ? 'info' : 'neutral'}>
                        {p.webhookSecretConfigurado ? 'Webhook assinado' : 'Webhook sem assinatura'}
                      </StatusBadge>
                    )}
                  </div>
                )}

                {teste && (
                  <p className={`text-xs ${teste.ok ? 'text-success-700' : 'text-danger-600'}`}>{teste.mensagem}</p>
                )}

                <div className="flex flex-wrap gap-2 mt-auto pt-1">
                  {podeEditar('MAQUININHAS') && (
                    <Button variant="secondary" size="sm" onClick={() => setEditandoProvedor(p)}>
                      <Pencil size={13} /> Configurar
                    </Button>
                  )}
                  {p.suportaApiNuvem && p.credencialConfigurada && (
                    <Button
                      variant="secondary" size="sm"
                      loading={testar.isPending && testar.variables?.provedor === p.provedor}
                      onClick={() => testar.mutate(p)}
                    >
                      <Plug size={13} /> Testar conexão
                    </Button>
                  )}
                  {p.provedor === 'MERCADO_PAGO' && p.credencialConfigurada && (
                    <Button variant="secondary" size="sm" onClick={() => setDispositivosDe(p)}>
                      <Smartphone size={13} /> Dispositivos
                    </Button>
                  )}
                </div>
              </Card>
            )
          })}
        </div>
      )}

      {/* ── Terminais ───────────────────────────────────────────────── */}
      <h2 className="text-sm font-semibold text-gray-700 mb-3">Maquininhas cadastradas</h2>
      <Card padding="none" className="overflow-hidden mb-8">
        {carregandoTerminais ? (
          <LoadingState />
        ) : (
          <Table>
            <THead>
              <tr>
                <TH>Nome</TH>
                <TH>Adquirente</TH>
                <TH>Integração</TH>
                <TH>Dispositivo / agente</TH>
                <TH>Aceita</TH>
                <TH>Status</TH>
                <TH />
              </tr>
            </THead>
            <TBody>
              {terminais.map(t => (
                <TR key={t.id}>
                  <TD className="font-medium text-gray-900">{t.nome}</TD>
                  <TD>{ROTULO_PROVEDOR[t.provedor]}</TD>
                  <TD className="text-gray-600">{ROTULO_MODO[t.modoIntegracao]}</TD>
                  <TD className="font-mono text-xs text-gray-500">
                    {t.identificadorDispositivo ?? t.agenteUrl ?? '—'}
                  </TD>
                  <TD className="text-xs text-gray-600">
                    {[t.aceitaDebito && 'Débito', t.aceitaCredito && `Crédito até ${t.maxParcelas}x`]
                      .filter(Boolean).join(' · ')}
                  </TD>
                  <TD>
                    <StatusBadge tone={t.ativo ? 'success' : 'neutral'}>{t.ativo ? 'Ativa' : 'Inativa'}</StatusBadge>
                  </TD>
                  <TD align="right">
                    {podeEditar('MAQUININHAS') && (
                      <Button variant="ghost" size="sm" onClick={() => setEditandoTerminal(t)} title="Editar">
                        <Pencil size={15} />
                      </Button>
                    )}
                  </TD>
                </TR>
              ))}
              {terminais.length === 0 && (
                <tr><td colSpan={7}>
                  <EmptyState>Nenhuma maquininha cadastrada. O PDV vai oferecer só o registro manual de cartão.</EmptyState>
                </td></tr>
              )}
            </TBody>
          </Table>
        )}
      </Card>

      {/* ── Webhooks ────────────────────────────────────────────────── */}
      {webhooks && (
        <Card padding="sm" className="mb-4">
          <p className="text-sm font-semibold text-gray-700 mb-1">URLs de notificação (webhook)</p>
          <p className="text-xs text-gray-500 mb-3">
            Opcional, mas recomendado: cadastre no painel de cada adquirente para o PDV receber a confirmação
            mais rápido. Empresa: <span className="font-mono">{webhooks.codigoEmpresa}</span>
          </p>
          {[
            { rotulo: 'Mercado Pago (PIX e Point)', url: urlCompleta(webhooks.mercadoPago) },
            { rotulo: 'Stone Connect', url: urlCompleta(webhooks.stone) },
          ].map(w => (
            <div key={w.rotulo} className="flex items-center gap-2 text-xs py-1">
              <span className="text-gray-500 w-44 shrink-0">{w.rotulo}</span>
              <code className="font-mono bg-gray-50 border rounded px-2 py-1 flex-1 truncate">{w.url}</code>
              <Button variant="ghost" size="sm" onClick={() => copiar(w.url)} title="Copiar">
                <Copy size={13} />
              </Button>
            </div>
          ))}
        </Card>
      )}

      <p className="text-xs text-gray-400 flex items-center gap-1.5">
        <BookOpen size={13} /> Passo a passo de cada adquirente e o contrato do agente local: docs/MAQUININHAS.md no repositório.
      </p>

      {editandoProvedor && (
        <ProvedorConfigModal provedor={editandoProvedor} onClose={() => setEditandoProvedor(null)} />
      )}
      {editandoTerminal && (
        <TerminalModal
          terminal={editandoTerminal}
          provedores={provedores}
          onClose={() => setEditandoTerminal(null)}
        />
      )}
      {dispositivosDe && (
        <DispositivosModal
          provedor={dispositivosDe}
          onUsar={id => {
            setDispositivosDe(null)
            setEditandoTerminal({ provedor: dispositivosDe.provedor, modoIntegracao: 'API_NUVEM', identificadorDispositivo: id })
          }}
          onClose={() => setDispositivosDe(null)}
        />
      )}
    </div>
  )
}
