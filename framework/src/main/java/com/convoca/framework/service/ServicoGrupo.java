package com.convoca.framework.service;

import com.convoca.framework.domain.Grupo;
import com.convoca.framework.domain.Participante;
import com.convoca.framework.domain.PerfilAtividade;
import com.convoca.framework.domain.Usuario;
import com.convoca.framework.domain.enums.Papel;
import com.convoca.framework.exception.*;
import com.convoca.framework.repository.GrupoRepository;
import com.convoca.framework.repository.ParticipanteRepository;
import com.convoca.framework.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Ponto fixo "usuários e grupos": entrada por código de convite e papéis (admin e membro).
 *
 * <p>O serviço cria o {@link Participante} sem conhecer a instância. A parte específica da
 * atividade (o {@code PerfilAtividade}) é fornecida pela instância, que é a única que sabe criá-la.
 */
@Service
public class ServicoGrupo {

    private final GrupoRepository grupoRepository;
    private final ParticipanteRepository participanteRepository;
    private final UsuarioRepository usuarioRepository;

    public ServicoGrupo(GrupoRepository grupoRepository,
                        ParticipanteRepository participanteRepository,
                        UsuarioRepository usuarioRepository) {
        this.grupoRepository = grupoRepository;
        this.participanteRepository = participanteRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public Usuario buscarUsuario(Long usuarioId) {
        if (usuarioId == null) {
            throw new RequisicaoInvalidaException("Informe o usuário que está realizando a ação.");
        }
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    public Participante exigirParticipante(Long usuarioId, Grupo grupo) {
        if (usuarioId == null) {
            throw new RequisicaoInvalidaException("Informe o usuário que está realizando a ação.");
        }
        return participanteRepository.findByUsuarioIdAndGrupoId(usuarioId, grupo.getId())
                .orElseThrow(() -> new AcessoNegadoException("Você não faz parte deste grupo."));
    }

    public Participante exigirAdmin(Long usuarioId, Grupo grupo) {
        Participante participante = exigirParticipante(usuarioId, grupo);
        if (!participante.isAdmin()) {
            throw new AcessoNegadoException("Apenas administradores do grupo podem realizar esta ação.");
        }
        return participante;
    }

    @Transactional
    public void EntrarNoGrupo(Long idUsuario, String codigoConvite, PerfilAtividade perfilAtividade) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findById(idUsuario);
        if (usuarioOpt.isEmpty()) {
            throw new RecursoNaoEncontradoException("Usuário não encontrado.");
        }
        Usuario usuario = usuarioOpt.get();

        Optional<Grupo> grupoOpt = grupoRepository.findByCodigoConvite(codigoConvite);
        if (grupoOpt.isEmpty()) {
            throw new CodigoConviteInvalidoException();
        }
        Grupo grupo = grupoOpt.get();

        Optional<Participante> participanteOpt = participanteRepository.findByUsuarioIdAndGrupoId(idUsuario, grupo.getId());
        if (participanteOpt.isPresent()) {
            throw new UsuarioJaParticipaException("O usuário já participa deste grupo.");
        }

        Participante participante = new Participante();
        participante.setUsuario(usuario);
        participante.setGrupo(grupo);
        participante.setPapel(Papel.MEMBRO);
        participante.setDataEntrada(LocalDateTime.now());
        participante.setPerfil(perfilAtividade);
        participanteRepository.save(participante);
    }

    /**
     * Cria o grupo e torna o fundador o primeiro ADMIN. A instância monta o grupo com os dados dela
     * (nome, endereço, perfil); o framework cuida do que é ponto fixo: código de convite, data e papel.
     * Mesmo padrão de {@link #EntrarNoGrupo}: o perfil do fundador vem pronto da instância.
     */
    @Transactional
    public Grupo criarGrupo(Grupo novoGrupo, Long fundadorId, PerfilAtividade perfilFundador) {
        Usuario fundador = buscarUsuario(fundadorId);

        novoGrupo.setCodigoConvite(UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        novoGrupo.setDataCriacao(LocalDateTime.now());
        Grupo grupoSalvo = grupoRepository.save(novoGrupo);

        Participante admin = new Participante();
        admin.setUsuario(fundador);
        admin.setGrupo(grupoSalvo);
        admin.setPapel(Papel.ADMIN);
        admin.setDataEntrada(LocalDateTime.now());
        admin.setPerfil(perfilFundador);
        participanteRepository.save(admin);

        return grupoSalvo;
    }
}
