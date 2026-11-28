package com.pelmanager.service;

import com.pelmanager.dto.FreeAgentResponseDTO;
import com.pelmanager.dto.request.FreeAgentRequestDTO;
import com.convoca.framework.domain.Endereco;
import com.pelmanager.entity.FreeAgent;
import com.convoca.framework.domain.Grupo;
import com.pelmanager.entity.PreferenciaJogador;
import com.convoca.framework.domain.Usuario;
import com.pelmanager.entity.enums.Posicao;
import com.convoca.framework.exception.RecursoNaoEncontradoException;
import com.convoca.framework.exception.RequisicaoInvalidaException;
import com.convoca.framework.repository.AvaliacaoRepository;
import com.pelmanager.repository.FreeAgentRepository;
import com.convoca.framework.repository.GrupoRepository;
import com.convoca.framework.repository.ParticipanteRepository;
import com.convoca.framework.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class FreeAgentService {

    private static final int LIMITE_PADRAO = 20;
    private static final int LIMITE_MAXIMO = 50;

    private final FreeAgentRepository freeAgentRepository;
    private final UsuarioRepository usuarioRepository;
    private final GrupoRepository grupoRepository;
    private final ParticipanteRepository participanteRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final PerfilPeladaService perfilPeladaService;

    public FreeAgentService(FreeAgentRepository freeAgentRepository,
                            UsuarioRepository usuarioRepository,
                            GrupoRepository grupoRepository,
                            ParticipanteRepository participanteRepository,
                            AvaliacaoRepository avaliacaoRepository,
                            PerfilPeladaService perfilPeladaService) {
        this.freeAgentRepository = freeAgentRepository;
        this.usuarioRepository = usuarioRepository;
        this.grupoRepository = grupoRepository;
        this.participanteRepository = participanteRepository;
        this.avaliacaoRepository = avaliacaoRepository;
        this.perfilPeladaService = perfilPeladaService;
    }


    @Transactional
    public FreeAgentResponseDTO ativar(FreeAgentRequestDTO dto) {
        Usuario usuario = usuarioRepository.findById(dto.usuarioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        Endereco endereco = usuario.getEndereco();
        if (endereco == null || vazioParaNulo(endereco.getCidade()) == null) {
            throw new RequisicaoInvalidaException(
                    "Cadastre a cidade no seu perfil antes de ficar disponível como free agent.");
        }

        FreeAgent freeAgent = freeAgentRepository.findByUsuarioId(usuario.getId())
                .orElseGet(FreeAgent::new);

        freeAgent.setUsuario(usuario);
        aplicarEndereco(freeAgent, endereco);
        freeAgent.setDataAtivacao(LocalDateTime.now());

        return paraDTO(freeAgentRepository.save(freeAgent));
    }

    @Transactional
    public void sincronizarEndereco(Usuario usuario) {
        if (usuario.getEndereco() == null) {
            return;
        }
        freeAgentRepository.findByUsuarioId(usuario.getId()).ifPresent(freeAgent -> {
            aplicarEndereco(freeAgent, usuario.getEndereco());
            freeAgentRepository.save(freeAgent);
        });
    }

    @Transactional
    public void desativar(Long usuarioId) {
        freeAgentRepository.deleteByUsuarioId(usuarioId);
    }

    public Optional<FreeAgentResponseDTO> buscarPorUsuario(Long usuarioId) {
        return freeAgentRepository.findByUsuarioId(usuarioId).map(this::paraDTO);
    }


    public List<FreeAgentResponseDTO> buscar(Long grupoId, String cidade, String estado,
                                             String bairro, Posicao posicao, Integer limite) {
        String cidadeBusca = vazioParaNulo(cidade);
        String estadoBusca = vazioParaNulo(estado);

        if (grupoId != null) {
            Grupo grupo = grupoRepository.findById(grupoId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Pelada não encontrada."));
            Endereco enderecoGrupo = grupo.getEndereco();
            if (cidadeBusca == null && enderecoGrupo != null) {
                cidadeBusca = enderecoGrupo.getCidade();
                if (estadoBusca == null) {
                    estadoBusca = enderecoGrupo.getEstado();
                }
            }
        }

        if (cidadeBusca == null) {
            throw new RequisicaoInvalidaException("Informe a cidade (ou o grupoId) pra buscar free agents.");
        }

        int limiteFinal = (limite == null || limite < 1) ? LIMITE_PADRAO : Math.min(limite, LIMITE_MAXIMO);
        String bairroBusca = vazioParaNulo(bairro);
        String estadoFinal = estadoBusca;

        List<FreeAgentResponseDTO> resultado = freeAgentRepository.findByCidadeIgnoreCase(cidadeBusca.trim())
                .stream()
                .filter(fa -> estadoFinal == null || fa.getEstado() == null
                        || fa.getEstado().equalsIgnoreCase(estadoFinal.trim()))
                .filter(fa -> {
                    if (posicao == null) return true;
                    PreferenciaJogador preferencia = perfilPeladaService.preferenciaDe(fa.getUsuario().getId());
                    return preferencia.getPosicaoPrimaria() == posicao || preferencia.getPosicaoSecundaria() == posicao;
                })
                .filter(fa -> grupoId == null
                        || !Boolean.TRUE.equals(
                        participanteRepository.existsByUsuarioIdAndGrupoId(fa.getUsuario().getId(), grupoId)))
                .map(this::paraDTO)
                .sorted(Comparator
                        .comparingInt((FreeAgentResponseDTO dto) ->
                                bairroBusca != null && bairroBusca.equalsIgnoreCase(dto.bairro()) ? 0 : 1)
                        .thenComparing(FreeAgentResponseDTO::notaMedia,
                                Comparator.nullsLast(Comparator.<Double>reverseOrder()))
                        .thenComparing(FreeAgentResponseDTO::nome, String.CASE_INSENSITIVE_ORDER))
                .limit(limiteFinal)
                .toList();

        return resultado;
    }

    private FreeAgentResponseDTO paraDTO(FreeAgent fa) {
        Usuario u = fa.getUsuario();
        Double media = avaliacaoRepository.calcularMediaDoUsuario(u.getId());
        Double mediaArredondada = media == null ? null : Math.round(media * 10) / 10.0;
        PreferenciaJogador preferencia = perfilPeladaService.preferenciaDe(u.getId());

        return new FreeAgentResponseDTO(
                u.getId(),
                u.getNome(),
                u.getApelido(),
                preferencia.getPosicaoPrimaria(),
                preferencia.getPosicaoSecundaria(),
                preferencia.getPernaDominante(),
                fa.getCidade(),
                fa.getEstado(),
                fa.getBairro(),
                mediaArredondada
        );
    }

    private String vazioParaNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    private void aplicarEndereco(FreeAgent freeAgent, Endereco endereco) {
        freeAgent.setCidade(endereco.getCidade().trim());
        String estado = vazioParaNulo(endereco.getEstado());
        freeAgent.setEstado(estado == null ? null : estado.toUpperCase());
        freeAgent.setBairro(vazioParaNulo(endereco.getBairro()));
    }
}
