/**
 * Cores para o que não aceita classe do Tailwind (Recharts, SVG, canvas).
 * Espelham os tokens de tailwind.config.js e as variáveis --chart-* do index.css:
 * ao trocar a identidade visual, atualizar os três.
 */
export const COR_GRAFICO = {
  primaria: '#0f6cbd',  // primary-600
  sucesso: '#107c10',   // success-600
  alerta: '#9e6000',    // warning-600
  perigo: '#c50f1f',    // danger-600
  custos: '#ca5010',
  grade: '#e1e5ea',
  eixo: '#5f6b7a',
} as const
