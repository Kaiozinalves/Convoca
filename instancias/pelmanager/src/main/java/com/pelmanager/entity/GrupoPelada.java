package com.pelmanager.entity;

import com.convoca.framework.domain.PerfilGrupo;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "grupo_peladas")
@Data
public class GrupoPelada {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @OneToMany(mappedBy = "grupo", cascade = CascadeType.ALL)
    private List<Participante> participantes;

    private String codigoConvite;

    private LocalDateTime dataCriacao;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "endereco_id")
    private Endereco endereco;

    private Boolean publica = true;

    // Mesmo encaixe de Participante.perfil: o grupo tem 0..1 PerfilGrupo (no PelManager, ConfiguracaoPelada).
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "perfil_id")
    private PerfilGrupo perfil;

    public boolean ehPublica() {
        return !Boolean.FALSE.equals(this.publica);
    }
}
