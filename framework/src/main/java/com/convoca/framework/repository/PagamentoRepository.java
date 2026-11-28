package com.convoca.framework.repository;

import com.convoca.framework.domain.Pagamento;
import com.convoca.framework.domain.enums.StatusPagamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {

    // Pagamento de um usuário em um evento
    Optional<Pagamento> findByEventoIdAndUsuarioId(Long eventoId, Long usuarioId);

    // Todos os pagamentos de um evento
    List<Pagamento> findByEventoId(Long eventoId);

    // Pagamentos com status específico em um evento
    List<Pagamento> findByEventoIdAndStatus(Long eventoId, StatusPagamento status);

    // Todos os pagamentos de um usuário
    List<Pagamento> findByUsuarioId(Long usuarioId);

    // Contar pagamentos com status desejado
    long countByEventoIdAndStatus(Long eventoId, StatusPagamento status);

}
