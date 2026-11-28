package com.convoca.framework.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Nota (de 1 a 5) e comentário opcional que um participante dá a outro depois de um evento. */
@Entity
@Table(name = "avaliacoes")
@Getter
@Setter
public class Avaliacao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @ManyToOne
    @JoinColumn(name = "avaliador_id", nullable = false)
    private Usuario avaliador;

    @ManyToOne
    @JoinColumn(name = "avaliado_id", nullable = false)
    private Usuario avaliado;

    @Column(nullable = false)
    private Integer nota;

    private String comentario;

    private LocalDateTime dataAvaliacao;
}

