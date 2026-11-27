package com.pelmanager.service;

import com.pelmanager.dto.*;
import com.pelmanager.dto.request.ConfiguracaoAvaliacaoRequestDTO;
import com.pelmanager.dto.request.EntrarGrupoPeladaRequestDTO;
import com.pelmanager.dto.request.GrupoPeladaRequestDTO;
import com.pelmanager.entity.ConfiguracaoPelada;
import com.convoca.framework.domain.Endereco;
import com.convoca.framework.domain.Grupo;
import com.pelmanager.entity.enums.CategoriaVoto;
import com.convoca.framework.exception.RecursoNaoEncontradoException;
import com.convoca.framework.exception.RequisicaoInvalidaException;
import com.convoca.framework.repository.GrupoRepository;
import com.convoca.framework.service.ServicoGrupo;
import com.convoca.framework.repository.EventoRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GrupoPeladaService {

    private static final int PRAZO_MAXIMO_HORAS = 168; // 7 dias

    private final GrupoRepository grupoRepository;
    private final EventoRepository rodadaRepository;
    private final PerfilPeladaService perfilPeladaService;
    private final ServicoGrupo servicoGrupo;

    public GrupoPeladaService(GrupoRepository grupoRepository,
                              EventoRepository rodadaRepository,
                              PerfilPeladaService perfilPeladaService,
                              ServicoGrupo servicoGrupo) {
        this.grupoRepository = grupoRepository;
        this.rodadaRepository = rodadaRepository;
        this.perfilPeladaService = perfilPeladaService;
        this.servicoGrupo = servicoGrupo;
    }

    @Transactional
    public GrupoPeladaResponseDTO criar(GrupoPeladaRequestDTO dto) {
        Endereco endereco = new Endereco();
        endereco.setLogradouro(dto.endereco().logradouro());
        endereco.setNumero(dto.endereco().numero());
        endereco.setBairro(dto.endereco().bairro());
        endereco.setCidade(dto.endereco().cidade());
        endereco.setEstado(dto.endereco().estado());
        endereco.setCep(dto.endereco().cep());

        Grupo grupo = new Grupo();
        grupo.setNome(dto.nome());
        grupo.setEndereco(endereco);
        grupo.setPublica(dto.publica() == null || dto.publica());
        grupo.setPerfil(new ConfiguracaoPelada());

        // O framework gera o código de convite e a data, e torna o fundador ADMIN.
        Grupo grupoSalvo = servicoGrupo.criarGrupo(grupo, dto.fundadorId(),
                perfilPeladaService.novoPerfilPara(dto.fundadorId()));

        return new GrupoPeladaResponseDTO(
                grupoSalvo.getId(),
                grupoSalvo.getNome(),
                grupoSalvo.getCodigoConvite(),
                grupoSalvo.getDataCriacao(),
                paraEnderecoDTO(grupoSalvo.getEndereco()),
                grupoSalvo.ehPublica()
        );
    }

    @Transactional
    public void entrarNoGrupo(EntrarGrupoPeladaRequestDTO dto) {
        servicoGrupo.EntrarNoGrupo(dto.IdUsuario(), dto.codigoConvite(),
                perfilPeladaService.novoPerfilPara(dto.IdUsuario()));
    }

    public List<GrupoPeladaResponseDTO> listarPeladasDoUsuario(Long usuarioId) {
        List<Grupo> grupos = grupoRepository.findByUsuarioId(usuarioId);

        return grupos.stream()
                .map(grupo -> new GrupoPeladaResponseDTO(
                        grupo.getId(),
                        grupo.getNome(),
                        grupo.getCodigoConvite(),
                        grupo.getDataCriacao(),
                        paraEnderecoDTO(grupo.getEndereco()),
                        grupo.ehPublica()
                ))
                .toList();
    }

    public GrupoPeladaDetalhesResponseDTO buscarDetalhes(Long id) {
        Grupo grupo = grupoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pelada não encontrada."));

        List<ParticipanteResponseDTO> participantes = grupo.getParticipantes().stream()
                .map(participante -> new ParticipanteResponseDTO(
                        participante.getId(),
                        participante.getUsuario().getId(),
                        participante.getUsuario().getNome(),
                        participante.getUsuario().getApelido(),
                        participante.getPapel(),
                        participante.getDataEntrada()
                ))
                .toList();

        List<RodadaResponseDTO> rodadas = rodadaRepository.findByGrupoIdOrderByIdDesc(id).stream()
                .map(RodadaService::paraRodadaDTO)
                .toList();

        ConfiguracaoPelada configuracao = perfilPeladaService.configuracaoDe(grupo);
        ConfiguracaoPagamentoDTO configPagamento = ConfiguracaoPagamentoDTO.builder()
                .ativo(configuracao.isSistemaPagamentoAtivo())
                .valorTotal(configuracao.getValorTotalPelada())
                .chavePixGerente(configuracao.getChavePixGerente())
                .build();

        return new GrupoPeladaDetalhesResponseDTO(
                grupo.getId(),
                grupo.getNome(),
                grupo.getCodigoConvite(),
                grupo.getDataCriacao(),
                participantes,
                rodadas,
                paraConfiguracaoDTO(grupo),
                paraEnderecoDTO(grupo.getEndereco()),
                configPagamento
        );
    }

    @Transactional
    public ConfiguracaoAvaliacaoDTO atualizarConfiguracaoAvaliacao(Long grupoId, ConfiguracaoAvaliacaoRequestDTO dto) {
        Grupo grupo = grupoRepository.findById(grupoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pelada não encontrada."));

        servicoGrupo.exigirAdmin(dto.usuarioId(), grupo);

        if (dto.melhorJogadorAtiva() == null || dto.melhorAssistenteAtiva() == null
                || dto.bagreAtiva() == null || dto.prazoHoras() == null) {
            throw new RequisicaoInvalidaException("Informe todas as categorias e o prazo da votação.");
        }
        if (dto.prazoHoras() < 1 || dto.prazoHoras() > PRAZO_MAXIMO_HORAS) {
            throw new RequisicaoInvalidaException(
                    "O prazo da votação deve ficar entre 1 e " + PRAZO_MAXIMO_HORAS + " horas.");
        }

        ConfiguracaoPelada configuracao = perfilPeladaService.configuracaoDe(grupo);
        configuracao.definirCategoria(CategoriaVoto.MELHOR_JOGADOR, dto.melhorJogadorAtiva());
        configuracao.definirCategoria(CategoriaVoto.MELHOR_ASSISTENTE, dto.melhorAssistenteAtiva());
        configuracao.definirCategoria(CategoriaVoto.BAGRE, dto.bagreAtiva());
        if (dto.revelacaoAtiva() != null) {
            configuracao.definirCategoria(CategoriaVoto.REVELACAO, dto.revelacaoAtiva());
        }
        configuracao.setPrazoAvaliacaoHoras(dto.prazoHoras());

        return paraConfiguracaoDTO(grupoRepository.save(grupo));
    }

    private EnderecoResponseDTO paraEnderecoDTO(Endereco endereco) {
        if (endereco == null) return null;
        return new EnderecoResponseDTO(
                endereco.getId(),
                endereco.getLogradouro(),
                endereco.getNumero(),
                endereco.getBairro(),
                endereco.getCidade(),
                endereco.getEstado(),
                endereco.getCep()
        );
    }

    private ConfiguracaoAvaliacaoDTO paraConfiguracaoDTO(Grupo grupo) {
        ConfiguracaoPelada configuracao = perfilPeladaService.configuracaoDe(grupo);
        return new ConfiguracaoAvaliacaoDTO(
                configuracao.categoriaAtiva(CategoriaVoto.MELHOR_JOGADOR),
                configuracao.categoriaAtiva(CategoriaVoto.MELHOR_ASSISTENTE),
                configuracao.categoriaAtiva(CategoriaVoto.BAGRE),
                configuracao.categoriaAtiva(CategoriaVoto.REVELACAO),
                configuracao.getPrazoAvaliacaoHoras()
        );
    }

}
