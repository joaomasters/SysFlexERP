# Módulos industriais

Este documento registra a auditoria do SysFlex feita antes de começar os módulos industriais, a arquitetura escolhida, o plano das sprints e o que já foi entregue. A expansão segue o código que já existe e não reescreve nada: o PDV, o estoque, o financeiro e o fiscal continuam como estão.

## 1. Auditoria do projeto (fase 0)

### Arquitetura atual

| Camada | O que existe |
| --- | --- |
| Backend | Spring Boot 3, Java 17, monólito modular. Pacotes em `com.acougue`: `entity` e `repository` compartilhados, regras em `modules/<área>` (service, controller, dto). |
| Banco | PostgreSQL com **um schema por empresa** (`public` para a principal e `tenant_*` para as demais). Flyway (`db/migration`, V1 a V39). O `MigradorSchemasEmpresas` aplica cada migration em todos os schemas no boot. O JPA roda com `ddl-auto: validate`. |
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
| 2. Engenharia e PCP | Ficha técnica industrial (estende o produto), BOM versionada com vigência, roteiro versionado, OP com estados e transições, congelamento da revisão de engenharia na liberação, planejamento básico | A fazer |
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
