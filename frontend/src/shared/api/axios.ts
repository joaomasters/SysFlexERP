import axios from 'axios'
import toast from 'react-hot-toast'
import { getToken, removeToken } from '../auth'
import { API_BASE_URL } from './ambiente'

/**
 * Texto do erro para o usuário. Erro de validação vem como { campo: mensagem } em vez de
 * { message }: sem isso o aviso saía como "[object Object]".
 */
export function mensagemDeErro(error: any): string {
  const data = error?.response?.data
  const campos = data && typeof data === 'object' ? Object.values(data).join(' · ') : ''
  return String(data?.message ?? (campos || data || error?.message || 'Erro desconhecido'))
}

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (res) => res,
  (error) => {
    if (error.response?.status === 401) {
      removeToken()
      window.location.href = '/login'
      return Promise.reject(error)
    }
    // Permite que uma chamada específica peça pra não mostrar o toast global
    // de erro — útil quando a tela já trata o estado de erro no próprio
    // layout (ex: "nenhum caixa aberto" não é uma falha, é um estado normal).
    if (error.config?.silent) {
      return Promise.reject(error)
    }
    toast.error(mensagemDeErro(error))
    return Promise.reject(error)
  }
)