package com.pelmanager.entity;

import com.convoca.framework.domain.Usuario;
import com.pelmanager.entity.enums.PernaDominante;
import com.pelmanager.entity.enums.Posicao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Como o usuário costuma jogar, fora de qualquer grupo: é o que ele informa no cadastro e no perfil,
 * o que a busca de Free Agent usa e o modelo copiado para o {@link PerfilJogador} de cada grupo novo.
 *
 * <p>Existe só no PelManager. A FK fica aqui (e não em {@code usuarios}) para que o {@code Usuario}
 * do núcleo não precise saber que esta classe existe.
 */
@Entity
@Table(name = "preferencias_jogador")
@Getter
@Setter
public class PreferenciaJogador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "posicao_primaria", length = 20)
    private Posicao posicaoPrimaria;

    @Enumerated(EnumType.STRING)
    @Column(name = "posicao_secundaria", length = 20)
    private Posicao posicaoSecundaria;

    @Enumerated(EnumType.STRING)
    @Column(name = "perna_dominante", length = 10)
    private PernaDominante pernaDominante;
}
