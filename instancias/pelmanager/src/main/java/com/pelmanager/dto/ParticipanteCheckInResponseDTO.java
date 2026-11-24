package com.pelmanager.dto;

import com.convoca.framework.domain.enums.Papel;
import com.pelmanager.entity.enums.Posicao;

public record ParticipanteCheckInResponseDTO(Long usuarioId,
                                             String nome,
                                             String apelido,
                                             Posicao posicao,
                                             String statusCheckIn) {
}
