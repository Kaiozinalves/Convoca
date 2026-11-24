package com.pelmanager.service;

import com.convoca.framework.domain.Grupo;
import com.convoca.framework.domain.Participante;
import com.convoca.framework.domain.Usuario;
import com.convoca.framework.service.ServicoGrupo;
import com.pelmanager.entity.enums.StatusCheckIn;
import com.pelmanager.repository.CheckInRepository;
import org.springframework.stereotype.Component;

/**
 * Temporário: as regras de papel (admin/membro) agora são do framework ({@link ServicoGrupo}).
 * Esta classe só repassa as chamadas, para os services do PelManager não mudarem neste passo.
 * Some no passo 3, quando o check-in for para o framework e temCheckInConfirmado for junto.
 */
@Component
public class PermissaoService {

    private final ServicoGrupo servicoGrupo;
    private final CheckInRepository checkInRepository;

    public PermissaoService(ServicoGrupo servicoGrupo, CheckInRepository checkInRepository) {
        this.servicoGrupo = servicoGrupo;
        this.checkInRepository = checkInRepository;
    }

    public Usuario buscarUsuario(Long usuarioId) {
        return servicoGrupo.buscarUsuario(usuarioId);
    }

    public Participante exigirParticipante(Long usuarioId, Grupo grupo) {
        return servicoGrupo.exigirParticipante(usuarioId, grupo);
    }

    public Participante exigirAdmin(Long usuarioId, Grupo grupo) {
        return servicoGrupo.exigirAdmin(usuarioId, grupo);
    }

    public boolean temCheckInConfirmado(Long rodadaId, Long usuarioId) {
        return usuarioId != null
                && checkInRepository.existsByRodadaIdAndUsuarioIdAndStatus(rodadaId, usuarioId, StatusCheckIn.CONFIRMADO);
    }
}
