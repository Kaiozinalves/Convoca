package com.convoca.framework.repository;

import com.convoca.framework.domain.CheckIn;
import com.convoca.framework.domain.enums.StatusCheckIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    Optional<CheckIn> findByEventoIdAndParticipanteUsuarioId(Long eventoId, Long usuarioId);

    boolean existsByEventoIdAndParticipanteUsuarioIdAndStatus(Long eventoId, Long usuarioId, StatusCheckIn status);

    List<CheckIn> findByEventoIdAndStatus(Long eventoId, StatusCheckIn status);

    @Query("SELECT COUNT(c) FROM CheckIn c WHERE c.evento.id = :eventoId AND c.status = :status")
    long contarPorEventoEStatus(@Param("eventoId") Long eventoId, @Param("status") StatusCheckIn status);
}
