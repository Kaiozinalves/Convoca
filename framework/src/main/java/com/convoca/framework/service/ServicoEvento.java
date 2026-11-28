package com.convoca.framework.service;

import com.convoca.framework.domain.*;
import com.convoca.framework.domain.enums.StatusCheckIn;
import com.convoca.framework.domain.enums.StatusEvento;
import com.convoca.framework.domain.enums.StatusPagamento;
import com.convoca.framework.exception.*;
import com.convoca.framework.hotspot.EstrategiaFormacaoEquipes;
import com.convoca.framework.hotspot.PoliticaCobranca;
import com.convoca.framework.hotspot.PoliticaReconhecimento;
import com.convoca.framework.repository.AvaliacaoRepository;
import com.convoca.framework.repository.CheckInRepository;
import com.convoca.framework.repository.EquipeRepository;
import com.convoca.framework.repository.EventoRepository;
import com.convoca.framework.repository.GrupoRepository;
import com.convoca.framework.repository.PagamentoRepository;
import com.convoca.framework.repository.ParticipanteRepository;
import com.convoca.framework.repository.ReconhecimentoRepository;
import com.convoca.framework.repository.VotoRepository;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pontos fixos "local", "eventos", "check-in", "formação de equipes" e "avaliação": agendamento,
 * local alternativo, ciclo agendado → em andamento → finalizado, confirmação/cancelamento de
 * presença, fechamento do check-in (equipes e cobranças), votos e avaliações.
 *
 * <p>O núcleo conduz os fluxos e chama os pontos variáveis da instância: {@link EstrategiaFormacaoEquipes}
 * e {@link PoliticaCobranca} no {@code fecharCheckIn}; {@link PoliticaReconhecimento} no
 * {@code finalizar} (abre ou não a votação) e no {@code apurar}.
 */
@Service
public class ServicoEvento {

    private final EventoRepository eventoRepository;
    private final GrupoRepository grupoRepository;
    private final CheckInRepository checkInRepository;
    private final ParticipanteRepository participanteRepository;
    private final VotoRepository votoRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final EquipeRepository equipeRepository;
    private final PagamentoRepository pagamentoRepository;
    private final ReconhecimentoRepository reconhecimentoRepository;
    private final ServicoGrupo servicoGrupo;
    private final EstrategiaFormacaoEquipes estrategiaFormacaoEquipes;
    private final PoliticaCobranca politicaCobranca;
    private final PoliticaReconhecimento politicaReconhecimento;

    public ServicoEvento(EventoRepository eventoRepository,
                         GrupoRepository grupoRepository,
                         CheckInRepository checkInRepository,
                         ParticipanteRepository participanteRepository,
                         VotoRepository votoRepository,
                         AvaliacaoRepository avaliacaoRepository,
                         EquipeRepository equipeRepository,
                         PagamentoRepository pagamentoRepository,
                         ReconhecimentoRepository reconhecimentoRepository,
                         ServicoGrupo servicoGrupo,
                         EstrategiaFormacaoEquipes estrategiaFormacaoEquipes,
                         PoliticaCobranca politicaCobranca,
                         PoliticaReconhecimento politicaReconhecimento) {
        this.eventoRepository = eventoRepository;
        this.grupoRepository = grupoRepository;
        this.checkInRepository = checkInRepository;
        this.participanteRepository = participanteRepository;
        this.votoRepository = votoRepository;
        this.avaliacaoRepository = avaliacaoRepository;
        this.equipeRepository = equipeRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.reconhecimentoRepository = reconhecimentoRepository;
        this.servicoGrupo = servicoGrupo;
        this.estrategiaFormacaoEquipes = estrategiaFormacaoEquipes;
        this.politicaCobranca = politicaCobranca;
        this.politicaReconhecimento = politicaReconhecimento;
    }

    public Evento buscarEvento(Long eventoId) {
        return eventoRepository.findById(eventoId)
                .orElseThrow(() -> new EventoNaoEncontradoException(eventoId));
    }

    /**
     * Agenda um evento. Sem local alternativo, o evento grava o endereço do grupo.
     */
    @Transactional
    public Evento agendar(Long grupoId, Long usuarioId, LocalDate data, LocalTime hora, Endereco localAlternativo) {
        Grupo grupo = grupoRepository.findById(grupoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Grupo não encontrado."));
        servicoGrupo.exigirAdmin(usuarioId, grupo);

        if (eventoRepository.existsByGrupoIdAndStatus(grupo.getId(), StatusEvento.AGENDADO)) {
            throw new EventoAtivoExistenteException();
        }

        Evento evento = new Evento();
        evento.setGrupo(grupo);
        evento.setData(data);
        evento.setHora(hora);
        evento.setStatus(StatusEvento.AGENDADO);
        evento.setEnderecoAlternativo(localAlternativo != null ? localAlternativo : grupo.getEndereco());
        return eventoRepository.save(evento);
    }

    /**
     * Troca o local do evento. {@code null} volta para o endereço do grupo.
     */
    @Transactional
    public Evento definirLocalAlternativo(Long eventoId, Long usuarioId, Endereco local) {
        Evento evento = buscarEvento(eventoId);
        servicoGrupo.exigirAdmin(usuarioId, evento.getGrupo());
        evento.setEnderecoAlternativo(local != null ? local : evento.getGrupo().getEndereco());
        return eventoRepository.save(evento);
    }

    /**
     * Confirma a presença. Quem tinha cancelado volta a CONFIRMADO no mesmo CheckIn.
     */
    @Transactional
    public CheckIn confirmarPresenca(Long eventoId, Long usuarioId) {
        Optional<Evento> eventoOpt = eventoRepository.findById(eventoId);
        if (eventoOpt.isEmpty()) {
            throw new EventoNaoEncontradoException(eventoId);
        }

        Optional<Participante> participanteOpt = participanteRepository.findByUsuarioIdAndGrupoId(usuarioId, eventoOpt.get().getGrupo().getId());
        if (participanteOpt.isEmpty()) {
            throw new AcessoNegadoException("Apenas membros do grupo podem fazer check-in neste evento.");
        }

        Participante participante = participanteOpt.get();

        Evento evento = eventoOpt.get();
        if (evento.getStatus() != StatusEvento.AGENDADO) {
            throw new OperacaoInvalidaException("Evento não está aberto para check-in.");
        }

        Optional<CheckIn> checkInOpt = checkInRepository.findByEventoIdAndParticipanteUsuarioId(eventoId, usuarioId);
        CheckIn checkInSalvo;
        if (checkInOpt.isPresent()) {
            CheckIn checkIn = checkInOpt.get();
            if (checkIn.getStatus() == StatusCheckIn.CONFIRMADO) {
                throw new CheckInDuplicadoException();
            }
            checkIn.setStatus(StatusCheckIn.CONFIRMADO);
            checkIn.setDataHoraCheckin(LocalDateTime.now());
            checkInSalvo = checkInRepository.save(checkIn);
        } else {
            CheckIn checkIn = new CheckIn();
            checkIn.setEvento(evento);
            checkIn.setParticipante(participante);
            checkIn.setDataHoraCheckin(LocalDateTime.now());
            checkIn.setStatus(StatusCheckIn.CONFIRMADO);
            checkInSalvo = checkInRepository.save(checkIn);
        }

        return checkInSalvo;
    }

    /**
     * Cancela a presença. Mantém a regra atual do PelManager: não valida o status do evento.
     * Se a pessoa já tinha cobrança em aberto, ela é cancelada; as dos outros não são recalculadas.
     */
    @Transactional
    public void cancelarPresenca(Long eventoId, Long usuarioId) {
        buscarEvento(eventoId);
        checkInRepository.findByEventoIdAndParticipanteUsuarioId(eventoId, usuarioId).ifPresent(checkIn -> {
            checkIn.setStatus(StatusCheckIn.CANCELADO);
            checkInRepository.save(checkIn);
        });
        pagamentoRepository.findByEventoIdAndUsuarioId(eventoId, usuarioId)
                .filter(pagamento -> !pagamento.foiPago())
                .ifPresent(pagamento -> {
                    pagamento.setStatus(StatusPagamento.CANCELADO);
                    pagamentoRepository.save(pagamento);
                });
    }

    public boolean temCheckInConfirmado(Long eventoId, Long usuarioId) {
        return usuarioId != null
                && checkInRepository.existsByEventoIdAndParticipanteUsuarioIdAndStatus(eventoId, usuarioId, StatusCheckIn.CONFIRMADO);
    }

    /**
     * Fecha o check-in: forma as equipes com os confirmados e gera as cobranças. Fluxo fixo:
     * valida o status → busca os confirmados → {@link EstrategiaFormacaoEquipes#formar} → salva as
     * equipes → {@link PoliticaCobranca#gerarCobrancas} → salva os pagamentos.
     *
     * <p>Pode ser refeito enquanto o evento está agendado: as equipes anteriores são desfeitas e as
     * cobranças, atualizadas (pagamentos já pagos nunca mudam). Quem confirmar depois entra no
     * próximo fechamento.
     */
    @Transactional
    public List<Equipe> fecharCheckIn(Evento evento) {
        evento = buscarEvento(evento.getId());
        if (evento.getStatus() != StatusEvento.AGENDADO) {
            throw new OperacaoInvalidaException("Só é possível fechar o check-in de um evento agendado.");
        }

        List<CheckIn> confirmados = checkInRepository.findByEventoIdAndStatus(evento.getId(), StatusCheckIn.CONFIRMADO);

        desfazerEquipes(evento.getId());
        List<Equipe> equipes = estrategiaFormacaoEquipes.formar(evento, confirmados);
        for (Equipe equipe : equipes) {
            equipe.setEvento(evento);
            equipe.getMembros().forEach(membro -> membro.setEquipe(equipe));
        }
        equipeRepository.saveAll(equipes);

        salvarCobrancas(evento, politicaCobranca.gerarCobrancas(evento, confirmados));

        evento.setDataCheckInFechado(LocalDateTime.now());
        eventoRepository.save(evento);
        return equipes;
    }

    /**
     * Gera de novo as cobranças com os confirmados de agora, sem mexer nas equipes. Serve para a
     * instância cobrar num momento que ela escolhe (no PelManager: ao iniciar sem sorteio, ou pelo
     * botão "gerar cobranças"). Devolve todos os pagamentos do evento.
     */
    @Transactional
    public List<Pagamento> atualizarCobrancas(Evento evento) {
        evento = buscarEvento(evento.getId());
        List<CheckIn> confirmados = checkInRepository.findByEventoIdAndStatus(evento.getId(), StatusCheckIn.CONFIRMADO);
        salvarCobrancas(evento, politicaCobranca.gerarCobrancas(evento, confirmados));
        return pagamentoRepository.findByEventoId(evento.getId());
    }

    private void desfazerEquipes(Long eventoId) {
        List<Equipe> anteriores = equipeRepository.findByEventoId(eventoId);
        for (Equipe anterior : anteriores) {
            if (anterior.getMembros() != null) {
                anterior.getMembros().forEach(checkIn -> checkIn.setEquipe(null));
            }
        }
        equipeRepository.deleteAll(anteriores);
    }

    /**
     * Aplica as cobranças da política sobre os pagamentos já gravados, reaproveitando as linhas (os
     * ids, usados no QR code, não mudam): quem segue cobrado tem o valor atualizado e volta a
     * PENDENTE, quem saiu da lista é cancelado e quem ainda não tinha pagamento ganha um.
     * Pagamentos já pagos nunca mudam.
     */
    private void salvarCobrancas(Evento evento, List<Pagamento> cobrancas) {
        Map<Long, Pagamento> cobrancaPorUsuario = new HashMap<>();
        for (Pagamento cobranca : cobrancas) {
            cobrancaPorUsuario.put(cobranca.getUsuario().getId(), cobranca);
        }

        for (Pagamento existente : pagamentoRepository.findByEventoId(evento.getId())) {
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
        for (Pagamento nova : cobrancaPorUsuario.values()) {
            nova.setEvento(evento);
            pagamentoRepository.save(nova);
        }
    }

    @Transactional
    public Evento iniciar(Long eventoId, Long usuarioId) {
        Evento evento = buscarEvento(eventoId);
        servicoGrupo.exigirAdmin(usuarioId, evento.getGrupo());

        if (evento.getStatus() != StatusEvento.AGENDADO) {
            throw new OperacaoInvalidaException("Só é possível iniciar um evento que está agendado.");
        }

        LocalDateTime agora = LocalDateTime.now();
        evento.setStatus(StatusEvento.EM_ANDAMENTO);
        evento.setIniciadoEm(agora);
        if (evento.getDataCheckInFechado() == null) {   // sem fecharCheckIn antes, o check-in fecha agora
            evento.setDataCheckInFechado(agora);
        }
        return eventoRepository.save(evento);
    }

    /**
     * Finaliza o evento. Se a {@link PoliticaReconhecimento} exige votação, abre a janela com as
     * categorias escolhidas pela instância (todas do catálogo da política) e o prazo dado; a apuração
     * fica para quando a janela fechar. Sem votação, apura na hora.
     */
    @Transactional
    public Evento finalizar(Long eventoId, Long usuarioId, Set<Categoria> categoriasVotacao, int prazoVotacaoHoras) {
        Evento evento = buscarEvento(eventoId);
        servicoGrupo.exigirAdmin(usuarioId, evento.getGrupo());

        if (evento.getStatus() == StatusEvento.FINALIZADO) {
            throw new OperacaoInvalidaException("Este evento já está finalizado.");
        }
        if (evento.getStatus() != StatusEvento.EM_ANDAMENTO) {
            throw new OperacaoInvalidaException("Inicie o evento antes de finalizá-lo.");
        }
        boolean exigeVotacao = politicaReconhecimento.exigeVotacao();
        if (exigeVotacao && !politicaReconhecimento.categorias().containsAll(categoriasVotacao)) {
            throw new RequisicaoInvalidaException("Há categorias de votação que a política de reconhecimento não conhece.");
        }

        LocalDateTime agora = LocalDateTime.now();
        evento.setStatus(StatusEvento.FINALIZADO);
        evento.setFinalizadoEm(agora);
        if (exigeVotacao) {
            evento.setVotacaoAte(agora.plusHours(prazoVotacaoHoras));
            evento.setCategoriasVotacao(new HashSet<>(categoriasVotacao));
        }
        Evento salvo = eventoRepository.save(evento);

        if (!exigeVotacao) {
            apurar(salvo);
        }
        return salvo;
    }

    /**
     * Apura o evento: pede os reconhecimentos à {@link PoliticaReconhecimento} e os grava. Só depois
     * que o evento é finalizado e a votação (se houver) fechou. Pode ser chamado de novo: os
     * reconhecimentos anteriores do evento são substituídos, então nada duplica.
     */
    @Transactional
    public List<Reconhecimento> apurar(Evento evento) {
        Evento atual = buscarEvento(evento.getId());
        if (atual.getStatus() != StatusEvento.FINALIZADO) {
            throw new OperacaoInvalidaException("Só é possível apurar um evento finalizado.");
        }
        if (atual.votacaoAberta()) {
            throw new OperacaoInvalidaException("A votação deste evento ainda está aberta.");
        }

        reconhecimentoRepository.deleteAll(reconhecimentoRepository.findByEventoId(atual.getId()));
        List<Reconhecimento> reconhecimentos = politicaReconhecimento.apurar(atual);
        reconhecimentos.forEach(reconhecimento -> reconhecimento.setEvento(atual));
        return reconhecimentoRepository.saveAll(reconhecimentos);
    }

    /**
     * Registra um voto. A instância entrega o Voto com votante, votado e categoria; o núcleo valida,
     * completa evento e dataVoto e salva.
     */
    @Transactional
    public Voto registrarVoto(Evento evento, Voto voto) {
        Optional<Evento> eventoOpt = eventoRepository.findById(evento.getId());
        if (eventoOpt.isEmpty()) {
            throw new EventoNaoEncontradoException(evento.getId());
        }
        evento = eventoOpt.get();

        if (evento.getStatus() != StatusEvento.FINALIZADO) {
            throw new OperacaoInvalidaException("Só é possível registrar votos em eventos finalizados.");
        }
        if (!evento.votacaoAberta()) {
            throw new OperacaoInvalidaException("O período de votação deste evento já foi encerrado.");
        }
        if (voto.getCategoria() == null) {
            throw new RequisicaoInvalidaException("Informe a categoria do voto.");
        }
        if (!evento.getCategoriasVotacao().contains(voto.getCategoria())) {
            throw new OperacaoInvalidaException("Categoria de voto não está ativa neste evento.");
        }
        if (voto.getVotante().getId() == null || voto.getVotado().getId() == null) {
            throw new RequisicaoInvalidaException("Informe o votante e o votado.");
        }
        if (voto.getVotante().getId().equals(voto.getVotado().getId())) {
            throw new RequisicaoInvalidaException("Não é possível votar em si mesmo.");
        }
        Usuario votante = servicoGrupo.buscarUsuario(voto.getVotante().getId());
        Usuario votado = servicoGrupo.buscarUsuario(voto.getVotado().getId());
        if (!temCheckInConfirmado(evento.getId(), votante.getId()) || !temCheckInConfirmado(evento.getId(), votado.getId())) {
            throw new OperacaoInvalidaException("Só participantes com presença confirmada podem votar e ser votados.");
        }

        if (votoRepository.existsByEventoIdAndCategoriaCodigoAndVotanteId(evento.getId(), voto.getCategoria().getCodigo(), votante.getId())) {
            throw new OperacaoInvalidaException("Você já votou nesta categoria neste evento.");
        }

        voto.setEvento(evento);
        voto.setVotante(votante);
        voto.setVotado(votado);
        voto.setDataVoto(LocalDateTime.now());
        try {
            // saveAndFlush: se duas requisições passarem juntas pela checagem acima,
            // a constraint única do banco barra a segunda aqui dentro, e não no commit.
            return votoRepository.saveAndFlush(voto);
        } catch (DataIntegrityViolationException e) {
            throw new OperacaoInvalidaException("Você já votou nesta categoria neste evento.");
        }

    }

    /**
     * Registra uma avaliação. A instância entrega a Avaliacao com avaliador, avaliado, nota e
     * comentário; o núcleo valida, completa evento e dataAvaliacao e salva.
     */
    @Transactional
    public Avaliacao registrarAvaliacao(Evento evento, Avaliacao avaliacao) {
        Optional<Evento> eventoOpt = eventoRepository.findById(evento.getId());
        if (eventoOpt.isEmpty()) {
            throw new EventoNaoEncontradoException(evento.getId());
        }
        evento = eventoOpt.get();

        if (evento.getStatus() != StatusEvento.FINALIZADO) {
            throw new OperacaoInvalidaException("Só é possível avaliar depois que o evento é finalizado.");
        }
        if (!evento.votacaoAberta()) {
            throw new OperacaoInvalidaException("O período de avaliação deste evento já foi encerrado.");
        }
        if (avaliacao.getNota() == null || avaliacao.getNota() < 1 || avaliacao.getNota() > 5) {
            throw new RequisicaoInvalidaException("A nota deve ficar entre 1 e 5.");
        }
        if (avaliacao.getAvaliador().getId() == null || avaliacao.getAvaliado().getId() == null) {
            throw new RequisicaoInvalidaException("Informe o avaliador e o avaliado.");
        }
        if (avaliacao.getAvaliador().getId().equals(avaliacao.getAvaliado().getId())) {
            throw new RequisicaoInvalidaException("Não é possível avaliar a si mesmo.");
        }
        Usuario avaliador = servicoGrupo.buscarUsuario(avaliacao.getAvaliador().getId());
        Usuario avaliado = servicoGrupo.buscarUsuario(avaliacao.getAvaliado().getId());
        if (!temCheckInConfirmado(evento.getId(), avaliador.getId()) || !temCheckInConfirmado(evento.getId(), avaliado.getId())) {
            throw new RequisicaoInvalidaException("Só participantes com presença confirmada podem avaliar e ser avaliados.");
        }

        if (avaliacaoRepository.existsByEventoIdAndAvaliadorIdAndAvaliadoId(evento.getId(), avaliador.getId(), avaliado.getId())) {
            throw new OperacaoInvalidaException("Você já avaliou este participante neste evento.");
        }

        avaliacao.setEvento(evento);
        avaliacao.setAvaliador(avaliador);
        avaliacao.setAvaliado(avaliado);
        avaliacao.setDataAvaliacao(LocalDateTime.now());
        return avaliacaoRepository.save(avaliacao);
    }
}


