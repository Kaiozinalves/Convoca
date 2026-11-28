package com.convoca.framework.repository;

import com.convoca.framework.domain.Reconhecimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReconhecimentoRepository extends JpaRepository<Reconhecimento, Long> {

    List<Reconhecimento> findByEventoId(Long eventoId);

    boolean existsByEventoId(Long eventoId);
}
