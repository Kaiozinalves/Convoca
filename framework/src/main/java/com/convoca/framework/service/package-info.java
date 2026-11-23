/**
 * Serviços com os <b>fluxos fixos</b> do framework (ex.: criar grupo, entrar por convite,
 * abrir/encerrar encontro, check-in, pagamentos).
 *
 * <p>Nos pontos em que o comportamento muda de instância para instância, o serviço
 * delega para uma interface de {@code hotspot} (inversão de controle: o framework chama
 * o código da instância).
 */
package com.convoca.framework.service;
