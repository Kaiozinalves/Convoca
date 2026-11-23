package com.convoca.framework.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Ponto variável estrutural: o que é específico da atividade para um participante
 * (no PelManager, posição e perna; no e-sports, rank e nickname).
 *
 * <p>O núcleo conhece só o id. Cada instância declara uma subclasse {@code @Entity}.
 * Com {@code JOINED}, esta tabela guarda o id e cada subclasse tem a sua tabela,
 * ligada a esta pela mesma chave primária.
 */
@Entity
@Table(name = "perfis_atividade")
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
public abstract class PerfilAtividade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}
