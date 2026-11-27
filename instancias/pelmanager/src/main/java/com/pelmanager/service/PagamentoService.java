package com.pelmanager.service;

import com.convoca.framework.domain.CheckIn;
import com.pelmanager.entity.ConfiguracaoPelada;
import com.pelmanager.entity.Pagamento;
import com.convoca.framework.domain.Evento;
import com.convoca.framework.domain.enums.StatusCheckIn;
import com.pelmanager.entity.enums.StatusPagamento;
import com.convoca.framework.exception.RecursoNaoEncontradoException;
import com.convoca.framework.exception.RequisicaoInvalidaException;
import com.pelmanager.exception.RodadaNaoEncontradaException;
import com.convoca.framework.repository.CheckInRepository;
import com.pelmanager.repository.PagamentoRepository;
import com.convoca.framework.repository.EventoRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PagamentoService {

    private final PagamentoRepository pagamentoRepository;
    private final CheckInRepository checkInRepository;
    private final GerarQrCodeService qrCodeService;
    private final EventoRepository rodadaRepository;
    private final PermissaoService permissaoService;
    private final PerfilPeladaService perfilPeladaService;

    public PagamentoService(PagamentoRepository pagamentoRepository,
                            CheckInRepository checkInRepository,
                            GerarQrCodeService qrCodeService,
                            EventoRepository rodadaRepository,
                            PermissaoService permissaoService,
                            PerfilPeladaService perfilPeladaService) {
        this.pagamentoRepository = pagamentoRepository;
        this.checkInRepository = checkInRepository;
        this.qrCodeService = qrCodeService;
        this.rodadaRepository = rodadaRepository;
        this.permissaoService = permissaoService;
        this.perfilPeladaService = perfilPeladaService;
    }

    @Transactional
    public List<Pagamento> gerarPagamentosComoAdmin(Long rodadaId, Long usuarioAdminId) {
        Evento rodada = rodadaRepository.findById(rodadaId)
                .orElseThrow(() -> new RodadaNaoEncontradaException(rodadaId));
        permissaoService.exigirAdmin(usuarioAdminId, rodada.getGrupo());
        return criarPagamentoAoFinalizarCheckIn(rodadaId);
    }

    @Transactional
    public List<Pagamento> criarPagamentoAoFinalizarCheckIn(Long rodadaId) {
        Evento rodada = rodadaRepository.findById(rodadaId)
                .orElseThrow(() -> new RodadaNaoEncontradaException(rodadaId));

        ConfiguracaoPelada configuracao = perfilPeladaService.configuracaoDe(rodada.getGrupo());
        if (!configuracao.isSistemaPagamentoAtivo()) {
            return pagamentoRepository.findByRodadaId(rodadaId);
        }

        List<CheckIn> confirmados = checkInRepository.findByEventoIdAndStatus(rodadaId, StatusCheckIn.CONFIRMADO);

        // Cancela pagamentos pendentes de quem não está mais confirmado
        List<Pagamento> existentes = pagamentoRepository.findByRodadaId(rodadaId);
        for (Pagamento p : existentes) {
            boolean segueConfirmado = confirmados.stream()
                    .anyMatch(c -> c.getUsuario().getId().equals(p.getUsuario().getId()));
            if (!segueConfirmado && !p.foiPago()) {
                p.setStatus(StatusPagamento.CANCELADO);
                pagamentoRepository.save(p);
            }
        }

        if (confirmados.isEmpty()) {
            rodada.setValorPorParticipante(BigDecimal.ZERO);
            rodadaRepository.save(rodada);
            return pagamentoRepository.findByRodadaId(rodadaId);
        }

        BigDecimal valorPorPessoa = configuracao.calcularValorPorPessoa(confirmados.size());
        LocalDateTime agora = LocalDateTime.now();

        for (CheckIn checkIn : confirmados) {
            Optional<Pagamento> existenteOpt = pagamentoRepository.findByRodadaIdAndUsuarioId(
                    rodadaId, checkIn.getUsuario().getId()
            );

            if (existenteOpt.isPresent()) {
                Pagamento existente = existenteOpt.get();
                if (!existente.foiPago()) {
                    existente.setValor(valorPorPessoa);
                    existente.setStatus(StatusPagamento.PENDENTE);
                    if (existente.getCriadoEm() == null) {
                        existente.setCriadoEm(agora);
                    }
                    pagamentoRepository.save(existente);
                }
            } else {
                Pagamento p = new Pagamento();
                p.setRodada(rodada);
                p.setUsuario(checkIn.getUsuario());
                p.setValor(valorPorPessoa);
                p.setStatus(StatusPagamento.PENDENTE);
                p.setCriadoEm(agora);
                pagamentoRepository.save(p);
            }
        }

        rodada.setValorPorParticipante(valorPorPessoa);
        rodadaRepository.save(rodada);
        return pagamentoRepository.findByRodadaId(rodadaId);
    }

    public List<Pagamento> obterPagamentosRodada(Long rodadaId) {
        return pagamentoRepository.findByRodadaId(rodadaId);
    }

    @Transactional
    public void marcarPago(Long pagamentoId, Long usuarioAdminId) {
        var pagamento = pagamentoRepository.findById(pagamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento não encontrado"));
        permissaoService.exigirAdmin(usuarioAdminId, pagamento.getRodada().getGrupo());
        pagamento.marcarComoPago();
        pagamentoRepository.save(pagamento);
    }

    public List<Pagamento> obterPendentes(Long rodadaId) {
        return pagamentoRepository.findByRodadaIdAndStatus(
                rodadaId,
                StatusPagamento.PENDENTE
        );
    }

    public Pagamento obterPagamento(Long pagamentoId) {
        return pagamentoRepository.findById(pagamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento não encontrado"));
    }

    public String gerarQrCode(Long pagamentoId) {
        var pagamento = obterPagamento(pagamentoId);
        String chavePixGerente = perfilPeladaService.configuracaoDe(pagamento.getRodada().getGrupo()).getChavePixGerente();

        if (chavePixGerente == null || chavePixGerente.isEmpty()) {
            throw new RequisicaoInvalidaException("Chave PIX não configurada no grupo");
        }

        return qrCodeService.gerarQrCode(chavePixGerente, pagamento.getValor());
    }

    public String gerarCopiaECola(Long pagamentoId) {
        var pagamento = obterPagamento(pagamentoId);
        String chavePixGerente = perfilPeladaService.configuracaoDe(pagamento.getRodada().getGrupo()).getChavePixGerente();

        if (chavePixGerente == null || chavePixGerente.isEmpty()) {
            throw new RequisicaoInvalidaException("Chave PIX não configurada no grupo");
        }

        return qrCodeService.gerarDadosPix(chavePixGerente, pagamento.getValor());
    }

    @Transactional
    public List<Pagamento> registrarDesistencia(Long rodadaId, Long usuarioId, Long usuarioAdminId) {
        Evento rodada = rodadaRepository.findById(rodadaId)
                .orElseThrow(() -> new RodadaNaoEncontradaException(rodadaId));
        permissaoService.exigirAdmin(usuarioAdminId, rodada.getGrupo());
        checkInRepository.findByEventoIdAndParticipanteUsuarioId(rodadaId, usuarioId).ifPresent(checkIn -> {
            checkIn.setStatus(StatusCheckIn.CANCELADO);
            checkInRepository.save(checkIn);
        });
        CalcularPorDesistencia(rodadaId);
        return pagamentoRepository.findByRodadaId(rodadaId);
    }

    @Transactional
    public void CalcularPorDesistencia(Long rodadaId) {
        Evento rodada = rodadaRepository.findById(rodadaId)
                .orElseThrow(() -> new RodadaNaoEncontradaException(rodadaId));

        List<Pagamento> todos = pagamentoRepository.findByRodadaId(rodadaId);
        for (Pagamento p : todos) {
            boolean segueConfirmado = checkInRepository.existsByEventoIdAndParticipanteUsuarioIdAndStatus(
                    rodadaId,
                    p.getUsuario().getId(),
                    StatusCheckIn.CONFIRMADO
            );
            if (!segueConfirmado && !p.foiPago()) {
                p.setStatus(StatusPagamento.CANCELADO);
                pagamentoRepository.save(p);
            }
        }

        long confirmadosAtuais = checkInRepository.contarPorEventoEStatus(
                rodadaId,
                StatusCheckIn.CONFIRMADO
        );

        if (confirmadosAtuais <= 0) {
            for (Pagamento p : todos) {
                if (!p.foiPago()) {
                    p.setStatus(StatusPagamento.CANCELADO);
                    pagamentoRepository.save(p);
                }
            }
            rodada.setValorPorParticipante(BigDecimal.ZERO);
            rodadaRepository.save(rodada);
            return;
        }

        BigDecimal novoValor = perfilPeladaService.configuracaoDe(rodada.getGrupo())
                .calcularValorPorPessoa((int) confirmadosAtuais);

        List<Pagamento> pendentes = obterPendentes(rodadaId);
        for (Pagamento p : pendentes) {
            p.setValor(novoValor);
            pagamentoRepository.save(p);
        }

        rodada.setValorPorParticipante(novoValor);
        rodadaRepository.save(rodada);
    }
}
