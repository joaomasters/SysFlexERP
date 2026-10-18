import axios from 'axios'
import toast from 'react-hot-toast'
import { getToken, removeToken } from '../auth'
import { API_BASE_URL } from './ambiente'

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
    const msg =
      error.response?.data?.message ??
      error.response?.data ??
      error.message ??
      'Erro desconhecido'
    toast.error(String(msg))
    return Promise.reject(error)
  }
)