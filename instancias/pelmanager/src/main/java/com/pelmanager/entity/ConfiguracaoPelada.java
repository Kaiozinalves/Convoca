package com.pelmanager.entity;

import com.convoca.framework.domain.PerfilGrupo;
import com.pelmanager.entity.enums.CategoriaVoto;
import com.pelmanager.entity.enums.ModoFormacao;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/** Parâmetros de um grupo de pelada: cobrança do campo e votação de fim de rodada. */
@Entity
@Table(name = "configuracoes_pelada")
@Getter
@Setter
public class ConfiguracaoPelada extends PerfilGrupo {

    @Column(name = "sistema_pagamento_ativo", nullable = false)
    private boolean sistemaPagamentoAtivo = false;

    // Valor total do campo, rateado entre os confirmados pelo RateioDoCampo.
    @Column(name = "valor_total_pelada")
    private BigDecimal valorTotalPelada;

    @Column(name = "chave_pix_gerente")
    private String chavePixGerente;

    // Categorias da votação de fim de rodada. O "bagre" nasce desligado: o rótulo pode ser
    // engraçado entre amigos, mas constrangedor em outros contextos. A revelação também nasce
    // desligada, como hoje (o frontend sempre envia revelacaoAtiva = false).
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "configuracao_pelada_categorias", joinColumns = @JoinColumn(name = "configuracao_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", length = 30)
    private Set<CategoriaVoto> categoriasAtivas =
            new HashSet<>(Set.of(CategoriaVoto.MELHOR_JOGADOR, CategoriaVoto.MELHOR_ASSISTENTE));

    // Quantas horas a votação fica aberta depois que a rodada é finalizada.
    @Column(name = "prazo_avaliacao_horas", nullable = false)
    private int prazoAvaliacaoHoras = 24;

    // Modo do último sorteio: cada rota de sorteio grava o seu antes de fechar o check-in.
    @Enumerated(EnumType.STRING)
    @Column(name = "modo_formacao", nullable = false, length = 20)
    private ModoFormacao modoFormacao = ModoFormacao.EQUILIBRADO;

    public boolean categoriaAtiva(CategoriaVoto categoria) {
        return categoriasAtivas.contains(categoria);
    }

    public void definirCategoria(CategoriaVoto categoria, boolean ativa) {
        if (ativa) {
            categoriasAtivas.add(categoria);
        } else {
            categoriasAtivas.remove(categoria);
        }
    }
}
