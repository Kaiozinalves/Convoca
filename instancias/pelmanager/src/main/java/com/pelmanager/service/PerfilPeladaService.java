package com.pelmanager.service;

import com.pelmanager.entity.ConfiguracaoPelada;
import com.convoca.framework.domain.Grupo;
import com.convoca.framework.domain.Participante;
import com.pelmanager.entity.PerfilJogador;
import com.pelmanager.entity.PreferenciaJogador;
import com.convoca.framework.domain.Usuario;
import com.pelmanager.entity.enums.PernaDominante;
import com.pelmanager.entity.enums.Posicao;
import com.convoca.framework.repository.ParticipanteRepository;
import com.pelmanager.repository.PreferenciaJogadorRepository;
import jakarta.transaction.Transactional;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

/**
 * Ponto único onde o PelManager enxerga os perfis genéricos do núcleo como os seus tipos concretos.
 *
 * <p>Os perfis são {@code LAZY}: o Hibernate entrega um proxy de {@code PerfilAtividade}/{@code PerfilGrupo},
 * que não pode ser convertido direto para {@code PerfilJogador}/{@code ConfiguracaoPelada}
 * ({@code ClassCastException}). {@link Hibernate#unproxy(Object)} devolve o objeto real.
 */
@Service
public class PerfilPeladaService {

    private final ParticipanteRepository participanteRepository;
    private final PreferenciaJogadorRepository preferenciaRepository;

    public PerfilPeladaService(ParticipanteRepository participanteRepository,
                               PreferenciaJogadorRepository preferenciaRepository) {
        this.participanteRepository = participanteRepository;
        this.preferenciaRepository = preferenciaRepository;
    }

    public ConfiguracaoPelada configuracaoDe(Grupo grupo) {
        Object perfil = Hibernate.unproxy(grupo.getPerfil());
        if (perfil instanceof ConfiguracaoPelada configuracao) {
            return configuracao;
        }
        throw new IllegalStateException("O grupo " + grupo.getId() + " está sem ConfiguracaoPelada.");
    }

    /** Perfil do participante. Se ele ainda não tiver um, devolve um perfil vazio (sem posições), sem salvar. */
    public PerfilJogador perfilDe(Participante participante) {
        Object perfil = Hibernate.unproxy(participante.getPerfil());
        return perfil instanceof PerfilJogador jogador ? jogador : new PerfilJogador();
    }

    /** Temporário: some no passo 3, quando o CheckIn passar a apontar para o Participante. */
    public PerfilJogador perfilNoGrupo(Long usuarioId, Long grupoId) {
        return participanteRepository.findByUsuarioIdAndGrupoId(usuarioId, grupoId)
                .map(this::perfilDe)
                .orElseGet(PerfilJogador::new);
    }

    /** Preferência do usuário. Se ele nunca informou, devolve uma vazia, sem salvar. */
    public PreferenciaJogador preferenciaDe(Long usuarioId) {
        return preferenciaRepository.findByUsuarioId(usuarioId).orElseGet(PreferenciaJogador::new);
    }

    /** Perfil para quem está entrando num grupo, copiado da preferência do usuário. */
    public PerfilJogador novoPerfilPara(Long usuarioId) {
        PerfilJogador perfil = new PerfilJogador();
        copiar(preferenciaDe(usuarioId), perfil);
        return perfil;
    }

    /**
     * Grava a preferência do usuário e propaga para o perfil de todos os grupos dele.
     * É a regra atual do frontend: a posição é editada uma vez só, no perfil do usuário.
     */
    @Transactional
    public PreferenciaJogador salvarPreferencia(Usuario usuario, Posicao primaria,
                                                Posicao secundaria, PernaDominante perna) {
        PreferenciaJogador preferencia = preferenciaRepository.findByUsuarioId(usuario.getId())
                .orElseGet(() -> {
                    PreferenciaJogador nova = new PreferenciaJogador();
                    nova.setUsuario(usuario);
                    return nova;
                });
        preferencia.setPosicaoPrimaria(primaria);
        preferencia.setPosicaoSecundaria(secundaria);
        preferencia.setPernaDominante(perna);
        preferenciaRepository.save(preferencia);

        var participacoes = participanteRepository.findByUsuarioId(usuario.getId());
        for (Participante participacao : participacoes) {
            PerfilJogador perfil = perfilDe(participacao);
            copiar(preferencia, perfil);
            participacao.setPerfil(perfil); // liga o perfil novo, se o participante ainda não tinha um
        }
        participanteRepository.saveAll(participacoes);
        return preferencia;
    }

    private static void copiar(PreferenciaJogador origem, PerfilJogador destino) {
        destino.setPosicaoPrimaria(origem.getPosicaoPrimaria());
        destino.setPosicaoSecundaria(origem.getPosicaoSecundaria());
        destino.setPernaDominante(origem.getPernaDominante());
    }
}
