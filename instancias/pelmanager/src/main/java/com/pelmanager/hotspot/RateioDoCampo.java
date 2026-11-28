package com.pelmanager.hotspot;

import com.convoca.framework.domain.Evento;
import com.convoca.framework.hotspot.RateioIgualitario;
import com.pelmanager.entity.ConfiguracaoPelada;
import com.pelmanager.service.PerfilPeladaService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Cobrança da pelada: o valor do campo dividido igualmente entre os confirmados. O pagamento é
 * feito por PIX à chave do gerente ({@code ConfiguracaoPelada.chavePixGerente}), usada pelo
 * {@code PagamentoService} para gerar o QR code.
 */
@Component
public class RateioDoCampo extends RateioIgualitario {

    private final PerfilPeladaService perfilPeladaService;

    public RateioDoCampo(PerfilPeladaService perfilPeladaService) {
        this.perfilPeladaService = perfilPeladaService;
    }

    /** Sem o sistema de pagamento ativo no grupo, não há o que ratear. */
    @Override
    protected BigDecimal valorTotal(Evento evento) {
        ConfiguracaoPelada configuracao = perfilPeladaService.configuracaoDe(evento.getGrupo());
        if (!configuracao.isSistemaPagamentoAtivo()) {
            return BigDecimal.ZERO;
        }
        return configuracao.getValorTotalPelada();
    }
}
