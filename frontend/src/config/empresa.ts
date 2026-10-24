// Dados do próprio sistema (rodapé do cupom etc.).
//
// Os dados de cada LOJA (nome, endereço, telefone, largura da bobina) não ficam
// mais aqui: são configurados por empresa em Administração → Comprovante
// (tabela configuracao_comprovante). Antes ficavam fixos neste arquivo com os
// dados do Açougue do Zé, e qualquer outra empresa imprimiria o cabeçalho dele.
export const SISTEMA = {
  nome: 'SysFlexERP',
  telefones: ['(43) 99808-8022', '(43) 99602-1880'],
}
