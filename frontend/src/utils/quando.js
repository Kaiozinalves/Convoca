// Utilitários de data/hora das rodadas (datas vêm do backend como "yyyy-MM-dd" e "HH:mm").

export function hojeISO() {
  const d = new Date();
  const mes = String(d.getMonth() + 1).padStart(2, "0");
  const dia = String(d.getDate()).padStart(2, "0");
  return `${d.getFullYear()}-${mes}-${dia}`;
}

// "2026-11-12" + "20:00" -> { dia: "Quinta", hora: "20h" }
export function formatarQuando(rodada) {
  const nome = new Date(`${rodada.data}T00:00:00`)
    .toLocaleDateString("pt-BR", { weekday: "long" })
    .split("-")[0];
  const [h, min] = (rodada.horario || "").split(":");
  const hora = h ? (min && min !== "00" ? `${Number(h)}h${min}` : `${Number(h)}h`) : "";
  return { dia: nome.charAt(0).toUpperCase() + nome.slice(1), hora };
}

// A rodada AGENDADA mais próxima (de hoje em diante), ou null.
export function escolherProximaRodada(rodadas) {
  const hoje = hojeISO();
  return (
    (rodadas || [])
      .filter((r) => r.status === "AGENDADA" && r.data >= hoje)
      .sort((a, b) => `${a.data} ${a.horario}`.localeCompare(`${b.data} ${b.horario}`))[0] || null
  );
}
