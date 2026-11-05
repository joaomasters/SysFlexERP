import toast from 'react-hot-toast'
import { getSessao } from './auth'

/*
 "Limpar cache" do SUPER_ADMIN (Acesso → Manutenção): o backend devolve a versão
 do cache da empresa em toda resposta (X-Versao-Cache). Quando ela muda, este
 navegador descarta o que guardou e recarrega a tela — sem deslogar ninguém.

 No PDV não recarrega sozinho: pode haver cobrança em andamento na maquininha.
 Avisa e o operador recarrega ao terminar a venda.
 */

const PREFIXO = 'sysflex_versao_cache:'
let avisoPdvMostrado = false

export function conferirVersaoCache(valor: unknown) {
  if (typeof valor !== 'string' || valor === '') return
  const sessao = getSessao()
  if (!sessao || sessao.jarvis) return
  const chave = PREFIXO + (sessao.empresa?.codigo ?? 'principal')

  let guardada: string | null
  try {
    guardada = localStorage.getItem(chave)
  } catch {
    return // sem localStorage (modo privado etc.): não dá para comparar
  }
  if (guardada === valor) return
  if (guardada === null) {
    // Primeira vez neste navegador: só registra
    try { localStorage.setItem(chave, valor) } catch { /* ignora */ }
    return
  }

  if (window.location.pathname.startsWith('/pdv')) {
    if (avisoPdvMostrado) return
    avisoPdvMostrado = true
    toast('O administrador limpou o cache do sistema. Ao terminar esta venda, aperte F5 para recarregar.',
      { duration: Infinity, icon: '🔄' })
    // Grava só quando recarregar: até lá o aviso continua valendo
    window.addEventListener('beforeunload', () => {
      try { localStorage.setItem(chave, valor) } catch { /* ignora */ }
    }, { once: true })
    return
  }

  try { localStorage.setItem(chave, valor) } catch { /* ignora */ }
  limparCacheNavegador().finally(() => window.location.reload())
}

/**
 * Cache do service worker e arquivos guardados pelo navegador. Os dados das
 * telas (react-query) ficam em memória e somem no recarregamento. A sessão e as
 * preferências deste computador (ex.: maquininha escolhida) são mantidas.
 */
async function limparCacheNavegador() {
  try {
    if ('caches' in window) {
      for (const nome of await caches.keys()) await caches.delete(nome)
    }
  } catch { /* ignora */ }
  try {
    const registros = await navigator.serviceWorker?.getRegistrations()
    for (const r of registros ?? []) await r.unregister()
  } catch { /* ignora */ }
}
