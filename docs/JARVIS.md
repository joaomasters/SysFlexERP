# JARVIS

Acesso da plataforma: mostra todas as empresas em uma tela e abre qualquer uma delas em modo leitura. Roda dentro do backend e do frontend atuais, sem service próprio.

## Como entrar

Na tela de login normal, use o código de empresa **000**. Aparece o campo do código de 6 dígitos do app autenticador. Nenhuma empresa recebe esse código, porque a numeração começa em 001.

## Configuração

São três variáveis no backend. Se alguma faltar, o código 000 fica desligado e se comporta como um código inexistente.

| Variável | Conteúdo |
| --- | --- |
| `JARVIS_LOGIN` | Nome de usuário |
| `JARVIS_SENHA` | Senha longa e exclusiva deste acesso |
| `JARVIS_TOTP_SEGREDO` | Segredo em Base32, com pelo menos 16 bytes |

Para gerar o segredo:

```bash
python -c "import base64,secrets;print(base64.b32encode(secrets.token_bytes(20)).decode())"
```

Cadastre o segredo no app autenticador (Google Authenticator, Authy ou similar) pela opção de **digitar a chave**, com o tipo **baseado em tempo**. Use valores diferentes em desenvolvimento e produção.

Para cortar o acesso, remova `JARVIS_LOGIN` e reinicie o backend: as sessões abertas caem junto.

## O que cada sessão pode fazer

| Sessão | Duração | Alcance |
| --- | --- | --- |
| Painel | 1 hora | Somente as rotas `/jarvis` |
| Ver empresa | 1 hora | Somente GET nas telas da empresa |

Mesmo sendo GET, a visualização não alcança `/admin`, `/jarvis`, `/balanca/agente` nem as chamadas às adquirentes em `/maquininhas/provedores/...`.

## Proteções do modo leitura

1. `RestricaoJarvis`, aplicada no `JwtAuthFilter`, recusa com 403 o que não for permitido.
2. `SchemaMultiTenantConnectionProvider` abre a conexão com `default_transaction_read_only`, então o próprio Postgres recusa gravações feitas pelo Hibernate. O valor volta ao normal antes de a conexão retornar ao pool.
3. `UsuarioAutenticado.pode` nega criar, editar e excluir. No frontend, isso esconde só os botões que consultam permissão. Telas que não consultam, como Produtos, continuam mostrando o botão, mas a ação é recusada com aviso na tela.

## Registro de acessos

Cada login e cada abertura de empresa ficam em `public.jarvis_acessos`, visíveis no próprio painel. A abertura é registrada antes de o token ser emitido: se o registro falhar, a visualização não acontece. As telas vistas durante a visualização não são registradas uma a uma.

## LGPD

O JARVIS dá acesso a dados pessoais de clientes das empresas. O contrato ou os termos de uso devem prever esse acesso para suporte e operação da plataforma.
