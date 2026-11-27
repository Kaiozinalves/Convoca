package com.convoca.framework.domain;

import com.convoca.framework.domain.enums.StatusEvento;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

/** Um encontro do grupo (no PelManager, uma rodada). */
@Entity
@Table(name = "eventos")
@Getter
@Setter
public class Evento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Um grupo tem vários eventos; um evento pertence a um grupo só.
    @ManyToOne
    @JoinColumn(name = "grupo_id", nullable = false)
    private Grupo grupo;

    // TEMPORÁRIO: sai no passo 5, quando a PoliticaCobranca assumir o cálculo dos pagamentos.
    private BigDecimal valorPorParticipante;

    private LocalDateTime dataCheckInFechado;

    private LocalDate data;
    private LocalTime hora;

    @Enumerated(EnumType.STRING)
    private StatusEvento status;

    // Início/fim reais (manuais) do evento e prazo final da votação.
    private LocalDateTime iniciadoEm;
    private LocalDateTime finalizadoEm;
    private LocalDateTime votacaoAte;

    // Categorias de votação habilitadas, copiadas da configuração do grupo quando o evento é finalizado.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "evento_categorias_votacao", joinColumns = @JoinColumn(name = "evento_id"))
    private Set<Categoria> categoriasVotacao = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "endereco_alternativo_id")
    private Endereco enderecoAlternativo;

    // Se o endereço alternativo for nulo, o evento acontece no endereço do grupo.
    public Endereco getEnderecoResolvido() {
        if (this.enderecoAlternativo != null) {
            return this.enderecoAlternativo;
        }
        return this.grupo.getEndereco();
    }

    public boolean estaAberta() {
        return this.status != null && this.status.permiteCheckin();
    }
}
