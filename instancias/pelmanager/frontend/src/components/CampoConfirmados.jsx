// Quadro tático: um campo com uma bolinha por jogador confirmado e círculos tracejados nas vagas em aberto.
// 22 posições (11 por lado). Preenche alternando os lados, do meio para fora, para o quadro nunca ficar torto.
const BASE = [
  [210, 195], [310, 195], [210, 120], [310, 120], [110, 195], [410, 195],
  [110, 120], [410, 120], [210, 265], [310, 265], [260, 60],
];

const POSICOES = BASE.flatMap(([x, y]) => [
  [x, y],
  [x, 680 - y],
]);

function iniciais(c) {
  const nome = (c.apelido || c.nome || "?").trim();
  const partes = nome.split(/\s+/);
  const letras = partes.length > 1 ? partes[0][0] + partes[1][0] : nome.slice(0, 2);
  return letras.toUpperCase();
}

export function CampoConfirmados({ confirmados, meuId }) {
  const naTela = confirmados.slice(0, POSICOES.length);
  const excedente = confirmados.length - naTela.length;

  return (
    <>
      <svg
        className="campo-svg"
        viewBox="0 0 520 680"
        role="img"
        aria-label={`Campo com ${confirmados.length} jogadores confirmados`}
      >
        <g className="campo-linhas">
          <rect x="20" y="20" width="480" height="640" />
          <line x1="20" y1="340" x2="500" y2="340" />
          <circle cx="260" cy="340" r="60" />
          <rect x="130" y="20" width="260" height="110" />
          <rect x="200" y="20" width="120" height="44" />
          <rect x="130" y="550" width="260" height="110" />
          <rect x="200" y="616" width="120" height="44" />
        </g>
        <circle className="campo-centro" cx="260" cy="340" r="4" />

        {POSICOES.map(([x, y], i) => {
          const jogador = naTela[i];
          if (!jogador) {
            return <circle key={i} className="campo-vaga" cx={x} cy={y} r="24" />;
          }
          const sou_eu = jogador.usuarioId === meuId;
          return (
            <g key={jogador.usuarioId ?? i}>
              <circle className={sou_eu ? "campo-eu" : "campo-jogador"} cx={x} cy={y} r="24" />
              <text className={sou_eu ? "campo-texto campo-texto--eu" : "campo-texto"} x={x} y={y + 5}>
                {iniciais(jogador)}
              </text>
            </g>
          );
        })}
      </svg>

      {/* alternativa em texto para leitores de tela */}
      <ul className="so-leitor">
        {confirmados.map((c) => (
          <li key={c.usuarioId}>{c.apelido || c.nome}</li>
        ))}
      </ul>

      {excedente > 0 && <p className="campo-excedente">+{excedente} confirmados além das 22 posições</p>}
    </>
  );
}
