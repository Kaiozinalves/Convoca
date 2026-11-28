package com.pelmanager.service;

import com.convoca.framework.service.ServicoEvento;
import com.pelmanager.dto.AvaliacaoResponseDTO;
import com.pelmanager.dto.request.AvaliacaoRequestDTO;
import com.convoca.framework.domain.Avaliacao;
import com.convoca.framework.domain.Evento;
import com.convoca.framework.domain.Usuario;
import com.convoca.framework.domain.enums.StatusEvento;
import com.convoca.framework.exception.OperacaoInvalidaException;
import com.convoca.framework.exception.RecursoNaoEncontradoException;
import com.convoca.framework.exception.EventoNaoEncontradoException;
import com.convoca.framework.exception.RequisicaoInvalidaException;
import com.convoca.framework.repository.AvaliacaoRepository;
import com.convoca.framework.repository.EventoRepository;
import com.convoca.framework.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AvaliacaoService {
    private final AvaliacaoRepository avaliacaoRepository;
    private final EventoRepository rodadaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ServicoEvento servicoEvento;

    public AvaliacaoService(AvaliacaoRepository avaliacaoRepository,
                            EventoRepository rodadaRepository,
                            UsuarioRepository usuarioRepository,
                            ServicoEvento servicoEvento) {
        this.avaliacaoRepository = avaliacaoRepository;
        this.rodadaRepository = rodadaRepository;
        this.usuarioRepository = usuarioRepository;
        this.servicoEvento = servicoEvento;
    }

    @Transactional
    public AvaliacaoResponseDTO avaliarJogador(AvaliacaoRequestDTO dto){

        Evento rodada = rodadaRepository.findById(dto.rodadaId())
                .orElseThrow(() -> new EventoNaoEncontradoException(dto.rodadaId()));

        if (rodada.getStatus() != StatusEvento.FINALIZADO) {
            throw new OperacaoInvalidaException("As avaliações só são permitidas após o fim da rodada.");
        }

        if (!VotacaoService.ABERTA.equals(VotacaoService.situacao(rodada))) {
            throw new OperacaoInvalidaException("O período de avaliação desta rodada já foi encerrado.");
        }

        Usuario avaliador = usuarioRepository.findById(dto.avaliadorId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Avaliador não encontrado."));

        Usuario avaliado = usuarioRepository.findById(dto.avaliadoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Jogador avaliado não encontrado."));

        if (dto.nota() == null || dto.nota() < 1 || dto.nota() > 5) {
            throw new RequisicaoInvalidaException("A nota deve ficar entre 1 e 5.");
        }
        if (avaliador.getId().equals(avaliado.getId())) {
            throw new RequisicaoInvalidaException("Você não pode avaliar a si mesmo.");
        }
        if (!servicoEvento.temCheckInConfirmado(rodada.getId(), avaliador.getId())
                || !servicoEvento.temCheckInConfirmado(rodada.getId(), avaliado.getId())) {
            throw new RequisicaoInvalidaException("Só jogadores confirmados nesta rodada podem participar da avaliação.");
        }

        if (avaliacaoRepository.existsByEventoIdAndAvaliadorIdAndAvaliadoId(rodada.getId(), avaliador.getId(), avaliado.getId())) {
            throw new OperacaoInvalidaException("Você já avaliou este jogador nesta rodada.");
        }

        Avaliacao avaliacao = new Avaliacao();
        avaliacao.setEvento(rodada);
        avaliacao.setAvaliador(avaliador);
        avaliacao.setAvaliado(avaliado);
        avaliacao.setNota(dto.nota());
        avaliacao.setComentario(dto.comentario());
        avaliacao.setDataAvaliacao(LocalDateTime.now());

        Avaliacao salva = avaliacaoRepository.save(avaliacao);

        return new AvaliacaoResponseDTO(salva.getId(),
                rodada.getId(),
                avaliador.getId(),
                avaliado.getId(),
                avaliado.getNome(),
                salva.getNota(),
                salva.getComentario(),
                salva.getDataAvaliacao());

    }

    public List<AvaliacaoResponseDTO> listarPorRodada(Long rodadaId) {
        List<Avaliacao> avaliacoes = avaliacaoRepository.findByEventoId(rodadaId);

        return avaliacoes.stream()
                .map(av -> new AvaliacaoResponseDTO(
                        av.getId(),
                        av.getEvento().getId(),
                        av.getAvaliador().getId(),
                        av.getAvaliado().getId(),
                        av.getAvaliado().getNome(),
                        av.getNota(),
                        av.getComentario(),
                        av.getDataAvaliacao()
                ))
                .toList();
    }

}
