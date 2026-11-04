# Assistente

Botão **Assistente** no topo das telas: a pessoa pergunta em português ("qual meu lucro hoje?", "qual o custo da picanha?") e recebe a resposta com os números do sistema. Usa o Google Gemini na camada gratuita.

## Como ligar

1. Crie uma chave em [Google AI Studio](https://aistudio.google.com/apikey).
2. No Railway, no service do backend, crie a variável `GEMINI_API_KEY` com a chave.
3. Pronto: sem a variável, o botão não aparece.

| Variável | Padrão | Para quê |
| --- | --- | --- |
| `GEMINI_API_KEY` | vazia (desligado) | Chave da API |
| `GEMINI_MODELO` | `gemini-3.6-flash` | Modelo; troque se o Google mudar o que é gratuito. Em nov/2026 o `gemini-3.8-flash` recusava por alta demanda na camada gratuita e o `gemini-flash-latest` não estava liberado para a API usada |
| `ASSISTENTE_LIMITE_USUARIO_HORA` | 20 | Perguntas por usuário por hora |
| `ASSISTENTE_LIMITE_EMPRESA_DIA` | 100 | Perguntas por empresa por dia |
| `ASSISTENTE_LIMITE_MINUTO` | 2 | Perguntas por minuto somando todas as empresas. Cada pergunta faz 2 ou mais chamadas, e nos testes a camada gratuita recusou (403 ou 429) com poucas chamadas por minuto |

## Como funciona

A IA não acessa o banco. Ela só pode pedir uma das consultas fixas de `FerramentasAssistente`:

| Consulta | Módulo exigido | O que devolve |
| --- | --- | --- |
| Resumo de vendas | Relatórios | Faturamento, quantidade de vendas, ticket médio, custo, lucro bruto e margem de um período |
| Produtos mais vendidos | Relatórios | Até 20 produtos com quantidade, faturamento, custo e lucro bruto |
| Custo de produto | Produtos | Até 5 produtos com custo, preço de venda, margem e estoque |
| Estoque baixo | Produtos | Até 20 produtos abaixo do estoque mínimo |

O backend executa a consulta e devolve o resultado ao Gemini, que escreve a resposta. O lucro segue a mesma regra do Relatório de Vendas: vendas finalizadas menos o custo dos itens vendidos, sem despesas.

## Proteções

- **Empresa:** as consultas rodam na sessão do usuário, no schema da empresa dele. Não existe argumento de empresa para a IA trocar.
- **Permissão:** a IA só recebe as consultas dos módulos que o usuário pode ver. Quem não tem nenhum desses módulos recebe uma explicação, sem gastar cota.
- **Somente leitura:** consultas fixas, em transação somente leitura. A visualização do JARVIS não mostra o assistente.
- **Argumentos validados:** datas no formato AAAA-MM-DD, período de até 366 dias, até 20 itens, busca de 2 a 60 caracteres.
- **Números só das consultas:** as instruções proíbem estimar valores e mandam avisar quando há item vendido sem custo cadastrado, o que faz o lucro parecer maior.
- **Assunto:** perguntas fora dos dados da loja recebem uma recusa educada. Isso vem das instruções e não é garantido, mas a IA não alcança nada além das consultas acima.
- **Histórico:** vem do navegador só como texto. Mensagens forjadas não viram chamadas de consulta.
- **Limites:** por usuário e por empresa, no máximo 5 rodadas de consulta por pergunta e 500 caracteres por pergunta.
- **Registro:** cada pergunta fica na Auditoria (módulo ASSISTENTE) com as consultas feitas.

## Dados e LGPD

As consultas devolvem só totais e dados de produto: nada de cliente, CPF ou telefone vai para o Google. A pergunta digitada vai, por isso a tela pede para não digitar dados pessoais.

Na camada gratuita, o Google pode usar perguntas e respostas para melhorar os produtos dele, e pessoas podem ler o conteúdo. A conversa não fica guardada na API (`store: false`), mas isso não muda a regra de uso da camada gratuita. Para uso em produção com empresas clientes, informe nos termos de uso ou passe para a camada paga, que não usa os dados.

A cota gratuita é uma só para todas as empresas e é **muito baixa**. Em nov/2026 o Google informou "limit: 20 requests per day on Free Tier" para o `gemini-3.6-flash`. Como cada pergunta usa 2 ou mais chamadas, isso dá cerca de **10 perguntas por dia no sistema inteiro**, além do limite por minuto. Quando estoura, o assistente avisa se volta em minutos ou só no dia seguinte. Serve para testar a experiência; para as lojas usarem de verdade, é preciso a camada paga.

## Testes

`playwright/tests/assistente` usa `playwright/support/gemini-falso.mjs` no lugar do Gemini: suba o falso, aponte o backend para ele com `GEMINI_URL=http://127.0.0.1:5545/v1beta` e `GEMINI_API_KEY` com qualquer valor, e rode os testes com `E2E_GEMINI_FALSO=http://127.0.0.1:5545`.
