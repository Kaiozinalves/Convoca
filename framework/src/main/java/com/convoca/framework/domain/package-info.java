/**
 * Entidades do núcleo (pontos fixos), idênticas nas três aplicações:
 * {@code Usuario}, {@code Grupo}, {@code Participante}, {@code Endereco}, {@code Evento},
 * {@code CheckIn}, {@code Equipe}, {@code Avaliacao}, {@code Voto}, {@code Reconhecimento},
 * {@code Pagamento} e o {@code @Embeddable} {@code Categoria}.
 *
 * <p>Pontos variáveis estruturais (classes abstratas que cada instância estende):
 * <ul>
 *   <li>{@code PerfilAtividade}: dados da pessoa naquela atividade (ex.: {@code PerfilJogador});</li>
 *   <li>{@code PerfilGrupo}: parâmetros de cada grupo (ex.: {@code ConfiguracaoPelada}).</li>
 * </ul>
 * O encaixe é por <b>composição</b>: {@code Participante} e {@code Grupo} <i>têm</i> um perfil (0..1),
 * em vez de serem subclassificados. O núcleo cria o {@code Participante} no convite sem conhecer
 * a instância; o perfil é preenchido depois. No JPA, os perfis usam {@code @Inheritance(JOINED)},
 * para que as tabelas do núcleo fiquem iguais em todas as aplicações.
 */
package com.convoca.framework.domain;
