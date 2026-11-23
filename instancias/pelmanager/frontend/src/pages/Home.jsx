import { useCallback, useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom"; // NOVO: useLocation
import { useAuth } from "../context/AuthContext";
import { Input, Select } from "../components/Input";
import { Button } from "../components/Button";
import { buscarDetalhesGrupo, criarGrupo, entrarNoGrupo, listarMeusGrupos } from "../api/grupo";
import { cancelarPresenca, confirmarPresenca, listarCheckinsDaRodada } from "../api/checkin";
import { extrairMensagemErro } from "../constants/enums";
import { escolherProximaRodada, formatarQuando } from "../utils/quando";

const ENDERECO_INICIAL = {
  logradouro: "",
  numero: "",
  bairro: "",
  cidade: "",
  estado: "",
  cep: "",
};

function siglaDoGrupo(nome) {
  const letras = nome.trim().split(/\s+/).filter((w) => w.length > 2).slice(0, 2).map((w) => Array.from(w)[0]);
  return (letras.join("") || Array.from(nome.trim())[0] || "?").toUpperCase();
}

export function Home() {
  const { usuario, sair } = useAuth();
  const location = useLocation(); // NOVO

  const [grupos, setGrupos] = useState([]);
  const [carregandoGrupos, setCarregandoGrupos] = useState(true);
  const [listaVemDoServidor, setListaVemDoServidor] = useState(false);

  const [nomeGrupo, setNomeGrupo] = useState("");
  const [visibilidade, setVisibilidade] = useState("publica"); // NOVO: "publica" | "privada"
  const [endereco, setEndereco] = useState(ENDERECO_INICIAL);
  const [criando, setCriando] = useState(false);
  const [erroCriar, setErroCriar] = useState(null);
  const [grupoRecemCriado, setGrupoRecemCriado] = useState(null);

  // NOVO: vindo de "Usar este código" (Buscar peladas), o campo já chega preenchido —
  // a pessoa ainda precisa clicar em "Entrar na pelada" pra confirmar.
  const [codigoConvite, setCodigoConvite] = useState(location.state?.codigoConvite || "");
  const [entrando, setEntrando] = useState(false);
  const [erroEntrar, setErroEntrar] = useState(null);
  const [entrouComSucesso, setEntrouComSucesso] = useState(false);

  // Única fonte de verdade da lista: sempre busca do backend.
  // É chamada no carregamento da página, depois de criar e depois de entrar
  // numa pelada — assim a lista nunca fica desatualizada.
  const carregarGrupos = useCallback(async () => {
    try {
      const dados = await listarMeusGrupos(usuario.id);
      setGrupos(dados);
      setListaVemDoServidor(true);
    } catch {
      // GET /grupos/meus ainda não existe no backend (ou falhou por outro
      // motivo) — mantém a lista vazia em vez de quebrar a tela.
      setListaVemDoServidor(false);
    }
  }, [usuario.id]);

  useEffect(() => {
    setCarregandoGrupos(true);
    carregarGrupos().finally(() => setCarregandoGrupos(false));
  }, [carregarGrupos]);

  // Próxima rodada agendada entre todas as peladas do usuário + quem já confirmou.
  const [detalhes, setDetalhes] = useState({});
  const [proxima, setProxima] = useState(null);
  const [checkins, setCheckins] = useState([]);
  const [erroPresenca, setErroPresenca] = useState(null);
  const [mudandoPresenca, setMudandoPresenca] = useState(false);

  useEffect(() => {
    if (grupos.length === 0) {
      setDetalhes({});
      setProxima(null);
      setCheckins([]);
      return undefined;
    }
    let cancelado = false;
    (async () => {
      const resultados = await Promise.allSettled(grupos.map((g) => buscarDetalhesGrupo(g.id)));
      if (cancelado) return;
      const mapa = {};
      const candidatas = [];
      resultados.forEach((res, i) => {
        if (res.status !== "fulfilled") return;
        const grupo = grupos[i];
        mapa[grupo.id] = { membros: res.value.participantes?.length ?? null };
        const rodada = escolherProximaRodada(res.value.rodadas);
        if (rodada) candidatas.push({ grupo, rodada });
      });
      candidatas.sort((a, b) =>
        `${a.rodada.data} ${a.rodada.horario}`.localeCompare(`${b.rodada.data} ${b.rodada.horario}`)
      );
      const escolhida = candidatas[0] || null;
      setDetalhes(mapa);
      setProxima(escolhida);
      if (!escolhida) {
        setCheckins([]);
        return;
      }
      try {
        const lista = await listarCheckinsDaRodada(escolhida.rodada.id);
        if (!cancelado) setCheckins(lista);
      } catch {
        if (!cancelado) setCheckins([]);
      }
    })();
    return () => {
      cancelado = true;
    };
  }, [grupos]);

  const confirmados = checkins.filter((c) => c.statusCheckIn === "CONFIRMADO");
  const euConfirmei = confirmados.some((c) => c.usuarioId === usuario.id);

  async function alternarPresenca() {
    if (!proxima) return;
    setErroPresenca(null);
    setMudandoPresenca(true);
    try {
      const rodadaId = proxima.rodada.id;
      if (euConfirmei) {
        await cancelarPresenca({ rodadaId, usuarioId: usuario.id });
      } else {
        await confirmarPresenca({ rodadaId, usuarioId: usuario.id });
      }
      setCheckins(await listarCheckinsDaRodada(rodadaId));
    } catch (err) {
      setErroPresenca(extrairMensagemErro(err, "Não foi possível atualizar sua presença agora."));
    } finally {
      setMudandoPresenca(false);
    }
  }

  function atualizarEndereco(campo, valor) {
    setEndereco((atual) => ({ ...atual, [campo]: valor }));
  }

  async function aoCriarGrupo(evento) {
    evento.preventDefault();
    setErroCriar(null);
    setGrupoRecemCriado(null);
    setCriando(true);
    try {
      const grupoCriado = await criarGrupo({
        nome: nomeGrupo,
        fundadorId: usuario.id,
        endereco,
        publica: visibilidade === "publica", // NOVO
      });
      setGrupoRecemCriado(grupoCriado);
      setNomeGrupo("");
      setVisibilidade("publica"); // NOVO
      setEndereco(ENDERECO_INICIAL);
      await carregarGrupos();
    } catch (err) {
      setErroCriar(
        extrairMensagemErro(err, "Não foi possível criar a pelada agora. Tenta de novo em instantes.")
      );
    } finally {
      setCriando(false);
    }
  }

  async function aoEntrarNoGrupo(evento) {
    evento.preventDefault();
    setErroEntrar(null);
    setEntrouComSucesso(false);
    setEntrando(true);
    try {
      await entrarNoGrupo({ idUsuario: usuario.id, codigoConvite });
      setEntrouComSucesso(true);
      setCodigoConvite("");
      await carregarGrupos();
    } catch (err) {
      setErroEntrar(
        extrairMensagemErro(err, "Código inválido ou você já participa dessa pelada.")
      );
    } finally {
      setEntrando(false);
    }
  }

  const quando = proxima ? formatarQuando(proxima.rodada) : null;
  const enderecoRodada = proxima ? proxima.rodada.endereco || proxima.grupo.endereco : null;
  const localRodada = proxima
    ? enderecoRodada?.logradouro || enderecoRodada?.bairro || enderecoRodada?.cidade || proxima.grupo.nome
    : "";
  const semGrupos = !carregandoGrupos && grupos.length === 0;

  return (
    <div className="home">
      <header className="home-header">
        <span className="marca marca--escura">Pelada</span>
        <div className="home-header-usuario">
          <span>Olá, {usuario?.nome?.split(" ")[0] || usuario?.apelido}</span>
          <Link className="link-simples" to="/peladas-proximas">
            Buscar peladas
          </Link>
          <Link className="link-simples" to="/free-agents">
            Minha disponibilidade
          </Link>
          <Link className="link-simples" to="/perfil">
            Editar perfil
          </Link>
          <button className="link-simples" onClick={sair}>
            Sair
          </button>
        </div>
      </header>

      <main className="quadro">
        <section className={`quadro-proxima${proxima ? " quadro-proxima--campo" : ""}`} aria-label="Próxima rodada">
          {proxima ? (
            <>
              <p className="quadro-contexto">Próxima rodada</p>
              <h1 className="quadro-titulo">
                {quando.dia}
                {quando.hora && `, ${quando.hora}`}
                <br />
                {localRodada}
              </h1>
              <p className="quadro-resumo">
                {proxima.grupo.nome}. {confirmados.length}{" "}
                {confirmados.length === 1 ? "confirmado" : "confirmados"} até agora.
              </p>
              <div
                className="quadro-vagas"
                role="img"
                aria-label={`${Math.min(confirmados.length, 22)} de 22 vagas preenchidas`}
              >
                {Array.from({ length: 22 }, (_, i) => (
                  <span key={i} className={i < confirmados.length ? "vaga vaga--cheia" : "vaga"} />
                ))}
              </div>
              <div className="linha-acoes quadro-acoes">
                <Button
                  type="button"
                  variante={euConfirmei ? "secundario" : "primario"}
                  carregando={mudandoPresenca}
                  onClick={alternarPresenca}
                >
                  {euConfirmei ? "Cancelar presença" : "Confirmar presença"}
                </Button>
                <Link
                  className="botao botao--secundario"
                  to={`/grupos/${proxima.grupo.id}/rodadas/${proxima.rodada.id}`}
                >
                  Ver rodada
                </Link>
              </div>
              {erroPresenca && (
                <p className="mensagem-erro" role="alert">
                  {erroPresenca}
                </p>
              )}
            </>
          ) : (
            <>
              <p className="quadro-contexto">Próxima rodada</p>
              <h1 className="quadro-titulo">Nenhuma rodada marcada</h1>
              <p className="quadro-resumo">
                {semGrupos
                  ? "Crie uma pelada ou entre com um código de convite para começar."
                  : "Quando o admin de uma das suas peladas agendar a próxima rodada, ela aparece aqui."}
              </p>
            </>
          )}
        </section>

        <div className="quadro-resto">
          <section className="painel painel--lista">
            <h2 className="painel-titulo">Suas peladas</h2>

            {carregandoGrupos && <p className="texto-suave">Carregando…</p>}

            {semGrupos && (
              <div className="estado-vazio">
                <p>Você ainda não faz parte de nenhuma pelada.</p>
                <p className="texto-suave">Crie a primeira ou entre com um código de convite, logo abaixo.</p>
              </div>
            )}

            {!carregandoGrupos && grupos.length > 0 && (
              <ul className="lista-grupos">
                {grupos.map((grupo) => {
                  const membros = detalhes[grupo.id]?.membros;
                  return (
                    <li key={grupo.id}>
                      <Link to={`/grupos/${grupo.id}`} className="item-grupo">
                        <span className={`item-grupo-sigla item-grupo-sigla--${grupo.id % 3}`} aria-hidden="true">
                          {siglaDoGrupo(grupo.nome)}
                        </span>
                        <span className="item-grupo-info">
                          <span className="item-grupo-nome">{grupo.nome}</span>
                          <span className="item-grupo-local">
                            {grupo.endereco?.cidade
                              ? `${grupo.endereco.cidade} · ${grupo.codigoConvite}`
                              : grupo.codigoConvite}
                            {grupo.publica === false && " · Privada"}
                          </span>
                        </span>
                        {membros != null && (
                          <span className="item-grupo-numero" aria-label={`${membros} jogadores`}>
                            {membros}
                          </span>
                        )}
                      </Link>
                    </li>
                  );
                })}
              </ul>
            )}

            {!carregandoGrupos && !listaVemDoServidor && (
              <p className="texto-suave texto-suave--nota">
                Não consegui carregar suas peladas do servidor agora — confira se o backend está rodando.
              </p>
            )}
          </section>

          <section className="painel">
            <h2 className="painel-titulo">Entrar com código de convite</h2>
            <form className="formulario formulario--codigo" onSubmit={aoEntrarNoGrupo} noValidate>
              <Input
                id="codigoConvite"
                label="Código de convite"
                placeholder="Ex.: 8F3A1C"
                required
                value={codigoConvite}
                onChange={(e) => setCodigoConvite(e.target.value.toUpperCase())}
              />
              <Button type="submit" variante="secundario" carregando={entrando}>
                Entrar
              </Button>
            </form>
            {erroEntrar && (
              <p className="mensagem-erro" role="alert">
                {erroEntrar}
              </p>
            )}
            {entrouComSucesso && (
              <p className="mensagem-sucesso" role="status">
                Você entrou na pelada!
              </p>
            )}
          </section>

          <details className="painel painel--criar" key={semGrupos ? "vazio" : "cheio"} open={semGrupos}>
            <summary>Criar nova pelada</summary>

            <form className="formulario" onSubmit={aoCriarGrupo} noValidate>
              <Input
                id="nomeGrupo"
                label="Nome do grupo"
                placeholder="Ex.: Pelada do Sábado"
                required
                value={nomeGrupo}
                onChange={(e) => setNomeGrupo(e.target.value)}
              />

              <Select
                id="visibilidade"
                label="Visibilidade"
                value={visibilidade}
                onChange={(e) => setVisibilidade(e.target.value)}
              >
                <option value="publica">Pública — aparece na busca de peladas próximas</option>
                <option value="privada">Privada — só entra quem tiver o código</option>
              </Select>

              <div className="formulario-linha">
                <Input
                  id="logradouro"
                  label="Rua / Av."
                  required
                  value={endereco.logradouro}
                  onChange={(e) => atualizarEndereco("logradouro", e.target.value)}
                />
                <Input
                  id="numero"
                  label="Número"
                  value={endereco.numero}
                  onChange={(e) => atualizarEndereco("numero", e.target.value)}
                />
              </div>

              <div className="formulario-linha">
                <Input
                  id="bairro"
                  label="Bairro"
                  value={endereco.bairro}
                  onChange={(e) => atualizarEndereco("bairro", e.target.value)}
                />
                <Input
                  id="cidade"
                  label="Cidade"
                  required
                  value={endereco.cidade}
                  onChange={(e) => atualizarEndereco("cidade", e.target.value)}
                />
              </div>

              <div className="formulario-linha">
                <Input
                  id="estado"
                  label="UF"
                  maxLength={2}
                  value={endereco.estado}
                  onChange={(e) => atualizarEndereco("estado", e.target.value.toUpperCase())}
                />
                <Input
                  id="cep"
                  label="CEP"
                  value={endereco.cep}
                  onChange={(e) => atualizarEndereco("cep", e.target.value)}
                />
              </div>

              {erroCriar && (
                <p className="mensagem-erro" role="alert">
                  {erroCriar}
                </p>
              )}
              {grupoRecemCriado && (
                <p className="mensagem-sucesso" role="status">
                  Pelada criada! Código de convite: <strong>{grupoRecemCriado.codigoConvite}</strong> —
                  compartilha com o pessoal.
                </p>
              )}

              <Button type="submit" carregando={criando}>
                Criar pelada
              </Button>
            </form>
          </details>
        </div>
      </main>
    </div>
  );
}
