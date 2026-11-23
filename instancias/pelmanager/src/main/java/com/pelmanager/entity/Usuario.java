package com.pelmanager.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Entity
@Table(name = "usuarios")
@Data
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    private String apelido;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String senha;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "endereco_id", unique = true)
    private Endereco endereco;

    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL)
    private List<Participante> gruposQueParticipa;

    @OneToMany(mappedBy = "avaliador", cascade = CascadeType.ALL)
    private List<Avaliacao> avaliacoesFeitas;

    @OneToMany(mappedBy = "avaliado", cascade = CascadeType.ALL)
    private List<Avaliacao> avaliacoesRecebidas;
}
