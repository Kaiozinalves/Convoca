package com.pelmanager.dto;

import com.convoca.framework.domain.Pagamento;
import com.convoca.framework.domain.enums.StatusPagamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Pagamento como o frontend lê: plano, sem expor Usuario e Evento inteiros. */
public record PagamentoResponseDTO(
        Long id,
        Long rodadaId,
        Long usuarioId,
        String usuarioNome,
        String usuarioApelido,
        BigDecimal valor,
        StatusPagamento status,
        LocalDateTime criadoEm,
        LocalDateTime pagoEm
) {
    public static PagamentoResponseDTO de(Pagamento p) {
        return new PagamentoResponseDTO(
                p.getId(),
                p.getEvento().getId(),
                p.getUsuario().getId(),
                p.getUsuario().getNome(),
                p.getUsuario().getApelido(),
                p.getValor(),
                p.getStatus(),
                p.getCriadoEm(),
                p.getPagoEm()
        );
    }
}
