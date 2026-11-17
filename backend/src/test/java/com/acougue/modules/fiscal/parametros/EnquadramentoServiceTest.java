package com.acougue.modules.fiscal.parametros;

import com.acougue.entity.ConfiguracaoFiscal;
import com.acougue.exception.BusinessException;
import com.acougue.repository.ConfiguracaoFiscalRepository;
import com.acougue.security.UsuarioAutenticado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Regime tributário por vigência")
class EnquadramentoServiceTest {

    private final EnquadramentoRepository repo = mock(EnquadramentoRepository.class);
    private final ConfiguracaoFiscalRepository configRepo = mock(ConfiguracaoFiscalRepository.class);
    private final EnquadramentoService service = new EnquadramentoService(repo, configRepo);
    private final ConfiguracaoFiscal config = ConfiguracaoFiscal.builder().id((short) 1).regimeTributario((short) 1).build();
    private Enquadramento atual;

    @BeforeEach
    void setUp() {
        atual = Enquadramento.builder().id(1L).regimeTributario((short) 1).inicio(LocalDate.of(2000, 1, 1)).build();
        when(repo.findFirstByOrderByInicioDesc()).thenReturn(Optional.of(atual));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(configRepo.findById((short) 1)).thenReturn(Optional.of(config));
        UsuarioAutenticado u = new UsuarioAutenticado(1L, "dono", "Dono", "SUPER_ADMIN", true, Map.of());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(u, null));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("nova vigência encerra a aberta na véspera e atualiza o regime da emissão")
    void abreVigencia() {
        LocalDate hoje = LocalDate.now();
        when(repo.vigenteEm(hoje)).thenReturn(Optional.of(Enquadramento.builder().regimeTributario((short) 3).inicio(hoje).build()));

        Enquadramento novo = service.registrar((short) 3, hoje, " Desenquadrou do Simples ");

        assertThat(atual.getFim()).isEqualTo(hoje.minusDays(1));
        assertThat(novo.getInicio()).isEqualTo(hoje);
        assertThat(novo.getObservacao()).isEqualTo("Desenquadrou do Simples");
        assertThat(config.getRegimeTributario()).isEqualTo((short) 3);
        verify(configRepo).travar();
    }

    @Test
    @DisplayName("não reescreve o passado nem repete o mesmo regime")
    void recusas() {
        assertThatThrownBy(() -> service.registrar((short) 3, LocalDate.of(2000, 1, 1), null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Vigências passadas não são alteradas");
        assertThatThrownBy(() -> service.registrar((short) 1, LocalDate.now(), null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("já está em Simples Nacional");
        assertThatThrownBy(() -> service.registrar((short) 9, LocalDate.now(), null)).hasMessageContaining("inválido");
        assertThat(atual.getFim()).isNull();
    }

    @Test
    @DisplayName("dia sem vigência cadastrada não supõe regime")
    void semVigencia() {
        when(repo.vigenteEm(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.regimeEm(LocalDate.of(1999, 5, 1))).hasMessageContaining("Não há regime");
    }

    @Test
    @DisplayName("vigência cobre do início ao fim, inclusive")
    void vigente() {
        Enquadramento e = Enquadramento.builder().inicio(LocalDate.of(2026, 1, 1)).fim(LocalDate.of(2026, 6, 30)).build();
        assertThat(e.vigenteEm(LocalDate.of(2026, 6, 30))).isTrue();
        assertThat(e.vigenteEm(LocalDate.of(2026, 7, 1))).isFalse();
        assertThat(e.vigenteEm(LocalDate.of(2025, 12, 31))).isFalse();
    }
}
