package com.acougue.security;

import com.acougue.entity.Usuario;
import com.acougue.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Transforma "usuarioId" em nome de usuário para exibição — e só faz isso
 * quando o usuário logado pode ver identificação de outros usuários
 * ({@link ContextoUsuario#podeVerIdentificacaoUsuarios()}).
 *
 * Para quem não pode, é um no-op total: nem consulta o banco, nem preenche
 * nada. Combinado com o {@code @JsonIgnore} nos campos de id das entidades e
 * {@code @JsonInclude(NON_NULL)} nos campos de nome, o dado simplesmente não
 * existe no JSON devolvido — não é "escondido na tela", nunca sai do servidor.
 *
 * Uso típico, no controller que devolve a lista:
 * <pre>
 *   identificacao.preencher(lista, Recebimento::getUsuarioId, Recebimento::setUsuarioNome);
 *   identificacao.preencherItem(um, Recebimento::getUsuarioId, Recebimento::setUsuarioNome);
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class IdentificacaoUsuario {

    private final UsuarioRepository usuarioRepo;

    public <T> void preencher(Collection<T> itens,
                              Function<T, Long> idDe,
                              BiConsumer<T, String> aplicarNome) {
        if (itens == null || itens.isEmpty() || !ContextoUsuario.podeVerIdentificacaoUsuarios()) {
            return;
        }
        Set<Long> ids = itens.stream().map(idDe).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> nomes = buscarNomes(ids);
        for (T item : itens) {
            Long id = idDe.apply(item);
            if (id != null) {
                aplicarNome.accept(item, nomes.getOrDefault(id, rotuloDesconhecido(id)));
            }
        }
    }

    /** Variante de conveniência para um único item (ex: GET por id). */
    public <T> void preencherItem(T item, Function<T, Long> idDe, BiConsumer<T, String> aplicarNome) {
        if (item != null) {
            preencher(Collections.singletonList(item), idDe, aplicarNome);
        }
    }

    /**
     * Mapa id -> nome, SEM checar permissão. Uso restrito a quem já exigiu
     * {@link ContextoUsuario#exigirVisualizacaoIdentificacaoUsuarios()} antes
     * (ex: relatórios de histórico de pagamentos, que são admin-only por inteiro).
     */
    public Map<Long, String> nomesPorId(Collection<Long> ids) {
        Set<Long> unicos = ids == null ? Set.of()
                : ids.stream().filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        return unicos.isEmpty() ? Map.of() : buscarNomes(unicos);
    }

    public String rotuloDesconhecido(Long id) {
        return "Usuário #" + id;
    }

    private Map<Long, String> buscarNomes(Set<Long> ids) {
        return usuarioRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNome));
    }
}
