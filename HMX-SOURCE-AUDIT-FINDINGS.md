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
- Unit tests run remotely before APK assembly; logs/reports retained on failure.
- No release publishing or upstream write credentials in CI.

## Validation state

Static checks: source/configuration review and Git whitespace validation only.
Unit tests: pending remote CI.
APK compilation: pending remote CI.
Device behavior: unverified.

Authenticated push is blocked until secure GitHub credentials are available in the environment. The credential pasted into chat was not embedded in code or workflow files. A successful build must be evidenced by a GitHub Actions run URL and artifact; no such run has occurred yet.
