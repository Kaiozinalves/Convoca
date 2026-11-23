package com.pelmanager.repository;

import com.pelmanager.entity.PreferenciaJogador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PreferenciaJogadorRepository extends JpaRepository<PreferenciaJogador, Long> {

    Optional<PreferenciaJogador> findByUsuarioId(Long usuarioId);
}
