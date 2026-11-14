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

// ── Sprint 2: engenharia de produto e PCP ──────────────────────────────
export type TipoItem = 'MATERIA_PRIMA' | 'SEMIACABADO' | 'ACABADO' | 'EMBALAGEM' | 'INSUMO'

export interface ProdutoIndustrial {
  id: number; codigoInterno?: string | null; nome: string; unidadeMedida: string; ean13?: string | null
  estoqueAtual?: number | null; tipoItem?: TipoItem | null
}

export interface FichaTecnica {
  id: number; produtoId: number; produtoCodigo?: string; produtoNome: string; unidade: string; tipoItem: TipoItem
  codigoDesenho?: string; revisaoDesenho?: string; pesoUnitarioKg?: number | null; pesoLinearKgM?: number | null
  comprimentoMm?: number | null; loteMinimo?: number | null; loteMultiplo?: number | null
  prazoProducaoDias?: number | null; controlaLote: boolean; observacao?: string; updatedAt: string
}

export type StatusVersao = 'RASCUNHO' | 'ATIVA' | 'ATIVO' | 'OBSOLETA' | 'OBSOLETO'

export interface BomItem {
  id?: number; sequencia?: number; componenteId: number; componenteCodigo?: string; componenteNome?: string
  unidade?: string; quantidade: number; perdaPercentual: number; observacao?: string | null
}

export interface BomResumo {
  id: number; produtoId: number; produtoCodigo?: string; produtoNome: string; unidade: string; versao: number
  status: StatusVersao; quantidadeBase: number; vigenciaInicio?: string; vigenciaFim?: string; itens: number
}

export interface Bom extends Omit<BomResumo, 'itens'> {
  observacao?: string; ativadaEm?: string; itens: BomItem[]
}

export interface ExplosaoLinha {
  nivel: number; produtoId: number; codigo?: string; nome: string; unidade: string; quantidade: number; versaoEstrutura?: number | null
}

export interface OperacaoRoteiro {
  id?: number; sequencia: number; descricao: string; centroTrabalhoId: number; centroTrabalhoNome?: string
  tipoMaquinaId?: number | null; tipoMaquinaNome?: string; tempoSetupMin: number; tempoPorUnidadeMin: number
  instrucoes?: string | null
}

export interface RoteiroResumo {
  id: number; produtoId: number; produtoCodigo?: string; produtoNome: string; versao: number; status: StatusVersao
  vigenciaInicio?: string; vigenciaFim?: string; operacoes: number
}

export interface Roteiro extends Omit<RoteiroResumo, 'operacoes'> {
  unidade: string; observacao?: string; ativadoEm?: string; operacoes: OperacaoRoteiro[]
}

export type StatusOP = 'PLANEJADA' | 'LIBERADA' | 'EM_PRODUCAO' | 'PAUSADA' | 'CONCLUIDA' | 'ENCERRADA' | 'CANCELADA'

export interface OrdemResumo {
  id: number; numero: string; produtoId: number; produtoCodigo?: string; produtoNome: string; unidade: string
  quantidade: number; status: StatusOP; prioridade: number; inicioPrevisto: string; fimPrevisto: string
  clienteNome?: string; pedidoReferencia?: string; atrasada: boolean
}

export interface MaterialOP {
  produtoId: number; codigo?: string; nome: string; unidade: string; quantidadeLiquida: number
  perdaPercentual: number; quantidadePrevista: number; saldoEstoque: number; falta: number
}

export interface OperacaoOP {
  id?: number | null; sequencia: number; descricao: string; centroTrabalhoId: number; centroTrabalhoNome: string
  tipoMaquinaId?: number | null; tipoMaquinaNome?: string | null; maquinaId?: number | null; maquinaCodigo?: string | null
  maquinaDescricao?: string | null; tempoSetupMin: number; tempoPrevistoMin: number; status: 'PENDENTE' | 'EM_EXECUCAO' | 'CONCLUIDA'
}

export interface EventoOP {
  id: number; statusAnterior?: StatusOP | null; statusNovo: StatusOP; motivo?: string | null; usuarioNome?: string | null; createdAt: string
}

export interface OrdemDetalhe {
  ordem: OrdemResumo; clienteId?: number | null; observacao?: string | null; versao: number
  bomVersao?: number | null; roteiroVersao?: number | null; liberadaEm?: string | null; previa: boolean
  pendenciasLiberacao: string[]; proximosStatus: StatusOP[]
  materiais: MaterialOP[]; operacoes: OperacaoOP[]; eventos: EventoOP[]
}

export interface MaquinaOpcao { id: number; codigo: string; descricao: string; centroTrabalhoId: number; tipoMaquinaId: number; status: StatusMaquina }

export interface CargaOP { ordemId: number; numero: string; produtoNome: string; status: StatusOP; minutos: number; estimada: boolean }

export interface CargaCentro {
  centroId: number; centroCodigo: string; centroNome: string; maquinas: number; capacidadeMin: number
  cargaMin: number; ocupacaoPercentual?: number | null; ordens: CargaOP[]
}

export interface Carga { de: string; ate: string; centros: CargaCentro[]; ordensSemRoteiro: string[] }

export const TIPOS_ITEM: Record<TipoItem, string> = {
  MATERIA_PRIMA: 'Matéria-prima', SEMIACABADO: 'Semiacabado', ACABADO: 'Acabado', EMBALAGEM: 'Embalagem', INSUMO: 'Insumo',
}

export const STATUS_VERSAO: Record<StatusVersao, { rotulo: string; tom: 'success' | 'warning' | 'neutral' }> = {
  RASCUNHO: { rotulo: 'Rascunho', tom: 'warning' },
  ATIVA: { rotulo: 'Ativa', tom: 'success' },
  ATIVO: { rotulo: 'Ativo', tom: 'success' },
  OBSOLETA: { rotulo: 'Obsoleta', tom: 'neutral' },
  OBSOLETO: { rotulo: 'Obsoleto', tom: 'neutral' },
}

export const STATUS_OP: Record<StatusOP, { rotulo: string; tom: 'success' | 'info' | 'warning' | 'danger' | 'neutral' | 'purple' }> = {
  PLANEJADA: { rotulo: 'Planejada', tom: 'neutral' },
  LIBERADA: { rotulo: 'Liberada', tom: 'info' },
  EM_PRODUCAO: { rotulo: 'Em produção', tom: 'purple' },
  PAUSADA: { rotulo: 'Pausada', tom: 'warning' },
  CONCLUIDA: { rotulo: 'Concluída', tom: 'success' },
  ENCERRADA: { rotulo: 'Encerrada', tom: 'neutral' },
  CANCELADA: { rotulo: 'Cancelada', tom: 'danger' },
}

/** Mesma regra do backend (OrdemProducaoService.exigeMotivo). */
export const exigeMotivo = (de: StatusOP, para: StatusOP) =>
  para === 'CANCELADA' || para === 'PAUSADA' || (de === 'LIBERADA' && para === 'PLANEJADA') || (de === 'CONCLUIDA' && para === 'EM_PRODUCAO')

// ── Sprint 3: execução (chão de fábrica) ───────────────────────────────
export type CategoriaParada = 'SETUP' | 'MANUTENCAO' | 'FALTA_MATERIAL' | 'QUALIDADE' | 'OPERACIONAL' | 'EXTERNA' | 'OUTRO'
export type StatusApontamento = 'EM_ANDAMENTO' | 'PAUSADO' | 'FINALIZADO' | 'ESTORNADO'

export interface MotivoParada { id: number; codigo: string; descricao: string; categoria: CategoriaParada; planejada: boolean; ativo: boolean }

export interface ParadaMaquina {
  id: number; maquinaId?: number | null; maquinaCodigo?: string | null; motivoId: number; motivoDescricao: string
  categoria: CategoriaParada; planejada: boolean; apontamentoId?: number | null; ordemId?: number | null
  inicio: string; fim?: string | null; minutos: number; observacao?: string | null; usuarioNome?: string | null
}

export interface Apontamento {
  id: number; ordemId: number; ordemNumero: string; produtoNome: string; unidade: string; operacaoId: number
  operacaoSequencia: number; operacaoDescricao: string; centroNome: string; maquinaId?: number | null; maquinaCodigo?: string | null
  operadorId: number; operadorNome?: string | null; status: StatusApontamento; inicio: string; fim?: string | null
  minutosParado: number; minutosTrabalhados: number; quantidadeBoa: number; quantidadeRefugo: number
  motivoRefugo?: string | null; lote?: string | null; concluiuOperacao: boolean; entradaEstoque: boolean
  ultimaOperacao: boolean; paradaAberta?: ParadaMaquina | null; observacao?: string | null; motivoEstorno?: string | null
}

export interface ConsumoOP {
  id: number; produtoId: number; codigo?: string; nome: string; unidade: string; quantidade: number; custoUnitario?: number | null
  loteMaterial?: string | null; apontamentoId?: number | null; observacao?: string | null; usuarioNome?: string | null
  createdAt: string; estornado: boolean; motivoEstorno?: string | null
}

export interface FilaItem {
  operacaoId: number; ordemId: number; ordemNumero: string; ordemStatus: StatusOP; prioridade: number; fimPrevisto: string
  atrasada: boolean; produtoNome: string; unidade: string; quantidadeOrdem: number; sequencia: number; descricao: string
  operacaoStatus: 'PENDENTE' | 'EM_EXECUCAO' | 'CONCLUIDA'; tipoMaquinaId?: number | null; tipoMaquinaNome?: string | null
  maquinaPlanejadaId?: number | null; maquinaPlanejadaCodigo?: string | null; produzidoBoa: number; refugo: number
  ultimaOperacao: boolean; instrucoes?: string | null
}

export interface MaquinaChao {
  id: number; codigo: string; descricao: string; centroTrabalhoId: number; tipoMaquinaId: number; status: StatusMaquina
  paradaAberta?: ParadaMaquina | null; apontamentoAbertoId?: number | null; ordemEmUso?: string | null
}

export interface ChaoRecursos { centros: { id: number; codigo: string; nome: string }[]; maquinas: MaquinaChao[]; motivos: MotivoParada[] }

export interface OperacaoAndamento {
  operacaoId: number; sequencia: number; descricao: string; centroNome: string; status: 'PENDENTE' | 'EM_EXECUCAO' | 'CONCLUIDA'
  tempoPrevistoMin: number; minutosTrabalhados: number; minutosParado: number; quantidadeBoa: number; quantidadeRefugo: number
  emProcesso?: number | null; apontamentosAbertos: number
}

export interface MaterialAndamento {
  produtoId: number; codigo?: string; nome: string; unidade: string; previsto: number; consumido: number; saldoEstoque: number; sugerido: number
}

export interface ExecucaoOP {
  ordemId: number; numero: string; status: StatusOP; quantidade: number; unidade: string; produzido: number; refugo: number
  percentualProduzido: number; operacoes: OperacaoAndamento[]; materiais: MaterialAndamento[]; apontamentos: Apontamento[]
  consumos: ConsumoOP[]; paradas: ParadaMaquina[]
}

export const CATEGORIAS_PARADA: Record<CategoriaParada, string> = {
  SETUP: 'Preparação (setup)', MANUTENCAO: 'Manutenção', FALTA_MATERIAL: 'Falta de material', QUALIDADE: 'Qualidade',
  OPERACIONAL: 'Operacional', EXTERNA: 'Externa (energia, utilidades)', OUTRO: 'Outro',
}

export const STATUS_APONTAMENTO: Record<StatusApontamento, { rotulo: string; tom: 'success' | 'info' | 'warning' | 'neutral' | 'danger' }> = {
  EM_ANDAMENTO: { rotulo: 'Em andamento', tom: 'info' },
  PAUSADO: { rotulo: 'Pausado', tom: 'warning' },
  FINALIZADO: { rotulo: 'Finalizado', tom: 'success' },
  ESTORNADO: { rotulo: 'Estornado', tom: 'neutral' },
}

/** Chave de idempotência dos comandos do chão de fábrica (o mesmo comando reenviado não duplica). */
export const novaChave = () =>
  (typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`)
