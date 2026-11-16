# Android verification runtime extraction

The approved design separates SMS verification execution from general Android runtime adapters.
The existing verification package has no dependency on runtime.common, runtime.contract, domain,
rule, database, resources or generated BuildConfig. It uses the pure verification values plus
Android, AndroidX, coroutines and xposed-kit contract/logging/runtime APIs.

## Ownership and dependency direction

- `verification` remains a pure JVM value/decision module. `rule` already consumes its values.
- `verification-runtime` owns Android SMS ingestion, execution, notification, accessibility,
  process coordination and diagnostics wiring. It consumes `verification` and the existing
  xposed-kit modules; it has no dependency on `runtime` or `hook`.
- `runtime` keeps general Android preferences, catalog adapters, diagnostics, backup and utilities.
  It has no dependency on `verification-runtime`.
- `hook` and the products select both Android modules explicitly where they consume them.
- Existing `io.github.magisk317.smscode.runtime.verification` packages stay stable. The module
  moves ownership without introducing aliases, forwarding APIs or a reverse dependency.

Putting Android execution into verification/domain/rule would break their platform-neutral
contract and make rule consumers acquire Android dependencies. Moving only packages would
leave the two responsibilities on the same compiler dependency surface.

## Implementation and validation plan

- [x] Move the complete verification production and test source trees, preserving bytes.
- [x] Declare the new Android library dependencies and add it to shared hook dependencies.
- [x] Extend module boundaries to enforce Android ownership and independent runtime edges.
- [x] Register the module in both parent settings, add direct dependencies only to XSC app/hook
      and xinyi app/hook:entry/runtime, and extend their existing validation task lists.
- [x] Rsync shared sources into the main product checkouts, preserving their Git metadata.
- [x] Run module boundaries, pure verification tests and both Android runtime/hook test suites,
      then compile affected product app/hook/runtime modules through Debian gw with warnings.
- [x] Confirm the moved files match their original blobs and validate a prohibited dependency
      fails the boundary guard before restoring the intended source state.

The new module uses the existing magisk.android.library convention and does not need resources,
a manifest, serialization, Room, test fixtures or new tool dependencies. Existing 35 test source
files move with their production code, including the internal test models and internal helper
access. No product source import changes are needed.

Main checkout mirroring is temporary until shared main is published and parent gitlinks update.
The integration owner coordinates Gradle, commits, push order, and checkout restoration.

## Validation evidence

Both main product checkouts passed `gw --warning-mode all` for shared module boundaries,
verification JVM tests, verification-runtime/runtime/hook Android unit tests and all direct
consumer compilations (XSC hook/app; xinyi hook:entry/runtime/app). Each checkout reported
20 verification, 167 verification-runtime, 128 runtime and 51 hook tests, with no failures,
errors or skipped tests. The 84 moved source files match their original Git blobs exactly.

A temporary verification-runtime → runtime Gradle dependency failed with
`verification-runtime must not depend on runtime`; restoring the build file passed again.
The only warning was the existing Gradle Configuration.setVisible deprecation.
Both parents' temporary configuration mirrors were restored from SHA256-checked backups;
shared checkout mirrors remain under the integration owner's control until gitlink update.
