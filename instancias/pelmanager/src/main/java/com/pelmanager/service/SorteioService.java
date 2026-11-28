package com.pelmanager.service;

import com.convoca.framework.service.ServicoEvento;
import com.convoca.framework.service.ServicoGrupo;
import com.pelmanager.dto.JogadorTimeResponseDTO;
import com.pelmanager.dto.SorteioResponseDTO;
import com.pelmanager.dto.TimeResponseDTO;
import com.convoca.framework.domain.Evento;
import com.convoca.framework.domain.Equipe;
import com.pelmanager.entity.enums.ModoFormacao;
import com.convoca.framework.repository.EquipeRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Sorteio de times do PelManager. Cada rota grava o modo na ConfiguracaoPelada e pede ao núcleo
 * para fechar o check-in; quem monta os times é a EstrategiaFormacaoEquipes (EquilibradoPorNotaEPosicao).
 */
@Service
public class SorteioService {
    private final EquipeRepository timeRepository;
    private final ServicoGrupo servicoGrupo;
    private final ServicoEvento servicoEvento;
    private final PerfilPeladaService perfilPeladaService;

    public SorteioService(EquipeRepository timeRepository,
                          ServicoGrupo servicoGrupo,
                          ServicoEvento servicoEvento,
                          PerfilPeladaService perfilPeladaService) {
        this.timeRepository = timeRepository;
        this.servicoGrupo = servicoGrupo;
        this.servicoEvento = servicoEvento;
        this.perfilPeladaService = perfilPeladaService;
    }

    @Transactional
    public SorteioResponseDTO sortearTimes(Long rodadaId, Long usuarioId) {
        return fecharCheckIn(rodadaId, usuarioId, ModoFormacao.EQUILIBRADO);
    }

    @Transactional
    public SorteioResponseDTO sortearTimesAleatorio(Long rodadaId, Long usuarioId) {
        return fecharCheckIn(rodadaId, usuarioId, ModoFormacao.ALEATORIO);
    }

    public SorteioResponseDTO buscarTimesDaRodada(Long rodadaId) {
        List<Equipe> times = timeRepository.findByEventoId(rodadaId);
        return new SorteioResponseDTO(rodadaId, mapearTimes(times));
    }

    private SorteioResponseDTO fecharCheckIn(Long rodadaId, Long usuarioId, ModoFormacao modo) {
        Evento rodada = servicoEvento.buscarEvento(rodadaId);
        servicoGrupo.exigirAdmin(usuarioId, rodada.getGrupo());

        perfilPeladaService.configuracaoDe(rodada.getGrupo()).setModoFormacao(modo);
        List<Equipe> times = servicoEvento.fecharCheckIn(rodada);

        return new SorteioResponseDTO(rodadaId, mapearTimes(times));
    }

    private List<TimeResponseDTO> mapearTimes(List<Equipe> times) {
        return times.stream().map(time -> {
            List<JogadorTimeResponseDTO> jogadoresDTO = time.getMembros().stream()
                    .map(c -> new JogadorTimeResponseDTO(
                            c.getUsuario().getId(),
                            c.getUsuario().getNome(),
                            c.getUsuario().getApelido(),
                            perfilPeladaService.perfilDe(c.getParticipante()).getPosicaoPrimaria()
                    ))
                    .toList();

            return new TimeResponseDTO(time.getId(), time.getNome(), jogadoresDTO);
        }).toList();
    }
}
