package com.convoca.framework.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Resultado da apuração de um evento numa categoria: quem foi reconhecido.
 * Classe nova do framework: antes, o PelManager recalculava o vencedor a cada consulta e não o
 * guardava. Quem decide os vencedores é a {@code PoliticaReconhecimento} da instância; o
 * {@code ServicoEvento.apurar} grava os registros (um por vencedor, então empate gera vários).
 */
@Entity
@Table(name = "reconhecimentos")
@Getter
@Setter
public class Reconhecimento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @Embedded
    private Categoria categoria;

    // Participante, e não Usuario: o reconhecimento vale dentro do grupo (como nos slides).
    @ManyToOne(optional = false)
    @JoinColumn(name = "vencedor_id", nullable = false)
    private Participante vencedor;
}
