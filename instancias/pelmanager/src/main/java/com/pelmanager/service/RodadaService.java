package com.pelmanager.service;

import com.pelmanager.dto.EnderecoResponseDTO;
import com.pelmanager.dto.RodadaResponseDTO;
import com.pelmanager.dto.RodadaStatusResponseDTO;
import com.pelmanager.dto.request.EnderecoRequestDTO;
import com.pelmanager.dto.request.RodadaRequestDTO;
import com.pelmanager.entity.ConfiguracaoPelada;
import com.pelmanager.entity.enums.CategoriaVoto;
import com.convoca.framework.domain.Endereco;
import com.convoca.framework.domain.Grupo;
import com.convoca.framework.domain.Evento;
import com.convoca.framework.domain.enums.StatusEvento;
import com.pelmanager.exception.OperacaoInvalidaException;
import com.convoca.framework.exception.RecursoNaoEncontradoException;
import com.convoca.framework.exception.RequisicaoInvalidaException;
import com.pelmanager.exception.RodadaAtivaExistenteException;
import com.pelmanager.exception.RodadaNaoEncontradaException;
import com.convoca.framework.repository.EnderecoRepository;
import com.convoca.framework.repository.GrupoRepository;
import com.convoca.framework.repository.EventoRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.Objects;

@Service
public class RodadaService {
    private final EventoRepository rodadaRepository;
    private final GrupoRepository grupoPeladaRepository;
    private final EnderecoRepository enderecoRepository;
    private final EnderecoService enderecoService;
    private final PermissaoService permissaoService;
    private final PagamentoService pagamentoService;
    private final PerfilPeladaService perfilPeladaService;

    public RodadaService(EventoRepository rodadaRepository,
                         GrupoRepository grupoPeladaRepository,
                         EnderecoRepository enderecoRepository,
                         EnderecoService enderecoService,
                         PermissaoService permissaoService,
                         PagamentoService pagamentoService,
                         PerfilPeladaService perfilPeladaService) {
        this.rodadaRepository = rodadaRepository;
        this.grupoPeladaRepository = grupoPeladaRepository;
        this.enderecoRepository = enderecoRepository;
        this.enderecoService = enderecoService;
        this.permissaoService = permissaoService;
        this.pagamentoService = pagamentoService;
        this.perfilPeladaService = perfilPeladaService;
    }

    @Transactional
    public RodadaResponseDTO agendarRodada(RodadaRequestDTO dto) {
        Grupo grupo = grupoPeladaRepository.findById(dto.grupoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Grupo não encontrado."));

        permissaoService.exigirAdmin(dto.usuarioId(), grupo);

        if (rodadaRepository.existsByGrupoIdAndStatus(grupo.getId(), StatusEvento.AGENDADO)) {
            throw new RodadaAtivaExistenteException();
        }

        Evento novaRodada = new Evento();
        novaRodada.setGrupo(grupo);
        novaRodada.setData(dto.data());
        novaRodada.setHora(dto.horario());
        novaRodada.setStatus(StatusEvento.AGENDADO);

        if (temEnderecoPreenchido(dto.enderecoAlternativo())) {
            Endereco novoEndereco = enderecoService.cadastrarEndereco(dto.enderecoAlternativo());
            novaRodada.setEnderecoAlternativo(novoEndereco);
        } else if (dto.enderecoAlternativoId() != null) {
            novaRodada.setEnderecoAlternativo(enderecoRepository.findById(dto.enderecoAlternativoId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Endereço alternativo não encontrado.")));
        } else {
            // Se não foi passado, grava fisicamente o endereço oficial do grupo na rodada
            novaRodada.setEnderecoAlternativo(grupo.getEndereco());
        }

        Evento rodadaSalva = rodadaRepository.save(novaRodada);
        return paraRodadaDTO(rodadaSalva);
    }

    @Transactional
    public RodadaResponseDTO atualizarEnderecoAlternativo(Long rodadaId, Long usuarioId, EnderecoRequestDTO dto) {
        Evento rodada = buscarRodada(rodadaId);
        permissaoService.exigirAdmin(usuarioId, rodada.getGrupo());
        if (!temEnderecoPreenchido(dto)) {
            throw new RequisicaoInvalidaException("Informe pelo menos a rua/avenida e a cidade do endereço alternativo.");
        }

        Endereco novoEndereco = enderecoService.cadastrarEndereco(dto);
        rodada.setEnderecoAlternativo(novoEndereco);
        return paraRodadaDTO(rodadaRepository.save(rodada));
    }

    @Transactional
    public RodadaResponseDTO removerEnderecoAlternativo(Long rodadaId, Long usuarioId) {
        Evento rodada = buscarRodada(rodadaId);
        permissaoService.exigirAdmin(usuarioId, rodada.getGrupo());
        rodada.setEnderecoAlternativo(rodada.getGrupo().getEndereco());
        return paraRodadaDTO(rodadaRepository.save(rodada));
    }

    @Transactional
    public RodadaStatusResponseDTO iniciarRodada(Long rodadaId, Long usuarioId) {
        Evento rodada = buscarRodada(rodadaId);
        permissaoService.exigirAdmin(usuarioId, rodada.getGrupo());

        if (rodada.getStatus() != StatusEvento.AGENDADO) {
            throw new OperacaoInvalidaException("Só é possível iniciar uma rodada que está agendada.");
        }

        LocalDateTime agora = LocalDateTime.now();
        rodada.setStatus(StatusEvento.EM_ANDAMENTO);
        rodada.setIniciadoEm(agora);
        rodada.setDataCheckInFechado(agora);
        Evento salva = rodadaRepository.save(rodada);

        pagamentoService.criarPagamentoAoFinalizarCheckIn(rodadaId);

        return paraStatusDTO(salva);
    }

    @Transactional
    public RodadaStatusResponseDTO finalizarRodada(Long rodadaId, Long usuarioId) {
        Evento rodada = buscarRodada(rodadaId);
        permissaoService.exigirAdmin(usuarioId, rodada.getGrupo());

        if (rodada.getStatus() == StatusEvento.FINALIZADO) {
            throw new OperacaoInvalidaException("Esta rodada já está finalizada.");
        }
        if (rodada.getStatus() != StatusEvento.EM_ANDAMENTO) {
            throw new OperacaoInvalidaException("Inicie a rodada antes de finalizá-la.");
        }

        LocalDateTime agora = LocalDateTime.now();
        ConfiguracaoPelada configuracao = perfilPeladaService.configuracaoDe(rodada.getGrupo());

        rodada.setStatus(StatusEvento.FINALIZADO);
        rodada.setFinalizadoEm(agora);
        rodada.setVotacaoAte(agora.plusHours(configuracao.getPrazoAvaliacaoHoras()));
        rodada.setCategoriasVotacao(configuracao.getCategoriasAtivas().stream()
                .map(CategoriaVoto::comoCategoria)
                .collect(Collectors.toCollection(HashSet::new)));
        return paraStatusDTO(rodadaRepository.save(rodada));
    }

    @Transactional
    public RodadaStatusResponseDTO encerrarVotacao(Long rodadaId, Long usuarioId) {
        Evento rodada = buscarRodada(rodadaId);
        permissaoService.exigirAdmin(usuarioId, rodada.getGrupo());

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
                .orElseThrow(() -> new RodadaNaoEncontradaException(rodadaId));
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
