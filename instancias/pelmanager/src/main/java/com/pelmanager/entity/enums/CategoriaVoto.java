package com.pelmanager.entity.enums;

import com.convoca.framework.domain.Categoria;


public enum CategoriaVoto {
    MELHOR_JOGADOR("Melhor jogador da rodada"),
    MELHOR_ASSISTENTE("Melhor assistente da rodada"),
    BAGRE("Bagre da rodada"),
    REVELACAO("Revelação da rodada");

    private final String rotulo;

    CategoriaVoto(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    /** Tradução para o valor genérico do núcleo (codigo = nome da constante). */
    public Categoria comoCategoria() {
        return new Categoria(name(), rotulo);
    }
}
