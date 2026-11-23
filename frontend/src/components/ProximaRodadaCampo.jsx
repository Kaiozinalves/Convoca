import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Button } from "./Button";
import { CampoConfirmados } from "./CampoConfirmados";
import { cancelarPresenca, confirmarPresenca, listarCheckinsDaRodada } from "../api/checkin";
import { extrairMensagemErro } from "../constants/enums";
import { escolherProximaRodada, formatarQuando } from "../utils/quando";

// Dentro de cada pelada: a próxima rodada agendada e quem vem, desenhado no campo.
export function ProximaRodadaCampo({ grupoId, rodadas, usuarioId }) {
  const proxima = useMemo(() => escolherProximaRodada(rodadas), [rodadas]);
  const rodadaId = proxima?.id;

  const [checkins, setCheckins] = useState([]);
  const [erro, setErro] = useState(null);
  const [mudando, setMudando] = useState(false);

  useEffect(() => {
    if (!rodadaId) return undefined;
    let cancelado = false;
    listarCheckinsDaRodada(rodadaId)
      .then((lista) => {
        if (!cancelado) setCheckins(lista);
      })
      .catch(() => {
        if (!cancelado) setCheckins([]);
      });
    return () => {
      cancelado = true;
    };
  }, [rodadaId]);

  if (!proxima) {
    return (
      <section className="painel" aria-label="Próxima rodada">
        <h2 className="painel-titulo">Próxima rodada</h2>
        <p className="texto-suave">Nenhuma rodada agendada ainda. Quando o admin marcar uma, os confirmados aparecem aqui, em campo.</p>
      </section>
    );
  }

  const confirmados = checkins.filter((c) => c.statusCheckIn === "CONFIRMADO");
  const euConfirmei = confirmados.some((c) => c.usuarioId === usuarioId);
  const quando = formatarQuando(proxima);
  const local = proxima.endereco?.logradouro || proxima.endereco?.bairro || null;

  async function alternarPresenca() {
    setErro(null);
    setMudando(true);
    try {
      if (euConfirmei) {
        await cancelarPresenca({ rodadaId, usuarioId });
      } else {
        await confirmarPresenca({ rodadaId, usuarioId });
      }
      setCheckins(await listarCheckinsDaRodada(rodadaId));
    } catch (err) {
      setErro(extrairMensagemErro(err, "Não foi possível atualizar sua presença agora."));
    } finally {
      setMudando(false);
    }
  }

  return (
    <section className="quadro-campo quadro-campo--grupo" aria-label="Próxima rodada">
      <div className="quadro-campo-topo">
        <div>
          <h2>
            {quando.dia}
            {quando.hora && `, ${quando.hora}`}
          </h2>
          {local && <p className="quadro-campo-local">{local}</p>}
        </div>
        <span>
          <b>{confirmados.length}</b> {confirmados.length === 1 ? "confirmado" : "confirmados"}
        </span>
      </div>

      <CampoConfirmados confirmados={confirmados} meuId={usuarioId} />

      <p className="quadro-campo-nota">Seu círculo aparece em amarelo. Os tracejados são vagas em aberto.</p>

      <div className="linha-acoes quadro-campo-acoes">
        <Button
          type="button"
          variante={euConfirmei ? "secundario" : "primario"}
          carregando={mudando}
          onClick={alternarPresenca}
        >
          {euConfirmei ? "Cancelar presença" : "Confirmar presença"}
        </Button>
        <Link className="botao botao--secundario" to={`/grupos/${grupoId}/rodadas/${rodadaId}`}>
          Ver rodada
        </Link>
      </div>
      {erro && (
        <p className="mensagem-erro" role="alert">
          {erro}
        </p>
      )}
    </section>
  );
}
