# smscode-core

Shared verification and Xposed infrastructure for the Magisk317 Android app family.

## Modules

### `domain`
- Pure Kotlin/JVM platform-neutral values and assembly logic.
- Owns `SmsMessagePart` and `SmsMessageAssembler` only; rule APIs are imported directly from `rule`.
- Android `Intent`/`SmsMessage` decoding belongs to `runtime`.

Recommended public surface for domain-owned APIs:
- `io.github.magisk317.smscode.domain.utils.SmsMessagePart`
- `io.github.magisk317.smscode.domain.utils.SmsMessageAssembler`

### `rule`
- Pure Kotlin/JVM rule models, catalog documents, parsers, matching policies, and deduplication utilities.
- Canonical owner of rule models, constants, catalog merge policy, and rule-specific utilities.
- Consumers import `io.github.magisk317.smscode.rule.*` directly; the former `domain` typealiases have been removed.

Recommended public surface:
- `io.github.magisk317.smscode.rule.model`
- `io.github.magisk317.smscode.rule.catalog`
- `io.github.magisk317.smscode.rule.utils`
- `io.github.magisk317.smscode.rule.constant`

### `contract`
- Pure Kotlin/JVM protocol/value-object layer shared by parent repositories.
- Contains no Android API; `Intent`, `Bundle`, `Context`, notifications, providers, and platform codecs live in `runtime`.
- Canonical owner of backup/update/package/diagnostics DTOs, runtime-provider IPC constants, SMS record cursor contracts, and typed preference specs/change contracts.

Recommended public surface:
- `io.github.magisk317.smscode.runtime.contract`

### `verification`
- Pure Kotlin/JVM SMS verification value and decision layer.
- Owns platform-neutral message/parse models, inbox decisions, dedup keys, trackers, and verification preference ports.
- Must not import Android, runtime adapters, or platform logging/telemetry.

Recommended public surface:
- `io.github.magisk317.smscode.verification`

### `runtime`
- Shared Android runtime adapters whose policy is supplied by parent repositories or pure modules.
- Owns `io.github.magisk317.smscode.runtime.verification`: Android verification receivers, notification/accessibility/provider actions, scheduling, process coordination, and telemetry wiring.
- Owns rule-catalog network, Asset, file-cache, and refresh-state adapters; catalog document models and merge policy belong to `rule`.
- Owns Android SMS `Intent` decoding through `SmsIntentDecoder`, which exposes a platform-neutral sender/body/timestamp result and a `decodeBody(Intent)` convenience API.
- Owns SharedPreferences/ContentResolver adapters, preference resolution and telemetry-backed commit coordination; typed specs and atomic change contracts belong to `contract`.
- Preference keys/defaults, DataStore adapters, cache invalidation, and cross-process publication stay in each parent repository.

Recommended public surface:
- `io.github.magisk317.smscode.runtime.verification`
- `io.github.magisk317.smscode.runtime.common.sms`
- `io.github.magisk317.smscode.runtime.common.prefs`

### `hook`
- Shared Xposed/libxposed compatibility layer and runtime glue. The Gradle module is named `hook`; its stable package namespace remains `io.github.magisk317.smscode.xposed`.
- Owns hook API abstractions, base hook classes, runtime logging/policy, and system input fallback helpers.
- Hook-side shared configuration must be provided by parent repos via `io.github.magisk317.smscode.xposed.prefs.CorePrefs`.

Recommended public surface:
- `io.github.magisk317.smscode.xposed.hookapi`
- `io.github.magisk317.smscode.xposed.runtime`
- `io.github.magisk317.smscode.xposed.hook`
- `io.github.magisk317.smscode.xposed.helper`

## API Boundary

Consumers should treat the modules as layered APIs instead of a bag of source files:

1. Prefer `rule` for stateless parsing, rule specs, catalog documents, and merge policy.
2. Prefer `domain` for platform-neutral SMS message parts and assembly.
3. Prefer `contract` for DTOs, wire constants, typed preference contracts, and other cross-module protocols.
4. Prefer `verification` for platform-neutral SMS verification values and decisions.
5. Prefer `runtime` for Android verification adapters and other shared platform mechanisms.
6. Prefer `hook` for hook/runtime infrastructure only; its public packages intentionally remain under `io.github.magisk317.smscode.xposed`.

Boundary rules:
- Do not import `io.github.magisk317.smscode.xposed` packages from pure UI modules unless the code is genuinely hook/runtime facing.
- Keep Android verification orchestration in `runtime.verification`; keep only platform-neutral decisions and values in `verification`.
- Keep Android process/runtime side effects out of `domain`, `rule`, `contract`, and `verification`.
- Parent repositories must install `CorePrefsAccess` before shared hooks read module settings.

## Preferences Foundation

Parent repositories declare their own typed specs and adapt one atomic storage transaction:

```kotlin
val enabled = PreferenceSpec.boolean(PrefConst.KEY_ENABLE, defaultValue = true)
val coordinator = PreferenceCommitCoordinator(
    persistence = AtomicPreferencePersistence { changes ->
        persistAllInOneTransaction(changes)
    },
    hooks = listOf(
        PreferencePostCommitHooks.invalidate { PrefsReader.invalidateCache() },
        PreferencePostCommitHooks.publish { HookPreferenceMirror.publish(context) },
    ),
)

val result = coordinator.commit {
    set(enabled, false)
}
```

The persistence adapter must apply the full `PreferenceChangeSet` atomically and return `false`
without partial writes when rejected. `invalidate` and `publish` hooks are never called for a
rejected/throwing persistence attempt or an empty edit. Hook failures are retained in
`PreferenceCommitResult.Persisted.postCommitFailures`; they do not rewrite a durable success into a
storage failure.

## Compatibility

Current baseline:

| Item | Value |
| --- | --- |
| `compileSdk` | 37 |
| `minSdk` | 26 |
| Java bytecode | 26 (from each embedding parent's `javaBytecode` catalog version) |
| Kotlin test runtime | JUnit 5 |

Known consumers at this workspace snapshot:
- `XposedSmsCode`
- `xinyi-relay`

Both parents pin the same submodule commits for:
- `smscode-core`
- `magisk-ui-kit`

This is the expected compatibility mode. If one parent needs a newer core behavior, update both parent repos together or prove the divergence intentionally in CI.

## Versioning Policy

- `smscode-core` follows source-compatibility-first versioning for parent repos.
- Public package moves or behavior changes that require parent repo updates must be recorded in [CHANGELOG.md](CHANGELOG.md).
- Parent repos should validate submodule SHA compatibility in CI before release.

## Testing

Run the executable module boundary guard from an embedding parent (this standalone repository intentionally has no Gradle wrapper):

```bash
# From XposedSmsCode or xinyi-relay
./gradlew :smscode-core:verifyModuleBoundaries
```

The guard is also wired into `:smscode-core:check`. It verifies that `domain`, `rule`, `contract`, and `verification` use the pure Kotlin/JVM convention, do not depend on Android-only libraries or the `runtime`/`hook` modules, and contain no `android.*` or `androidx.*` imports in main or test sources. It also verifies that `runtime` and `hook` use the Android library convention.

- `rule` should keep parsing, catalog mapping/merge, and dedup logic covered by JVM tests; `domain` should cover SMS assembly.
- `contract` and `verification` must compile and test on a plain JVM classpath without Android SDK types.
- Android verification adapters belong to `runtime` tests; Xposed behavior belongs to `hook` tests.
- New shared APIs should land with tests in this repository before parent repos adopt them.
