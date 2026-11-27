package com.convoca.framework.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Uma das equipes formadas a partir dos presentes de um evento (no PelManager, um time). */
@Entity
@Table(name = "equipes")
@Getter
@Setter
public class Equipe {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    private String nome;

    @OneToMany(mappedBy = "equipe")
    private List<CheckIn> membros;
}
