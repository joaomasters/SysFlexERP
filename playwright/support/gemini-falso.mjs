// Imitação mínima da Interactions API do Gemini, para os testes do assistente rodarem
// sem chave nem cota. Uso: node support/gemini-falso.mjs [porta]
// e o backend com GEMINI_URL=http://127.0.0.1:<porta>/v1beta e GEMINI_API_KEY=qualquer.
//
// 1ª chamada: pede a consulta conforme a pergunta. 2ª: devolve como texto o resultado
// que o backend mandou, provando que os números vêm do banco e não do modelo.
// GET /ultima devolve o último corpo recebido, para o teste conferir o que saiu do sistema.
import { createServer } from 'node:http'

const porta = Number(process.argv[2] ?? 5545)
let ultima = null

function chamadaPara(pergunta) {
  const hoje = new Date().toLocaleDateString('sv-SE', { timeZone: 'America/Sao_Paulo' })
  const custo = pergunta.match(/custo d[aeo] (.+?)\??$/i)
  if (custo) return { name: 'custo_produto', arguments: { busca: custo[1] } }
  if (/estoque baixo/i.test(pergunta)) return { name: 'estoque_baixo', arguments: {} }
  if (/mais vend/i.test(pergunta)) return { name: 'produtos_mais_vendidos', arguments: { data_inicio: hoje, data_fim: hoje, limite: 5 } }
  return { name: 'resumo_vendas', arguments: { data_inicio: hoje, data_fim: hoje } }
}

createServer((req, res) => {
  if (req.method === 'GET' && req.url === '/ultima') {
    res.writeHead(200, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify(ultima))
    return
  }
  let corpo = ''
  req.on('data', c => { corpo += c })
  req.on('end', () => {
    const pedido = JSON.parse(corpo)
    ultima = { chave: req.headers['x-goog-api-key'], corpo: pedido }
    const resultado = pedido.input.find(p => p.type === 'function_result')
    const pergunta = pedido.input.filter(p => p.type === 'user_input').at(-1).content[0].text
    const steps = resultado
      ? [{ type: 'model_output', content: [{ type: 'text', text: `Resultado: ${resultado.result[0].text}` }] }]
      : [
          { type: 'thought', signature: 'assinatura-falsa' },
          { type: 'function_call', id: 'call_1', ...chamadaPara(pergunta) },
        ]
    res.writeHead(200, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify({ id: 'falso', status: 'completed', steps }))
  })
}).listen(porta, '127.0.0.1')
