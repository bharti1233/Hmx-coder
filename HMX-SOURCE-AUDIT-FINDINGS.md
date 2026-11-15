# HMX source audit and debug baseline

Source: AndroidHarness `28ceacb04ee4ea4117cc46d417e49be3304dc085`.

## Coverage and limits

The complete source tree was obtained. Targeted deep review covered the native agent/run lifecycle, provider contract, tool registration, shell routing, package installation, Android toolchain provisioning, filesystem operations, patch application, checkpoints, MCP OAuth, updater trust policy, manifest, backup rules, Gradle/CMake configuration and CI publishing paths. This is not a claim that every line or every dependency has been audited. No runtime tests or APK builds ran locally.

## Findings

| Finding | Evidence | Disposition |
|---|---|---|
| Native orchestration is in the application | AgentEngine and RunManager | Architecture matches HMX requirement |
| Lexical file containment permits resolution through an external symlink | FileFs.resolve normalizes without canonicalizing | Canonicalize before containment check; regression tests added for remote CI |
| Patch deletion can report success when delete returns false | ApplyPatchTool commit phase | Check return value and report failure |
| Patch description overstates cross-file atomicity | Sequential commit after validation | Description corrected; transactional rollback remains future work |
| Updater targets upstream releases | UpdateManager and UpdateInstallPolicy | Retargeted to HMX repository; trust-policy test updated |
| Upstream CI optionally publishes to a different repository | GPU runtime/nightly workflows | Replaced with debug artifact-only HMX workflow |
| Callback scheme collides with upstream installation | Manifest, MainActivity, McpOAuth | Changed to hmxcoder |
| Runtime Android platform installer stops at API 36 | AndroidSdkRepository | Documented; not proof of full SDK 37/Compose/Flutter support |
| Release build uses debug signing; release lint disabled | app/build.gradle.kts | Remains an explicit release-readiness issue; this milestone builds debug only |
| File checkpoints are text based | CheckpointStore | Binary/full-workspace restore not guaranteed |
| Package checksums depend on fetched index | LinuxEnvironment | Signed metadata verification not established |
| App-scoped coroutine state ends on process death | RunManager | Resume behavior needs device validation |
| Free-provider/GPU endpoints retain upstream dependencies | HarnessProvider, GpuDownloadWorker | Documented; production endpoint migration remains open |

The symlink fix narrows one resolution boundary. It is not a complete sandbox: directory traversal helpers, direct FsNode operations, shell execution and concurrent symlink changes require additional hardening and runtime tests.

## Debug baseline

- HMX display name and distinct application ID.
- Internal Kotlin package retained for a small initial diff.
- MIT copyright preserved.
- Debug APK and matching GPU packages uploaded as GitHub Actions artifacts.
- Unit tests run after APK assembly; the APK artifact is uploaded first, so a test failure still leaves a downloadable build while the job reports failure.
- No release publishing or upstream write credentials in CI.

## Validation state

Static checks: source/configuration review, Git whitespace validation, workflow YAML parse check.
Unit tests: 2252 executed, 0 failures, 0 errors, 0 skipped (GitHub Actions `testDebugUnitTest`).
APK compilation: `:app:assembleDebug` succeeded; artifact uploaded.
Device behavior: unverified. Nothing has been installed or launched.

### Evidence

- Runs: <https://github.com/bharti1233/Hmx-coder/actions/runs/43698666425> (commit `526ea53`, tip, conclusion `success`) and <https://github.com/bharti1233/Hmx-coder/actions/runs/43697555579> (commit `b97821a`, conclusion `success`).
- Artifacts: `HMX-Coder-AI-debug-526ea53…` (85,752,495 bytes) and `HMX-build-reports-526ea53…`; the earlier run's artifacts are the same size class.
- The APK artifact was downloaded and its bundled `SHA256SUMS.txt` verified locally: all three files reported `OK`.
- APK inside the artifact: `HMX-Coder-AI-debug.apk`, 102,822,373 bytes.

Checks performed on the downloaded artifact (not on a device):

- `unzip -t` reported no errors; the archive contains 23 `classes*.dex` files, `AndroidManifest.xml`, `resources.arsc`, and native libraries for `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64`.
- The binary manifest string pool contains `com.hmx.coder.debug` and the `hmxcoder` scheme.
- `resources.arsc` contains the `HMX Coder AI Debug` label.
- An APK Signing Block is present (v2+ signature; the debug key is used, as this is a debug build).

Earlier runs in the same workflow failed for CI configuration reasons, not source reasons: the retired `setup-android@v3` default `tools` package, a workflow heredoc that broke YAML block indentation, and a transient plugin-repository resolution failure on the first Gradle run. All three were corrected.

Two unit tests failed on the first successful compile: the MCP OAuth URL assertion still expected the old `androidharness` callback scheme, and a Whisper client test waited only 3 seconds for a loopback handshake. Both were fixed; the 3-second assertion that the client actually closes the call was left intact.

### What is still not proven

- Compilation on a device or emulator, installation, startup, and every runtime feature.
- That the in-app SDK/toolchain installer can build projects targeting API 37, Compose, Flutter or the other listed languages.
- Transactional rollback, sandbox isolation, and provider endpoint independence.

