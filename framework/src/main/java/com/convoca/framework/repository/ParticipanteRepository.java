package com.convoca.framework.repository;

import com.convoca.framework.domain.Participante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParticipanteRepository extends JpaRepository<Participante, Long> {

    Boolean existsByUsuarioIdAndGrupoId(Long usuarioId, Long grupoId);

    Optional<Participante> findByUsuarioIdAndGrupoId(Long usuarioId, Long grupoId);

    List<Participante> findByUsuarioId(Long usuarioId);
}
