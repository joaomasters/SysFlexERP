package com.acougue.modules.acesso.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TrocarSenhaDTO {
    @NotBlank(message = "Nova senha é obrigatória")
    @Size(min = 8, max = 72, message = "Senha deve ter entre 8 e 72 caracteres")
    private String novaSenha;

    /**
     * Obrigatória ao trocar a PRÓPRIA senha: sem ela, quem pegasse uma sessão
     * aberta (computador do caixa destravado) trocava a senha do dono da conta.
     * Administrador redefinindo a senha de OUTRO usuário não precisa informar.
     */
    private String senhaAtual;
}
