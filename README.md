# Convoca

**Convoca** é um framework em Java/Spring Boot para criar aplicações de gestão de **grupos que se reúnem periodicamente**: pelada, noite de esportes com amigos, mutirão de ação social e qualquer outra atividade em que alguém convoca, as pessoas confirmam presença e o encontro acontece.

O framework concentra o que é comum a todas essas atividades (os **pontos fixos**) e expõe **pontos variáveis** (hot spots) para que cada aplicação defina o que é específico da sua atividade. Cada aplicação construída sobre o Convoca é uma **instância** independente do framework.

Projeto desenvolvido para a disciplina de Projeto Detalhado de Software (DIM0138, UFRN).

## Instâncias

| Instância | Pasta | Atividade | Situação |
|---|---|---|---|
| **PelManager** | [`instancias/pelmanager`](instancias/pelmanager/README.md) | Peladas de futebol: rodadas, check-in, sorteio de times, avaliações, votações e pagamentos | Aplicação existente, sendo migrada para o framework |
| **Noite de Esportes** | `instancias/noite-esportes` | Noite de esportes com amigos | Esqueleto |
| **Ação Social** | `instancias/acao-social` | Grupo que se reúne para uma ação social | Esqueleto |

O PelManager original, de antes da criação do framework, está preservado em [Kaiozinalves/pelmanager](https://github.com/Kaiozinalves/pelmanager).

## Arquitetura

```text
             ┌─────────────────────────── Convoca (framework) ───────────────────────────┐
             │  domain · repository · service  ← pontos fixos                            │
             │  hotspot                         ← interfaces dos pontos variáveis         │
             └──────────────▲──────────────────────────▲─────────────────────────▲───────┘
                            │ depende                  │ depende                 │ depende
             ┌──────────────┴─────────┐  ┌─────────────┴──────────┐  ┌───────────┴────────────┐
             │ PelManager             │  │ Noite de Esportes      │  │ Ação Social            │
             │ controllers · DTOs     │  │ controllers · DTOs     │  │ controllers · DTOs     │
             │ impl. dos hot spots    │  │ impl. dos hot spots    │  │ impl. dos hot spots    │
             │ frontend (React)       │  │ frontend               │  │ frontend               │
             └────────────────────────┘  └────────────────────────┘  └────────────────────────┘
```

Decisões de projeto:

- **Inversão de controle:** os serviços do framework conduzem os fluxos e, nos pontos que variam, chamam as implementações de hot spot fornecidas pela instância (injetadas pelo Spring).
- **Composição em vez de herança:** os atributos específicos de cada atividade não estendem as entidades do framework. Eles são compostos a elas por meio dos tipos definidos em `hotspot`.
- **O framework termina na camada de serviço:** controllers REST, DTOs da API e o tratamento HTTP de exceções (`@RestControllerAdvice`) ficam em cada instância. Assim, cada aplicação define as próprias rotas e contratos (o PelManager mantém `/api/grupos`, `/api/rodadas`, `/api/checkins` etc.).
- **Instâncias são aplicações separadas:** cada uma tem a sua classe `@SpringBootApplication`, o seu banco e o seu frontend.

### Pontos fixos e pontos variáveis

> **A preencher com base nos diagramas do projeto.**

| Tipo | Elemento | Descrição |
|---|---|---|
| Ponto fixo | _…_ | _…_ |
| Hot spot | _…_ | _…_ |

## Estrutura do repositório

```text
.
├── pom.xml                         # POM pai: módulos e versões comuns
├── mvnw, mvnw.cmd, .mvn/           # Maven Wrapper
├── framework/                      # convoca-framework (biblioteca, sem main)
│   └── src/main/java/com/convoca/framework/
│       ├── domain/                 # entidades genéricas (+ enums/)
│       ├── repository/             # repositórios Spring Data JPA
│       ├── service/                # fluxos fixos
│       ├── hotspot/                # interfaces dos pontos variáveis
│       ├── dto/                    # objetos na fronteira dos serviços
│       └── exception/              # exceções de negócio genéricas
└── instancias/
    ├── pelmanager/                 # com.pelmanager: API + frontend/
    ├── noite-esportes/             # com.noiteesportes
    └── acao-social/                # com.acaosocial
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
4. Implemente as interfaces de `com.convoca.framework.hotspot` para a sua atividade.
5. Escreva os controllers e DTOs da API, chamando os serviços do framework.
