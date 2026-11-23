// Ilustração de linha única: um recorte de campo com círculo central e uma "bola".
// É o único elemento gráfico de destaque do app — o resto da interface fica sóbrio.
export function GraficoCampo() {
  return (
    <svg
      viewBox="0 0 400 400"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className="grafico-campo"
      role="presentation"
      aria-hidden="true"
    >
      <line x1="40" y1="200" x2="360" y2="200" stroke="currentColor" strokeWidth="1.5" opacity="0.6" />
      <circle cx="200" cy="200" r="90" stroke="currentColor" strokeWidth="1.5" opacity="0.6" />
      <circle cx="200" cy="200" r="3" fill="currentColor" opacity="0.7" />
      <path
        d="M40 60 L40 340"
        stroke="currentColor"
        strokeWidth="1.5"
        opacity="0.6"
      />
      <path
        d="M40 130 L100 130 L100 270 L40 270"
        stroke="currentColor"
        strokeWidth="1.5"
        opacity="0.6"
      />
      <circle cx="260" cy="140" r="14" fill="#FFB020" />
    </svg>
  );
}
