package com.convoca.framework.exception;

public class UsuarioJaParticipaException extends RuntimeException {
    public UsuarioJaParticipaException(String message) {
        super(message)
        ;
    }

    public UsuarioJaParticipaException() {
        super("Você já participa deste grupo.");
    }
}
