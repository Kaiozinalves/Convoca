package com.pelmanager.service;

import com.pelmanager.dto.CheckInResponseDTO;
import com.pelmanager.dto.ParticipanteCheckInResponseDTO;
import com.pelmanager.dto.request.CheckInRequestDTO;
import com.convoca.framework.domain.CheckIn;
import com.convoca.framework.domain.Evento;
import com.convoca.framework.domain.Participante;
import com.convoca.framework.domain.Usuario;
import com.convoca.framework.domain.enums.StatusCheckIn;
import com.convoca.framework.domain.enums.StatusEvento;
import com.convoca.framework.exception.AcessoNegadoException;
import com.pelmanager.exception.CheckInDuplicadoException;
import com.pelmanager.exception.OperacaoInvalidaException;
import com.convoca.framework.exception.RecursoNaoEncontradoException;
import com.pelmanager.exception.RodadaNaoEncontradaException;
import com.convoca.framework.repository.CheckInRepository;
import com.convoca.framework.repository.ParticipanteRepository;
import com.convoca.framework.repository.EventoRepository;
import com.convoca.framework.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CheckInService {
    private final CheckInRepository checkInRepository;
    private final EventoRepository rodadaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ParticipanteRepository participanteRepository;
    private final PermissaoService permissaoService;
    private final PagamentoService pagamentoService;
    private final PerfilPeladaService perfilPeladaService;

    public CheckInService(CheckInRepository checkInRepository,
                          EventoRepository rodadaRepository,
                          UsuarioRepository usuarioRepository,
                          ParticipanteRepository participanteRepository,
                          PermissaoService permissaoService,
                          PagamentoService pagamentoService,
                          PerfilPeladaService perfilPeladaService) {
        this.checkInRepository = checkInRepository;
        this.rodadaRepository = rodadaRepository;
        this.usuarioRepository = usuarioRepository;
        this.participanteRepository = participanteRepository;
        this.permissaoService = permissaoService;
        this.pagamentoService = pagamentoService;
        this.perfilPeladaService = perfilPeladaService;
    }

    @Transactional
    public CheckInResponseDTO realizarCheckIn(CheckInRequestDTO checkInRequestDTO) {
        Optional<Evento> rodada = rodadaRepository.findById(checkInRequestDTO.rodadaId());
        if (rodada.isEmpty()) {
            throw new RodadaNaoEncontradaException(checkInRequestDTO.rodadaId());
        }

        Evento rodada1 = rodada.get();
        if (!rodada1.estaAberta()) {
            throw new OperacaoInvalidaException("Rodada não está aberta para check-in.");
        }

        Usuario usuario = usuarioRepository.findById(checkInRequestDTO.usuarioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));

        Participante participante = participanteRepository
                .findByUsuarioIdAndGrupoId(usuario.getId(), rodada1.getGrupo().getId())
                .orElseThrow(() -> new AcessoNegadoException("Apenas membros do grupo podem fazer check-in nesta rodada."));

        Optional<CheckIn> existenteOpt = checkInRepository.findByEventoIdAndParticipanteUsuarioId(rodada1.getId(), usuario.getId());
        CheckIn checkInSalvo;
        if (existenteOpt.isPresent()) {
            CheckIn existente = existenteOpt.get();
            if (existente.getStatus() == StatusCheckIn.CONFIRMADO) {
                throw new CheckInDuplicadoException();
            }
            existente.setStatus(StatusCheckIn.CONFIRMADO);
            existente.setDataHoraCheckin(LocalDateTime.now());
            checkInSalvo = checkInRepository.save(existente);
        } else {
            CheckIn checkIn = new CheckIn();
            checkIn.setEvento(rodada1);
            checkIn.setParticipante(participante);
            checkIn.setDataHoraCheckin(LocalDateTime.now());
            checkIn.setStatus(StatusCheckIn.CONFIRMADO);
            checkInSalvo = checkInRepository.save(checkIn);
        }

        pagamentoService.criarPagamentoAoFinalizarCheckIn(rodada1.getId());

        return new CheckInResponseDTO(
                checkInSalvo.getId(),
                rodada1.getId(),
                usuario.getId(),
                usuario.getNome(),
                checkInSalvo.getDataHoraCheckin(),
                checkInSalvo.getStatus()
        );
    }

    @Transactional
    public List<ParticipanteCheckInResponseDTO> cancelarCheckIn(Long rodadaId, Long usuarioId) {
        Evento rodada = rodadaRepository.findById(rodadaId)
                .orElseThrow(() -> new RodadaNaoEncontradaException(rodadaId));

        checkInRepository.findByEventoIdAndParticipanteUsuarioId(rodada.getId(), usuarioId).ifPresent(checkIn -> {
            checkIn.setStatus(StatusCheckIn.CANCELADO);
            checkInRepository.save(checkIn);
        });

        pagamentoService.CalcularPorDesistencia(rodadaId);
        return listarStatusDaRodada(rodadaId);
    }

    public List<ParticipanteCheckInResponseDTO> listarStatusDaRodada(Long rodadaId) {
        Evento rodada = rodadaRepository.findById(rodadaId)
                .orElseThrow(() -> new RodadaNaoEncontradaException(rodadaId));

        return rodada.getGrupo().getParticipantes().stream()
                .map(participante -> {
                    Usuario usuario = participante.getUsuario();
                    Optional<CheckIn> checkIn = checkInRepository.findByEventoIdAndParticipanteUsuarioId(rodadaId, usuario.getId());

                    String status = checkIn.isPresent() ? checkIn.get().getStatus().name() : "PENDENTE";

                    return new ParticipanteCheckInResponseDTO(
                            usuario.getId(),
                            usuario.getNome(),
                            usuario.getApelido(),
                            perfilPeladaService.perfilDe(participante).getPosicaoPrimaria(),
                            status
                    );
                })
                .toList();
    }

    @Transactional
    public List<ParticipanteCheckInResponseDTO> confirmarTodos(Long rodadaId, Long usuarioAdminId) {
        Evento rodada = rodadaRepository.findById(rodadaId)
                .orElseThrow(() -> new RodadaNaoEncontradaException(rodadaId));
        permissaoService.exigirAdmin(usuarioAdminId, rodada.getGrupo());

        if (rodada.getStatus() != StatusEvento.AGENDADO) {
            throw new OperacaoInvalidaException("Só é possível confirmar todos enquanto a rodada está agendada.");
        }

        LocalDateTime agora = LocalDateTime.now();
        for (var participante : rodada.getGrupo().getParticipantes()) {
            Usuario usuario = participante.getUsuario();
            Optional<CheckIn> checkInExistente = checkInRepository.findByEventoIdAndParticipanteUsuarioId(
                    rodadaId, usuario.getId());

            if (checkInExistente.isPresent()) {
                CheckIn checkIn = checkInExistente.get();
                if (checkIn.getStatus() != StatusCheckIn.CONFIRMADO) {
                    checkIn.setStatus(StatusCheckIn.CONFIRMADO);
                    checkIn.setDataHoraCheckin(agora);
                    checkInRepository.save(checkIn);
                }
                continue;
            }

            CheckIn checkIn = new CheckIn();
            checkIn.setEvento(rodada);
            checkIn.setParticipante(participante);
            checkIn.setDataHoraCheckin(agora);
            checkIn.setStatus(StatusCheckIn.CONFIRMADO);
            checkInRepository.save(checkIn);
        }

        pagamentoService.criarPagamentoAoFinalizarCheckIn(rodadaId);

        return listarStatusDaRodada(rodadaId);
    }
}
