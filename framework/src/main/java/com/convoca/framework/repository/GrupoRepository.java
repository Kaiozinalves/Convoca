package com.convoca.framework.repository;


import com.convoca.framework.domain.Grupo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GrupoRepository extends JpaRepository<Grupo, Long> {

    Optional<Grupo> findByCodigoConvite(String codigoConvite);

    @Query("SELECT g FROM Grupo g JOIN g.participantes p WHERE p.usuario.id = :usuarioId")
    List<Grupo> findByUsuarioId(@Param("usuarioId") Long usuarioId);

}
