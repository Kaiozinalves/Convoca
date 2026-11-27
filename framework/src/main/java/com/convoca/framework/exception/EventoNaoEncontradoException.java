package com.convoca.framework.exception;

public class EventoNaoEncontradoException extends RuntimeException {
    public EventoNaoEncontradoException(Long eventoId) {
        super(String.format("Nenhum evento foi encontrado com o ID %d.", eventoId));
    }
}
