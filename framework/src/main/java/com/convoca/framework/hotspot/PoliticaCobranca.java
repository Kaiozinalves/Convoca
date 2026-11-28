package com.convoca.framework.hotspot;

import com.convoca.framework.domain.CheckIn;
import com.convoca.framework.domain.Evento;
import com.convoca.framework.domain.Pagamento;

import java.util.List;

/**
 * Ponto variável "cobrança": quem paga quanto em um evento.
 *
 * <p>Devolve os pagamentos ainda não salvos; quem os grava é o núcleo. Uma instância sem
 * cobrança devolve uma lista vazia.
 */
public interface PoliticaCobranca {

    List<Pagamento> gerarCobrancas(Evento evento, List<CheckIn> confirmados);
}
