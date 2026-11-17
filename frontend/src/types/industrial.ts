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
  ligaId?: number | null; ligaCodigo?: string | null; codigoDesenho?: string; revisaoDesenho?: string; pesoUnitarioKg?: number | null; pesoLinearKgM?: number | null
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

// ── Sprint 4: extrusão ─────────────────────────────────────────────────
export interface ElementoLiga { elemento: string; minimo?: number | null; maximo?: number | null }

export interface Liga {
  id: number; codigo: string; descricao: string; norma?: string | null; densidadeGCm3?: number | null
  observacao?: string | null; ativo: boolean; elementos: ElementoLiga[]
}

export type StatusLoteMp = 'QUARENTENA' | 'LIBERADO' | 'BLOQUEADO'

export interface ComposicaoLote { elemento: string; percentual: number; minimo?: number | null; maximo?: number | null; dentro: boolean }

export interface LoteMp {
  id: number; numero: string; produtoId: number; produtoNome: string; unidade: string; ligaId: number; ligaCodigo: string
  fornecedorId?: number | null; fornecedorNome?: string | null; certificado?: string | null; dataRecebimento: string
  quantidade: number; consumido: number; saldo: number; diametroMm?: number | null; comprimentoMm?: number | null
  status: StatusLoteMp; motivoStatus?: string | null; statusEm?: string | null; observacao?: string | null; versao: number
  composicao: ComposicaoLote[]; desvios: string[]
}

export interface LoteDisponivel { id: number; numero: string; ligaCodigo: string; saldo: number; dataRecebimento: string; certificado?: string | null }

export type EtapaReceita = 'AQUECIMENTO' | 'EXTRUSAO' | 'RESFRIAMENTO' | 'TRATAMENTO_TERMICO' | 'OUTRA'

export interface ParametroReceita {
  id?: number; codigo: string; nome: string; unidade?: string | null; alvo?: number | null; minimo?: number | null
  maximo?: number | null; obrigatorio: boolean
}

export interface Receita {
  id: number; codigo: string; descricao: string; etapa: EtapaReceita; ligaId: number; ligaCodigo: string
  tipoMaquinaId: number; tipoMaquinaNome: string; produtoId?: number | null; produtoNome?: string | null; revisao: number
  observacao?: string | null; ativo: boolean; updatedAt: string; parametros: ParametroReceita[]
}

export interface ValorMedicao {
  codigo: string; nome: string; unidade?: string | null; valor: number; alvo?: number | null; minimo?: number | null
  maximo?: number | null; dentroFaixa: boolean
}

export interface Medicao {
  id: number; ordemId: number; ordemNumero: string; apontamentoId?: number | null; maquinaCodigo?: string | null
  receitaId: number; receitaCodigo: string; receitaRevisao: number; etapa: EtapaReceita; origem: 'MANUAL' | 'SENSOR'
  medidoEm: string; foraEspecificacao: boolean; observacao?: string | null; usuarioNome?: string | null; valores: ValorMedicao[]
}

export type TipoPerda = 'TOPO_TARUGO' | 'PONTA_PERFIL' | 'RETALHO' | 'SUCATA_PROCESSO' | 'OUTRO'

export interface PerdaMetalica {
  id: number; ordemId: number; apontamentoId?: number | null; tipo: TipoPerda; quantidadeKg: number; observacao?: string | null
  usuarioNome?: string | null; createdAt: string; estornada: boolean; motivoEstorno?: string | null
}

export interface Rendimento {
  entradaKg?: number | null; produzidoKg?: number | null; perdasKg: number; naoApuradoKg?: number | null
  rendimentoPercentual?: number | null; avisos: string[]
}

export interface MetalurgiaOP {
  medicoes: Medicao[]; perdas: PerdaMetalica[]; rendimento: Rendimento
  lotesConsumidos: { loteId: number; numero: string; ligaCodigo: string; quantidade: number }[]
}

export const STATUS_LOTE_MP: Record<StatusLoteMp, { rotulo: string; tom: 'success' | 'warning' | 'danger' }> = {
  QUARENTENA: { rotulo: 'Quarentena', tom: 'warning' },
  LIBERADO: { rotulo: 'Liberado', tom: 'success' },
  BLOQUEADO: { rotulo: 'Bloqueado', tom: 'danger' },
}

export const ETAPAS_RECEITA: Record<EtapaReceita, string> = {
  AQUECIMENTO: 'Aquecimento', EXTRUSAO: 'Extrusão', RESFRIAMENTO: 'Resfriamento', TRATAMENTO_TERMICO: 'Tratamento térmico', OUTRA: 'Outra',
}

export const TIPOS_PERDA: Record<TipoPerda, string> = {
  TOPO_TARUGO: 'Topo de tarugo', PONTA_PERFIL: 'Ponta de perfil', RETALHO: 'Retalho', SUCATA_PROCESSO: 'Sucata de processo', OUTRO: 'Outra',
}

/** Valor dentro de [mínimo, máximo]; limite em branco não restringe (mesma regra do backend). */
export const dentroDaFaixa = (v: number, min?: number | null, max?: number | null) =>
  (min == null || v >= min) && (max == null || v <= max)

/** "460 a 500 °C", "até 0,35", "a partir de 2" ou "sem faixa". */
export const faixaTexto = (min?: number | null, max?: number | null, unidade?: string | null) => {
  const f = (x: number) => x.toLocaleString('pt-BR', { maximumFractionDigits: 4 })
  const u = unidade ? ` ${unidade}` : ''
  if (min != null && max != null) return `${f(min)} a ${f(max)}${u}`
  if (max != null) return `até ${f(max)}${u}`
  if (min != null) return `a partir de ${f(min)}${u}`
  return 'sem faixa'
}

// ── Sprints 5 e 6: qualidade, rastreabilidade e expedição ──────────────
export type SituacaoLote = 'QUARENTENA' | 'LIBERADO' | 'BLOQUEADO' | 'ESTORNADO'

export interface LoteProduto {
  id: number; codigo: string; produtoId: number; produtoNome: string; unidade: string; ordemId?: number | null
  ordemNumero?: string | null; origem: 'PRODUCAO' | 'DIVISAO' | 'UNIAO'; quantidadeInicial: number; saldo: number
  reservado: number; disponivel: number; situacao: SituacaoLote; motivoSituacao?: string | null; situacaoEm?: string | null
  createdAt: string; versao: number; temPlano: boolean
}

export interface Etiqueta {
  codigo: string; produtoNome: string; unidade: string; quantidade: number; ordemNumero?: string | null
  data?: string | null; situacao: SituacaoLote; qrSvg: string
}

export interface CaracteristicaPlano {
  id?: number; codigo: string; nome: string; tipo: 'MEDIDA' | 'ATRIBUTO'; unidade?: string | null
  minimo?: number | null; maximo?: number | null; obrigatorio: boolean
}

export interface PlanoInspecao {
  id: number; codigo: string; descricao: string; produtoId: number; produtoNome: string; revisao: number
  observacao?: string | null; ativo: boolean; updatedAt: string; caracteristicas: CaracteristicaPlano[]
}

export interface InspecaoLote {
  id: number; loteId: number; loteCodigo: string; planoCodigo: string; planoRevisao: number; resultado: 'APROVADO' | 'REPROVADO'
  amostra?: number | null; observacao?: string | null; inspetorNome?: string | null; createdAt: string
  valores: { codigo: string; nome: string; tipo: string; unidade?: string | null; valorNumero?: number | null; minimo?: number | null; maximo?: number | null; conforme: boolean }[]
  naoConformidadeNumero?: string | null
}

export type DisposicaoNc = 'USAR_COMO_ESTA' | 'RETRABALHAR' | 'SUCATEAR' | 'SEM_LOTE'

export interface NaoConformidade {
  id: number; numero: string; origem: 'INSPECAO' | 'PROCESSO' | 'CLIENTE' | 'INTERNA'; loteId?: number | null
  loteCodigo?: string | null; ordemId?: number | null; ordemNumero?: string | null; inspecaoId?: number | null
  descricao: string; status: 'ABERTA' | 'ENCERRADA'; disposicao?: DisposicaoNc | null; causa?: string | null
  acaoCorretiva?: string | null; abertaPorNome?: string | null; createdAt: string; encerradaPorNome?: string | null
  encerradaEm?: string | null; versao: number
}

export interface LoteResumo { id: number; codigo: string; produtoNome: string; situacao: SituacaoLote; quantidadeInicial: number; saldo: number; ordemNumero?: string | null; nivel: number }
export interface DestinoLote { expedicaoNumero: string; cliente: string; clienteDocumento?: string | null; carregadaEm?: string | null; loteCodigo: string; quantidade: number }

export interface RastreioLote {
  lote: LoteProduto; qrSvg: string; ascendentes: LoteResumo[]; descendentes: LoteResumo[]
  origens: { loteMpId: number; numero: string; material: string; ligaCodigo: string; fornecedor?: string | null; certificado?: string | null; quantidade: number; ordemNumero?: string | null }[]
  destinos: DestinoLote[]; inspecoes: InspecaoLote[]; naoConformidades: NaoConformidade[]
  movimentos: { tipo: string; quantidade: number; documento?: string | null; usuarioNome?: string | null; createdAt: string }[]
  medicoesForaDaFaixa: number
}

export interface RastreioLoteMp {
  loteMpId: number; numero: string; material: string; ligaCodigo: string; fornecedor?: string | null; certificado?: string | null
  status: StatusLoteMp; ordens: string[]; lotesProduzidos: LoteResumo[]; destinos: DestinoLote[]
}

export interface ResultadoBusca { tipo: 'LOTE' | 'LOTE_MP'; id: number; codigo: string; descricao: string }

export type StatusExpedicao = 'ABERTA' | 'CONFERIDA' | 'CARREGADA' | 'CANCELADA'

export interface ExpedicaoResumo {
  id: number; numero: string; clienteId: number; clienteNome: string; pedidoReferencia?: string | null; dataPrevista: string
  status: StatusExpedicao; itens: number; notaFiscalId?: number | null
}

export interface ReservaExpedicao { id: number; loteId: number; loteCodigo: string; loteSituacao: SituacaoLote; quantidade: number; conferida: boolean; conferidaEm?: string | null }

export interface ItemExpedicao {
  id: number; produtoId: number; produtoNome: string; unidade: string; quantidade: number; reservado: number; pendente: number
  pesoKg?: number | null; reservas: ReservaExpedicao[]
}

export interface Expedicao {
  id: number; numero: string; clienteId: number; clienteNome: string; clienteDocumento?: string | null; clienteEndereco?: string | null
  pedidoReferencia?: string | null; dataPrevista: string; status: StatusExpedicao; transportadora?: string | null; placa?: string | null
  motorista?: string | null; observacao?: string | null; carregadaEm?: string | null; carregadaPorNome?: string | null
  notaFiscalId?: number | null; motivoCancelamento?: string | null; versao: number; pesoTotalKg: number; pesoCompleto: boolean
  itens: ItemExpedicao[]; pendencias: string[]
}

export const SITUACAO_LOTE: Record<SituacaoLote, { rotulo: string; tom: 'success' | 'warning' | 'danger' | 'neutral' }> = {
  QUARENTENA: { rotulo: 'Quarentena', tom: 'warning' },
  LIBERADO: { rotulo: 'Liberado', tom: 'success' },
  BLOQUEADO: { rotulo: 'Bloqueado', tom: 'danger' },
  ESTORNADO: { rotulo: 'Estornado', tom: 'neutral' },
}

export const DISPOSICOES_NC: Record<Exclude<DisposicaoNc, 'SEM_LOTE'>, string> = {
  USAR_COMO_ESTA: 'Usar como está (concessão)', RETRABALHAR: 'Retrabalhar (fica bloqueado até reinspeção)', SUCATEAR: 'Sucatear (baixa o saldo e o estoque)',
}

export const ORIGENS_NC: Record<NaoConformidade['origem'], string> = {
  INSPECAO: 'Inspeção', PROCESSO: 'Processo', CLIENTE: 'Reclamação de cliente', INTERNA: 'Interna',
}

export const STATUS_EXPEDICAO: Record<StatusExpedicao, { rotulo: string; tom: 'info' | 'purple' | 'success' | 'neutral' }> = {
  ABERTA: { rotulo: 'Aberta', tom: 'info' },
  CONFERIDA: { rotulo: 'Conferida', tom: 'purple' },
  CARREGADA: { rotulo: 'Carregada', tom: 'success' },
  CANCELADA: { rotulo: 'Cancelada', tom: 'neutral' },
}
