package com.pelmanager.service;

import com.convoca.framework.domain.Grupo;
import com.convoca.framework.domain.Participante;
import com.convoca.framework.domain.Usuario;
import com.pelmanager.entity.enums.StatusCheckIn;
import com.pelmanager.exception.AcessoNegadoException;
import com.pelmanager.exception.RecursoNaoEncontradoException;
import com.pelmanager.exception.RequisicaoInvalidaException;
import com.pelmanager.repository.CheckInRepository;
import com.convoca.framework.repository.ParticipanteRepository;
import com.convoca.framework.repository.UsuarioRepository;
import org.springframework.stereotype.Component;


@Component
public class PermissaoService {

    private final ParticipanteRepository participanteRepository;
    private final CheckInRepository checkInRepository;
    private final UsuarioRepository usuarioRepository;

    public PermissaoService(ParticipanteRepository participanteRepository,
                            CheckInRepository checkInRepository,
                            UsuarioRepository usuarioRepository) {
        this.participanteRepository = participanteRepository;
        this.checkInRepository = checkInRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public Usuario buscarUsuario(Long usuarioId) {
        if (usuarioId == null) {
            throw new RequisicaoInvalidaException("Informe o usuário que está realizando a ação.");
        }
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    public Participante exigirParticipante(Long usuarioId, Grupo grupo) {
        if (usuarioId == null) {
            throw new RequisicaoInvalidaException("Informe o usuário que está realizando a ação.");
        }
        return participanteRepository.findByUsuarioIdAndGrupoId(usuarioId, grupo.getId())
                .orElseThrow(() -> new AcessoNegadoException("Você não faz parte desta pelada."));
    }

    public Participante exigirAdmin(Long usuarioId, Grupo grupo) {
        Participante participante = exigirParticipante(usuarioId, grupo);
        if (!participante.isAdmin()) {
            throw new AcessoNegadoException("Apenas administradores da pelada podem realizar esta ação.");
        }
        return participante;
    }

    public boolean temCheckInConfirmado(Long rodadaId, Long usuarioId) {
        return usuarioId != null
                && checkInRepository.existsByRodadaIdAndUsuarioIdAndStatus(rodadaId, usuarioId, StatusCheckIn.CONFIRMADO);
    }
}
