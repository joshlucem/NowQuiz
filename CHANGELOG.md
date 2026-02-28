# Changelog

All notable changes to NowQuiz will be documented in this file.

This project follows a simple release log format inspired by Keep a Changelog.

## [beta-1.0.0] - 2026-02-28

First public beta release.

### Added

- Initial Paper plugin structure for `dev.joshlucem.nowquiz`
- Official support target for:
  - Paper 1.20.6
  - Paper 1.21.1
  - Paper 1.21.3
  - Paper 1.21.4
  - Paper 1.21.10+
- Compatibility focus for Purpur and Pufferfish on the same version range
- Quiz round system with:
  - automatic scheduling
  - manual start and stop commands
  - one active round at a time
  - configurable timers
- Support for:
  - `MULTIPLE`
  - `TRUE_FALSE`
  - `OPEN`
- Clickable chat answers using Adventure `ClickEvent` and `HoverEvent`
- Optional chat-based answer flow with limited interception rules
- MiniMessage-driven formatting for plugin messages
- SQLite persistence for player statistics
- Async storage executor so SQL does not block the main server thread
- In-memory cache for active player stats and question selection
- Question repeat avoidance with configurable cooldown
- Reward system with:
  - Vault economy support when available
  - vanilla XP rewards
  - item rewards
  - console commands
  - player commands
- Player statistics commands:
  - `/nowquiz stats [player]`
  - `/nowquiz top [wins|streak]`
- Dynamic language loading with:
  - built-in Spanish (`es`)
  - built-in British English (`en`)
  - optional `custom.yml` override layer
- Default Spanish message set
- Expanded default question pool in Spanish across multiple categories
- Reward feedback messages for money, XP, items, and command-based rewards
- Public-facing project documentation in `README.md`

### Changed

- Project version set to `beta-1.0.0`
- Default plugin language set to Spanish
- Default reward profiles rebalanced so question difficulty has more appropriate payouts
- Documentation tone adjusted to read more like a maintained public repository

### Notes

- `es.yml` and `en.yml` are bundled built-in language files
- `lang: custom` uses `plugins/NowQuiz/lang/custom.yml` as the only supported runtime override file
- Pure Spigot is not a supported target
