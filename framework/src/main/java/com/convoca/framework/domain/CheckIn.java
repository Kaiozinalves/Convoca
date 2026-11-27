package com.convoca.framework.domain;

import com.convoca.framework.domain.enums.StatusCheckIn;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Confirmação (ou cancelamento) de presença de um participante num evento. */
@Entity
@Table(name = "checkins")
@Getter
@Setter
public class CheckIn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    // Participante, e não Usuario: é pelo participante que se chega ao perfil da atividade.
    @ManyToOne
    @JoinColumn(name = "participante_id", nullable = false)
    private Participante participante;

    @ManyToOne
    @JoinColumn(name = "equipe_id")
    private Equipe equipe;

    private LocalDateTime dataHoraCheckin;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_checkin", nullable = false)
    private StatusCheckIn status;

    /**
     * Atalho para quem fez o check-in. Não é coluna (o Hibernate mapeia pelos campos), então
     * consultas JPQL e métodos derivados precisam usar o caminho {@code participante.usuario}.
     */
    public Usuario getUsuario() {
        return participante.getUsuario();
    }
}
