# NowQuiz

NowQuiz is a quiz plugin for Paper servers. It runs automatic trivia rounds, sends questions through Adventure chat components, and lets players answer by clicking an option in chat or by using the optional chat or command answer flow.

It does not use NMS or internal server classes. Persistence is handled with SQLite, and all interactive chat output is built with Adventure + MiniMessage.

## Official Compatibility

NowQuiz is officially compatible with:

- Paper 1.20.6
- Paper 1.21.1
- Paper 1.21.3
- Paper 1.21.4
- Paper 1.21.10+

It should also run on compatible forks in the same version range:

- Purpur
- Pufferfish

Pure Spigot is not a support target.

## What It Does

- Schedules automatic trivia rounds at a configurable interval
- Supports `MULTIPLE`, `TRUE_FALSE`, and `OPEN` questions
- Renders clickable answer options with Adventure `ClickEvent` and `HoverEvent`
- Uses MiniMessage for configurable formatting, including RGB colors
- Supports optional chat answers with low-interference capture rules
- Grants configurable rewards (Vault money, XP, items, console commands, player commands)
- Stores persistent player stats in SQLite
- Exposes `/nowquiz stats` and `/nowquiz top`

For clickable answers, the plugin sends components that run the internal command:

```text
/nowquiz answer <roundId> <option>
```

This is a regular Adventure chat click, not a client mod feature.

## Technical Notes

- Java 21
- Maven build
- Paper API only
- Adventure + MiniMessage for interactive chat output
- SQLite via JDBC
- SQL and blocking storage work run on a dedicated async executor
- Bukkit and Paper actions (messages, rewards, scheduler work) stay on the main thread
- Active player stats are cached in memory during normal use

## Installation

1. Build the plugin with Maven or download a release jar.
2. Place the jar in your server's `plugins/` directory.
3. Start or restart the server.
4. Review the generated files in `plugins/NowQuiz/`.

### Build From Source

```bash
mvn -DskipTests package
```

The shaded jar is written to `target/`.

## Quick Configuration

The plugin creates these files on first start:

- `config.yml`: runtime behavior, scheduling, answer rules, broadcast scope, debug toggle
- `questions.yml`: question bank grouped by category
- `rewards.yml`: reusable reward profiles
- `lang/es.yml`: bundled Spanish reference file
- `lang/en.yml`: bundled British English reference file

`lang: es` is the default. `lang: en` switches to the built-in English file. `lang: custom` loads `plugins/NowQuiz/lang/custom.yml` and applies it on top of the built-in Spanish defaults. `es.yml` and `en.yml` are copied for reference, but the plugin uses the bundled versions at runtime.

Minimal example:

```yaml
lang: es

enabled: true

auto:
  enabled: true
  interval-seconds: 300

round:
  time-limit-seconds: 30
  allow-multiple-winners: false

answer:
  allow-click: true
  allow-chat: true
  chat-prefix: "!"
  cooldown-ms: 750
  min-human-ms: 250
```

## Adding Questions

Questions are loaded from `questions.yml` and grouped by category. Each question should keep a stable `id`. The default bundle ships with a larger Spanish question set.

Example multiple-choice question:

```yaml
general:
  - id: "hearts_01"
    type: "MULTIPLE"
    question: "Cuantos corazones tiene un jugador sin efectos?"
    options:
      A: "10"
      B: "20"
      C: "15"
      D: "12"
    correct: "A"
    difficulty: "easy"
    rewards:
      money: 150
      xp: 5
```

Example open-answer question:

```yaml
general:
  - id: "ore_01"
    type: "OPEN"
    question: "Que material se necesita para fabricar una lodestone?"
    correct: "lingote de netherita"
    aliases:
      - "netherita"
```

## Commands

- `/nowquiz start [category]`
- `/nowquiz stop`
- `/nowquiz ask <questionId>`
- `/nowquiz reload`
- `/nowquiz answer <roundId> <option|text>`
- `/nowquiz stats [player]`
- `/nowquiz top [wins|streak]`

## Permissions

- `nowquiz.admin`
- `nowquiz.use`
- `nowquiz.start`
- `nowquiz.stop`
- `nowquiz.reload`
- `nowquiz.stats`
- `nowquiz.top`

## Reward Model

NowQuiz supports:

- Vault economy rewards when Vault and an economy provider are present
- Vanilla XP rewards
- Item rewards with basic meta
- Console commands
- Player-executed commands

If Vault is enabled in config but no economy provider is available, only the money reward is skipped and a warning is logged.

## FAQ

### Why not Spigot?

NowQuiz depends on Paper APIs that are part of the supported target. The plugin is built around Adventure chat components, so supporting pure Spigot is not a goal here.

### How do clickable answers work?

Each option is sent as its own Adventure component with:

- a `HoverEvent` that explains the action
- a `ClickEvent.runCommand(...)` that submits the answer command

The plugin checks the round id, the option, duplicate submissions, cooldowns, and minimum response timing before accepting the answer.

### Does chat answering interfere with normal chat?

Only during an active round, and only for players who are eligible for that round. If `answer.chat-prefix` is configured, the plugin only captures messages that start with that prefix.

### How does the language system work?

NowQuiz ships with two built-in language files in `plugins/NowQuiz/lang/`:

- `es.yml`
- `en.yml`

Those are the built-in supported languages. If you want custom wording, create `plugins/NowQuiz/lang/custom.yml` and set `lang: custom` in `config.yml`. Changes to `es.yml` and `en.yml` are not used as runtime overrides.

### Why SQLite?

SQLite is enough for this plugin and keeps deployment simple. SQL work stays async, and the plugin keeps active stats cached in memory.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
