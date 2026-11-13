// Módulo industrial: estrutura produtiva (espelha EstruturaDto.java)

export interface Unidade { id: number; codigo: string; nome: string; cidade?: string; uf?: string; ativo: boolean }

export interface Setor { id: number; unidadeId: number; unidadeNome: string; codigo: string; nome: string; ativo: boolean }

export interface Centro {
  id: number; setorId: number; setorNome: string; unidadeId: number; unidadeNome: string
  codigo: string; nome: string; custoHora?: number; ativo: boolean
}

export interface Turno {
  id: number; codigo: string; nome: string; horaInicio: string; horaFim: string
  /** 7 posições, segunda a domingo, '1' = trabalha */
  diasSemana: string; minutos: number; ativo: boolean
}

export type CategoriaMaquina = 'FORNO' | 'PRENSA_EXTRUSAO' | 'CORTE' | 'TRATAMENTO_TERMICO' | 'CNC' | 'ACABAMENTO'
  | 'EMBALAGEM' | 'OUTRO'

export interface Parametro {
  id?: number; codigo: string; nome: string; unidade?: string; tipoValor: 'NUMERO' | 'TEXTO'
  minimo?: number | null; maximo?: number | null; obrigatorio: boolean
}

export interface TipoMaquina {
  id: number; codigo: string; nome: string; categoria: CategoriaMaquina; ativo: boolean; parametros: Parametro[]
}

export type StatusMaquina = 'DISPONIVEL' | 'EM_OPERACAO' | 'PARADA' | 'EM_MANUTENCAO' | 'INATIVA'

export interface ValorParametro {
  parametroId: number; codigo: string; nome: string; unidade?: string; valorNumero?: number | null; valorTexto?: string | null
}

export interface Maquina {
  id: number; codigo: string; descricao: string; tipoMaquinaId: number; tipoMaquinaNome: string
  categoria: CategoriaMaquina; centroTrabalhoId: number; centroTrabalhoNome: string; setorNome: string
  unidadeNome: string; capacidade?: number | null; unidadeCapacidade?: string | null; status: StatusMaquina
  custoHora?: number | null; horimetro: number; fabricante?: string; modelo?: string; numeroSerie?: string
  anoFabricacao?: number | null; observacao?: string; versao: number; parametros: ValorParametro[]; turnoIds: number[]
}

export interface EventoMaquina {
  id: number; tipo: 'STATUS' | 'MANUTENCAO_PREVENTIVA' | 'MANUTENCAO_CORRETIVA' | 'HORIMETRO' | 'OBSERVACAO'
  statusAnterior?: StatusMaquina; statusNovo?: StatusMaquina; horimetro?: number; custo?: number
  inicio?: string; fim?: string; descricao: string; usuarioNome?: string; createdAt: string
}

export type TipoFerramenta = 'MATRIZ' | 'DISPOSITIVO' | 'FERRAMENTA_CORTE' | 'OUTRO'
export type StatusFerramenta = 'DISPONIVEL' | 'EM_USO' | 'EM_MANUTENCAO' | 'DESCARTADA'

export interface Ferramenta {
  id: number; codigo: string; descricao: string; tipo: TipoFerramenta; revisao?: string
  tipoMaquinaId?: number; tipoMaquinaNome?: string; vidaUtilCiclos?: number; ciclosUtilizados: number
  percentualVidaUsada?: number; status: StatusFerramenta; custoAquisicao?: number; observacao?: string
}

export interface Alocacao {
  id: number; usuarioId: number; usuarioNome?: string; centroTrabalhoId: number; centroTrabalhoNome: string
  turnoId: number; turnoNome: string; funcao?: string; vigenteDe: string; vigenteAte?: string; vigente: boolean
}

export interface Operador { id: number; nome: string }

export const CATEGORIAS: Record<CategoriaMaquina, string> = {
  FORNO: 'Forno', PRENSA_EXTRUSAO: 'Prensa de extrusão', CORTE: 'Corte', TRATAMENTO_TERMICO: 'Tratamento térmico',
  CNC: 'CNC', ACABAMENTO: 'Acabamento', EMBALAGEM: 'Embalagem', OUTRO: 'Outro',
}

export const STATUS_MAQUINA: Record<StatusMaquina, { rotulo: string; tom: 'success' | 'info' | 'warning' | 'danger' | 'neutral' }> = {
  DISPONIVEL: { rotulo: 'Disponível', tom: 'success' },
  EM_OPERACAO: { rotulo: 'Em operação', tom: 'info' },
  PARADA: { rotulo: 'Parada', tom: 'warning' },
  EM_MANUTENCAO: { rotulo: 'Em manutenção', tom: 'danger' },
  INATIVA: { rotulo: 'Inativa', tom: 'neutral' },
}

export const UNIDADES_CAPACIDADE: Record<string, string> = {
  KG_H: 'kg/h', T_H: 't/h', PECAS_H: 'peças/h', M_MIN: 'm/min', M_H: 'm/h', CICLOS_H: 'ciclos/h', KG_CARGA: 'kg por carga',
}

export const TIPOS_FERRAMENTA: Record<TipoFerramenta, string> = {
  MATRIZ: 'Matriz', DISPOSITIVO: 'Dispositivo', FERRAMENTA_CORTE: 'Ferramenta de corte', OUTRO: 'Outro',
}

export const STATUS_FERRAMENTA: Record<StatusFerramenta, { rotulo: string; tom: 'success' | 'info' | 'warning' | 'neutral' }> = {
  DISPONIVEL: { rotulo: 'Disponível', tom: 'success' },
  EM_USO: { rotulo: 'Em uso', tom: 'info' },
  EM_MANUTENCAO: { rotulo: 'Em manutenção', tom: 'warning' },
  DESCARTADA: { rotulo: 'Descartada', tom: 'neutral' },
}
