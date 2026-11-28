package com.convoca.framework.hotspot;

import com.convoca.framework.domain.CheckIn;
import com.convoca.framework.domain.Evento;
import com.convoca.framework.domain.Pagamento;
import com.convoca.framework.domain.enums.StatusPagamento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link PoliticaCobranca} pronta do núcleo: divide um valor total em partes iguais entre os
 * confirmados (Template Method).
 *
 * <p>O esqueleto ({@link #gerarCobrancas}) é fixo. A subclasse só diz quanto o evento custa
 * ({@link #valorTotal}) e, se quiser, troca a forma de dividir ({@link #calcularValorPorPessoa}).
 */
public abstract class RateioIgualitario implements PoliticaCobranca {

    @Override
    public final List<Pagamento> gerarCobrancas(Evento evento, List<CheckIn> confirmados) {
        if (confirmados.isEmpty()) {
            return List.of();
        }
        BigDecimal total = valorTotal(evento);
        if (total == null || total.signum() <= 0) {
            return List.of();   // nada a ratear
        }

        BigDecimal valorPorPessoa = calcularValorPorPessoa(total, confirmados.size());
        LocalDateTime agora = LocalDateTime.now();
        List<Pagamento> cobrancas = new ArrayList<>();
        for (CheckIn checkIn : confirmados) {
            Pagamento pagamento = new Pagamento();
            pagamento.setEvento(evento);
            pagamento.setUsuario(checkIn.getUsuario());
            pagamento.setValor(valorPorPessoa);
            pagamento.setStatus(StatusPagamento.PENDENTE);
            pagamento.setCriadoEm(agora);
            cobrancas.add(pagamento);
        }
        return cobrancas;
    }

    /** Quanto o evento custa no total. Zero ou {@code null} significa que não há cobrança. */
    protected abstract BigDecimal valorTotal(Evento evento);

    /** Divisão em partes iguais, arredondada para centavos. */
    protected BigDecimal calcularValorPorPessoa(BigDecimal total, int qtd) {
        return total.divide(BigDecimal.valueOf(qtd), 2, RoundingMode.HALF_UP);
    }
}
