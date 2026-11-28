package com.pelmanager.service;

import com.convoca.framework.service.ServicoEvento;
import com.convoca.framework.service.ServicoGrupo;
import com.convoca.framework.domain.CheckIn;
import com.pelmanager.entity.ConfiguracaoPelada;
import com.convoca.framework.domain.Pagamento;
import com.convoca.framework.domain.Evento;
import com.convoca.framework.domain.enums.StatusCheckIn;
import com.convoca.framework.domain.enums.StatusPagamento;
import com.convoca.framework.exception.RecursoNaoEncontradoException;
import com.convoca.framework.exception.RequisicaoInvalidaException;
import com.convoca.framework.exception.EventoNaoEncontradoException;
import com.convoca.framework.repository.CheckInRepository;
import com.convoca.framework.repository.PagamentoRepository;
import com.convoca.framework.repository.EventoRepository;
import com.convoca.framework.hotspot.PoliticaCobranca;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PagamentoService {

    private final PagamentoRepository pagamentoRepository;
    private final CheckInRepository checkInRepository;
    private final GerarQrCodeService qrCodeService;
    private final EventoRepository rodadaRepository;
    private final ServicoGrupo servicoGrupo;
    private final ServicoEvento servicoEvento;
    private final PerfilPeladaService perfilPeladaService;
    private final PoliticaCobranca politicaCobranca;

    public PagamentoService(PagamentoRepository pagamentoRepository,
                            CheckInRepository checkInRepository,
                            GerarQrCodeService qrCodeService,
                            EventoRepository rodadaRepository,
                            ServicoGrupo servicoGrupo,
                            ServicoEvento servicoEvento,
                            PerfilPeladaService perfilPeladaService,
                            PoliticaCobranca politicaCobranca) {
        this.pagamentoRepository = pagamentoRepository;
        this.checkInRepository = checkInRepository;
        this.qrCodeService = qrCodeService;
        this.rodadaRepository = rodadaRepository;
        this.servicoGrupo = servicoGrupo;
        this.servicoEvento = servicoEvento;
        this.perfilPeladaService = perfilPeladaService;
        this.politicaCobranca = politicaCobranca;
    }

    @Transactional
    public List<Pagamento> gerarPagamentosComoAdmin(Long rodadaId, Long usuarioAdminId) {
        Evento rodada = rodadaRepository.findById(rodadaId)
                .orElseThrow(() -> new EventoNaoEncontradoException(rodadaId));
        servicoGrupo.exigirAdmin(usuarioAdminId, rodada.getGrupo());
        return criarPagamentoAoFinalizarCheckIn(rodadaId);
    }

    @Transactional
    public List<Pagamento> criarPagamentoAoFinalizarCheckIn(Long rodadaId) {
        Evento rodada = rodadaRepository.findById(rodadaId)
                .orElseThrow(() -> new EventoNaoEncontradoException(rodadaId));

        ConfiguracaoPelada configuracao = perfilPeladaService.configuracaoDe(rodada.getGrupo());
        if (!configuracao.isSistemaPagamentoAtivo()) {
            return pagamentoRepository.findByEventoId(rodadaId);
        }

        List<CheckIn> confirmados = checkInRepository.findByEventoIdAndStatus(rodadaId, StatusCheckIn.CONFIRMADO);

        // A PoliticaCobranca (RateioDoCampo) diz quem deve quanto; aqui só conciliamos com o que já existe.
        // TEMPORÁRIO: no passo 6 as cobranças passam a ser geradas uma vez, no fecharCheckIn do núcleo.
        conciliar(rodadaId, politicaCobranca.gerarCobrancas(rodada, confirmados));
        return pagamentoRepository.findByEventoId(rodadaId);
    }

    /**
     * Aplica as cobranças calculadas sobre os pagamentos já gravados: quem segue cobrado tem o valor
     * atualizado (e volta a PENDENTE se tinha sido cancelado), quem não está mais na lista é cancelado
     * e quem ainda não tinha pagamento ganha um. Pagamentos já pagos nunca mudam.
     */
    private void conciliar(Long rodadaId, List<Pagamento> cobrancas) {
        Map<Long, Pagamento> cobrancaPorUsuario = new HashMap<>();
        for (Pagamento cobranca : cobrancas) {
            cobrancaPorUsuario.put(cobranca.getUsuario().getId(), cobranca);
        }

        for (Pagamento existente : pagamentoRepository.findByEventoId(rodadaId)) {
            Pagamento cobranca = cobrancaPorUsuario.remove(existente.getUsuario().getId());
            if (existente.foiPago()) {
                continue;
            }
            if (cobranca == null) {
                existente.setStatus(StatusPagamento.CANCELADO);
            } else {
                existente.setValor(cobranca.getValor());
                existente.setStatus(StatusPagamento.PENDENTE);
                if (existente.getCriadoEm() == null) {
                    existente.setCriadoEm(cobranca.getCriadoEm());
                }
            }
            pagamentoRepository.save(existente);
        }

        // Sobraram só as cobranças de quem ainda não tinha pagamento neste evento.
        pagamentoRepository.saveAll(cobrancaPorUsuario.values());
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
        servicoEvento.cancelarPresenca(rodadaId, usuarioId);
        CalcularPorDesistencia(rodadaId);
        return pagamentoRepository.findByEventoId(rodadaId);
    }

    /** Recalcula as cobranças depois que alguém sai: o rateio entre os que ficaram é o mesmo do check-in. */
    @Transactional
    public void CalcularPorDesistencia(Long rodadaId) {
        criarPagamentoAoFinalizarCheckIn(rodadaId);
    }
}
