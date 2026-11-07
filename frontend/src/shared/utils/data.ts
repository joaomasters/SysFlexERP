/**
 * Datas "AAAA-MM-DD" no fuso de quem está usando (Brasil), para filtros e campos <input type="date">.
 * Não use toISOString().slice(0, 10): ele converte para UTC e, depois das 21h, já dá o dia seguinte.
 */
export const dataISO = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

/** Hoje (ou hoje mais/menos N dias) no formato AAAA-MM-DD. */
export function hojeISO(deslocamentoDias = 0) {
  const d = new Date()
  d.setDate(d.getDate() + deslocamentoDias)
  return dataISO(d)
}
