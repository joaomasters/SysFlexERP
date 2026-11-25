import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Download, Play } from 'lucide-react'
import { api } from '@/shared/api/axios'
import { PageHeader, Card, Button } from '@/shared/components/ui'
import { baixarCsv } from '@/modules/produtos/planilhaFiscal'

interface Resultado {
  colunas: string[]
  linhas: (string | null)[][]
  cortado: boolean
  milissegundos: number
}

const EXEMPLOS = [
  {
    rotulo: 'Produtos sem dados fiscais (formato da planilha fiscal)',
    sql: `SELECT codigo_interno, ean13 AS codigo_barras, nome AS descricao, ncm, cest, cfop, origem, csosn
FROM produtos
WHERE ativo AND (ncm IS NULL OR cfop IS NULL OR csosn IS NULL)
ORDER BY nome`,
  },
  {
    rotulo: 'Quantos produtos sem NCM por tipo',
    sql: `SELECT tipo_produto, COUNT(*) AS produtos, COUNT(*) FILTER (WHERE ncm IS NULL) AS sem_ncm
FROM produtos
WHERE ativo
GROUP BY tipo_produto
ORDER BY sem_ncm DESC`,
  },
  {
    rotulo: 'NCMs já usados (para repetir nos parecidos)',
    sql: `SELECT ncm, COUNT(*) AS produtos, MIN(nome) AS exemplo
FROM produtos
WHERE ativo AND ncm IS NOT NULL
GROUP BY ncm
ORDER BY produtos DESC`,
  },
]

function celulaCsv(v: string | null) {
  if (v == null) return ''
  return /[;"\r\n]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v
}

/** Só o SUPER_ADMIN vê (Sidebar) e só ele consegue consultar (ConsultaSqlService). Somente leitura. */
export default function ConsultaSqlPage() {
  const [sql, setSql] = useState(EXEMPLOS[0].sql)
  const [erro, setErro] = useState<string | null>(null)

  const consultar = useMutation({
    mutationFn: (texto: string) =>
      api.post<Resultado>('/manutencao/consulta-sql', { sql: texto }, { silent: true } as never).then(r => r.data),
    onMutate: () => setErro(null),
    onError: (e: { response?: { data?: { message?: string } } }) =>
      setErro(e.response?.data?.message ?? 'Não foi possível executar a consulta.'),
  })
  const r = consultar.data

  function executar() {
    if (sql.trim() && !consultar.isPending) consultar.mutate(sql)
  }

  function baixar() {
    if (!r) return
    const linhas = [r.colunas.map(celulaCsv).join(';'), ...r.linhas.map(l => l.map(celulaCsv).join(';'))]
    baixarCsv('﻿' + linhas.join('\r\n'), `consulta-${new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')}.csv`)
  }

  return (
    <div className="p-4 sm:p-6 space-y-4">
      <PageHeader title="Consulta SQL" subtitle="Somente leitura, nos dados desta empresa" />

      <Card padding="md" className="space-y-3">
        <p className="text-xs text-gray-500">
          Só <b>SELECT</b> (ou WITH), uma consulta por vez, até 15 segundos e 1.000 linhas. Senhas, tokens,
          certificados e dados de outras empresas ficam fora. Toda consulta vai para a Auditoria.
        </p>
        <div className="flex flex-wrap gap-2">
          {EXEMPLOS.map(ex => (
            <button key={ex.rotulo} type="button" onClick={() => setSql(ex.sql)}
                    className="text-xs px-2.5 py-1 rounded-full border border-gray-300 text-gray-600 hover:bg-gray-50">
              {ex.rotulo}
            </button>
          ))}
        </div>
        <label htmlFor="consulta-sql" className="sr-only">Consulta SQL</label>
        <textarea
          id="consulta-sql"
          value={sql}
          onChange={e => setSql(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); executar() } }}
          spellCheck={false}
          rows={8}
          className="w-full font-mono text-sm rounded-lg border border-gray-300 p-3 focus:outline-none focus:ring-2 focus:ring-primary-500"
        />
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="primary" onClick={executar} loading={consultar.isPending}>
            <Play size={16} /> Executar <span className="text-xs opacity-75">(Ctrl+Enter)</span>
          </Button>
          {r && (
            <Button variant="secondary" onClick={baixar} disabled={r.linhas.length === 0}>
              <Download size={16} /> Baixar CSV
            </Button>
          )}
          {r && (
            <span className="text-xs text-gray-500" aria-live="polite">
              {r.linhas.length} linha(s){r.cortado ? ' — mostrando só as primeiras 1.000' : ''} em {r.milissegundos} ms
            </span>
          )}
        </div>
        {erro && <p role="alert" className="text-sm text-danger-600 whitespace-pre-wrap">{erro}</p>}
      </Card>

      {r && (
        <Card padding="none" className="overflow-hidden">
          {r.linhas.length === 0 ? (
            <p className="p-6 text-sm text-gray-500 text-center">Nenhuma linha.</p>
          ) : (
            <div className="overflow-auto max-h-[60vh]">
              <table className="text-xs w-full" aria-label="Resultado da consulta">
                <thead className="bg-gray-50 sticky top-0">
                  <tr>
                    {r.colunas.map((c, i) => (
                      <th key={i} className="px-3 py-2 text-left font-semibold text-gray-600 whitespace-nowrap">{c}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {r.linhas.map((l, i) => (
                    <tr key={i} className="border-t">
                      {l.map((v, j) => (
                        <td key={j} className="px-3 py-1.5 whitespace-nowrap font-mono text-gray-800">
                          {v ?? <span className="text-gray-300">null</span>}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      )}
    </div>
  )
}
