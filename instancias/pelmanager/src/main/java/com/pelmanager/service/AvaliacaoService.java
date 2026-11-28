package com.pelmanager.service;

import com.convoca.framework.service.ServicoEvento;
import com.pelmanager.dto.AvaliacaoResponseDTO;
import com.pelmanager.dto.request.AvaliacaoRequestDTO;
import com.convoca.framework.domain.Avaliacao;
import com.convoca.framework.domain.Evento;
import com.convoca.framework.domain.Usuario;
import com.convoca.framework.exception.RecursoNaoEncontradoException;
import com.convoca.framework.exception.EventoNaoEncontradoException;
import com.convoca.framework.repository.AvaliacaoRepository;
import com.convoca.framework.repository.EventoRepository;
import com.convoca.framework.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

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

        Usuario avaliador = usuarioRepository.findById(dto.avaliadorId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Avaliador não encontrado."));

        Usuario avaliado = usuarioRepository.findById(dto.avaliadoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Jogador avaliado não encontrado."));

        // As regras da avaliação (janela, nota, presença, duplicidade) ficam no ServicoEvento.
        Avaliacao avaliacao = new Avaliacao();
        avaliacao.setAvaliador(avaliador);
        avaliacao.setAvaliado(avaliado);
        avaliacao.setNota(dto.nota());
        avaliacao.setComentario(dto.comentario());

        Avaliacao salva = servicoEvento.registrarAvaliacao(rodada, avaliacao);

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
