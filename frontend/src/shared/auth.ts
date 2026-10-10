export interface PermissaoModulo {
  modulo: string
  rotulo: string
  ver: boolean
  criar: boolean
  editar: boolean
  excluir: boolean
}

export interface SessaoUsuario {
  token: string
  usuarioId: number
  username: string
  nome: string
  perfil: string
  superAdmin: boolean
  permissoes: PermissaoModulo[]
}

const SESSAO_KEY = 'acougue_sessao'

export const getSessao = (): SessaoUsuario | null => {
  const raw = localStorage.getItem(SESSAO_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as SessaoUsuario
  } catch {
    return null
  }
}

export const setSessao = (sessao: SessaoUsuario): void => {
  localStorage.setItem(SESSAO_KEY, JSON.stringify(sessao))
}

export const removeSessao = (): void => {
  localStorage.removeItem(SESSAO_KEY)
}

// Mantidos por compatibilidade com código existente que só precisa do token
export const getToken = (): string | null => getSessao()?.token ?? null
export const removeToken = (): void => removeSessao()
export const isAuthenticated = (): boolean => !!getToken()

// Usado em qualquer fluxo que precise atrelar uma ação ao operador logado
// (abrir/fechar caixa, sangria, venda) — evita operadorId fixo no código.
export const getUsuarioId = (): number | null => getSessao()?.usuarioId ?? null
export const getNomeUsuario = (): string | null => getSessao()?.nome ?? null