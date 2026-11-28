package com.pelmanager.service;

import com.convoca.framework.service.ServicoEvento;
import com.convoca.framework.service.ServicoGrupo;
import com.pelmanager.dto.*;
import com.pelmanager.dto.request.VotoRequestDTO;
import com.convoca.framework.domain.CheckIn;
import com.convoca.framework.domain.Evento;
import com.convoca.framework.domain.Usuario;
import com.convoca.framework.domain.Voto;
import com.pelmanager.entity.enums.CategoriaVoto;
import com.convoca.framework.domain.enums.StatusCheckIn;
import com.convoca.framework.domain.enums.StatusEvento;
import com.convoca.framework.exception.RequisicaoInvalidaException;
import com.convoca.framework.exception.EventoNaoEncontradoException;
import com.convoca.framework.repository.CheckInRepository;
import com.convoca.framework.repository.EventoRepository;
import com.convoca.framework.repository.ReconhecimentoRepository;
import com.convoca.framework.repository.UsuarioRepository;
import com.convoca.framework.repository.VotoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class VotacaoService {

    public static final String AGUARDANDO_RODADA = "AGUARDANDO_RODADA";
    public static final String ABERTA = "ABERTA";
    public static final String ENCERRADA = "ENCERRADA";

    private final EventoRepository rodadaRepository;
    private final VotoRepository votoRepository;
    private final CheckInRepository checkInRepository;
    private final UsuarioRepository usuarioRepository;
    private final ServicoGrupo servicoGrupo;
    private final ServicoEvento servicoEvento;
    private final ReconhecimentoRepository reconhecimentoRepository;

    public VotacaoService(EventoRepository rodadaRepository,
                          VotoRepository votoRepository,
                          CheckInRepository checkInRepository,
                          UsuarioRepository usuarioRepository,
                          ServicoGrupo servicoGrupo,
                          ServicoEvento servicoEvento,
                          ReconhecimentoRepository reconhecimentoRepository) {
        this.rodadaRepository = rodadaRepository;
        this.votoRepository = votoRepository;
        this.checkInRepository = checkInRepository;
        this.usuarioRepository = usuarioRepository;
        this.servicoGrupo = servicoGrupo;
        this.servicoEvento = servicoEvento;
        this.reconhecimentoRepository = reconhecimentoRepository;
    }

    @Transactional
    public VotoRegistradoDTO votar(Long rodadaId, VotoRequestDTO dto) {
        Evento rodada = buscarRodada(rodadaId);

        // Validação do request (API do PelManager); as regras do voto ficam no ServicoEvento.
        if (dto.categoria() == null) {
            throw new RequisicaoInvalidaException("Informe a categoria do voto.");
        }
        if (dto.votanteId() == null || dto.votadoId() == null) {
            throw new RequisicaoInvalidaException("Informe quem está votando e em quem.");
        }

        Voto voto = new Voto();
        voto.setCategoria(dto.categoria().comoCategoria());
        voto.setVotante(servicoGrupo.buscarUsuario(dto.votanteId()));
        voto.setVotado(servicoGrupo.buscarUsuario(dto.votadoId()));

        Voto registrado = servicoEvento.registrarVoto(rodada, voto);
        return new VotoRegistradoDTO(rodadaId, dto.categoria().name(), registrado.getDataVoto());
    }

    @Transactional
    public VotacaoResponseDTO consultar(Long rodadaId, Long usuarioId) {
        Evento rodada = buscarRodada(rodadaId);
        servicoGrupo.exigirParticipante(usuarioId, rodada.getGrupo());

        String situacao = situacao(rodada);
        if (rodada.getStatus() != StatusEvento.FINALIZADO) {
            return new VotacaoResponseDTO(rodadaId, situacao, null, false, 0, List.of(), List.of());
        }

        List<CheckIn> confirmados = checkInRepository.findByEventoIdAndStatus(rodadaId, StatusCheckIn.CONFIRMADO);
        int totalEleitores = confirmados.size();
        boolean elegivel = confirmados.stream().anyMatch(c -> c.getUsuario().getId().equals(usuarioId));
        boolean encerrada = ENCERRADA.equals(situacao);
        if (encerrada && !reconhecimentoRepository.existsByEventoId(rodadaId)) {
            servicoEvento.apurar(rodada);   // primeira consulta depois do prazo: registra os vencedores
        }

        // Candidatos: quem confirmou presença, exceto o próprio usuário.
        List<JogadorResumoDTO> candidatos = elegivel
                ? confirmados.stream()
                    .map(CheckIn::getUsuario)
                    .filter(u -> !u.getId().equals(usuarioId))
                    .map(this::resumo)
                    .sorted(Comparator.comparing(JogadorResumoDTO::nome, String.CASE_INSENSITIVE_ORDER))
                    .toList()
                : List.of();

        Map<CategoriaVoto, Map<Long, Long>> apuracao = apurar(rodadaId);
        Map<Long, Usuario> usuariosVotados = carregarUsuarios(apuracao);
        Set<CategoriaVoto> jaVotei = votoRepository.findByEventoIdAndVotanteId(rodadaId, usuarioId).stream()
                .map(voto -> CategoriaVoto.valueOf(voto.getCategoria().getCodigo()))
                .collect(Collectors.toSet());

        List<CategoriaVotacaoDTO> categorias = new ArrayList<>();
        for (CategoriaVoto categoria : CategoriaVoto.values()) {
            if (!rodada.getCategoriasVotacao().contains(categoria.comoCategoria())) {
                continue;
            }
            Map<Long, Long> contagem = apuracao.getOrDefault(categoria, Map.of());
            int votosRecebidos = (int) contagem.values().stream().mapToLong(Long::longValue).sum();

            // Resultado só aparece quando não dá mais para votar (prazo/encerramento manual)
            // ou quando todos os eleitores já votaram nesta categoria.
            boolean resultadoLiberado = encerrada || (totalEleitores > 0 && votosRecebidos >= totalEleitores);
            ResultadoCategoriaDTO resultado = resultadoLiberado
                    ? montarResultado(contagem, usuariosVotados, votosRecebidos)
                    : null;

            categorias.add(new CategoriaVotacaoDTO(
                    categoria.name(),
                    categoria.getRotulo(),
                    jaVotei.contains(categoria),
                    votosRecebidos,
                    totalEleitores,
                    resultado
            ));
        }

        return new VotacaoResponseDTO(rodadaId, situacao, rodada.getVotacaoAte(), elegivel, totalEleitores, categorias, candidatos);
    }

    /** AGUARDANDO_RODADA | ABERTA | ENCERRADA — usado também por RodadaService. */
    public static String situacao(Evento rodada) {
        if (rodada.getStatus() != StatusEvento.FINALIZADO) {
            return AGUARDANDO_RODADA;
        }
        return rodada.votacaoAberta() ? ABERTA : ENCERRADA;
    }


    private ResultadoCategoriaDTO montarResultado(Map<Long, Long> contagem,
                                                  Map<Long, Usuario> usuarios,
                                                  int totalVotos) {
        List<ApuracaoDTO> ranking = contagem.entrySet().stream()
                .map(e -> {
                    Usuario u = usuarios.get(e.getKey());
                    return new ApuracaoDTO(u.getId(), u.getNome(), u.getApelido(), e.getValue().intValue());
                })
                .sorted(Comparator.comparingInt(ApuracaoDTO::votos).reversed()
                        .thenComparing(ApuracaoDTO::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int maximo = ranking.isEmpty() ? 0 : ranking.get(0).votos();
        List<ApuracaoDTO> vencedores = ranking.stream()
                .filter(a -> a.votos() == maximo)
                .toList();

        return new ResultadoCategoriaDTO(totalVotos, vencedores.size() > 1, vencedores, ranking);
    }

    private Map<CategoriaVoto, Map<Long, Long>> apurar(Long rodadaId) {
        Map<CategoriaVoto, Map<Long, Long>> resultado = new EnumMap<>(CategoriaVoto.class);
        for (Object[] linha : votoRepository.apurarVotos(rodadaId)) {
            CategoriaVoto categoria = CategoriaVoto.valueOf((String) linha[0]);
            Long votadoId = (Long) linha[1];
            Long quantidade = (Long) linha[2];
            resultado.computeIfAbsent(categoria, c -> new HashMap<>()).put(votadoId, quantidade);
        }
        return resultado;
    }

    private Map<Long, Usuario> carregarUsuarios(Map<CategoriaVoto, Map<Long, Long>> apuracao) {
        Set<Long> ids = apuracao.values().stream()
                .flatMap(m -> m.keySet().stream())
                .collect(Collectors.toSet());
        return usuarioRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Usuario::getId, u -> u));
    }

    private JogadorResumoDTO resumo(Usuario usuario) {
        return new JogadorResumoDTO(usuario.getId(), usuario.getNome(), usuario.getApelido());
    }

    private Evento buscarRodada(Long rodadaId) {
        return rodadaRepository.findById(rodadaId)
                .orElseThrow(() -> new EventoNaoEncontradoException(rodadaId));
    }
}
