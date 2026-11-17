package com.acougue.modules.fiscal.emissao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InutilizacaoRepository extends JpaRepository<Inutilizacao, Long> {

    List<Inutilizacao> findByModeloAndSerieAndAmbienteAndStatusIn(Short modelo, Short serie, String ambiente, List<String> status);

    List<Inutilizacao> findTop100ByOrderByIdDesc();
}
