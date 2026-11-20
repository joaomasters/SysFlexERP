/**
 * Ilustração do painel esquerdo da tela de login: barras crescentes com uma seta de
 * tendência, sobre ondas suaves. SVG plano (sem gradientes, brilhos ou animação), nas
 * cores da marca. Puramente decorativa (aria-hidden).
 */

/** Barra com o topo arredondado e a base reta. */
const barra = (x: number, topo: number, largura: number, base: number, raio = 7) =>
  `M${x},${base} V${topo + raio} Q${x},${topo} ${x + raio},${topo} H${x + largura - raio} Q${x + largura},${topo} ${x + largura},${topo + raio} V${base} Z`

export default function LoginIlustracao() {
  const base = 232
  const largura = 50
  const barras = [
    { x: 46, h: 62, cor: 'fill-primary-900' },
    { x: 116, h: 100, cor: 'fill-primary-800' },
    { x: 186, h: 140, cor: 'fill-primary-700' },
    { x: 256, h: 184, cor: 'fill-primary-600' },
  ]
  return (
    <div aria-hidden="true" className="pointer-events-none select-none">
      <svg viewBox="0 0 360 260" className="h-auto w-full max-w-md lg:max-w-lg xl:max-w-2xl 2xl:max-w-3xl">
        {/* Linhas de referência */}
        {[64, 118, 172].map(y => (
          <line key={y} x1="20" x2="340" y1={y} y2={y} className="stroke-primary-200" strokeWidth="1" strokeDasharray="3 5" />
        ))}
        {/* Barras */}
        {barras.map(b => (
          <path key={b.x} d={barra(b.x, base - b.h, largura, base)} className={b.cor} />
        ))}
        {/* Linha de base */}
        <line x1="20" x2="340" y1={base} y2={base} className="stroke-primary-300" strokeWidth="2" strokeLinecap="round" />
        {/* Seta de tendência */}
        <path
          d="M26,196 C86,168 150,150 204,112 S284,50 322,26"
          fill="none"
          className="stroke-primary-400"
          strokeWidth="6"
          strokeLinecap="round"
        />
        <polygon points="334,18 308,22 322,44" className="fill-primary-400" />
      </svg>
    </div>
  )
}

/** Ondas suaves de fundo, ancoradas na base do painel. */
export function LoginOndas() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 400 160"
      preserveAspectRatio="none"
      className="pointer-events-none absolute inset-x-0 bottom-0 h-40 w-full xl:h-52"
    >
      <path d="M0,70 C90,20 170,110 260,70 S370,30 400,50 V160 H0 Z" className="fill-primary-100" />
      <path d="M0,110 C80,70 180,140 270,105 S370,80 400,95 V160 H0 Z" className="fill-primary-200/70" />
    </svg>
  )
}
