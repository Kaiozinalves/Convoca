package com.convoca.framework.domain;

import com.convoca.framework.domain.enums.StatusPagamento;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cobrança de um participante num evento. Quem gera os pagamentos é a {@code PoliticaCobranca}
 * da instância; o núcleo só guarda o valor e o status.
 */
@Getter
@Setter
@Entity
@Table(name = "pagamentos")
public class Pagamento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Qual evento
    @ManyToOne
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    // Qual usuário deve pagar
    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPagamento status = StatusPagamento.PENDENTE;

    private LocalDateTime criadoEm;
    private LocalDateTime pagoEm;

    public boolean foiPago() {
        return status == StatusPagamento.PAGO;
    }

    // quando o gerente confirmar que recebeu o pagamento
    public void marcarComoPago() {
        this.status = StatusPagamento.PAGO;
        this.pagoEm = LocalDateTime.now();
    }
}
