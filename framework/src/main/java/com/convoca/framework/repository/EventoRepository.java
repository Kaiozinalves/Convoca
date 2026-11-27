package com.convoca.framework.repository;

import com.convoca.framework.domain.Evento;
import com.convoca.framework.domain.enums.StatusEvento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventoRepository extends JpaRepository<Evento, Long> {

    boolean existsByGrupoIdAndStatus(Long grupoId, StatusEvento status);

    List<Evento> findByGrupoIdOrderByIdDesc(Long grupoId);
}
