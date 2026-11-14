# Módulos industriais

Este documento registra a auditoria do SysFlex feita antes de começar os módulos industriais, a arquitetura escolhida, o plano das sprints e o que já foi entregue. A expansão segue o código que já existe e não reescreve nada: o PDV, o estoque, o financeiro e o fiscal continuam como estão.

## 1. Auditoria do projeto (fase 0)

### Arquitetura atual

| Camada | O que existe |
| --- | --- |
| Backend | Spring Boot 3, Java 17, monólito modular. Pacotes em `com.acougue`: `entity` e `repository` compartilhados, regras em `modules/<área>` (service, controller, dto). |
| Banco | PostgreSQL com **um schema por empresa** (`public` para a principal e `tenant_*` para as demais). Flyway (`db/migration`, V1 a V40). O `MigradorSchemasEmpresas` aplica cada migration em todos os schemas no boot. O JPA roda com `ddl-auto: validate`. |
| Multi-empresa | O `TenantResolvingFilter` e o `TenantIdentifierResolver` trocam o schema por requisição. `public.tenants` é lida sempre via JDBC (`TenantRepository`). |
| Segurança | JWT (`JwtAuthFilter`). Permissões por **módulo e ação**: a enum `Modulo`, a anotação `@ExigirPermissao` e a tabela `perfil_permissoes` (ver, criar, editar, excluir). O SUPER_ADMIN tem acesso total. O modo JARVIS é só leitura, garantido também pelo Postgres. |
| Auditoria | `AuditoriaAspect`: toda ação de escrita com `@ExigirPermissao` é registrada sozinha em `log_auditoria`. |
| Erros | `GlobalExceptionHandler`: `BusinessException` → 422, validação → 400, não encontrado → 404, restrição única conhecida → 422 com mensagem amigável. |
| Estoque | `Produto` (saldo em `estoque_atual`), `MovimentacaoEstoque` (tipo, quantidade, custo, `documento_ref`), `LoteEstoque` (validade, FIFO) e `EstoqueService` (entrada, saída, estorno, ajuste). A desossa (`DesossaService`) é o precedente de "transformar um produto em outros" com rateio de custo. |
| Fiscal | `modules/fiscal/emissao`: NFC-e via `ProvedorFiscal` (simulado em homologação; provedor real ainda pendente). NF de saída gerencial (`NotaFiscalSaida`). |
| Frontend | React, TypeScript, Vite, Tailwind e react-query. Componentes em `shared/components/ui` (PageHeader, Table, Modal, Field, Button, StatusBadge, FilterTabs, Paginacao). Menu em `Sidebar.tsx`, filtrado por `usePermissao`. |
| Testes | JUnit 5, Mockito e AssertJ (406 testes no backend). Playwright E2E (projeto `chromium` e projeto `global` para testes que mudam configuração da empresa). |

### Recursos reaproveitáveis

- **Isolamento por empresa:** vem do próprio schema. As tabelas industriais não precisam de `empresa_id`: uma referência entre empresas é impossível no banco.
- **Permissões e auditoria:** um valor novo na enum `Modulo` e o `@ExigirPermissao` dão RBAC no backend e registro de auditoria sem código a mais.
- **Produtos, clientes, fornecedores e usuários:** são os cadastros únicos. A indústria os referencia e não duplica.
- **`EstoqueService` e `MovimentacaoEstoque`:** são o único estoque. Consumo de OP, entrada de acabado e refugo serão movimentações com `documento_ref` apontando a origem (ex.: `OP#123/AP#45`), como a desossa já faz.

### Riscos de regressão e como foram tratados

| Risco | Tratamento |
| --- | --- |
| Uma migration nova quebrar alguma empresa | Só `CREATE TABLE`/`ADD COLUMN IF NOT EXISTS`. Nada é renomeado nem apagado. |
| `ddl-auto: validate` derrubar o boot | Entidades conferidas contra a migration, subindo num banco vazio. |
| Mexer no fluxo do PDV ou do estoque | A sprint 1 não toca em nenhum serviço existente. |
| Empresas que só usam o PDV verem a indústria | Habilitação por empresa, desligada por padrão, garantida no backend (interceptor) e no menu. |
| Concorrência em cadastros | Restrições únicas no banco, conferência antes de gravar e bloqueio otimista (`@Version`) na máquina. |

### Pontos que vão precisar de extensão

- **Estoque industrial (WIP, quarentena, refugo):** hoje o saldo é por produto. A sprint 3 vai precisar de saldo por produto, lote e situação, sem quebrar o `estoque_atual` que o PDV usa. Proposta: uma tabela nova `ind_saldo_lote`, com o `estoque_atual` continuando como o total disponível.
- **Pedido de venda:** o sistema tem venda de PDV e faturamento de fiado, mas não tem um pedido de venda B2B. A produção sob encomenda e a expedição vão precisar dele (sprint 2 ou 6).
- **Unidades de medida:** `produtos.unidade_medida` é texto livre. A conversão entre kg, metro e peça só será feita com fator técnico informado na ficha do produto, nunca estimada.

## 2. Arquitetura proposta

- **Um pacote por área**, dentro do monólito: `modules/industrial/estrutura` (sprint 1), depois `engenharia`, `pcp`, `producao`, `metalurgia`, `qualidade`, `rastreabilidade`, `expedicao` e `custos`. As entidades industriais ficam dentro do pacote da área (não em `entity`), para os limites ficarem claros.
- **Tabelas com prefixo `ind_`.**
- **Permissões por área:** um `Modulo` por perfil funcional. Hoje existe `IND_ESTRUTURA`. Os próximos são `IND_ENGENHARIA`, `IND_PCP`, `IND_APONTAMENTO` (operador), `IND_QUALIDADE`, `IND_EXPEDICAO` e `IND_CUSTOS`. Os perfis sugeridos (gestor de produção, planejador, operador, inspetor…) são montados na tela Perfis com essas permissões.
- **Rotas `/industrial/**`:** todas passam pelo `ModuloIndustrialInterceptor`.
- **Sem Kafka, Redis ou microserviço novo.** Eventos internos só se uma necessidade real aparecer.
- **Sem comandos a máquinas:** nada envia comando a forno, prensa ou CLP. Leituras de sensores (OPC UA) entram depois, por adaptadores, sempre marcadas como "sensor" e diferentes do valor digitado.

## 3. Plano das sprints

| Sprint | Entrega | Estado |
| --- | --- | --- |
| 1. Fundação | Habilitação por empresa, permissão, unidades, setores, centros de trabalho, turnos, tipos de máquina com parâmetros, máquinas (status, horímetro, manutenção, histórico, turnos), ferramentas/matrizes e alocação de operadores | **Entregue** |
| 2. Engenharia e PCP | Ficha técnica industrial (estende o produto), BOM versionada com vigência, roteiro versionado, OP com estados e transições, congelamento da revisão de engenharia na liberação, planejamento básico | **Entregue** |
| 3. Execução (MES) | Apontamento (início, pausa, fim, parcial), paradas e motivos, consumo real, WIP por lote, tela de chão de fábrica para tablet, baixa idempotente no estoque | A fazer |
| 4. Extrusão | Ligas, tarugos e lotes de matéria-prima com certificado, receitas por liga e equipamento, medições (aquecimento, extrusão, tratamento térmico) com origem manual/sensor, perdas | A fazer |
| 5. Qualidade e rastreabilidade | Planos de inspeção, inspeções e não conformidades, quarentena e liberação, genealogia de lotes (divisão e união), consulta da origem ao cliente, QR por lote | A fazer |
| 6. Expedição | Pedido de expedição, reserva de lote, separação, conferência, romaneio, carregamento sem dupla baixa, bloqueio por qualidade, ligação com o fiscal existente sem emitir nota automaticamente | A fazer |
| 7. Custos, MRP e indicadores | Custo estimado × real por OP, MRP com sugestões (sem aprovar sozinho), dashboards, OEE só quando houver dados confiáveis | A fazer |

## 4. Sprint 1: o que foi entregue

### Banco: `V39__industrial_estrutura.sql`

| Tabela | Conteúdo |
| --- | --- |
| `public.tenants.modulo_industrial` | Liberação por empresa (padrão `false`) |
| `ind_unidade`, `ind_setor`, `ind_centro_trabalho` | Hierarquia fábrica → setor → centro (com custo/hora) |
| `ind_turno` | Horário (pode virar a meia-noite) e dias da semana |
| `ind_tipo_maquina`, `ind_tipo_maquina_parametro` | Categoria (forno, prensa, corte…) e os parâmetros técnicos de cada tipo, com unidade, faixa e obrigatoriedade |
| `ind_maquina`, `ind_maquina_parametro`, `ind_maquina_turno` | Máquina, os valores dos parâmetros e o calendário de turnos |
| `ind_maquina_evento` | Histórico só de inserção: status, horímetro, manutenção preventiva e corretiva (período e custo) |
| `ind_ferramenta` | Matriz, dispositivo ou ferramenta, com revisão, vida útil em ciclos e status |
| `ind_alocacao_operador` | Usuário × centro × turno, com vigência |

Também concede a permissão `IND_ESTRUTURA` a ADMIN e SUPER_ADMIN; os demais perfis ficam sem acesso.

### API (todas com o prefixo `/api`)

| Rota | Permissão |
| --- | --- |
| `GET /industrial/situacao` | qualquer usuário logado (o menu usa) |
| `GET POST PUT /industrial/{unidades,setores,centros,turnos,tipos-maquina,maquinas,ferramentas}` | IND_ESTRUTURA ver / criar / editar |
| `POST /industrial/{unidades,setores,centros,turnos,tipos-maquina}/{id}/inativar` e `/reativar` | excluir / editar |
| `POST /industrial/maquinas/{id}/status` (com `versao`), `/horimetro`, `/manutencoes` · `GET /historico` | editar / ver |
| `POST /industrial/ferramentas/{id}/status` | editar |
| `GET /industrial/operadores`, `GET POST /industrial/alocacoes`, `POST /industrial/alocacoes/{id}/encerrar` | ver / criar / editar |
| `PATCH /admin/tenants/{id}/modulo-industrial` `{ "habilitado": true }` | só o administrador da plataforma |

### Regras de negócio

- **Códigos:** sem espaços nas pontas e em maiúsculas, únicos (o setor é único dentro da unidade; a ferramenta é única por código e revisão).
- **Inativação em cadeia:** uma unidade não é inativada com setor ativo, um setor com centro ativo, nem um centro com máquina não inativa.
- **Parâmetros da máquina:** precisam pertencer ao tipo dela, respeitar a faixa e incluir os obrigatórios. Um parâmetro com valor em alguma máquina não pode ser removido do tipo. Nenhuma temperatura ou força fica fixa no código.
- **Status da máquina:**
  - mudar para o mesmo status é recusado;
  - uma máquina INATIVA só volta para DISPONÍVEL;
  - só entra EM_OPERACAO quem está DISPONÍVEL ou PARADA;
  - toda mudança vira evento no histórico, com o usuário que a fez;
  - a tela envia a versão que carregou, e uma alteração concorrente é recusada em vez de sobrescrita.
- **Horímetro:** não diminui.
- **Ferramenta:** descartada não volta a ser usada, e os ciclos utilizados não diminuem.
- **Alocação de operador:** o mesmo operador não fica em dois centros no mesmo turno com vigências sobrepostas. Alocação não se apaga: é encerrada com uma data final.

### Frontend

- **Menu "Indústria → Estrutura industrial":** aparece só com a indústria liberada e a permissão `IND_ESTRUTURA`.
- **Tela `/industrial/estrutura`:** uma aba por cadastro. A tela de operação da máquina tem status, horímetro, manutenção e histórico.
- **Empresas:** coluna "Indústria" para liberar ou bloquear.

### Testes

- **Backend:** `MaquinaServiceTest` (15) e `EstruturaIndustrialTest` (9). A suíte completa passou: 406 testes, 0 falhas.
- **E2E:** `playwright/tests/industrial/estrutura.global.spec.ts` (3), cobrindo o bloqueio sem liberação, o cadastro pela tela e a faixa de parâmetro com troca de status.
- **Regressão:** a suíte E2E completa passou com 55 testes e 0 falhas.

### Como ligar numa empresa

1. Entre como super admin da empresa principal e abra **Administração → Empresas**.
2. Clique em "Bloqueada" na coluna **Indústria** da empresa. O status muda para "Liberada".
3. Os usuários da empresa recarregam a página e veem o menu **Indústria**. Dê a permissão "Indústria: Estrutura produtiva" aos perfis que vão cadastrar.

### Reversão

A migration só acrescenta, então voltar o código para a versão anterior funciona com o banco já migrado: as tabelas `ind_*` e a coluna `modulo_industrial` ficam paradas, sem efeito. Não há rollback de banco a fazer. Para esconder a indústria sem reverter código, basta bloquear a empresa na tela Empresas.

### Limitações conhecidas

- **Alocação simultânea:** duas alocações sobrepostas criadas exatamente ao mesmo tempo podem passar as duas, porque a conferência é feita no serviço, sem restrição de exclusão no banco. O risco é baixo num cadastro administrativo.
- **Cache da situação:** o menu consulta a liberação a cada 5 minutos. Depois de liberar, o usuário pode precisar recarregar a página.
- **Faixa de parâmetro editada depois:** se a faixa de um parâmetro mudar, os valores já gravados fora da nova faixa só são cobrados na próxima vez que a máquina for salva.

## 5. Sprint 2: engenharia de produto e PCP

### Banco: `V40__industrial_engenharia_pcp.sql`

| Tabela | Conteúdo |
| --- | --- |
| `ind_ficha_tecnica` | Uma por produto: tipo de item (matéria-prima, semiacabado, acabado, embalagem, insumo), desenho e revisão, pesos de referência, comprimento, lote mínimo e múltiplo, prazo de produção, controle de lote |
| `ind_bom`, `ind_bom_item` | Estrutura de produto versionada (rascunho → ativa → obsoleta), com quantidade base, vigência e componentes com perda % |
| `ind_roteiro`, `ind_roteiro_operacao` | Roteiro versionado igual à BOM. Cada operação tem centro de trabalho, tipo de máquina (opcional), preparação em minutos e minutos por unidade |
| `ind_ordem_producao` | OP com número sequencial (`OP000001`), produto, quantidade, prioridade 1–5, período previsto, pedido, status e versão (bloqueio otimista) |
| `ind_op_material`, `ind_op_operacao` | Materiais e operações **copiados** da BOM e do roteiro ativos na liberação, já calculados para a quantidade da OP |
| `ind_op_evento` | Histórico só de inserção das mudanças de status, com motivo e usuário |

- **Uma versão ativa por produto:** garantido também no banco, por índice único parcial (`uk_ind_bom_ativa` e `uk_ind_roteiro_ativo`).
- **Sequência dos itens:** a restrição única da sequência é adiável (`DEFERRABLE`), para permitir reordenar componentes e operações numa única gravação.
- **Permissões:** concede `IND_ENGENHARIA` e `IND_PCP` a ADMIN e SUPER_ADMIN. Os demais perfis ficam sem acesso.
- **Tabela de produtos:** não foi alterada. A ficha técnica é uma tabela à parte.

### API

| Rota | Permissão |
| --- | --- |
| `GET /industrial/produtos` | ver engenharia **ou** PCP (não exige o módulo Produtos; não traz preços) |
| `GET /industrial/engenharia/recursos` | IND_ENGENHARIA ver. Centros e tipos de máquina ativos, para montar o roteiro sem acesso à estrutura |
| `GET /industrial/engenharia/fichas` · `PUT /industrial/engenharia/fichas/{produtoId}` | ver / editar |
| `GET POST /industrial/engenharia/boms` · `GET PUT DELETE /boms/{id}` | ver / criar / editar / excluir (só rascunho) |
| `POST /boms/{id}/ativar`, `/obsoletar` · `POST /boms/{id}/nova-versao` · `GET /boms/{id}/explosao?quantidade=` | editar / criar / ver |
| `…/engenharia/roteiros` | as mesmas rotas da BOM, sem a explosão |
| `GET POST /industrial/ordens` (`?status=`) · `GET PUT /ordens/{id}` | IND_PCP ver / criar / editar (só planejada) |
| `POST /industrial/ordens/{id}/status` `{status, versao, motivo}` | editar |
| `PUT /industrial/ordens/{id}/operacoes/{operacaoId}/maquina` `{maquinaId, versao}` | editar |
| `GET /industrial/pcp/carga?de=&ate=` · `GET /industrial/pcp/maquinas` | ver |

### Regras de negócio

- **Versões:**
  - só o rascunho é editado ou excluído;
  - há no máximo um rascunho por produto;
  - "nova versão" copia qualquer versão para um rascunho com o próximo número;
  - ativar torna obsoleta a versão ativa anterior e fecha a vigência dela na véspera;
  - para ativar, a BOM precisa ter ao menos um componente ativo, e o roteiro ao menos uma operação em centro ativo.
- **Componentes:**
  - o produto e os componentes precisam estar ativos;
  - o componente não se repete na mesma estrutura;
  - nenhum produto é componente de si mesmo, nem indiretamente: o ciclo é verificado pelas estruturas ativas, em até 20 níveis.
- **Quantidades:**
  - ficam na unidade de medida do próprio componente, sem conversão automática;
  - a necessidade é `quantidade × qtd. OP ÷ quantidade base ÷ (1 − perda%)`; com 10% de perda, entra 1/0,9 do líquido;
  - a explosão desce pelas estruturas ativas dos componentes que também são fabricados.
- **Ficha técnica:** o lote mínimo precisa ser múltiplo do lote múltiplo. A OP respeita os dois.
- **Status da OP:**

  | De | Para |
  | --- | --- |
  | Planejada | Liberada, Cancelada |
  | Liberada | Em produção, Planejada (estorno da liberação), Cancelada |
  | Em produção | Pausada, Concluída |
  | Pausada | Em produção |
  | Concluída | Encerrada, Em produção (reabrir) |
  | Encerrada, Cancelada | finais |

  - O motivo é obrigatório para cancelar, pausar, voltar para planejada e reabrir.
  - Uma OP em produção não é cancelada nesta sprint: o estorno de produção virá com os apontamentos.
- **Liberação:**
  - só libera com BOM e roteiro ativos e com componentes e centros ativos; a tela mostra essas pendências antes;
  - na liberação, materiais e operações são copiados para a OP, com o tempo previsto = preparação + minutos por unidade × quantidade;
  - mudar a engenharia depois **não altera** a OP liberada;
  - voltar para planejada descarta a cópia, o que só é permitido se nenhuma operação começou.
- **Edição e concorrência:** a OP só é editada enquanto planejada. Toda alteração confere a versão carregada pela tela.
- **Máquina da operação:** precisa ser do centro da operação, não estar inativa e ser do tipo pedido, quando houver um.
- **Numeração:** o número é reservado só depois das validações, então uma OP recusada não deixa buraco.
- **Carga × capacidade:**
  - a capacidade soma os turnos ativos das máquinas não inativas do centro, nos dias marcados do período;
  - a carga soma o tempo previsto das OPs abertas: das liberadas, o que foi copiado; das planejadas, uma estimativa pelo roteiro ativo;
  - a carga é rateada pelos dias da OP que caem no período (até 92 dias);
  - OPs planejadas sem roteiro aparecem à parte;
  - é só informativa: não reprograma nada.
- **Estoque:** esta sprint não reserva nem baixa material. A OP mostra o saldo atual de cada material e quanto falta.

### Frontend

- **Indústria → Engenharia de produto** (`/industrial/engenharia`):
  - abas Estruturas, Roteiros e Fichas técnicas;
  - o rascunho é editado no próprio modal, com "Salvar rascunho" e "Salvar e ativar";
  - versões ativadas são só leitura, com "Nova versão a partir desta", "Tornar obsoleta" e, na estrutura, o cálculo da explosão.
- **Indústria → Ordens de produção** (`/industrial/ordens`):
  - a lista filtra por situação e marca as OPs atrasadas;
  - o detalhe da OP mostra:
    - materiais com saldo e falta (como prévia enquanto planejada);
    - operações com a escolha da máquina;
    - pendências de liberação;
    - histórico;
    - os botões das transições permitidas, que pedem motivo quando é obrigatório;
  - a aba **Carga dos centros** mostra a ocupação por centro, com as OPs de cada um.

### Testes

- **Backend:**
  - `EngenhariaTest` (18): necessidade com perda, versões, ciclo direto e indireto, componente repetido, ativação com obsolescência, explosão multinível, roteiro e ficha técnica;
  - `OrdemProducaoTest` (14): lote, datas, transições, liberação com congelamento, motivo, versão, estorno, máquina por centro e tipo, capacidade e rateio;
  - suíte completa: 438 testes, 0 falhas.
- **API de ponta a ponta:** script com cerca de 80 verificações, rodado num Postgres novo depois da V40, sem falhas. Cobre a trava por empresa, o ciclo, as versões, os lotes, a liberação congelada, a concorrência, a carga e a auditoria `IND_ENGENHARIA`/`IND_PCP`.
- **E2E:** `playwright/tests/industrial/engenharia-pcp.global.spec.ts` (3) cobre a estrutura criada e ativada pela tela com explosão, a OP criada e liberada pela tela com a falta de material e o motivo obrigatório, e a carga do centro.
- **Configuração do Playwright:** o projeto `global` passou a rodar com 1 worker. Dois arquivos globais mudando a mesma configuração da empresa em paralelo se atrapalhavam.

### Reversão

Como na sprint 1, a migration só cria tabelas. Voltar o código deixa as tabelas novas paradas, sem efeito. Para esconder engenharia e PCP de um perfil, basta tirar as permissões `IND_ENGENHARIA` e `IND_PCP`.

### Limitações conhecidas

- **Cliente da OP:** a API aceita `clienteId`, mas a tela ainda não tem seletor de cliente, só o campo "pedido do cliente". Isso entra quando houver o pedido de venda B2B (ver seção 1).
- **Vigência:** começa na data da ativação. Ainda não dá para agendar uma versão para entrar em vigor no futuro.
- **Unidade de medida:** a conversão entre unidades (kg ↔ m ↔ peça) não é automática. Os pesos da ficha são só referência.
- **Escolha da máquina:** trocar a máquina de uma operação não muda a versão da OP, então duas pessoas escolhendo máquina na mesma operação ao mesmo tempo: vale a última.
- **Carga:** não considera paradas, manutenção programada nem eficiência. É a carga teórica pelos tempos padrão.
