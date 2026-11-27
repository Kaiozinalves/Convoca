package com.convoca.framework.domain.enums;

/** Ciclo de vida de um evento: agendado → em andamento → finalizado, ou cancelado. */
public enum StatusEvento {
    AGENDADO,
    EM_ANDAMENTO,
    FINALIZADO,
    CANCELADO;

    public boolean permiteCheckin() {
        return this == AGENDADO;
    }
}
