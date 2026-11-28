package com.pelmanager.service;

import com.convoca.framework.service.ServicoEvento;
import com.convoca.framework.service.ServicoGrupo;
import com.pelmanager.dto.CheckInResponseDTO;
import com.pelmanager.dto.ParticipanteCheckInResponseDTO;
import com.pelmanager.dto.request.CheckInRequestDTO;
import com.convoca.framework.domain.CheckIn;
import com.convoca.framework.domain.Evento;
import com.convoca.framework.domain.Usuario;
import com.convoca.framework.domain.enums.StatusCheckIn;
import com.convoca.framework.domain.enums.StatusEvento;
import com.convoca.framework.exception.OperacaoInvalidaException;
import com.convoca.framework.exception.EventoNaoEncontradoException;
import com.convoca.framework.repository.CheckInRepository;
import com.convoca.framework.repository.EventoRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CheckInService {
    private final CheckInRepository checkInRepository;
    private final EventoRepository rodadaRepository;
    private final ServicoGrupo servicoGrupo;
    private final ServicoEvento servicoEvento;
    private final PerfilPeladaService perfilPeladaService;

    public CheckInService(CheckInRepository checkInRepository,
                          EventoRepository rodadaRepository,
                          ServicoGrupo servicoGrupo,
                          ServicoEvento servicoEvento,
                          PerfilPeladaService perfilPeladaService) {
        this.checkInRepository = checkInRepository;
        this.rodadaRepository = rodadaRepository;
        this.servicoGrupo = servicoGrupo;
        this.servicoEvento = servicoEvento;
        this.perfilPeladaService = perfilPeladaService;
    }

    @Transactional
    public CheckInResponseDTO realizarCheckIn(CheckInRequestDTO checkInRequestDTO) {
        CheckIn checkIn = servicoEvento.confirmarPresenca(checkInRequestDTO.rodadaId(), checkInRequestDTO.usuarioId());

        return new CheckInResponseDTO(
                checkIn.getId(),
                checkIn.getEvento().getId(),
                checkIn.getUsuario().getId(),
                checkIn.getUsuario().getNome(),
                checkIn.getDataHoraCheckin(),
                checkIn.getStatus()
        );
    }

    @Transactional
    public List<ParticipanteCheckInResponseDTO> cancelarCheckIn(Long rodadaId, Long usuarioId) {
        servicoEvento.cancelarPresenca(rodadaId, usuarioId);
        return listarStatusDaRodada(rodadaId);
    }

    public List<ParticipanteCheckInResponseDTO> listarStatusDaRodada(Long rodadaId) {
        Evento rodada = rodadaRepository.findById(rodadaId)
                .orElseThrow(() -> new EventoNaoEncontradoException(rodadaId));

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
                .orElseThrow(() -> new EventoNaoEncontradoException(rodadaId));
        servicoGrupo.exigirAdmin(usuarioAdminId, rodada.getGrupo());

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

        return listarStatusDaRodada(rodadaId);
    }
}
