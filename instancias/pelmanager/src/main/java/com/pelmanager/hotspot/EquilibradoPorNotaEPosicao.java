package com.pelmanager.hotspot;

import com.convoca.framework.domain.CheckIn;
import com.convoca.framework.domain.Equipe;
import com.convoca.framework.domain.Evento;
import com.convoca.framework.exception.OperacaoInvalidaException;
import com.convoca.framework.hotspot.EstrategiaFormacaoEquipes;
import com.convoca.framework.repository.AvaliacaoRepository;
import com.pelmanager.entity.PerfilJogador;
import com.pelmanager.entity.enums.ModoFormacao;
import com.pelmanager.entity.enums.Posicao;
import com.pelmanager.service.PerfilPeladaService;
import com.pelmanager.sorteio.BalanceadorDeTimes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Formação de times da pelada. No modo {@code EQUILIBRADO} (padrão), usa o {@link BalanceadorDeTimes}
 * com a nota média das avaliações e as posições do {@code PerfilJogador}. No modo {@code ALEATORIO}
 * ({@code ConfiguracaoPelada.modoFormacao}), só garante um goleiro por time e embaralha o resto.
 *
 * <p>Regras da pelada nos dois modos: pelo menos 10 confirmados, times de 5 e pelo menos um
 * goleiro (posição primária ou secundária) por time.
 */
@Component
public class EquilibradoPorNotaEPosicao implements EstrategiaFormacaoEquipes {

    private static final int JOGADORES_POR_TIME = 5;
    private static final double NOTA_SEM_AVALIACAO = 5.0;

    private final AvaliacaoRepository avaliacaoRepository;
    private final PerfilPeladaService perfilPeladaService;

    public EquilibradoPorNotaEPosicao(AvaliacaoRepository avaliacaoRepository,
                                     PerfilPeladaService perfilPeladaService) {
        this.avaliacaoRepository = avaliacaoRepository;
        this.perfilPeladaService = perfilPeladaService;
    }

    @Override
    public List<Equipe> formar(Evento evento, List<CheckIn> confirmados) {
        int numTimes = calcularNumeroDeTimes(confirmados.size());
        validarQuantidadeMinimaDeGoleiros(confirmados, numTimes);

        ModoFormacao modo = perfilPeladaService.configuracaoDe(evento.getGrupo()).getModoFormacao();
        return modo == ModoFormacao.ALEATORIO
                ? formarAleatorio(confirmados, numTimes)
                : formarEquilibrado(confirmados);
    }

    private List<Equipe> formarEquilibrado(List<CheckIn> confirmados) {
        List<BalanceadorDeTimes.Jogador> jogadores = confirmados.stream()
                .map(checkIn -> {
                    Double media = avaliacaoRepository.calcularMediaDoUsuario(checkIn.getUsuario().getId());
                    PerfilJogador perfil = perfil(checkIn);
                    return new BalanceadorDeTimes.Jogador(
                            checkIn.getUsuario().getId(),
                            media != null ? media : NOTA_SEM_AVALIACAO,
                            perfil.getPosicaoPrimaria(),
                            perfil.getPosicaoSecundaria());
                })
                .toList();

        BalanceadorDeTimes.Resultado resultado = new BalanceadorDeTimes().sortear(jogadores, JOGADORES_POR_TIME);

        Map<Long, CheckIn> checkInPorUsuarioId = confirmados.stream()
                .collect(Collectors.toMap(checkIn -> checkIn.getUsuario().getId(), checkIn -> checkIn));

        List<Equipe> times = new ArrayList<>();
        for (BalanceadorDeTimes.TimeMontado timeMontado : resultado.times()) {
            List<CheckIn> membros = timeMontado.escalacoes().stream()
                    .map(escalacao -> checkInPorUsuarioId.get(escalacao.jogador().id()))
                    .filter(Objects::nonNull)
                    .toList();
            times.add(novoTime(times.size() + 1, membros));
        }
        return times;
    }

    private List<Equipe> formarAleatorio(List<CheckIn> confirmados, int numTimes) {
        // Um goleiro sorteado para cada time; o resto embaralhado e distribuído em rodízio.
        List<CheckIn> goleiros = new ArrayList<>(confirmados.stream()
                .filter(checkIn -> podeSerGoleiro(perfil(checkIn)))
                .toList());
        Collections.shuffle(goleiros);
        List<CheckIn> goleirosTitulares = goleiros.subList(0, numTimes);
        Set<Long> idsGoleirosTitulares = goleirosTitulares.stream()
                .map(CheckIn::getId)
                .collect(Collectors.toSet());

        List<CheckIn> restante = new ArrayList<>(confirmados.stream()
                .filter(checkIn -> !idsGoleirosTitulares.contains(checkIn.getId()))
                .toList());
        Collections.shuffle(restante);

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

        List<Equipe> times = new ArrayList<>();
        for (List<CheckIn> membros : jogadoresPorTime) {
            times.add(novoTime(times.size() + 1, membros));
        }
        return times;
    }

    private static Equipe novoTime(int numero, List<CheckIn> membros) {
        Equipe time = new Equipe();
        time.setNome("Time " + numero);
        time.setMembros(new ArrayList<>(membros));
        return time;
    }

    private static int calcularNumeroDeTimes(int totalConfirmados) {
        if (totalConfirmados < 2 * JOGADORES_POR_TIME) {
            throw new OperacaoInvalidaException("Mínimo de 10 jogadores confirmados para formar 2 times.");
        }
        return totalConfirmados / JOGADORES_POR_TIME;
    }

    private void validarQuantidadeMinimaDeGoleiros(List<CheckIn> confirmados, int numTimes) {
        int goleirosMinimos = Math.max(2, numTimes);
        long goleirosDisponiveis = confirmados.stream()
                .filter(checkIn -> podeSerGoleiro(perfil(checkIn)))
                .count();

        if (goleirosDisponiveis < goleirosMinimos) {
            throw new OperacaoInvalidaException(
                    "Para sortear os times, é necessário pelo menos " + goleirosMinimos
                            + " jogadores com posição primária ou secundária de GOLEIRO."
            );
        }
    }

    private PerfilJogador perfil(CheckIn checkIn) {
        return perfilPeladaService.perfilDe(checkIn.getParticipante());
    }

    private static boolean podeSerGoleiro(PerfilJogador perfil) {
        return perfil.getPosicaoPrimaria() == Posicao.GOLEIRO
                || perfil.getPosicaoSecundaria() == Posicao.GOLEIRO;
    }
}
