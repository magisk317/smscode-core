# Changelog

## Unreleased

### Added
- Added shared-repo governance docs for module boundaries, compatibility expectations, and testing policy.
- Added direct JVM coverage for `domain` parsing, blacklist matching, and dedup helpers.
- Added a neutral runtime preferences foundation with typed specs/snapshots, atomic change sets,
  explicit commit outcomes, and post-success invalidate/publish hooks.

### Changed
- Converted `verification` to a pure Kotlin/JVM value-and-decision module and moved Android verification adapters/tests to `runtime.verification`.
- Moved backup/update/diagnostics/package/record compatibility models directly to their canonical `contract` APIs and removed the old runtime typealiases.
- Moved runtime-provider IPC constants, SMS cursor contracts, backup wire constants, and typed preference specs/change contracts into the pure `contract` module.
- Removed deprecated `SmsMessageUtils`, duplicate auto-input fallback wrappers, and legacy log-size compatibility APIs.
- Renamed Gradle module directories to single-word module names: `domain`, `contract`, `runtime`, `hook`, `verification`, `xposed`, and `rule`.
- Clarified that parent repositories are expected to advance `smscode-core` in lockstep when shared behavior changes.
- Moved pure rule-catalog document models and merge policy from `runtime.common.rules` to the canonical `rule.catalog` package; Android-backed catalog loading remains in `runtime`.
- Switched shared notification hook config reads to `CorePrefs`; parent repositories must install `CorePrefsAccess` instead of relying on `*.pref.provider` fallback.
- Runtime logs now rotate into daily JSONL files, drop legacy text-log compatibility, delete old `runtime*.log` files, and prune by retention days instead of trimming by single-file size.
