package com.convoca.framework.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

import java.util.Objects;

/**
 * Categoria de reconhecimento de um evento (no PelManager, "melhor jogador", "bagre"...).
 * É um valor, não uma entidade: não tem id nem tabela própria, e vive embutida em quem a usa
 * (as categorias de votação do Evento; no passo 4, Voto e Reconhecimento).
 */
@Embeddable
@Getter
public class Categoria {

    @Column(name = "categoria_codigo", length = 30, nullable = false)
    private String codigo;

    @Column(name = "categoria_rotulo")
    private String rotulo;

    protected Categoria() {
        // exigido pelo JPA
    }

    public Categoria(String codigo, String rotulo) {
        this.codigo = codigo;
        this.rotulo = rotulo;
    }

    // TODO(human): equals e hashCode. A Categoria é um valor (@Embeddable) guardado num Set do Evento:
    // decida o que faz duas categorias serem "a mesma" e implemente os dois métodos de forma coerente.
    @Override public boolean equals(Object o){
        if (this == o){ return true;}
        if(!(o instanceof Categoria outraCategoria)){ return false;}
        return Objects.equals(codigo, outraCategoria.codigo);
    }

    @Override public int hashCode() {
        return Objects.hash(codigo);
    }
}
