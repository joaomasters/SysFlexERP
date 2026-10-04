package com.acougue.config;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public Hibernate6Module hibernate6Module() {
        Hibernate6Module module = new Hibernate6Module();
        module.enable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);

        // Por padrão o módulo trata @Transient (JPA) como "não serializar no JSON".
        // Os campos de NOME de usuário (usuarioNome, criadoPorNome, ...) são @Transient
        // — não vão pro banco — mas PRECISAM ir no JSON, preenchidos por IdentificacaoUsuario
        // só para administradores. Sem isto o campo nunca chega ao frontend.
        module.disable(Hibernate6Module.Feature.USE_TRANSIENT_ANNOTATION);
        return module;
    }
}
