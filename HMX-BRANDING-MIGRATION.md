# HMXHARNESS branding and package migration

Target: HMXPANEL / HMXHARNESS, application ID and Kotlin namespace `com.hmx.harness`,
version `1.0.0` (code 1).

## What changed

| Area | Before | After |
|---|---|---|
| Application label | HMX Coder AI | HMXHARNESS |
| Debug label | HMX Coder AI Debug | HMXHARNESS Debug |
| Application ID | `com.hmx.coder` | `com.hmx.harness` |
| Debug application ID | `com.hmx.coder.debug` | `com.hmx.harness.debug` |
| Kotlin namespace | `com.androidharness.app` | `com.hmx.harness` |
| Feature module namespace | `com.androidharness.gpu` | `com.hmx.harness.gpu` |
| Source directories | `java/com/androidharness/{app,gpu}` | `java/com/hmx/harness/{,gpu}` |
| AIDL package | `com.androidharness.app` | `com.hmx.harness` |
| AppCompat theme | `Theme.AndroidHarness` | `Theme.HMXHARNESS` |
| MCP callback | `hmxcoder://mcp/oauth` | `hmxharness://mcp/oauth` |
| Version | 3.4 (38) | 1.0.0 (1) |
| Primary palette seed | `#0B57D0` (Google blue) | `#7C3AED` (electric purple) |
| Launcher background | `#1B1B1F` | `#14061F` |
| Launcher glyph | white | `#C9A6FF` |

Migration mechanics: `git mv` for every source set (`main`, `test`, `androidTest`,
`debug`, `githubDelivery`, `playDelivery`, `local_gpu`) followed by a scripted
replacement of the package and class-path forms (`com.androidharness.app`,
`com.androidharness.gpu`, `com/androidharness/...`, `com\androidharness\...`) across
1154 code, resource, AIDL, ProGuard, script and documentation files. Package
declarations were then verified file-by-file against their directory paths: 0
mismatches.

## Deliberately preserved

- Upstream provenance: `LICENSE`, `HMX-UPSTREAM.md`, `ANDROIDHARNESS-FEASIBILITY-AUDIT.md`,
  `HMX-CODER-AI-FOUNDATION-RESEARCH.md`, `HMX-NATIVE-ARCHITECTURE-DECISION.md`,
  `HMX-SOURCE-AUDIT-FINDINGS.md`, and the `Sanuu7/AndroidHarness` links.
- Third-party dependency coordinates and service integrations.
- Internal, non-brand identifiers: `.harness-scratch`, the
  `/data/local/tmp/androidharness-scratch` shell path, the legacy
  `androidharness`/`androidharness.debug` shell allowlist entries, and the
  `androidharness-plugin` npm discovery keyword.
- The agent engine, run lifecycle, tools, providers, checkpoints, subagents, memory,
  Termux/Shizuku/SSH execution paths and every feature they expose.
- All Room entities, migrations and database names. No database was reset; the
  application-ID change (`com.hmx.coder` → `com.hmx.harness`) means a *new install*
  starts with empty app storage, so data moves only through the built-in
  chat/workspace export and import.

## Release signing

| Input | Source |
|---|---|
| `HMX_RELEASE_STORE_FILE` | Path written by CI after decoding `HMX_RELEASE_STORE_FILE_BASE64` |
| `HMX_RELEASE_STORE_PASSWORD` | GitHub secret |
| `HMX_RELEASE_KEY_ALIAS` | GitHub secret |
| `HMX_RELEASE_KEY_PASSWORD` | GitHub secret |

`app/build.gradle.kts` creates the `release` signing config only when all four are
present and the keystore file exists. Otherwise the release variant has no signing
config, and `assembleRelease`/`bundleRelease` abort with an explicit message. The
debug key is never used for release, and no credential has a default or is committed.

## Verification

CI is the only build environment. `verify` runs unit tests and `lintDebug`; `build`
produces the debug and signed release APKs, verifies signatures, package IDs, version
fields, non-debuggable status and native ABIs, and uploads checksummed artifacts.
Results are recorded in the final report — not in this file, which documents intent
only.
