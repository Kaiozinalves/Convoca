package com.pelmanager.service;

import com.convoca.framework.service.ServicoEvento;
import com.convoca.framework.service.ServicoGrupo;
import com.pelmanager.entity.ConfiguracaoPelada;
import com.convoca.framework.domain.Pagamento;
import com.convoca.framework.domain.Evento;
import com.convoca.framework.domain.enums.StatusPagamento;
import com.convoca.framework.exception.RecursoNaoEncontradoException;
import com.convoca.framework.exception.RequisicaoInvalidaException;
import com.convoca.framework.exception.EventoNaoEncontradoException;
import com.convoca.framework.repository.PagamentoRepository;
import com.convoca.framework.repository.EventoRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PagamentoService {

    private final PagamentoRepository pagamentoRepository;
    private final GerarQrCodeService qrCodeService;
    private final EventoRepository rodadaRepository;
    private final ServicoGrupo servicoGrupo;
    private final ServicoEvento servicoEvento;
    private final PerfilPeladaService perfilPeladaService;

    public PagamentoService(PagamentoRepository pagamentoRepository,
                            GerarQrCodeService qrCodeService,
                            EventoRepository rodadaRepository,
                            ServicoGrupo servicoGrupo,
                            ServicoEvento servicoEvento,
                            PerfilPeladaService perfilPeladaService) {
        this.pagamentoRepository = pagamentoRepository;
        this.qrCodeService = qrCodeService;
        this.rodadaRepository = rodadaRepository;
        this.servicoGrupo = servicoGrupo;
        this.servicoEvento = servicoEvento;
        this.perfilPeladaService = perfilPeladaService;
    }

    @Transactional
    public List<Pagamento> gerarPagamentosComoAdmin(Long rodadaId, Long usuarioAdminId) {
        Evento rodada = rodadaRepository.findById(rodadaId)
                .orElseThrow(() -> new EventoNaoEncontradoException(rodadaId));
        servicoGrupo.exigirAdmin(usuarioAdminId, rodada.getGrupo());
        return atualizarCobrancas(rodadaId);
    }

    /**
     * Gera de novo as cobranças com os confirmados de agora (ao iniciar a rodada e no botão "gerar
     * cobranças"). O sorteio já gera as cobranças no fecharCheckIn do núcleo; check-in e cancelamento
     * não recalculam nada. Com o sistema de pagamento desligado, nada muda.
     */
    @Transactional
    public List<Pagamento> atualizarCobrancas(Long rodadaId) {
        Evento rodada = rodadaRepository.findById(rodadaId)
                .orElseThrow(() -> new EventoNaoEncontradoException(rodadaId));

        ConfiguracaoPelada configuracao = perfilPeladaService.configuracaoDe(rodada.getGrupo());
        if (!configuracao.isSistemaPagamentoAtivo()) {
            return pagamentoRepository.findByEventoId(rodadaId);
        }

        return servicoEvento.atualizarCobrancas(rodada);
    }

    /**
     * Valor atual por participante, para o RodadaResponseDTO: o de uma cobrança pendente (ou, se todos
     * já pagaram, o de uma paga). {@code null} quando não há cobrança ativa.
     */
    public BigDecimal valorPorParticipante(Long rodadaId) {
        List<Pagamento> pagamentos = pagamentoRepository.findByEventoId(rodadaId);
        return pagamentos.stream()
                .filter(p -> p.getStatus() == StatusPagamento.PENDENTE)
                .findFirst()
                .or(() -> pagamentos.stream().filter(Pagamento::foiPago).findFirst())
                .map(Pagamento::getValor)
                .orElse(null);
    }

    public List<Pagamento> obterPagamentosRodada(Long rodadaId) {
        return pagamentoRepository.findByEventoId(rodadaId);
    }

    @Transactional
    public void marcarPago(Long pagamentoId, Long usuarioAdminId) {
        var pagamento = pagamentoRepository.findById(pagamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento não encontrado"));
        servicoGrupo.exigirAdmin(usuarioAdminId, pagamento.getEvento().getGrupo());
        pagamento.marcarComoPago();
        pagamentoRepository.save(pagamento);
    }

    public List<Pagamento> obterPendentes(Long rodadaId) {
        return pagamentoRepository.findByEventoIdAndStatus(
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
        String chavePixGerente = perfilPeladaService.configuracaoDe(pagamento.getEvento().getGrupo()).getChavePixGerente();

        if (chavePixGerente == null || chavePixGerente.isEmpty()) {
            throw new RequisicaoInvalidaException("Chave PIX não configurada no grupo");
        }

        return qrCodeService.gerarQrCode(chavePixGerente, pagamento.getValor());
    }

    public String gerarCopiaECola(Long pagamentoId) {
        var pagamento = obterPagamento(pagamentoId);
        String chavePixGerente = perfilPeladaService.configuracaoDe(pagamento.getEvento().getGrupo()).getChavePixGerente();

        if (chavePixGerente == null || chavePixGerente.isEmpty()) {
            throw new RequisicaoInvalidaException("Chave PIX não configurada no grupo");
        }

        return qrCodeService.gerarDadosPix(chavePixGerente, pagamento.getValor());
    }

    @Transactional
    public List<Pagamento> registrarDesistencia(Long rodadaId, Long usuarioId, Long usuarioAdminId) {
        Evento rodada = rodadaRepository.findById(rodadaId)
                .orElseThrow(() -> new EventoNaoEncontradoException(rodadaId));
        servicoGrupo.exigirAdmin(usuarioAdminId, rodada.getGrupo());
        servicoEvento.cancelarPresenca(rodadaId, usuarioId);   // cancela também a cobrança em aberto dele
        return pagamentoRepository.findByEventoId(rodadaId);
    }
}
