package com.pelmanager.service;

import com.pelmanager.dto.EstatisticasResponseDTO;
import com.pelmanager.dto.LinhaRankingTemporadaDTO;
import com.pelmanager.dto.LinhaEstatisticaDTO;
import com.pelmanager.dto.RankingTemporadaResponseDTO;
import com.pelmanager.entity.Gol;
import com.convoca.framework.domain.Evento;
import com.convoca.framework.domain.Usuario;
import com.convoca.framework.domain.Voto;
import com.pelmanager.entity.enums.CategoriaVoto;
import com.convoca.framework.domain.enums.StatusCheckIn;
import com.convoca.framework.domain.enums.StatusEvento;
import com.convoca.framework.exception.OperacaoInvalidaException;
import com.convoca.framework.exception.RecursoNaoEncontradoException;
import com.convoca.framework.exception.EventoNaoEncontradoException;
import com.convoca.framework.repository.CheckInRepository;
import com.pelmanager.repository.GolRepository;
import com.convoca.framework.repository.GrupoRepository;
import com.convoca.framework.repository.EventoRepository;
import com.convoca.framework.repository.UsuarioRepository;
import com.convoca.framework.repository.VotoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class EstatisticasService {

    private final GolRepository golRepository;
    private final EventoRepository rodadaRepository;
    private final GrupoRepository grupoRepository;
    private final VotoRepository votoRepository;
    private final CheckInRepository checkInRepository;
    private final UsuarioRepository usuarioRepository;

    public EstatisticasService(GolRepository golRepository,
                               EventoRepository rodadaRepository,
                               GrupoRepository grupoRepository,
                               VotoRepository votoRepository,
                               CheckInRepository checkInRepository,
                               UsuarioRepository usuarioRepository) {
        this.golRepository = golRepository;
        this.rodadaRepository = rodadaRepository;
        this.grupoRepository = grupoRepository;
        this.votoRepository = votoRepository;
        this.checkInRepository = checkInRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public EstatisticasResponseDTO daRodada(Long rodadaId) {
        if (!rodadaRepository.existsById(rodadaId)) {
            throw new EventoNaoEncontradoException(rodadaId);
        }
        return montar(rodadaId, golRepository.findByRodadaIdOrderByIdAsc(rodadaId));
    }

    public EstatisticasResponseDTO doGrupo(Long grupoId) {
        if (!grupoRepository.existsById(grupoId)) {
            throw new RecursoNaoEncontradoException("Pelada não encontrada.");
        }
        return montar(grupoId, golRepository.findByRodadaGrupoId(grupoId));
    }

    public RankingTemporadaResponseDTO temporada(Long grupoId, Long rodadaId) {
        if (!grupoRepository.existsById(grupoId)) {
            throw new RecursoNaoEncontradoException("Pelada não encontrada.");
        }

        Evento rodadaAtual = rodadaRepository.findById(rodadaId)
                .orElseThrow(() -> new EventoNaoEncontradoException(rodadaId));
        if (!rodadaAtual.getGrupo().getId().equals(grupoId)) {
            throw new RecursoNaoEncontradoException("A rodada não pertence a esta pelada.");
        }
        if (rodadaAtual.getStatus() != StatusEvento.FINALIZADO) {
            throw new OperacaoInvalidaException("O ranking da temporada fica disponível depois que a rodada termina.");
        }

        int ano = rodadaAtual.getData().getYear();
        List<Evento> temporada = rodadaRepository.findByGrupoIdOrderByIdDesc(grupoId).stream()
                .filter(r -> r.getStatus() == StatusEvento.FINALIZADO)
                .filter(r -> r.getData() != null && r.getData().getYear() == ano)
                .filter(r -> antesOuIgual(r, rodadaAtual))
                .sorted(Comparator.comparing(Evento::getData).thenComparing(Evento::getId))
                .toList();
        List<Evento> anteriores = temporada.stream()
                .filter(r -> antesOuIgual(r, rodadaAtual) && !r.getId().equals(rodadaAtual.getId()))
                .toList();

        Map<Long, List<Gol>> golsPorRodada = golRepository.findByRodadaGrupoId(grupoId).stream()
                .filter(g -> temporada.stream().anyMatch(r -> r.getId().equals(g.getRodada().getId())))
                .collect(Collectors.groupingBy(g -> g.getRodada().getId()));
        Map<Long, Integer> golsAtuais = contarGols(temporada, golsPorRodada, Gol::getAutor);
        Map<Long, Integer> assistenciasAtuais = contarGols(temporada, golsPorRodada, Gol::getAssistente);
        Map<Long, Integer> golsAnteriores = contarGols(anteriores, golsPorRodada, Gol::getAutor);
        Map<Long, Integer> assistenciasAnteriores = contarGols(anteriores, golsPorRodada, Gol::getAssistente);

        Map<Long, Integer> votosAtuais = contarVotosMelhorJogador(temporada);
        Map<Long, Integer> votosAnteriores = contarVotosMelhorJogador(anteriores);

        return new RankingTemporadaResponseDTO(
                grupoId,
                rodadaId,
                ano,
                montarRanking(votosAtuais, votosAnteriores),
                montarRanking(golsAtuais, golsAnteriores),
                montarRanking(assistenciasAtuais, assistenciasAnteriores)
        );
    }

    private boolean antesOuIgual(Evento a, Evento b) {
        int comparacaoData = a.getData().compareTo(b.getData());
        return comparacaoData < 0 || (comparacaoData == 0 && a.getId() <= b.getId());
    }

    private Map<Long, Integer> contarGols(List<Evento> rodadas,
                                          Map<Long, List<Gol>> golsPorRodada,
                                          Function<Gol, Usuario> extrator) {
        return rodadas.stream()
                .flatMap(r -> golsPorRodada.getOrDefault(r.getId(), List.of()).stream())
                .map(extrator)
                .filter(usuario -> usuario != null)
                .collect(Collectors.toMap(Usuario::getId, usuario -> 1, Integer::sum));
    }

    private Map<Long, Integer> contarVotosMelhorJogador(List<Evento> rodadas) {
        if (rodadas.isEmpty()) return Map.of();
        List<Long> idsRodadas = rodadas.stream().map(Evento::getId).toList();
        Map<Long, List<Voto>> porRodada = votoRepository
                .findByEventoIdInAndCategoriaCodigo(idsRodadas, CategoriaVoto.MELHOR_JOGADOR.name()).stream()
                .collect(Collectors.groupingBy(v -> v.getEvento().getId()));

        Map<Long, Integer> votos = new HashMap<>();
        for (Evento rodada : rodadas) {
            if (!rodada.getCategoriasVotacao().contains(CategoriaVoto.MELHOR_JOGADOR.comoCategoria())) continue;

            List<Voto> votosDaRodada = porRodada.getOrDefault(rodada.getId(), List.of());
            int totalEleitores = checkInRepository
                    .findByEventoIdAndStatus(rodada.getId(), StatusCheckIn.CONFIRMADO).size();
            boolean apuravel = VotacaoService.ENCERRADA.equals(VotacaoService.situacao(rodada))
                    || (totalEleitores > 0 && votosDaRodada.size() >= totalEleitores);
            if (!apuravel) continue;

            votosDaRodada.forEach(voto -> votos.merge(voto.getVotado().getId(), 1, Integer::sum));
        }
        return votos;
    }

    private List<LinhaRankingTemporadaDTO> montarRanking(Map<Long, Integer> atual,
                                                          Map<Long, Integer> anterior) {
        Set<Long> idsUsuarios = new java.util.HashSet<>(atual.keySet());
        idsUsuarios.addAll(anterior.keySet());
        Map<Long, Usuario> todosUsuarios = usuarioRepository.findAllById(idsUsuarios).stream()
                .collect(Collectors.toMap(Usuario::getId, usuario -> usuario));
        List<Map.Entry<Long, Integer>> ordenados = atual.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(entry -> nomeJogador(todosUsuarios, entry.getKey()), String.CASE_INSENSITIVE_ORDER))
                .toList();
        Map<Long, Integer> posicoesAnteriores = new HashMap<>();
        int indiceAnterior = 0;
        int posicaoAnterior = 0;
        Integer quantidadeAnterior = null;
        for (Map.Entry<Long, Integer> entry : anterior.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(entry -> nomeJogador(todosUsuarios, entry.getKey()), String.CASE_INSENSITIVE_ORDER))
                .toList()) {
            if (!entry.getValue().equals(quantidadeAnterior)) {
                posicaoAnterior = indiceAnterior + 1;
                quantidadeAnterior = entry.getValue();
            }
            posicoesAnteriores.put(entry.getKey(), posicaoAnterior);
            indiceAnterior++;
        }

        List<LinhaRankingTemporadaDTO> ranking = new java.util.ArrayList<>();
        Integer quantidadeAnteriorAtual = null;
        int posicaoAtual = 0;
        for (int i = 0; i < Math.min(5, ordenados.size()); i++) {
            Map.Entry<Long, Integer> entry = ordenados.get(i);
            if (!entry.getValue().equals(quantidadeAnteriorAtual)) {
                posicaoAtual = i + 1;
                quantidadeAnteriorAtual = entry.getValue();
            }
            Integer posicaoAntes = posicoesAnteriores.get(entry.getKey());
            String movimento = posicaoAntes == null ? "NOVO"
                    : posicaoAtual < posicaoAntes ? "SUBIU"
                    : posicaoAtual > posicaoAntes ? "DESCEU" : "MANTEVE";
            Usuario usuario = todosUsuarios.get(entry.getKey());
            if (usuario != null) {
                ranking.add(new LinhaRankingTemporadaDTO(
                        usuario.getId(), usuario.getNome(), usuario.getApelido(), entry.getValue(),
                        posicaoAtual, posicaoAntes, movimento));
            }
        }
        return ranking;
    }

    private String nomeJogador(Map<Long, Usuario> usuarios, Long usuarioId) {
        return usuarios.containsKey(usuarioId) ? usuarios.get(usuarioId).getNome() : "";
    }

    private EstatisticasResponseDTO montar(Long referenciaId, List<Gol> gols) {
        List<LinhaEstatisticaDTO> artilharia = contar(gols, Gol::getAutor);
        List<LinhaEstatisticaDTO> assistencias = contar(
                gols.stream().filter(g -> g.getAssistente() != null).toList(),
                Gol::getAssistente);
        return new EstatisticasResponseDTO(referenciaId, gols.size(), artilharia, assistencias);
    }

    private List<LinhaEstatisticaDTO> contar(List<Gol> gols, Function<Gol, Usuario> extrator) {
        Map<Long, Usuario> usuarios = new LinkedHashMap<>();
        Map<Long, Integer> contagem = new LinkedHashMap<>();
        for (Gol gol : gols) {
            Usuario usuario = extrator.apply(gol);
            usuarios.putIfAbsent(usuario.getId(), usuario);
            contagem.merge(usuario.getId(), 1, Integer::sum);
        }
        return contagem.entrySet().stream()
                .map(e -> {
                    Usuario u = usuarios.get(e.getKey());
                    return new LinhaEstatisticaDTO(u.getId(), u.getNome(), u.getApelido(), e.getValue());
                })
                .sorted(Comparator.comparingInt(LinhaEstatisticaDTO::quantidade).reversed()
                        .thenComparing(LinhaEstatisticaDTO::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
