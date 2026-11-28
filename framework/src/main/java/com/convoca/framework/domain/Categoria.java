package com.convoca.framework.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

import java.util.Objects;

/**
 * Categoria de reconhecimento de um evento (no PelManager, "melhor jogador", "bagre"...).
 * É um valor, não uma entidade: não tem id nem tabela própria, e vive embutida em quem a usa
 * (as categorias de votação do Evento, o Voto e o Reconhecimento).
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

    // Duas categorias são a mesma quando têm o mesmo código (o rótulo é só texto de exibição).
    @Override public boolean equals(Object o){
        if (this == o){ return true;}
        if(!(o instanceof Categoria outraCategoria)){ return false;}
        return Objects.equals(codigo, outraCategoria.codigo);
    }

    @Override public int hashCode() {
        return Objects.hash(codigo);
    }
}
