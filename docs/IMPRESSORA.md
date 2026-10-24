# Impressora de cupom: guia de instalação

Como ligar uma impressora térmica de balcão (ex.: **Knup KP-IM602**, USB, bobina de 80 mm) ao PDV do SysFlex para imprimir o **comprovante de venda** (cupom não fiscal).

O sistema roda no navegador: ele monta o cupom no tamanho exato da bobina e manda para a **impressora padrão do Windows**. Não é preciso instalar nenhum programa do SysFlex no computador do caixa, só o driver da impressora.

---

## 1. Instalar a impressora no Windows

1. Ligue a impressora na tomada e conecte o cabo **USB** ao computador do caixa.
2. Instale o **driver da Knup** para o modelo KP-IM602. Ele vem no CD/pendrive da caixa ou fica no site da Knup, na área de suporte e downloads.
3. Abra **Configurações → Bluetooth e dispositivos → Impressoras e scanners**, clique na Knup e escolha **Definir como padrão**.
   - Desative a opção *"Permitir que o Windows gerencie minha impressora padrão"*; senão ele troca a padrão sozinho.
4. Em **Preferências de impressão** da Knup, confira:
   - **Papel:** bobina de 80 mm (ex.: "80 x 297 mm" ou "Roll paper 80mm");
   - **Margens:** as menores possíveis.
5. Imprima uma **página de teste** pelo Windows para confirmar que a impressora responde.

---

## 2. Configurar o comprovante no sistema

Em **Administração → Comprovante** (permissão "Comprovante de Venda", que ADMIN e SUPER_ADMIN têm por padrão):

- **Cabeçalho:** nome que sai no cupom, CNPJ, endereço, bairro, cidade e telefone.
- **Rodapé:** mensagem no fim do cupom (ex.: *"Obrigado pela preferência! Trocas em até 7 dias com este cupom."*).
- **Largura da bobina:** **80 mm** para a KP-IM602.
- **Vias por venda:** 1 a 3.
- **Ao finalizar a venda:** *perguntar ao caixa* (padrão) ou *imprimir sempre*.
- Mostrar ou ocultar o aviso de "não é documento fiscal", o operador e o cliente.

Use **Imprimir teste** para conferir o resultado na bobina antes de salvar.

---

## 3. Imprimir sem a janela de impressão (recomendado no caixa)

Por padrão o navegador abre a janela "Imprimir" a cada cupom. Para o cupom sair **direto** na impressora:

1. **Primeiro, uma impressão normal**, para o Chrome guardar as opções:
   - Em Administração → Comprovante, clique em **Imprimir teste**.
   - Na janela do Chrome, escolha a **Knup**. Em *Mais configurações*: **Margens: Nenhuma**, **Escala: Padrão**, desmarque **Cabeçalhos e rodapés**.
   - Imprima. O Chrome passa a usar essas opções.
2. **Crie um atalho só para o PDV** na área de trabalho do caixa:
   - Clique com o botão direito na área de trabalho → **Novo → Atalho**.
   - Em "local do item", cole (ajuste o caminho se o Chrome estiver em outra pasta):
     ```
     "C:\Program Files\Google\Chrome\Application\chrome.exe" --kiosk-printing --app=https://sysflexerp.vercel.app/pdv
     ```
   - Nome: **PDV SysFlex**.
3. **Feche todas as janelas do Chrome** e abra o PDV **sempre por esse atalho**.

> ⚠️ A opção `--kiosk-printing` só vale se o Chrome for aberto por esse atalho com **nenhuma outra janela do Chrome aberta**. Se o Chrome já estiver aberto, o atalho reaproveita aquela janela e a caixa de impressão volta a aparecer.

---

## 4. No dia a dia (PDV)

- Ao **finalizar a venda**, aparece *"Imprimir o comprovante para o cliente?"*: **Enter** imprime, **Esc** não imprime. Com "imprimir sempre" configurado, sai direto, sem perguntar.
- **Reimprimir último cupom** (coluna da direita do PDV) imprime de novo a última venda finalizada naquele caixa.

---

## 5. Problemas comuns

| Sintoma | O que fazer |
|---|---|
| Nada sai na impressora | Confira se a Knup é a **impressora padrão** e se a página de teste do Windows imprime |
| Abre a janela de impressão | Abra o PDV pelo **atalho** do passo 3, com o Chrome todo fechado antes |
| Cupom cortado na lateral ou letra muito pequena | Confira se a **largura** em Administração → Comprovante bate com a bobina (80 ou 58 mm) e se a margem no Chrome está em **Nenhuma** |
| Sai uma página em branco depois do cupom | Ajuste o papel no driver da Knup para o modo **rolo/bobina** (altura contínua), não A4 |
| Cupom com cabeçalho de outra loja | Cada empresa tem a própria configuração: entre com o código da empresa certa e confira em Administração → Comprovante |

---

## Para o futuro

Abrir a **gaveta de dinheiro** ou **cortar o papel** automaticamente exige falar direto com a impressora (comandos ESC/POS). Isso pede um pequeno programa local, como o agente da balança, e pode ser feito se houver necessidade.
