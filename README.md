# Convoca

**Convoca** é um framework em Java/Spring Boot para criar aplicações de gestão de **grupos que se reúnem periodicamente**: racha de futebol, campeonato de e-sports entre amigos, ação de voluntariado e qualquer outra atividade em que o grupo precisa confirmar presença, formar equipes, avaliar e dividir custos.

O framework entrega um núcleo pronto de **grupos, eventos, check-in, equipes, avaliação, votação e pagamento** (os **pontos fixos**) e expõe **cinco pontos variáveis** (hot spots) para que cada aplicação defina o que é específico da sua atividade. Cada aplicação construída sobre o Convoca é uma **instância** independente do framework.

Projeto desenvolvido para a disciplina de Projeto Detalhado de Software (DIM0138, UFRN).

## Instâncias

| Instância | Pasta | Atividade | Situação |
|---|---|---|---|
| **PelManager** | [`instancias/pelmanager`](instancias/pelmanager/README.md) | Racha semanal de amigos: confirma presença, sorteia times, vota e divide o campo | Aplicação existente, sendo migrada para o framework |
| **Noite de E-sports** | `instancias/esports` | Campeonato online semanal entre amigos, com inscrição e prêmio | Esqueleto |
| **Grupo de Voluntariado** | `instancias/voluntariado` | Ação social mensal, com tarefas divididas entre os voluntários | Esqueleto |

O PelManager original, de antes da criação do framework, está preservado em [Kaiozinalves/pelmanager](https://github.com/Kaiozinalves/pelmanager).

## Arquitetura

```text
             ┌─────────────────────────── Convoca (framework) ───────────────────────────┐
             │  domain · repository · service  ← pontos fixos                            │
             │  hotspot                         ← interfaces dos pontos variáveis         │
             └──────────────▲──────────────────────────▲─────────────────────────▲───────┘
                            │ depende                  │ depende                 │ depende
             ┌──────────────┴─────────┐  ┌─────────────┴──────────┐  ┌───────────┴────────────┐
             │ PelManager             │  │ Noite de E-sports      │  │ Grupo de Voluntariado  │
             │ controllers · DTOs     │  │ controllers · DTOs     │  │ controllers · DTOs     │
             │ impl. dos hot spots    │  │ impl. dos hot spots    │  │ impl. dos hot spots    │
             │ frontend (React)       │  │ frontend               │  │ frontend               │
             └────────────────────────┘  └────────────────────────┘  └────────────────────────┘
```

Decisões de projeto:

- **Inversão de controle:** os serviços do framework conduzem os fluxos e, nos pontos que variam, chamam as implementações de hot spot fornecidas pela instância (injetadas pelo Spring).
- **Strategy nos pontos comportamentais:** formação de equipes, reconhecimento e cobrança são interfaces que cada instância implementa.
- **Composição nos pontos estruturais:** `Participante` e `Grupo` *têm* um perfil (0..1), em vez de serem subclassificados. O núcleo cria o participante no convite sem conhecer a instância, e o perfil é preenchido depois. Os perfis são classes abstratas mapeadas com `@Inheritance(JOINED)`, para que as tabelas do núcleo fiquem iguais nas três aplicações.
- **Template Method na cobrança:** o núcleo oferece `RateioIgualitario`, em que o esqueleto do rateio é fixo e só o valor total varia.
- **O framework termina na camada de serviço:** controllers REST, DTOs da API e o tratamento HTTP de exceções (`@RestControllerAdvice`) ficam em cada instância. Assim, cada aplicação define as próprias rotas e contratos (o PelManager mantém `/api/grupos`, `/api/rodadas`, `/api/checkins` etc.).
- **Instâncias são aplicações separadas:** cada uma tem a sua classe `@SpringBootApplication`, o seu banco e o seu frontend.

### Pontos fixos e pontos variáveis

**Pontos fixos**, controlados por `ServicoGrupo` e `ServicoEvento` (nenhuma instância altera a ordem das etapas):

| Ponto fixo | O que o núcleo garante |
|---|---|
| Usuários e grupos | Convite por código; papéis de admin e membro |
| Local | Endereço do grupo e local alternativo por evento |
| Eventos | Agendamento e ciclo: agendado → em andamento → finalizado (ou cancelado) |
| Check-in | Confirmação e cancelamento de presença |
| Formação de equipes | O ato de distribuir os presentes em equipes (a estratégia é variável) |
| Avaliação | Nota e comentário entre participantes |

**Pontos variáveis:**

| # | Ponto variável | Tipo | Contrato | PelManager | Noite de E-sports | Grupo de Voluntariado |
|---|---|---|---|---|---|---|
| 1 | Formação de equipes | Interface (Strategy) | `EstrategiaFormacaoEquipes.formar(evento, confirmados)` | `EquilibradoPorNotaEPosicao` | `EquilibradoPorRank` | `PorTarefaEHabilidade` |
| 2 | Votação e reconhecimento | Interface (Strategy) | `PoliticaReconhecimento`: `categorias()`, `exigeVotacao()`, `apurar(evento)` | `ReconhecimentoPorVotos` (vota) | `MvpPorMediaDeNotas` | `DestaquePorFrequencia` |
| 3 | Política de cobrança | Interface (Strategy) + Template Method | `PoliticaCobranca.gerarCobrancas(evento, confirmados)` | `RateioDoCampo` (estende `RateioIgualitario`) | `InscricaoComPremio` | `DoacaoOpcional` (sem cobrança) |
| 4 | Perfil do participante | Classe abstrata | `PerfilAtividade` | `PerfilJogador`: posições, perna dominante | `PerfilGamer`: rank, nickname | `PerfilVoluntario`: habilidades, horas |
| 5 | Configuração do grupo | Classe abstrata | `PerfilGrupo` | `ConfiguracaoPelada`: valor do campo, PIX, categorias, modo de formação | `ConfiguracaoEsports`: inscrição, jogo | `ConfiguracaoVoluntariado`: PIX para doação |

Exemplo de inversão de controle, em `ServicoEvento.fecharCheckIn(evento)`: o núcleo valida o status, busca os confirmados, chama `formar(...)`, salva as equipes, chama `gerarCobrancas(...)` e salva os pagamentos.

## Estrutura do repositório

```text
.
├── pom.xml                         # POM pai: módulos e versões comuns
├── mvnw, mvnw.cmd, .mvn/           # Maven Wrapper
├── framework/                      # convoca-framework (biblioteca, sem main)
│   └── src/main/java/com/convoca/framework/
│       ├── domain/                 # entidades do núcleo + PerfilAtividade/PerfilGrupo (+ enums/)
│       ├── repository/             # repositórios Spring Data JPA
│       ├── service/                # fluxos fixos
│       ├── hotspot/                # interfaces Strategy + RateioIgualitario
│       ├── dto/                    # objetos na fronteira dos serviços
│       └── exception/              # exceções de negócio genéricas
└── instancias/
    ├── pelmanager/                 # com.pelmanager: API + frontend/
    ├── esports/                    # com.esports
    └── voluntariado/               # com.voluntariado
```

Cada pacote do framework tem um `package-info.java` descrevendo a sua responsabilidade.

## Tecnologias

- **Framework e backends:** Java 17, Spring Boot 4, Spring Data JPA/Hibernate, Bean Validation, Lombok, Maven multi-módulo.
- **Banco de dados:** PostgreSQL (um banco por instância).
- **Frontends:** React + Vite.

## Como compilar e executar

Requisitos: JDK 17 ou superior, PostgreSQL e Node.js (para os frontends).

Na raiz do repositório, compile e instale o framework e todas as instâncias:

```bash
./mvnw clean install
```

Para subir uma instância (exemplo: PelManager):

```bash
./mvnw -pl instancias/pelmanager spring-boot:run
```

No Windows, use `./mvnw.cmd`. A configuração de banco e do frontend de cada instância está no README dela, como o [README do PelManager](instancias/pelmanager/README.md).

## Como criar uma nova instância

1. Crie o módulo em `instancias/<nome>/` com um `pom.xml` que tenha `convoca-parent` como `<parent>` e dependa de `convoca-framework`.
2. Adicione o módulo em `<modules>` no `pom.xml` da raiz.
3. Crie a classe `@SpringBootApplication` e ative o `spring-boot-maven-plugin`.
4. Implemente os três pontos comportamentais (`EstrategiaFormacaoEquipes`, `PoliticaReconhecimento`, `PoliticaCobranca`) e estenda os dois estruturais (`PerfilAtividade`, `PerfilGrupo`).
5. Escreva os controllers e DTOs da API, chamando os serviços do framework.
