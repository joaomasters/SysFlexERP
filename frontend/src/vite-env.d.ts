/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Origem do backend (sem /api). Vazio = mesmo domínio, via rewrite/proxy. Ver shared/api/ambiente.ts */
  readonly VITE_API_URL?: string
  /** Nome do ambiente: production | development. Fora de produção aparece uma faixa de aviso. */
  readonly VITE_AMBIENTE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
