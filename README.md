# smscode-core

Shared verification and Xposed infrastructure for the Magisk317 Android app family.

## Modules

### `domain`
- Pure verification models and parsing utilities.
- Rule protocol objects, rule-set index models, and mapping helpers for shared repositories.
- Intended consumers:
  - `core` UI/view-model layers
  - `runtime` data/runtime layers
  - `app` hook adapters when only stateless parsing logic is needed

Recommended public surface:
- `io.github.magisk317.smscode.domain.model`
- `io.github.magisk317.smscode.domain.utils`
- `io.github.magisk317.smscode.domain.constant`

### `contract`
- Pure protocol/value-object layer shared by parent repositories.
- Intended for update payloads, package-environment DTOs, rule import/export contracts, and lightweight diagnostics formats.

### `verification`
- Shared SMS verification orchestration.
- Owns notification payload, inbox observation decisions, dispatch coordination, and post-parse helpers.

Recommended public surface:
- `io.github.magisk317.smscode.verification`

### `xposed`
- Shared Xposed/libxposed compatibility layer and runtime glue.
- Owns hook API abstractions, base hook classes, runtime logging/policy, and system input fallback helpers.
- Hook-side shared configuration must be provided by parent repos via `io.github.magisk317.smscode.xposed.prefs.CorePrefs`.

Recommended public surface:
- `io.github.magisk317.smscode.xposed.hookapi`
- `io.github.magisk317.smscode.xposed.runtime`
- `io.github.magisk317.smscode.xposed.hook`
- `io.github.magisk317.smscode.xposed.helper`

## API Boundary

Consumers should treat the modules as layered APIs instead of a bag of source files:

1. Prefer `domain` for stateless parsing, rule specs, and shared value objects.
2. Prefer `contract` for protocol/value-object sharing across repositories.
3. Prefer `verification` for SMS-code pipeline orchestration.
4. Prefer `xposed` for hook/runtime infrastructure only.

Boundary rules:
- Do not import `xposed` from pure UI modules unless the code is genuinely hook/runtime facing.
- Do not duplicate verification orchestration in parent repositories when the behavior already exists in `verification`.
- Keep Android process/runtime side effects out of `domain`.
- Parent repositories must install `CorePrefsAccess` before shared hooks read module settings.

## Compatibility

Current baseline:

| Item | Value |
| --- | --- |
| `compileSdk` | 37 |
| `minSdk` | 26 |
| Java bytecode | 17 |
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

- `domain` should keep pure parsing and dedup logic covered by JVM tests.
- `verification` and `xposed` should keep orchestration and hook-policy decisions covered by JVM tests.
- New shared APIs should land with tests in this repository before parent repos adopt them.
