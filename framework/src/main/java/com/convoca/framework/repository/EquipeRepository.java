package com.convoca.framework.repository;

import com.convoca.framework.domain.Equipe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipeRepository extends JpaRepository<Equipe, Long> {

    List<Equipe> findByEventoId(Long eventoId);
}
