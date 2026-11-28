package com.convoca.framework.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Um voto de um participante, numa categoria, em um evento.
 * A constraint única garante no banco "um voto por pessoa e categoria" mesmo com
 * requisições concorrentes. A identidade do votante nunca é exposta nos DTOs de resultado.
 */
@Entity
@Table(
        name = "votos",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_voto_evento_categoria_votante",
                columnNames = {"evento_id", "categoria_codigo", "votante_id"}
        )
)
@Getter
@Setter
public class Voto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    // A instância traduz a sua lista de categorias para Categoria (no PelManager, CategoriaVoto.comoCategoria()).
    @Embedded
    private Categoria categoria;

    @ManyToOne(optional = false)
    @JoinColumn(name = "votante_id", nullable = false)
    private Usuario votante;

    @ManyToOne(optional = false)
    @JoinColumn(name = "votado_id", nullable = false)
    private Usuario votado;

    @Column(name = "data_voto", nullable = false)
    private LocalDateTime dataVoto;
}
