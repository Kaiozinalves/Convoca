package com.pelmanager.hotspot;

import com.convoca.framework.domain.Categoria;
import com.convoca.framework.domain.Evento;
import com.convoca.framework.domain.Reconhecimento;
import com.convoca.framework.hotspot.PoliticaReconhecimento;
import com.convoca.framework.repository.ParticipanteRepository;
import com.convoca.framework.repository.VotoRepository;
import com.pelmanager.entity.enums.CategoriaVoto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reconhecimento da pelada: votação de fim de rodada (melhor jogador, melhor assistente, bagre,
 * revelação). Em cada categoria ativa na rodada vence quem teve mais votos; empate gera um
 * reconhecimento para cada empatado. Só contam votos de quem confirmou presença.
 */
@Component
public class ReconhecimentoPorVotos implements PoliticaReconhecimento {

    private final VotoRepository votoRepository;
    private final ParticipanteRepository participanteRepository;

    public ReconhecimentoPorVotos(VotoRepository votoRepository, ParticipanteRepository participanteRepository) {
        this.votoRepository = votoRepository;
        this.participanteRepository = participanteRepository;
    }

    @Override
    public List<Categoria> categorias() {
        return Arrays.stream(CategoriaVoto.values()).map(CategoriaVoto::comoCategoria).toList();
    }

    @Override
    public boolean exigeVotacao() {
        return true;
    }

    @Override
    public List<Reconhecimento> apurar(Evento evento) {
        // código da categoria -> (id do usuário votado -> votos)
        Map<String, Map<Long, Long>> votosPorCategoria = new HashMap<>();
        for (Object[] linha : votoRepository.apurarVotos(evento.getId())) {
            votosPorCategoria.computeIfAbsent((String) linha[0], codigo -> new HashMap<>())
                    .put((Long) linha[1], (Long) linha[2]);
        }

        List<Reconhecimento> reconhecimentos = new ArrayList<>();
        for (Categoria categoria : evento.getCategoriasVotacao()) {
            Map<Long, Long> contagem = votosPorCategoria.getOrDefault(categoria.getCodigo(), Map.of());
            long maximo = contagem.values().stream().mapToLong(Long::longValue).max().orElse(0);
            if (maximo == 0) {
                continue;   // ninguém votou nesta categoria
            }
            contagem.forEach((usuarioId, votos) -> {
                if (votos == maximo) {
                    participanteRepository.findByUsuarioIdAndGrupoId(usuarioId, evento.getGrupo().getId())
                            .ifPresent(vencedor -> {
                                Reconhecimento reconhecimento = new Reconhecimento();
                                reconhecimento.setCategoria(new Categoria(categoria.getCodigo(), categoria.getRotulo()));
                                reconhecimento.setVencedor(vencedor);
                                reconhecimentos.add(reconhecimento);
                            });
                }
            });
        }
        return reconhecimentos;
    }
}
