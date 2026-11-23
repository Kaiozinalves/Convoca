/**
 * Pontos variáveis comportamentais (Strategy): interfaces que cada instância implementa.
 * <ul>
 *   <li>{@code EstrategiaFormacaoEquipes}: {@code formar(evento, confirmados) : List<Equipe>};</li>
 *   <li>{@code PoliticaReconhecimento}: {@code categorias()}, {@code exigeVotacao()},
 *       {@code apurar(evento) : List<Reconhecimento>};</li>
 *   <li>{@code PoliticaCobranca}: {@code gerarCobrancas(evento, confirmados) : List<Pagamento>}.</li>
 * </ul>
 * Também contém {@code RateioIgualitario}, uma {@code PoliticaCobranca} pronta e abstrata
 * (Template Method): o esqueleto do rateio é fixo e a subclasse só define {@code valorTotal(evento)}.
 *
 * <p>As implementações ficam nas instâncias e são injetadas pelo Spring nos serviços do framework.
 */
package com.convoca.framework.hotspot;
