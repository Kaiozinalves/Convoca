package com.convoca.framework.repository;

import com.convoca.framework.domain.Categoria;
import com.convoca.framework.domain.Voto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VotoRepository extends JpaRepository<Voto, Long> {

    boolean existsByEventoIdAndCategoriaCodigoAndVotanteId(Long eventoId, String codigoCategoria, Long votanteId);

    List<Voto> findByEventoIdAndVotanteId(Long eventoId, Long votanteId);

    List<Voto> findByEventoIdInAndCategoriaCodigo(List<Long> eventoIds, String codigoCategoria);

    /** Votos por (código da categoria, votado). Só contam votos de quem confirmou presença. */
    @Query("""
            select v.categoria.codigo, v.votado.id, count(v)
            from Voto v
            where v.evento.id = :eventoId
              and exists (
                    select c.id from CheckIn c
                    where c.evento.id = :eventoId
                      and c.participante.usuario.id = v.votante.id
                      and c.status = com.convoca.framework.domain.enums.StatusCheckIn.CONFIRMADO)
            group by v.categoria.codigo, v.votado.id
            """)
    List<Object[]> apurarVotos(@Param("eventoId") Long eventoId);

    boolean existsByEventoIdAndCategoriaAndVotanteId(Long id, Categoria categoria, Long id1);
}
