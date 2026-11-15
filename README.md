# HMXHARNESS

**HMXPANEL** · Android-native autonomous coding agent · Kotlin + Jetpack Compose

HMXHARNESS runs the agent loop on the phone. Planning, model calls, tool execution,
file edits, shell access, git, builds, tests, memory, approvals, checkpoints and
subagents are owned by the app — it is not a client for a desktop agent server.

## Identity

| Field | Value |
|---|---|
| Application name | HMXHARNESS |
| Application ID | `com.hmx.harness` |
| Kotlin namespace | `com.hmx.harness` |
| Organization | HMXPANEL |
| Version name / code | `1.0.0` / `1` |
| Debug application ID | `com.hmx.harness.debug` |
| Minimum / target / compile SDK | 26 / 36 / 37 |
| NDK | 27.2.12479018 |

## Build and release

CI is the only build environment for this repository; no APK is built locally.

| Workflow job | What it does |
|---|---|
| `verify` | Unit tests (`testDebugUnitTest`) and `lintDebug` |
| `build` | `assembleDebug`, signed `assembleRelease`, APK verification, checksums, artifact upload |

Release signing uses a dedicated keystore that exists only in CI:

| Secret | Purpose |
|---|---|
| `HMX_RELEASE_STORE_FILE_BASE64` | Base64 PKCS#12/JKS keystore, decoded to a temporary file |
| `HMX_RELEASE_STORE_PASSWORD` | Keystore password |
| `HMX_RELEASE_KEY_ALIAS` | Key alias |
| `HMX_RELEASE_KEY_PASSWORD` | Key password |

The Gradle build reads the decoded path from `HMX_RELEASE_STORE_FILE` plus the other
three variables. If any is missing, `assembleRelease` and `bundleRelease` fail
explicitly; there is no debug-key fallback. The temporary keystore is deleted after
the job.

Artifacts per successful run:

- `HMXHARNESS-debug-<sha>` — `HMXHARNESS-debug.apk`, GPU runtime packages, `SHA256SUMS.txt`, `SOURCE_COMMIT.txt`
- `HMXHARNESS-release-<sha>` — `HMXHARNESS-release.apk`, `SHA256SUMS.txt`, `signing-certificate.txt`, `SOURCE_COMMIT.txt`
- `HMXHARNESS-reports-<sha>` — unit-test and lint reports

## What the agent does

The upstream feature set is intact: chat agent with plan/act loop, native file and
patch tools, workspace abstraction (app storage or SAF tree), shell tiers (Termux SSH,
root, Shizuku, app Linux, toybox), Android toolchain provisioning, MCP client with
OAuth, checkpoints, subagents, memory, GitHub integration, editor and terminal.

See [FEATURES.md](FEATURES.md) for the full reference and [repowiki/](repowiki) for
generated code documentation.

## Verification status

- Compilation and unit tests are evidenced by GitHub Actions runs, not by assumption.
- Device behaviour (installation, launch, shell tiers, Shizuku, provider calls) is
  **not** covered by CI and is unverified unless a run log on a real device says
  otherwise.
- Release readiness is not implied by a successful build; see
  [HMX-SOURCE-AUDIT-FINDINGS.md](HMX-SOURCE-AUDIT-FINDINGS.md) for known limits.

## Provenance

HMXHARNESS derives from [AndroidHarness](https://github.com/Sanuu7/AndroidHarness) at
commit `28ceacb04ee4ea4117cc46d417e49be3304dc085`, MIT licensed. The upstream
copyright and license text are preserved in [LICENSE](LICENSE); see
[HMX-UPSTREAM.md](HMX-UPSTREAM.md) and [HMX-BRANDING-MIGRATION.md](HMX-BRANDING-MIGRATION.md).

Third-party dependencies, bundled assets and service integrations keep their own
licenses and identity. Upstream GitHub, Discord and GPU-runtime references that are
still functional in code are intentional, not leftover branding.
