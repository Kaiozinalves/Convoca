/**
 * Serviços que controlam o fluxo (pontos fixos): {@code ServicoGrupo} (grupos, convites, papéis)
 * e {@code ServicoEvento} ({@code agendar}, {@code confirmarPresenca}/{@code cancelarPresenca},
 * {@code fecharCheckIn}, {@code iniciar}/{@code finalizar}, {@code registrarVoto}, {@code apurar}).
 *
 * <p>Inversão de controle: o serviço decide <i>quando</i> e em que <i>ordem</i>; a instância decide
 * <i>como</i>. Exemplo, {@code fecharCheckIn(evento)}: valida o status, busca os confirmados, chama
 * {@code EstrategiaFormacaoEquipes.formar}, salva as equipes, chama
 * {@code PoliticaCobranca.gerarCobrancas} e salva os pagamentos. Nenhuma instância altera essa ordem.
 */
package com.convoca.framework.service;
