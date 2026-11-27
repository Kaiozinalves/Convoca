package com.pelmanager.service;

import com.pelmanager.dto.JogadorSorteioDTO;
import com.pelmanager.dto.JogadorTimeResponseDTO;
import com.pelmanager.dto.SorteioResponseDTO;
import com.pelmanager.dto.TimeResponseDTO;
import com.convoca.framework.domain.CheckIn;
import com.pelmanager.entity.PerfilJogador;
import com.convoca.framework.domain.Evento;
import com.convoca.framework.domain.Equipe;
import com.convoca.framework.domain.Usuario;
import com.pelmanager.entity.enums.Posicao;
import com.convoca.framework.domain.enums.StatusCheckIn;
import com.pelmanager.exception.OperacaoInvalidaException;
import com.pelmanager.exception.RodadaNaoEncontradaException;
import com.pelmanager.repository.AvaliacaoRepository;
import com.convoca.framework.repository.CheckInRepository;
import com.convoca.framework.repository.EventoRepository;
import com.convoca.framework.repository.EquipeRepository;
import com.pelmanager.sorteio.BalanceadorDeTimes;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SorteioService {
    private final CheckInRepository checkInRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final EventoRepository rodadaRepository;
    private final EquipeRepository timeRepository;
    private final PermissaoService permissaoService;
    private final PerfilPeladaService perfilPeladaService;

    public SorteioService(CheckInRepository checkInRepository, AvaliacaoRepository avaliacaoRepository,
                          EventoRepository rodadaRepository, EquipeRepository timeRepository,
                          PermissaoService permissaoService,
                          PerfilPeladaService perfilPeladaService) {
        this.checkInRepository = checkInRepository;
        this.avaliacaoRepository = avaliacaoRepository;
        this.rodadaRepository = rodadaRepository;
        this.timeRepository = timeRepository;
        this.permissaoService = permissaoService;
        this.perfilPeladaService = perfilPeladaService;
    }

    @Transactional
    public SorteioResponseDTO sortearTimes(Long rodadaId, Long usuarioId) {
        Evento rodada = rodadaRepository.findById(rodadaId)
                .orElseThrow(() -> new RodadaNaoEncontradaException(rodadaId));

        permissaoService.exigirAdmin(usuarioId, rodada.getGrupo());

        List<CheckIn> confirmados = buscarJogadoresConfirmados(rodadaId);
        int numTimes = calcularNumeroDeTimes(confirmados.size());
        validarQuantidadeMinimaDeGoleiros(confirmados, numTimes);

        limparSorteioAnterior(rodadaId);

        List<JogadorSorteioDTO> ranqueados = calcularOverallDosJogadores(confirmados);
        List<BalanceadorDeTimes.Jogador> jogadores = ranqueados.stream()
                .map(jog -> new BalanceadorDeTimes.Jogador(
                        jog.checkIn().getUsuario().getId(),
                        jog.overall(),
                        jog.posicaoPrimaria(),
                        jog.posicaoSecundaria()))
                .toList();

        BalanceadorDeTimes balanceador = new BalanceadorDeTimes();
        BalanceadorDeTimes.Resultado resultado = balanceador.sortear(jogadores, 5);

        Map<Long, CheckIn> checkInPorUsuarioId = confirmados.stream()
                .collect(Collectors.toMap(
                        checkIn -> checkIn.getUsuario().getId(),
                        checkIn -> checkIn));

        List<Equipe> timesSorteados = new ArrayList<>();
        int index = 1;
        for (BalanceadorDeTimes.TimeMontado timeMontado : resultado.times()) {
            Equipe time = new Equipe();
            time.setNome("Time " + index++);
            time.setEvento(rodada);

            List<CheckIn> jogadoresDoTime = timeMontado.escalacoes().stream()
                    .map(escalacao -> checkInPorUsuarioId.get(escalacao.jogador().id()))
                    .filter(Objects::nonNull)
                    .toList();

            jogadoresDoTime.forEach(checkIn -> checkIn.setEquipe(time));
            time.setMembros(jogadoresDoTime);
            timesSorteados.add(time);
        }

        timeRepository.saveAll(timesSorteados);

        return new SorteioResponseDTO(rodadaId, mapearTimes(timesSorteados));
    }


    @Transactional
    public SorteioResponseDTO sortearTimesAleatorio(Long rodadaId, Long usuarioId) {
        Evento rodada = rodadaRepository.findById(rodadaId)
                .orElseThrow(() -> new RodadaNaoEncontradaException(rodadaId));

        permissaoService.exigirAdmin(usuarioId, rodada.getGrupo());

        List<CheckIn> confirmados = buscarJogadoresConfirmados(rodadaId);
        int numTimes = calcularNumeroDeTimes(confirmados.size());
        validarQuantidadeMinimaDeGoleiros(confirmados, numTimes);

        limparSorteioAnterior(rodadaId);

        // Separa quem pode jogar de goleiro do resto
        List<CheckIn> goleiros = new ArrayList<>(confirmados.stream()
                .filter(c -> podeSerGoleiro(perfil(c)))
                .toList());
        Collections.shuffle(goleiros);


        List<CheckIn> goleirosTitulares = goleiros.subList(0, numTimes);
        Set<Long> idsGoleirosTitulares = goleirosTitulares.stream()
                .map(CheckIn::getId)
                .collect(Collectors.toSet());

        List<CheckIn> restante = new ArrayList<>(confirmados.stream()
                .filter(c -> !idsGoleirosTitulares.contains(c.getId()))
                .toList());
        Collections.shuffle(restante);

        List<Equipe> times = new ArrayList<>();
        for (int i = 0; i < numTimes; i++) {
            Equipe time = new Equipe();
            time.setNome("Time " + (i + 1));
            time.setEvento(rodada);
            times.add(time);
        }

        List<List<CheckIn>> jogadoresPorTime = new ArrayList<>();
        for (int i = 0; i < numTimes; i++) {
            List<CheckIn> lista = new ArrayList<>();
            lista.add(goleirosTitulares.get(i));
            jogadoresPorTime.add(lista);
        }

        int indiceTime = 0;
        for (CheckIn jogador : restante) {
            jogadoresPorTime.get(indiceTime).add(jogador);
            indiceTime = (indiceTime + 1) % numTimes;
        }

        for (int i = 0; i < numTimes; i++) {
            Equipe time = times.get(i);
            List<CheckIn> jogadoresDoTime = jogadoresPorTime.get(i);
            jogadoresDoTime.forEach(checkIn -> checkIn.setEquipe(time));
            time.setMembros(jogadoresDoTime);
        }

        timeRepository.saveAll(times);

        return new SorteioResponseDTO(rodadaId, mapearTimes(times));
    }

    public SorteioResponseDTO buscarTimesDaRodada(Long rodadaId) {
        List<Equipe> times = timeRepository.findByEventoId(rodadaId);
        return new SorteioResponseDTO(rodadaId, mapearTimes(times));
    }

    private void limparSorteioAnterior(Long rodadaId) {
        List<Equipe> antigos = timeRepository.findByEventoId(rodadaId);
        for (Equipe antigo : antigos) {
            if (antigo.getMembros() != null) {
                antigo.getMembros().forEach(checkIn -> checkIn.setEquipe(null));
            }
        }
        timeRepository.deleteAll(antigos);
    }

    private List<TimeResponseDTO> mapearTimes(List<Equipe> times) {
        return times.stream().map(time -> {
            List<JogadorTimeResponseDTO> jogadoresDTO = time.getMembros().stream()
                    .map(c -> new JogadorTimeResponseDTO(
                            c.getUsuario().getId(),
                            c.getUsuario().getNome(),
                            c.getUsuario().getApelido(),
                            perfil(c).getPosicaoPrimaria()
                    ))
                    .toList();

            return new TimeResponseDTO(time.getId(), time.getNome(), jogadoresDTO);
        }).toList();
    }

    private List<CheckIn> buscarJogadoresConfirmados(Long rodadaId) {
        return checkInRepository.findByEventoIdAndStatus(rodadaId, StatusCheckIn.CONFIRMADO);
    }

    private int calcularNumeroDeTimes(int totalConfirmados) {
        if (totalConfirmados < 10) {
            throw new OperacaoInvalidaException("Mínimo de 10 jogadores confirmados para formar 2 times.");
        }
        return totalConfirmados / 5;
    }

    private void validarQuantidadeMinimaDeGoleiros(List<CheckIn> confirmados, int numTimes) {
        int goleirosMinimos = Math.max(2, numTimes);
        long goleirosDisponiveis = confirmados.stream()
                .filter(c -> podeSerGoleiro(perfil(c)))
                .count();

        if (goleirosDisponiveis < goleirosMinimos) {
            throw new OperacaoInvalidaException(
                    "Para sortear os times, é necessário pelo menos " + goleirosMinimos
                            + " jogadores com posição primária ou secundária de GOLEIRO."
            );
        }
    }

    private List<JogadorSorteioDTO> calcularOverallDosJogadores(List<CheckIn> confirmados) {
        return confirmados.stream().map(c -> {
            Usuario u = c.getUsuario();
            Double media = avaliacaoRepository.calcularMediaDoJogador(u.getId());
            double overallFinal = (media != null) ? media : 5.0;
            PerfilJogador perfil = perfil(c);
            return new JogadorSorteioDTO(c, perfil.getPosicaoPrimaria(), perfil.getPosicaoSecundaria(), overallFinal);
        }).toList();
    }

    private PerfilJogador perfil(CheckIn checkIn) {
        return perfilPeladaService.perfilDe(checkIn.getParticipante());
    }

    private static boolean podeSerGoleiro(PerfilJogador perfil) {
        return perfil.getPosicaoPrimaria() == Posicao.GOLEIRO
                || perfil.getPosicaoSecundaria() == Posicao.GOLEIRO;
    }
}
