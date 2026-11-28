package com.pelmanager.entity.enums;

/** Como o PelManager sorteia os times de uma rodada (lido pelo EquilibradoPorNotaEPosicao). */
public enum ModoFormacao {
    /** Equilibra nota média das avaliações e posição (BalanceadorDeTimes). */
    EQUILIBRADO,
    /** Ignora as notas: só garante um goleiro por time e embaralha o resto. */
    ALEATORIO
}
