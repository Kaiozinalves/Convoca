package com.pelmanager.repository;

import com.convoca.framework.domain.Evento;
import com.convoca.framework.domain.enums.StatusCheckIn;
import com.convoca.framework.domain.enums.StatusEvento;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface RodadaBuscaRepository extends Repository<Evento, Long> {

    @Query("""
            SELECT r FROM Evento r
            JOIN FETCH r.grupo g
            LEFT JOIN FETCH g.endereco ge
            LEFT JOIN FETCH r.enderecoAlternativo ea
            WHERE r.status = :status
              AND r.data >= :hoje
              AND (g.publica IS NULL OR g.publica = true)
              AND LOWER(COALESCE(ea.cidade, ge.cidade)) = LOWER(:cidade)
            ORDER BY r.data ASC, r.hora ASC
            """)
    List<Evento> buscarNaCidade(@Param("status") StatusEvento status,
                                @Param("hoje") LocalDate hoje,
                                @Param("cidade") String cidade);

    // Linhas [rodadaId, quantidade de check-ins com esse status]
    @Query("""
            SELECT c.evento.id, COUNT(c) FROM CheckIn c
            WHERE c.evento.id IN :rodadaIds AND c.status = :status
            GROUP BY c.evento.id
            """)
    List<Object[]> contarCheckinsPorRodada(@Param("rodadaIds") Collection<Long> rodadaIds,
                                           @Param("status") StatusCheckIn status);

    // Linhas [grupoId, quantidade de participantes]
    @Query("""
            SELECT p.grupo.id, COUNT(p) FROM Participante p
            WHERE p.grupo.id IN :grupoIds
            GROUP BY p.grupo.id
            """)
    List<Object[]> contarParticipantesPorGrupo(@Param("grupoIds") Collection<Long> grupoIds);
}
