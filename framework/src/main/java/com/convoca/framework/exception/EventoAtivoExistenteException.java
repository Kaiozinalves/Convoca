package com.convoca.framework.exception;

public class EventoAtivoExistenteException extends RuntimeException {
    public EventoAtivoExistenteException(String message) {
        super(message);
    }

    public EventoAtivoExistenteException() {
        super("Não é possível agendar. Já existe um evento em andamento ou agendado para este grupo.");
    }
}
