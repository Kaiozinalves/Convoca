package com.convoca.framework.hotspot;

import com.convoca.framework.domain.CheckIn;
import com.convoca.framework.domain.Equipe;
import com.convoca.framework.domain.Evento;

import java.util.List;

/**
 * Ponto variável "formação de equipes": como distribuir os presentes de um evento.
 *
 * <p>Devolve as equipes ainda não salvas, cada uma com o nome e os {@code membros} preenchidos.
 * O núcleo liga cada CheckIn à sua equipe e grava tudo. Se não der para formar equipes com os
 * confirmados (poucos presentes, papéis faltando), a implementação lança uma exceção de negócio
 * do framework, e o fechamento do check-in é desfeito.
 */
public interface EstrategiaFormacaoEquipes {

    List<Equipe> formar(Evento evento, List<CheckIn> confirmados);
}
