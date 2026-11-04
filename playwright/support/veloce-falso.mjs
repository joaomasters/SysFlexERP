// Imitação mínima da API Smart TEF da Veloce (manual v1027), para os testes da
// maquininha rodarem sem a máquina. Uso: node support/veloce-falso.mjs [porta]
// e o backend com PAGAMENTO_VELOCE_URL=http://127.0.0.1:<porta>/v1.
//
// - Login aceita qualquer e-mail; a senha "errada" é recusada.
// - Só existe o dispositivo de token EOIVM.
// - A cobrança é aprovada 1,5 s depois e a Veloce falsa chama a url_retorno,
//   menos: valor terminado em ,13 → negada; valor 9,99 → fica esperando o cartão.
// - GET /ultimo devolve o último pagamento criado e o resultado do aviso enviado.
import { createServer } from 'node:http'

const porta = Number(process.argv[2] ?? 5546)
const TOKEN_API = 'token-api-falso'
const pagamentos = []
let ultimo = null
let ultimoAviso = null

const agora = () => new Date().toLocaleString('sv-SE', { timeZone: 'America/Sao_Paulo' })
const responder = (res, status, corpo) => {
  res.writeHead(status, { 'Content-Type': 'application/json' })
  res.end(JSON.stringify(corpo))
}

async function avisar(p) {
  try {
    const r = await fetch(p.url_retorno, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(p),
    })
    ultimoAviso = { url: p.url_retorno, status: r.status }
  } catch (e) {
    ultimoAviso = { url: p.url_retorno, erro: String(e) }
  }
}

createServer((req, res) => {
  let texto = ''
  req.on('data', c => { texto += c })
  req.on('end', () => {
    const url = new URL(req.url, 'http://x')
    const corpo = texto ? JSON.parse(texto) : {}
    const autenticado = req.headers.token === TOKEN_API

    if (req.method === 'GET' && url.pathname === '/ultimo') return responder(res, 200, { pagamento: ultimo, aviso: ultimoAviso })

    if (req.method === 'POST' && url.pathname === '/v1/acessar') {
      if (corpo.senha === 'errada') return responder(res, 400, { ok: false, message: 'Senha incorreta', result: [] })
      return responder(res, 200, { ok: true, message: 'Acesso permitido', result: { token: TOKEN_API } })
    }
    if (!autenticado) return responder(res, 401, { ok: false, message: 'Token inválido', result: null })

    if (req.method === 'POST' && url.pathname === '/v1/pagamento/criar') {
      if (corpo.token !== 'EOIVM') return responder(res, 404, { ok: false, message: 'Token de dispositivo não encontrado', result: null })
      const p = {
        id_tbl_pagamento: pagamentos.length + 1, pagamento_id: corpo.pagamento_id, indice: corpo.indice,
        valor: corpo.valor, tipo: corpo.tipo, parcelas: corpo.parcelas, token: corpo.token, cnpj: corpo.cnpj,
        url_retorno: corpo.url_retorno, status: 0, status_cancelamento: null, dt_exclusao: null,
        bandeira: null, auth_code: null, adquirente_code: null, dt_cadastro: agora(),
      }
      pagamentos.push(p)
      ultimo = { ...corpo }
      const centavos = Math.round(Number(corpo.valor) * 100)
      if (centavos !== 999) {
        setTimeout(() => {
          if (p.dt_exclusao) return
          if (centavos % 100 === 13) {
            Object.assign(p, { status: 3, status_mensagem: 'Saldo insuficiente' })
          } else {
            Object.assign(p, { status: 2, bandeira: 'MASTERCARD', auth_code: '076442', adquirente_code: '882342', dt_pagamento: agora() })
          }
          avisar(p)
        }, 1500)
      }
      return responder(res, 200, { ok: true, message: 'Sucesso ao criar pagamento', result: p })
    }

    if (req.method === 'GET' && url.pathname === '/v1/pagamento/listar') {
      const daEmpresa = pagamentos.filter(p => p.cnpj === url.searchParams.get('cnpj'))
      if (!url.searchParams.get('dt_incial') || !url.searchParams.get('dt_final')) {
        return responder(res, 400, { ok: false, message: 'Período obrigatório', result: [] })
      }
      return daEmpresa.length
        ? responder(res, 200, { ok: true, message: 'A pesquisa retornou resultados', result: daEmpresa })
        : responder(res, 404, { ok: false, message: 'A pesquisa não retornou resultados', result: [] })
    }

    if (req.method === 'POST' && url.pathname === '/v1/pagamento/rejeitar') {
      const p = pagamentos.find(x => x.pagamento_id === corpo.pagamento_id && x.token === corpo.token)
      if (!p) return responder(res, 404, { ok: false, message: 'Token de dispositivo não encontrado', result: null })
      if (p.status === 2 || p.dt_exclusao) return responder(res, 422, { ok: false, message: 'Pagamento já realizado ou rejeitado', result: null })
      p.dt_exclusao = agora()
      return responder(res, 200, { ok: true, message: 'Sucesso ao rejeitar pagamento', result: null })
    }

    responder(res, 404, { ok: false, message: 'Rota inexistente na Veloce falsa', result: null })
  })
}).listen(porta, '127.0.0.1')
