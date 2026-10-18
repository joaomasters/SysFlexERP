/**
 * Para qual backend este build do frontend aponta.
 *
 * - VITE_API_URL vazio (produção e `npm run dev`): chamadas vão para "/api" no
 *   próprio domínio, e quem repassa ao backend é o rewrite do vercel.json
 *   (produção) ou o proxy do vite.config.ts (local).
 * - VITE_API_URL preenchido (ex: deploys de Preview/develop no Vercel): o
 *   navegador chama direto esse backend, ex: https://sismeethouse-development.up.railway.app
 *
 * As variáveis VITE_* entram no build: mudou no Vercel, precisa de um novo deploy.
 */
const urlBackend = (import.meta.env.VITE_API_URL ?? '').trim().replace(/\/+$/, '')

export const API_BASE_URL = urlBackend ? `${urlBackend}/api` : '/api'

/** "development", "production"... Só exibido quando não for produção. */
export const AMBIENTE = (import.meta.env.VITE_AMBIENTE ?? 'production').trim().toLowerCase()

export const EH_PRODUCAO = AMBIENTE === 'production'

/** URL pública completa de um caminho da API ("/api/..."), ex: para cadastrar webhooks. */
export function urlPublicaApi(caminho: string): string {
  return (urlBackend || window.location.origin) + caminho
}
