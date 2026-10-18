# Maquininhas de cartão: guia de configuração

Este guia mostra como ligar as maquininhas **Stone**, **Mercado Pago** e **Sicredi** ao PDV do SysFlex.
Toda a configuração é feita **por empresa** na tela **Administração → Maquininhas** (`/acesso/maquininhas`).
O acesso a essa tela é controlado pela permissão `MAQUININHAS`: SUPER_ADMIN e ADMIN têm acesso por padrão.

---

## 1. Antes de tudo: chave de criptografia (uma vez, no servidor)

Os tokens e chaves das adquirentes ficam criptografados no banco de dados (AES-256-GCM).
Defina esta variável de ambiente no backend (no Railway: *Variables*):

```
PAGAMENTO_CRIPTO_CHAVE=<texto longo e aleatório>
```

Para gerar um valor: `openssl rand -base64 32`

- Sem essa variável, o sistema funciona normalmente, mas **não deixa salvar credenciais**. A tela mostra um aviso vermelho.
- **Não troque a chave depois de cadastrar credenciais.** As credenciais já salvas ficariam ilegíveis e seria preciso cadastrá-las de novo.
- Guarde a chave num cofre de senhas. Ela vale para todas as empresas.

---

## 2. Os três modos de integração

Cada maquininha é cadastrada com um modo de integração:

| Modo | Como funciona | Precisa instalar algo no caixa? | Disponível para |
|---|---|---|---|
| **API em nuvem** | O servidor envia a cobrança pela internet e a maquininha "acorda" pedindo o cartão. O PDV acompanha até aprovar ou negar. | Não | Mercado Pago (Point), Stone (Stone Connect) |
| **Agente local (TEF)** | O navegador do caixa chama um programa TEF instalado no computador, ligado à maquininha. | Sim, o agente TEF (ver seção 6) | Stone, Sicredi, Mercado Pago |
| **Manual** | Maquininha não integrada: o operador digita o valor nela e informa NSU e autorização no PDV. | Não | Todas |

Em todos os modos, a cobrança fica registrada na tabela `transacao_terminal`. A venda só fecha com cartão "integrado" se a transação estiver **APROVADA** e bater com a venda, o tipo (débito/crédito) e o valor.

---

## 3. Mercado Pago (Point Smart / Point Pro), API em nuvem

1. **Credenciais.** Entre em <https://www.mercadopago.com.br/developers/panel/app>, crie ou abra a aplicação e vá em *Credenciais de produção*. Copie o **Access Token** (`APP_USR-...`). Para testes, use o de teste (`TEST-...`).
2. No SysFlex, abra **Maquininhas → Mercado Pago → Configurar**:
   - marque **Adquirente ativa**;
   - escolha o ambiente (Sandbox ou Produção);
   - cole o **Access Token**;
   - salve e clique em **Testar conexão**. O teste mostra o apelido da conta.
3. **Vincule a Point à conta.** Pelo app do Mercado Pago, a maquininha precisa estar na mesma conta do token.
4. Clique em **Dispositivos**. As Points da conta aparecem na lista.
   - Se uma Point não estiver em **Modo PDV**, clique em **Ativar modo PDV**. Reinicie a maquininha se ela não mudar sozinha.
   - Clique em **Cadastrar** na Point desejada. O formulário abre com o `device_id` preenchido (ex.: `PAX_A910__SMARTPOS1234567`). Dê um nome, como "Caixa 1 — Mercado Pago", e salve.
5. **Webhook (recomendado).** No painel do Mercado Pago, em *Suas integrações → Webhooks*:
   - cole a URL que aparece no fim da tela de Maquininhas: `https://<seu-domínio>/api/pagamento/webhook/mercadopago?tenant=<código>`;
   - marque os eventos **Pagamentos** e **Integrações Point**;
   - copie a **Assinatura secreta**, volte ao SysFlex e cole no campo *Assinatura secreta do webhook*. A partir daí, notificações sem assinatura válida são recusadas.

O mesmo Access Token passa a ser usado pelo **PIX** do PDV dessa empresa. A variável `MP_ACCESS_TOKEN` do servidor continua valendo como reserva para empresas sem token cadastrado.

---

## 4. Stone

A Stone tem dois caminhos. Qual deles vale para você depende do que for contratado com a Stone.

### 4a. Stone Connect, API em nuvem (POS Android)

1. Peça ao seu gerente Stone a **habilitação do Stone Connect** e a **Secret Key** da conta (`sk_...` em produção, `sk_test_...` em sandbox).
2. Em **Maquininhas → Stone → Configurar**: marque Ativa, cole a Secret Key, salve e use **Testar conexão**.
3. Cadastre a maquininha em modo **API em nuvem**, informando o **número de série** da POS (etiqueta atrás do aparelho).
4. Webhook (opcional): no painel da Stone/Pagar.me, cadastre `https://<seu-domínio>/api/pagamento/webhook/stone?tenant=<código>` com os eventos de pedido (`order.*`).

> ⚠️ **Homologação:** a integração usa a API de pedidos da Stone (core v5) com `poi_payment_settings`. Antes de usar em produção, faça uma venda de teste em sandbox com o time da Stone. Se o plano exigir algum campo a mais, todo o pedido é montado num único método: `StoneConnectProvedor.montarPedido()`.

### 4b. Stone via TEF no computador

Se a Stone fornecer um TEF instalado no computador (maquininha ligada por USB ou Bluetooth), cadastre a maquininha em modo **Agente local** e siga a seção 6.

---

## 5. Sicredi

A Sicredi **não oferece API em nuvem** para maquininhas. Há dois caminhos:

- **Agente local (TEF):** peça à Sicredi o TEF homologado para PDV (normalmente via PayGo ou SiTef), instale no computador do caixa junto com o agente da seção 6 e cadastre a maquininha em modo **Agente local**.
- **Manual:** cadastre em modo **Manual**. O operador passa o cartão na maquininha e informa NSU e autorização no PDV.

Em **Maquininhas → Sicredi → Configurar**, basta marcar como ativa e, se quiser, preencher o código do estabelecimento (campo informativo).

---

## 6. Contrato do agente local (TEF)

O PDV roda no navegador e chama um programa no próprio computador do caixa. Qualquer agente que siga este contrato funciona: uma ponte para o TEF da Stone, da Sicredi, PayGo, SiTef etc.

**Requisição** (feita pelo navegador do caixa):

```http
POST {agenteUrl}/transaction
Content-Type: application/json

{
  "amount": 4590,            // centavos
  "type": "CREDIT",          // "DEBIT" | "CREDIT"
  "installments": 3,         // 1 no débito
  "reference": "SYSFLEX-TT-123"  // id único da cobrança no SysFlex
}
```

**Resposta** (HTTP 200, assim que o cliente concluir na maquininha):

```json
{
  "status": "APPROVED",      // ou "DECLINED"
  "nsu": "000123456",
  "authorizationCode": "A1B2C3",
  "brand": "VISA",
  "message": "texto exibido quando negado"
}
```

Requisitos do agente:

- **Tempo:** o PDV espera até **120 segundos** pela resposta.
- **CORS:** o agente deve responder `Access-Control-Allow-Origin` com a origem do SysFlex (ex.: `https://sis-meet-house.vercel.app`) e aceitar o preflight `OPTIONS` com `Content-Type`.
- **Rede privada (Chrome):** como o site é HTTPS público chamando `localhost`, o Chrome envia um preflight com `Access-Control-Request-Private-Network: true`. O agente deve responder `Access-Control-Allow-Private-Network: true`.
- **Idempotência:** se o mesmo `reference` chegar duas vezes, o agente deve devolver o resultado da primeira vez em vez de cobrar de novo.

Se o agente não responder, o PDV oferece **Tentar de novo**, **Concluir manualmente** (o operador digita o comprovante) ou **Cancelar**.

---

## 7. Como fica no PDV

1. No pagamento, escolha **Débito** ou **Crédito**.
2. A maquininha usada por último naquele computador já vem selecionada. Dá para trocar no seletor.
3. Clique em **Enviar para maquininha** e o cliente paga.
4. Aprovado: o pagamento entra na lista com bandeira e NSU. Negado: aparece o motivo e o botão **Tentar novamente**.
5. Enquanto há uma cobrança em andamento, o modal não deixa trocar a forma de pagamento nem fechar. Cancele a cobrança primeiro.
6. Para remover um cartão **já aprovado** da lista, o sistema pede confirmação: o estorno precisa ser feito **na própria maquininha**.
7. Sem nenhuma maquininha cadastrada, o PDV continua oferecendo o registro manual de cartão, como antes.

---

## 8. Checklist de implantação

- [ ] `PAGAMENTO_CRIPTO_CHAVE` definida no backend
- [ ] Adquirente ativa e credencial cadastrada, com **Testar conexão** OK
- [ ] Maquininha cadastrada (e, no Mercado Pago, em modo PDV)
- [ ] Webhook cadastrado no painel da adquirente (recomendado)
- [ ] Venda de teste aprovada no PDV, conferindo NSU e autorização
- [ ] Venda de teste negada ou cancelada, conferindo que o PDV permite tentar de novo
- [ ] Para Agente local: CORS e Private Network testados no Chrome do caixa

## 9. Para desenvolvedores

| Peça | Onde |
|---|---|
| Migration (tabelas, permissão, correção do CHECK de `CHEQUE`) | `backend/src/main/resources/db/migration/V24__maquininhas_cartao.sql` |
| Fluxo da transação (máquina de estados) | `modules/pagamento/TransacaoTerminalService.java` |
| Adquirentes em nuvem | `modules/pagamento/provedor/` (implemente `ProvedorTerminalNuvem` para adicionar uma nova) |
| Criptografia das credenciais | `modules/pagamento/CredencialCripto.java` |
| Webhooks | `modules/pagamento/WebhookPagamentoController.java` (empresa via `?tenant=`, ver `TenantResolvingFilter`) |
| Validação no fechamento da venda | `PdvService.fecharVenda` → `TransacaoTerminalService.validarParaPagamento` |
| Tela de configuração | `frontend/src/modules/pagamento/MaquininhasPage.tsx` |
| Painel de cartão no PDV | `frontend/src/modules/pdv/components/PainelCartao.tsx` |
