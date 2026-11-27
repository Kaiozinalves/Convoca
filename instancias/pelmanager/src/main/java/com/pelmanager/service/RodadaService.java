package com.pelmanager.service;

import com.convoca.framework.service.ServicoEvento;
import com.convoca.framework.service.ServicoGrupo;
import com.pelmanager.dto.EnderecoResponseDTO;
import com.pelmanager.dto.RodadaResponseDTO;
import com.pelmanager.dto.RodadaStatusResponseDTO;
import com.pelmanager.dto.request.EnderecoRequestDTO;
import com.pelmanager.dto.request.RodadaRequestDTO;
import com.pelmanager.entity.ConfiguracaoPelada;
import com.pelmanager.entity.enums.CategoriaVoto;
import com.convoca.framework.domain.Endereco;
import com.convoca.framework.domain.Categoria;
import com.convoca.framework.domain.Evento;
import com.convoca.framework.domain.enums.StatusEvento;
import com.convoca.framework.exception.OperacaoInvalidaException;
import com.convoca.framework.exception.RecursoNaoEncontradoException;
import com.convoca.framework.exception.RequisicaoInvalidaException;
import com.convoca.framework.exception.EventoNaoEncontradoException;
import com.convoca.framework.repository.EnderecoRepository;
import com.convoca.framework.repository.EventoRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Objects;

@Service
public class RodadaService {
    private final EventoRepository rodadaRepository;
    private final EnderecoRepository enderecoRepository;
    private final EnderecoService enderecoService;
    private final ServicoGrupo servicoGrupo;
    private final ServicoEvento servicoEvento;
    private final PagamentoService pagamentoService;
    private final PerfilPeladaService perfilPeladaService;

    public RodadaService(EventoRepository rodadaRepository,
                         EnderecoRepository enderecoRepository,
                         EnderecoService enderecoService,
                         ServicoGrupo servicoGrupo,
                         ServicoEvento servicoEvento,
                         PagamentoService pagamentoService,
                         PerfilPeladaService perfilPeladaService) {
        this.rodadaRepository = rodadaRepository;
        this.enderecoRepository = enderecoRepository;
        this.enderecoService = enderecoService;
        this.servicoGrupo = servicoGrupo;
        this.servicoEvento = servicoEvento;
        this.pagamentoService = pagamentoService;
        this.perfilPeladaService = perfilPeladaService;
    }

    @Transactional
    public RodadaResponseDTO agendarRodada(RodadaRequestDTO dto) {
        // O PelManager resolve o local a partir do DTO; sem local alternativo, o framework usa o do grupo.
        Endereco localAlternativo = null;
        if (temEnderecoPreenchido(dto.enderecoAlternativo())) {
            localAlternativo = enderecoService.cadastrarEndereco(dto.enderecoAlternativo());
        } else if (dto.enderecoAlternativoId() != null) {
            localAlternativo = enderecoRepository.findById(dto.enderecoAlternativoId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Endereço alternativo não encontrado."));
        }
        Evento rodadaSalva = servicoEvento.agendar(dto.grupoId(), dto.usuarioId(), dto.data(), dto.horario(), localAlternativo);
        return paraRodadaDTO(rodadaSalva);
    }

    @Transactional
    public RodadaResponseDTO atualizarEnderecoAlternativo(Long rodadaId, Long usuarioId, EnderecoRequestDTO dto) {
        if (!temEnderecoPreenchido(dto)) {
            throw new RequisicaoInvalidaException("Informe pelo menos a rua/avenida e a cidade do endereço alternativo.");
        }
        Endereco novoEndereco = enderecoService.cadastrarEndereco(dto);
        return paraRodadaDTO(servicoEvento.definirLocalAlternativo(rodadaId, usuarioId, novoEndereco));
    }

    @Transactional
    public RodadaResponseDTO removerEnderecoAlternativo(Long rodadaId, Long usuarioId) {
        return paraRodadaDTO(servicoEvento.definirLocalAlternativo(rodadaId, usuarioId, null));
    }

    @Transactional
    public RodadaStatusResponseDTO iniciarRodada(Long rodadaId, Long usuarioId) {
        Evento salva = servicoEvento.iniciar(rodadaId, usuarioId);
        pagamentoService.criarPagamentoAoFinalizarCheckIn(rodadaId);   // até o passo 5 (PoliticaCobranca)
        return paraStatusDTO(salva);
    }

    @Transactional
    public RodadaStatusResponseDTO finalizarRodada(Long rodadaId, Long usuarioId) {
        // Categorias e prazo vêm da ConfiguracaoPelada até a PoliticaReconhecimento (passo 7).
        ConfiguracaoPelada configuracao = perfilPeladaService.configuracaoDe(buscarRodada(rodadaId).getGrupo());
        Set<Categoria> categorias = configuracao.getCategoriasAtivas().stream()
                .map(CategoriaVoto::comoCategoria)
                .collect(Collectors.toCollection(HashSet::new));
        return paraStatusDTO(servicoEvento.finalizar(rodadaId, usuarioId, categorias, configuracao.getPrazoAvaliacaoHoras()));
    }

    @Transactional
    public RodadaStatusResponseDTO encerrarVotacao(Long rodadaId, Long usuarioId) {
        Evento rodada = buscarRodada(rodadaId);
        servicoGrupo.exigirAdmin(usuarioId, rodada.getGrupo());

        if (rodada.getStatus() != StatusEvento.FINALIZADO) {
            throw new OperacaoInvalidaException("A votação só existe depois que a rodada é finalizada.");
        }
        if (!VotacaoService.ABERTA.equals(VotacaoService.situacao(rodada))) {
            throw new OperacaoInvalidaException("A votação desta rodada já está encerrada.");
        }

        rodada.setVotacaoAte(LocalDateTime.now());
        return paraStatusDTO(rodadaRepository.save(rodada));
    }

    public static RodadaResponseDTO paraRodadaDTO(Evento rodada) {
        Endereco resolvido = rodada.getEnderecoResolvido();
        EnderecoResponseDTO enderecoDTO = null;
        if (resolvido != null) {
            enderecoDTO = new EnderecoResponseDTO(
                    resolvido.getId(),
                    resolvido.getLogradouro(),
                    resolvido.getNumero(),
                    resolvido.getBairro(),
                    resolvido.getCidade(),
                    resolvido.getEstado(),
                    resolvido.getCep()
            );
        }

        Endereco enderecoGrupo = rodada.getGrupo() != null ? rodada.getGrupo().getEndereco() : null;
        boolean alternativo = rodada.getEnderecoAlternativo() != null
                && (enderecoGrupo == null || !Objects.equals(rodada.getEnderecoAlternativo().getId(), enderecoGrupo.getId()));

        return new RodadaResponseDTO(
                rodada.getId(),
                rodada.getGrupo().getId(),
                rodada.getData(),
                rodada.getHora(),
                statusParaApi(rodada.getStatus()),
                enderecoDTO,
                alternativo,
                rodada.getValorPorParticipante()
        );
    }

    /** O núcleo usa AGENDADO/FINALIZADO...; o frontend do PelManager espera AGENDADA/FINALIZADA... */
    private static String statusParaApi(StatusEvento status) {
        return switch (status) {
            case AGENDADO -> "AGENDADA";
            case EM_ANDAMENTO -> "EM_ANDAMENTO";
            case FINALIZADO -> "FINALIZADA";
            case CANCELADO -> "CANCELADA";
        };
    }

    private boolean temEnderecoPreenchido(EnderecoRequestDTO dto) {
        if (dto == null) return false;
        boolean temLogradouro = dto.logradouro() != null && !dto.logradouro().isBlank();
        boolean temCidade = dto.cidade() != null && !dto.cidade().isBlank();
        return temLogradouro && temCidade;
    }

    private Evento buscarRodada(Long rodadaId) {
        return rodadaRepository.findById(rodadaId)
                .orElseThrow(() -> new EventoNaoEncontradoException(rodadaId));
    }

    private RodadaStatusResponseDTO paraStatusDTO(Evento rodada) {
        return new RodadaStatusResponseDTO(
                rodada.getId(),
                statusParaApi(rodada.getStatus()),
                rodada.getIniciadoEm(),
                rodada.getFinalizadoEm(),
                rodada.getVotacaoAte()
        );
    }
}
