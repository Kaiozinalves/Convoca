package com.pelmanager.entity;

import com.convoca.framework.domain.PerfilAtividade;
import com.pelmanager.entity.enums.PernaDominante;
import com.pelmanager.entity.enums.Posicao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Perfil do participante numa pelada: como ele joga neste grupo. */
@Entity
@Table(name = "perfis_jogador")
@Getter
@Setter
public class PerfilJogador extends PerfilAtividade {

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
