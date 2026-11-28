package com.convoca.framework.hotspot;

import com.convoca.framework.domain.Categoria;
import com.convoca.framework.domain.Evento;
import com.convoca.framework.domain.Reconhecimento;

import java.util.List;

/**
 * Ponto variável "reconhecimento": quem se destaca num evento e como isso é decidido.
 *
 * <ul>
 *   <li>{@link #categorias()}: o catálogo de categorias que a política sabe apurar. A instância
 *       escolhe, dentre elas, quais valem em cada evento.</li>
 *   <li>{@link #exigeVotacao()}: se {@code true}, o núcleo abre uma janela de votação ao finalizar
 *       o evento e só apura quando ela fecha; se {@code false}, apura logo ao finalizar.</li>
 *   <li>{@link #apurar(Evento)}: devolve os reconhecimentos, ainda não salvos (um por vencedor;
 *       empate gera um para cada empatado). A implementação pode ler votos, avaliações e check-ins
 *       pelos repositórios do framework.</li>
 * </ul>
 */
public interface PoliticaReconhecimento {

    List<Categoria> categorias();

    boolean exigeVotacao();

    List<Reconhecimento> apurar(Evento evento);
}
