package com.convoca.framework.repository;

import com.convoca.framework.domain.Avaliacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long> {
    boolean existsByEventoIdAndAvaliadorIdAndAvaliadoId(Long eventoId, Long avaliadorId, Long avaliadoId);

    List<Avaliacao> findByEventoId(Long eventoId);

    @Query("SELECT AVG(a.nota) FROM Avaliacao a WHERE a.avaliado.id = :usuarioId")
    Double calcularMediaDoUsuario(@Param("usuarioId") Long usuarioId);
}
