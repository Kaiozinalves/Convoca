/**
 * <b>Convoca</b>: núcleo do framework de gestão de grupos que se reúnem periodicamente.
 *
 * <p>Contém os <b>pontos fixos</b> (frozen spots), comuns a todas as instâncias,
 * e as <b>interfaces dos pontos variáveis</b> (hot spots), que cada instância implementa.
 *
 * <p>O framework termina na camada de serviço: os controllers REST ficam em cada
 * instância, que expõe a API com as próprias rotas e DTOs e chama os services daqui.
 */
package com.convoca.framework;
