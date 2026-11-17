# Contábil e Fiscal

Evolução do módulo Fiscal para um módulo Contábil e Fiscal preparado para a Reforma Tributária do
Consumo (RTC: IBS, CBS e Imposto Seletivo). Este documento é o registro vivo do programa: diagnóstico,
decisões, matriz de cobertura, fontes normativas, versões, testes e pendências. Ele só afirma o que
está implementado e verificado. O que não existe aparece como ausente.

Branch: `feature/contabil-fiscal` (a partir de `develop` em `cf94772`).

---

## 1. Diagnóstico (Etapa 0), feito no código em 16/11/2026

Classificação usada: **existente e funcional** · **incompleta** · **ausente** · **dependente de configuração** ·
**integração externa** (depende de serviço de terceiro ou oficial) · **definição normativa** (depende
de regra ainda não publicada ou não estabilizada).

### 1.1 Emissão de documentos fiscais

| Capacidade | Situação | Onde está / por quê |
|---|---|---|
| NFC-e (modelo 65) da venda do PDV, "CPF na nota" | **incompleta** | `modules/fiscal/emissao/NfceService.java`. O fluxo completo existe (numeração sob trava, chave, QR Code v2, rateio de desconto, DANFE), mas só há o provedor **simulado**. Não existe provedor real que fale com a SEFAZ. |
| Provedor real de comunicação com a SEFAZ | **ausente** / **integração externa** | `ProvedorFiscal` é a porta. A única implementação é `ProvedorFiscalSimulado`, ligada só com `fiscal.provedor-simulado=true` e recusada em produção. |
| Consulta de NFC-e pela chave | **existente** (nesta entrega) | `NfceService.consultar` + `ProvedorFiscal.consultarNfce`. Funciona no simulado; o provedor real ainda precisa implementar. |
| Cancelamento, inutilização, carta de correção | **ausente** | Não há método na porta nem tela. |
| Contingência (offline / EPEC) | **ausente** | O status `CONTINGENCIA` existe na tabela (V36), mas nada o produz. |
| NFC-e no regime normal (CST) | **ausente** | `NfceService` recusa `regime_tributario = 3`. |
| UF diferente do PR | **ausente** | `configuracao_fiscal.uf CHECK IN ('PR')` (V36). Validação de IE só para o PR (`DocumentosFiscais.inscricaoEstadualPrValida`). |
| NF-e (modelo 55) emitida pelo sistema | **ausente** | Existem a série/numeração e as pendências de configuração, mas não existe fluxo de emissão. |
| "Notas Fiscais de Saída" (`/fiscal/notas`) | **existente e funcional**, porém **registro manual** | `NotaFiscalService.java`. Registra uma nota emitida fora do sistema e baixa o estoque quando vira `EMITIDA`. Nada é transmitido. Antes a tela dizia "Nova Danfe" e "Emitida", o que sugeria autorização (corrigido nesta entrega, ver §3). |
| NFS-e / DANFSe | **ausente** / **integração externa** | O gerador oficial de DANFSe (Go) está em `C:\dev\calculadora-rtc-gerador-danfse` e não está integrado. |
| Certificado A1 | **existente e funcional** | `CertificadoDigital.java`. Lê PKCS#12, recusa vencido e sem chave privada; guarda cifrado (`CredencialCripto`, chave `PAGAMENTO_CRIPTO_CHAVE`). Senha e CSC nunca voltam na API (`ConfiguracaoFiscalDTO` só diz se está configurado). |
| Pendências da configuração | **existente e funcional** | `PendenciasFiscais.java`, calculadas de verdade. A emissão não liga com pendência. |
| CNPJ alfanumérico (Receita, jul/2026) | Emitente e chave: **existente**. Cadastro: **incompleta** antes desta entrega | `DocumentosFiscais.cnpjValido` e `ChaveAcesso` já aceitavam. O cadastro de fornecedores usava `estoque/DocumentoFiscal`, que só aceitava dígitos (recusava CNPJ alfanumérico válido); clientes não validavam nada. Unificado nesta entrega. |

### 1.2 Entradas, apuração, escrituração e contabilidade

| Capacidade | Situação | Onde está / por quê |
|---|---|---|
| Recebimento de mercadoria com XML da NF-e | **incompleta** | `RecebimentoService.uploadXml` guarda o XML como texto. Não há leitura do XML: não confere emitente, itens, impostos nem chave. Hoje não existe parser e, portanto, nenhum risco de XXE; quando a importação for feita, o parser precisa ter DTD e entidades externas desligadas. |
| Motor de tributação (ICMS, ST, PIS/COFINS, IPI) | **ausente** | O produto tem só NCM, CEST, CFOP, origem e CSOSN (`entity/Produto.java`). Não há regra por operação, UF ou destinatário. |
| IBS / CBS / IS (RTC) | **ausente** / **integração externa** / **definição normativa** | Ver §4. |
| Apuração de impostos | **ausente** | |
| Plano de contas, lançamentos em partidas dobradas | **ausente** | Nenhuma tabela contábil nas migrations V1–V44. O financeiro tem contas a pagar e receber com `categoria` em texto livre (V2). |
| DRE | **existente** (gerencial) | `frontend/src/modules/financeiro/DrePage.tsx`. É gerencial, sobre vendas e contas, e não sai de razão contábil. |
| SPED Fiscal / Contribuições / ECD / ECF, DCTFWeb, EFD-Reinf | **ausente** | |

### 1.3 Plataforma

| Capacidade | Situação | Onde está |
|---|---|---|
| Isolamento por empresa (tenant) | **existente e funcional** | Um schema por tenant (`tenant/`, Flyway por schema em `MigradorSchemasEmpresas`). |
| Vários estabelecimentos (matriz/filiais) por empresa | **ausente** | `configuracao_fiscal` é uma linha única (`id = 1`): um emitente por tenant. |
| Parâmetros fiscais versionados por vigência | **ausente** | O regime tributário é um campo único, sem data de início/fim. |
| Permissões e auditoria | **existente e funcional** | `@ExigirPermissao` + `AuditoriaAspect` (audita toda ação que não seja `VER`). Módulo `FISCAL` (V36). |
| Numeração sem MAX+1 | **existente e funcional** | O próximo número fica na configuração, incrementado sob `SELECT ... FOR UPDATE` (`ConfiguracaoFiscalRepository.travar`). |
| Dinheiro em `BigDecimal` / `NUMERIC` | **existente e funcional** | |

### 1.4 Diretórios oficiais locais

| Projeto | Versão / commit | Requisitos | Situação para o ERP |
|---|---|---|---|
| `C:\dev\calculadora-rtc-regime-regular` (Receita Federal, MIT) | tag `1.6.1`, `150cccb`, sem alterações locais | Java 21, Spring Boot 3.5; base SQLite gerada por `mvnw flyway:migrate -Dflyway.configFiles=flyway/flyway-pro.conf`; porta padrão 8080 (precisa de outra) | **integração externa**, não integrada. API `POST /api/calculadora/regime-geral` e `/observabilidade/regime-geral` (erro por item, `CALCULO_SIMULADO` e cabeçalho `x-warning-dados-simulados`). NF-e ABI (77) e DERE (94) ainda não implementados pela própria calculadora. |
| `C:\dev\calculadora-rtc-simples-nacional` | tag `0.0.6`, `c5b0f01` | Java 21 | **integração externa** + **definição normativa**. Só traz percentuais da 1ª faixa. As alíquotas efetivas (PGDAS-D) estão previstas para versão futura. **Não usar a 1ª faixa como padrão.** |
| `C:\dev\calculadora-rtc-gerador-danfse` | tag `1.0.0`, `8d79e6b` | Go 1.27+ (não instalado) ou Docker (instalado) | **integração externa**, não integrada. `POST /api/danfse`; limite de XML configurável (`XML_MAX_SIZE_BYTES`). |

O ERP fica em Java 17 e as calculadoras em Java 21. Por isso elas rodam **como serviços separados**
(processo ou container), consumidos por HTTP atrás de uma porta, e nunca como biblioteca dentro do ERP.

---

## 2. Decisões de arquitetura

1. **Monólito modular, portas e adaptadores para o que é novo.** Nada de microsserviços, Kafka ou outro banco. Toda capacidade que dependa de terceiro entra por uma porta (`ProvedorFiscal` já é uma), com um adaptador por fornecedor e um simulado só para teste.
2. **Calculadoras oficiais como serviços externos** com adaptador HTTP no ERP (Etapa 3). Se uma calculadora falhar, o resultado é *cálculo indisponível*: **nunca zero de imposto**.
3. **Estados separados para valores tributários** (Etapa 3): simulado, calculado, declarado, débito e crédito não se misturam numa coluna só.
4. **Migrations só aditivas.** Ampliar um `CHECK` (como no V45) é aditivo: nenhum dado existente muda.
5. **Transmissão fora da transação do banco** (implementado nesta entrega, §3).

---

## 3. Etapa 1, primeira entrega: semântica de estados e saneamento

### 3.1 O que mudou

**Emissão da NFC-e em três passos** (`NfceService.emitirParaVenda`):
1. Em uma transação: confere tudo, reserva o número sob a trava, **gera a chave de acesso** (cNF aleatório de 8 dígitos, diferente do número) e grava o documento `PENDENTE` com a chave. Confirma no banco **antes** de transmitir.
2. Sem transação: transmite pelo provedor. Nenhuma linha do banco fica travada esperando a SEFAZ.
3. Em outra transação: grava o resultado no documento e na venda.

Antes, tudo rodava numa única transação. Se o servidor caísse depois da SEFAZ autorizar, o rollback apagava o documento e devolvia o número: a nota existia na SEFAZ e não no sistema, e o número seria reusado.

**Novo status `DESCONHECIDO`** (migration `V45__fiscal_resultado_desconhecido.sql`). Contrato da porta (`ProvedorFiscal`):

| O que acontece na transmissão | Status | A venda pode emitir de novo? |
|---|---|---|
| SEFAZ autorizou | `AUTORIZADA` | não precisa (pedir de novo devolve a mesma) |
| SEFAZ recusou | `REJEITADA` / `DENEGADA` | sim, com novo número |
| Provedor garante que nada foi enviado (`FalhaAntesDoEnvioException`) | `ERRO` (na tela: "Não transmitida") | sim |
| Qualquer outra falha (timeout, conexão caída) | `DESCONHECIDO` | **não**, até consultar |
| Servidor caiu entre os passos 1 e 3 | fica `PENDENTE` | **não**, até consultar |

Antes, o timeout virava `ERRO` e liberava uma segunda emissão para a mesma venda: risco de duas notas autorizadas.

**Consulta pela chave**: `POST /fiscal/nfce/documentos/{id}/consultar` (permissão PDV/CRIAR, auditada). Só uma resposta definitiva (autorizada, rejeitada, denegada ou cancelada) muda o status. Sem resposta, ou se o provedor não consulta, a nota continua `DESCONHECIDO` e a mensagem orienta a conferir no portal da SEFAZ.

**Telas:**
- Fiscal → Emissão de Notas → Documentos mostra rótulos claros ("Resultado desconhecido", "Não transmitida", "Em transmissão"), um aviso quando há notas em aberto e o botão **Consultar situação**.
- O PDV avisa "sem resposta da SEFAZ: pode ter sido autorizada" e imprime o comprovante **não fiscal**. O cupom fiscal só sai com `AUTORIZADA`, como já era em `ReciboCupom`.
- A tela "Notas Fiscais de Saída" virou explicitamente **registro manual**: "Registrar nota de saída" no lugar de "Nova Danfe", status "Saída registrada" no lugar de "EMITIDA", e um subtítulo dizendo que nada é transmitido. O valor gravado no banco não mudou.

**CNPJ alfanumérico no cadastro**: fornecedores e clientes usam a mesma regra do emitente (`DocumentosFiscais.cnpjValido`, caracteres valendo ASCII − 48). No backend, `estoque/DocumentoFiscal` passou a delegar. No frontend, `shared/utils/documento.ts` substitui as cópias que existiam em `FornecedoresPage` e `ClientesPage`. O cliente passou a ter validação no backend (antes não tinha):
- no cadastro novo;
- na edição, só quando o documento muda. Documento antigo inválido não trava a edição e aparece no saneamento.

**Saneamento cadastral** (`GET /fiscal/saneamento`, aba "Saneamento cadastral" em Emissão de Notas). As contagens saem do banco na hora da consulta:

| Grupo | Severidade |
|---|---|
| Pendências do emitente | bloqueia emissão |
| Produtos ativos sem NCM, CFOP ou CSOSN (CSOSN só fora do regime normal) | bloqueia emissão |
| Clientes com CPF/CNPJ inválido | bloqueia emissão |
| Fornecedores com CPF/CNPJ inválido | atenção |
| Fornecedores sem CPF/CNPJ | atenção |

Cada grupo traz o impacto, a tela onde corrigir e até 20 exemplos.

### 3.2 Verificação

- Backend: `mvn test`, **508 testes, 0 falhas**. Novos:
  - `NfceServiceTest`: sem resposta → `DESCONHECIDO` com a chave gravada antes do envio; falha antes do envio → `ERRO`; bloqueio de nova emissão; consulta que resolve; consulta inconclusiva; cNF diferente do número.
  - `SaneamentoCadastralServiceTest`.
  - `ClienteDocumentoTest`.
  - CNPJ alfanumérico em `DocumentoFiscalTest` (exemplo oficial `12.ABC.345/01DE-35`).
- Migration V45 aplicada pelo Flyway num banco descartável (porta 5544) em todos os schemas de tenant. Conferido no catálogo: há exatamente um `CHECK` de status por schema, já com `DESCONHECIDO`.
- E2E (Playwright, backend com `FISCAL_PROVEDOR_SIMULADO=true` no banco descartável):
  - `fiscal/nfce-pdv.global.spec.ts`: 5/5, incluindo o fluxo novo. O PDV fica sem resposta, o comprovante sai não fiscal, a API recusa nova emissão (422), a tela mostra "Resultado desconhecido", "Consultar situação" resolve para "Autorizada" e a chave continua a mesma.
  - `fiscal/saneamento.spec.ts`: 2/2. O fornecedor com CNPJ alfanumérico é aceito pela tela e o dígito errado é recusado pela API.
  - `fiscal/emissao.spec.ts`: passa. O teste da lista vazia foi ajustado para o banco que já tem NFC-e de execuções anteriores.
  - Suíte completa: tudo passa, exceto `industrial/estrutura.global.spec.ts`. Nele, o centro recém-criado cai na 2ª página da listagem, porque o banco de teste já acumulou 38 centros. É um problema dos dados acumulados no banco de teste, não desta entrega.
- Para testar o resultado desconhecido de ponta a ponta, o provedor simulado "fica sem resposta" para o CPF de teste `111.444.777-35` (`ProvedorFiscalSimulado.CPF_SEM_RESPOSTA`). Isso só existe com o simulado ligado, que nunca roda em produção.

### 3.3 Integrações executadas nesta entrega

Nenhuma integração externa foi chamada. SEFAZ, calculadoras e DANFSe não foram acionados. A emissão foi exercitada só com o provedor simulado, em homologação.

### 3.4 Limitações conhecidas desta entrega

- "Não encontrada na SEFAZ" (cStat 217) na consulta ainda não tem tratamento próprio: o primeiro provedor real precisa traduzir para um status definitivo. Até lá, a nota continua `DESCONHECIDO`.
- A consulta resolve a nota, mas não inutiliza números perdidos (`REJEITADA`/`ERRO`). A inutilização é pendência.
- O saneamento ainda não cobre NCM inexistente na tabela oficial, só NCM em branco, nem endereço/IBGE de destinatário de NF-e.

---

## 3b. Etapa 1b: vigências, numeração e inutilização

### O que mudou
- **Regime tributário por vigência** (`fiscal_enquadramento`, V46; `modules/fiscal/parametros`):
  - Nada é editado nem apagado. Uma vigência nova encerra a aberta na véspera e não pode começar antes da atual (o passado não se reescreve).
  - Na migração, o regime que estava configurado vira a primeira vigência, desde 2000-01-01, com a observação de que o início real é desconhecido.
  - Trocar o regime na tela de configuração abre uma vigência a partir de hoje.
  - A NFC-e lê o regime vigente na data (`EnquadramentoService.regimeEm`) e nunca supõe um regime: dia sem vigência é recusado.
  - Endpoints `GET/POST /fiscal/enquadramentos`, com o histórico em Configuração.
- **A numeração nunca volta.** Salvar a configuração com o próximo número ≤ o último já usado na série/ambiente é recusado. Antes, dava para voltar e reaproveitar números.
- **Inutilização de numeração** (`fiscal_inutilizacao`, V46; `InutilizacaoService`):
  - Lacunas da série: números rejeitados, não transmitidos ou pulados. "Resultado desconhecido" aparece, mas não pode ser inutilizado antes da consulta.
  - Pedido com justificativa de 15 a 255 caracteres, gravado como `DESCONHECIDO` antes da transmissão (fora da transação).
  - `HOMOLOGADA` marca os documentos da faixa como `INUTILIZADA`. Sem resposta, o pedido fica `DESCONHECIDO` e bloqueia nova tentativa na mesma faixa. Provedor sem suporte desfaz o pedido.
  - Porta `ProvedorFiscal.inutilizar`; o simulado homologa em homologação.
  - Tela em Documentos emitidos → "Numeração da NFC-e a inutilizar".

### Verificação
- Backend: 519 testes, 0 falhas. Novos: `EnquadramentoServiceTest`, `InutilizacaoServiceTest` (lacunas, homologação, recusas, sem resposta, provedor sem suporte) e testes de numeração/vigência no `ModuloFiscalTest`.
- V46 aplicada no banco descartável: uma vigência migrada por schema.
- E2E fiscal 13/13: número pulado aparece como lacuna e é inutilizado pela tela; voltar a numeração é recusado (422); a vigência em vigor aparece.

### Limitações
- Estabelecimentos (matriz e filiais) continuam pendentes: a configuração ainda é uma linha por empresa. A mudança é grande (emitente, séries e certificado por estabelecimento) e fica para quando houver o primeiro cliente com filial.
- A inutilização de NF-e (55) usa o mesmo serviço, mas não há tela, porque a NF-e ainda não é emitida pelo sistema.

---

## 4. RTC: matriz de transição (planejamento, nada implementado)

| Período | O que vale | O que o ERP precisará |
|---|---|---|
| 2026 | Ano de teste: CBS 0,9% e IBS 0,1% destacados no documento, compensáveis com PIS/COFINS | Campos IBS/CBS no XML conforme as NTs vigentes; cálculo via calculadora oficial do regime regular |
| 2027–2028 | CBS plena, PIS/COFINS extintos; IS começa; IBS ainda em teste | Motor por estado de valor (simulado/calculado/declarado/débito/crédito) |
| 2029–2032 | IBS sobe gradualmente, ICMS/ISS caem | Convivência ICMS + IBS por período: parâmetros versionados por vigência |
| 2033 | Modelo novo pleno | |

Fontes a confirmar a cada etapa antes de implementar: LC 214/2025 e regulamentação, NTs da NF-e/NFC-e para a RTC e a documentação das calculadoras em `docs/` de cada projeto oficial. As alíquotas do Simples Nacional **não** serão inferidas da 1ª faixa.

---

## 5. Pendências e próximas etapas

**Etapa 1 (fundação), restante:** estabelecimentos por empresa (matriz/filiais).

**Etapa 2, fluxo fiscal:**
- Primeiro provedor real (consulta, cancelamento, inutilização).
- NF-e modelo 55.
- Importação do XML de entrada com parser endurecido contra XXE.

**Etapa 3, RTC:** adaptadores HTTP para as calculadoras oficiais (Java 21, porta própria), com o estado "cálculo indisponível".

**Etapa 4, contabilidade:** plano de contas e lançamentos em partidas dobradas.

**Etapa 5, escrituração e apuração.**

**Etapa 6, obrigações acessórias.**
