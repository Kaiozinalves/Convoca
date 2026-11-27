package com.convoca.framework.service;

import com.convoca.framework.domain.Categoria;
import com.convoca.framework.domain.CheckIn;
import com.convoca.framework.domain.Endereco;
import com.convoca.framework.domain.Evento;
import com.convoca.framework.domain.Grupo;
import com.convoca.framework.domain.Participante;
import com.convoca.framework.domain.enums.StatusCheckIn;
import com.convoca.framework.domain.enums.StatusEvento;
import com.convoca.framework.exception.AcessoNegadoException;
import com.convoca.framework.exception.CheckInDuplicadoException;
import com.convoca.framework.exception.EventoAtivoExistenteException;
import com.convoca.framework.exception.EventoNaoEncontradoException;
import com.convoca.framework.exception.OperacaoInvalidaException;
import com.convoca.framework.exception.RecursoNaoEncontradoException;
import com.convoca.framework.repository.CheckInRepository;
import com.convoca.framework.repository.EventoRepository;
import com.convoca.framework.repository.GrupoRepository;
import com.convoca.framework.repository.ParticipanteRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Pontos fixos "local", "eventos" e "check-in": agendamento, local alternativo, ciclo
 * agendado → em andamento → finalizado e confirmação/cancelamento de presença.
 *
 * <p>Ainda faltam {@code fecharCheckIn}, {@code registrarVoto} e {@code apurar}, que dependem
 * dos pontos variáveis (passos 5 a 7).
 */
@Service
public class ServicoEvento {

    private final EventoRepository eventoRepository;
    private final GrupoRepository grupoRepository;
    private final CheckInRepository checkInRepository;
    private final ParticipanteRepository participanteRepository;
    private final ServicoGrupo servicoGrupo;

    public ServicoEvento(EventoRepository eventoRepository,
                         GrupoRepository grupoRepository,
                         CheckInRepository checkInRepository,
                         ParticipanteRepository participanteRepository,
                         ServicoGrupo servicoGrupo) {
        this.eventoRepository = eventoRepository;
        this.grupoRepository = grupoRepository;
        this.checkInRepository = checkInRepository;
        this.participanteRepository = participanteRepository;
        this.servicoGrupo = servicoGrupo;
    }

    public Evento buscarEvento(Long eventoId) {
        return eventoRepository.findById(eventoId)
                .orElseThrow(() -> new EventoNaoEncontradoException(eventoId));
    }

    /** Agenda um evento. Sem local alternativo, o evento grava o endereço do grupo. */
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

    /** Troca o local do evento. {@code null} volta para o endereço do grupo. */
    @Transactional
    public Evento definirLocalAlternativo(Long eventoId, Long usuarioId, Endereco local) {
        Evento evento = buscarEvento(eventoId);
        servicoGrupo.exigirAdmin(usuarioId, evento.getGrupo());
        evento.setEnderecoAlternativo(local != null ? local : evento.getGrupo().getEndereco());
        return eventoRepository.save(evento);
    }

    /** Confirma a presença. Quem tinha cancelado volta a CONFIRMADO no mesmo CheckIn. */
    @Transactional
    public CheckIn confirmarPresenca(Long eventoId, Long usuarioId){
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
        if(evento.getStatus() != StatusEvento.AGENDADO){
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
        }else {
            CheckIn checkIn = new CheckIn();
            checkIn.setEvento(evento);
            checkIn.setParticipante(participante);
            checkIn.setDataHoraCheckin(LocalDateTime.now());
            checkIn.setStatus(StatusCheckIn.CONFIRMADO);
            checkInSalvo = checkInRepository.save(checkIn);
        }

        return checkInSalvo;
    }

    /** Cancela a presença. Mantém a regra atual do PelManager: não valida o status do evento. */
    @Transactional
    public void cancelarPresenca(Long eventoId, Long usuarioId) {
        buscarEvento(eventoId);
        checkInRepository.findByEventoIdAndParticipanteUsuarioId(eventoId, usuarioId).ifPresent(checkIn -> {
            checkIn.setStatus(StatusCheckIn.CANCELADO);
            checkInRepository.save(checkIn);
        });
    }

    public boolean temCheckInConfirmado(Long eventoId, Long usuarioId) {
        return usuarioId != null
                && checkInRepository.existsByEventoIdAndParticipanteUsuarioIdAndStatus(eventoId, usuarioId, StatusCheckIn.CONFIRMADO);
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
        evento.setDataCheckInFechado(agora);
        return eventoRepository.save(evento);
    }

    /**
     * Finaliza o evento e abre a janela de votação.
     * TEMPORÁRIO: categorias e prazo vêm da instância até a PoliticaReconhecimento (passo 7).
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

        LocalDateTime agora = LocalDateTime.now();
        evento.setStatus(StatusEvento.FINALIZADO);
        evento.setFinalizadoEm(agora);
        evento.setVotacaoAte(agora.plusHours(prazoVotacaoHoras));
        evento.setCategoriasVotacao(new HashSet<>(categoriasVotacao));
        return eventoRepository.save(evento);
    }
}
