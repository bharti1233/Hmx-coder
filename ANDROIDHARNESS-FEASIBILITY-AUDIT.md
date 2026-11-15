# AndroidHarness Feasibility Audit for HMX Coder AI

Date: November 15, 2026

Scope: source-level architecture, execution, build configuration and adoption review. No application execution or APK build was performed locally or remotely.

## Decision

**Proceed with AndroidHarness as the native Android foundation for validation.** Its Kotlin application owns the agent loop and local tool execution. A terminal-based OpenCode server is not required for that architecture.

This is a source-level go decision, not a production-readiness or build-success certification. Complete dependency licensing, clean remote builds, device compatibility and task success remain unverified.

## Evidence baseline and method

Official repository: https://github.com/Sanuu7/AndroidHarness

Pinned audit revision: `28ceacb04ee4ea4117cc46d417e49be3304dc085`, discovered in the earlier repository-tree audit and used for the detailed source retrieval in this follow-up.

Initial reads of README, license, manifest and top-level build configuration used `main`. Detailed runtime, agent, checkpoint, native CMake, Gradle wrapper and CI reads used the pinned revision. Do not assume that a moving branch remains identical to the audit revision. Before importing source, compare the build and licensing files against the pinned revision.

GitHub's tree API was rate-limited during this follow-up. GitHub directory pages and raw source URLs were used to locate and inspect files. Some long files were inspected through targeted excerpts, not a complete line-by-line audit. No repository clone or source execution was needed.

Evidence labels:

- **Source verified:** behavior is represented in inspected implementation.
- **Documented:** upstream describes behavior, but this audit did not establish it through execution.
- **UNKNOWN:** not established by inspected evidence.
- **Proposed:** an HMX design or validation action, not an existing capability claim.

## 1. Native agent execution path

Source root below is `app/src/main/java/com/androidharness/app/`.

| Stage | Implementation | Finding |
|---|---|---|
| Application run ownership | `agent/RunManager.kt` | Application-scoped coroutine supervisor owns runs; UI observes state |
| Agent invocation | `RunManager.kt`, around lines 648–706 | Calls `engine.run()` with history, permissions, workspace, model options, budgets and durable-event callback |
| Autonomous loop | `agent/AgentEngine.kt`, around line 414 | Repeats model/tool rounds, checks budget and processes delegated results |
| Model inference | `AgentEngine.kt`, around line 641; `llm/Llm.kt` | Calls `LlmProvider.streamChat()` and consumes streaming events |
| Planning restriction | `AgentEngine.kt`, around lines 470–473 | Plan mode filters tool schemas to read-only tools; enforcement beyond schema filtering needs full review |
| Tool dispatch | `tools/Tool.kt`, `AgentEngine.kt` | Registry and execution context connect model calls to native tools |
| Local shell | `tools/ShellTool.kt`, `data/env/ShellTierRouter.kt` | Dispatches commands to the selected local execution tier or SSH workspace |
| Persistence | `RunManager.kt`, `data/CheckpointStore.kt` | Persists events and pre-edit snapshots; exposes rewind results |

The core loop is native Kotlin. An API model supplies responses, but the app decides which tools are available and executes them.

### Lifecycle limitation

`RunManager` uses `CoroutineScope(SupervisorJob() + Dispatchers.Default)`. Its source comment explicitly distinguishes minimizing/rotation/navigation from process death: process death ends the active run. The foreground service and wakelock improve run survival but are not durable execution guarantees.

HMX must distinguish:

1. UI recreation while the process remains alive.
2. Lost network connectivity during a model request.
3. Process termination during a tool operation.
4. Explicit cancellation.

Resuming a conversation after process death must not blindly repeat an operation whose side effects may already have occurred.

## 2. Local execution architecture

`ShellTierRouter.kt` defines these execution tiers:

| Tier | Meaning | Dependency |
|---|---|---|
| `APP_LINUX` | Termux-derived toolchain under the app UID | Downloaded packages and compatibility setup |
| `TOYBOX` | Android's basic system shell fallback | System tools only |
| `PRIVILEGED` | Shell-level access | Shizuku or compatible ADB transport |
| `ROOT` | Explicit root service | Optional verified root access |
| `TERMUX_SSH` | SSH-based execution path | Captured remote/SSH workspace |

The enum name `TERMUX_SSH` should not be interpreted as evidence that all SSH workspaces are local Termux instances. Workspace routing determines the destination.

App-private projects route to the app UID because a shell-UID service cannot freely enter another app's private storage. Normal workspace development should remain possible without root or Shizuku; actual device coverage is pending validation.

### Termux-derived environment is implemented inside the app

The implementation class `LinuxEnvironmentManager` is located in **`data/env/LinuxEnvironment.kt`**, not a file named `LinuxEnvironmentManager.kt`.

Inspected behavior includes:

- Fetching package metadata from `https://packages.termux.dev/apt/termux-main`.
- Parsing Debian package metadata and dependency lists.
- Downloading `.deb` archives and comparing SHA-256 digests.
- Relocating absolute Termux symlinks and selected launcher paths.
- Setting `PREFIX`, `PATH`, `JAVA_HOME`, `LD_LIBRARY_PATH` and `LD_PRELOAD`.
- Using `libtermux-exec-ld-preload.so` and compatibility shims for app-UID execution.
- Repairing Git helper/config paths and maintaining execution-capable scratch directories.

This is a substantial portability layer. A separate Termux application is not the only way it executes tools, but the package ecosystem and Android compatibility mechanisms remain dependencies.

### Limitations requiring validation

- A toybox shell is not a full Python/Node/JDK development environment.
- Shared storage cannot be treated as an ordinary executable POSIX filesystem.
- Package relocation may fail for programs with additional compiled-in paths.
- The inspected package resolver simplifies dependency alternatives and constraints; it is not proof of full apt dependency semantics.
- Download checksums establish consistency with fetched metadata. Signed repository metadata verification was not established in the inspected path.
- Android version, ABI, OEM behavior and distribution rules need separate evaluation.

## 3. Android and Flutter toolchain findings

`tools/AndroidSdkTools.kt` implements `AndroidSdkInstallTool`. It installs:

- `openjdk-21`
- `d8`
- `apksigner`
- `aapt2`
- Optionally `gradle`
- An Android platform `android.jar` downloaded from Google's SDK repository

`data/env/AndroidSdk.kt` declares:

| Setting | Inspected value |
|---|---|
| Supported Android platform APIs | 26–36 |
| Default platform API | 35 |
| Platform listing | Google's `repository2-3.xml` |
| Extracted platform component | `android.jar` |
| Archive verification | Size/checksum information from repository listing |

`AndroidBuildToolchain.kt` repairs D8/R8/apksigner launchers and locates an installed JDK. It explicitly describes itself as relocation/file checks, not a full SDK installer.

**Important distinction:** installing these pieces supports a foundation for simple on-device APK creation. It does not prove a complete standard Android SDK layout or compatibility with arbitrary Android Gradle Plugin projects, Compose compiler plugins, native builds or Flutter plugins.

The application build configuration inspected on `main` declares **compile SDK 37**, while the pinned on-device installer supports up to API 36. These are different toolchains. Do not promise that the app can build its own current source using that installer.

| Development target | Current assessment |
|---|---|
| Node/Python project modification and tests | Local execution mechanism exists; package/device validation pending |
| Java source and simple APK pipeline | Native package/SDK setup mechanisms exist; end-to-end success UNKNOWN |
| Kotlin JVM | Toolchain availability and execution UNKNOWN in this audit |
| Gradle Android application | Optional Gradle installation exists; complete AGP compatibility UNKNOWN |
| Jetpack Compose | Full on-device build compatibility UNKNOWN |
| Flutter/Dart | No complete Flutter SDK provisioning/build path verified |
| C/C++ and NDK | Package shell access is not proof of Android NDK integration; UNKNOWN |
| Remote build through SSH | Command transport exists; SDK environment still required |

## 4. Provider architecture and upstream service coupling

`llm/Llm.kt` defines `LlmProvider.streamChat(...) : Flow<StreamEvent>`, with configuration and model options separate from the engine.

Protocol types include OpenAI-compatible Chat Completions, OpenAI Responses, Anthropic and Gemini. `ProviderFactory.create()` also recognizes local inference, ChatGPT integration and the built-in Harness provider.

This abstraction is suitable for HMX. Provider names such as OpenRouter, DeepSeek, NVIDIA or Groq should normally be configuration presets over a validated protocol adapter, not branches inside the agent loop.

`llm/HarnessProvider.kt` contains upstream-specific service defaults, including OpenCode Zen, Kilo and Pollinations endpoints, plus AndroidHarness attribution headers. These are inference gateways, not an OpenCode agent server. Nevertheless, they are external service dependencies and are not a guaranteed free service entitlement for HMX.

Proposed initial HMX provider policy: explicit user-configured provider credentials/endpoints, with no silent reliance on upstream's branded free-provider pool. Local inference can remain optional.

## 5. Checkpoints and verification

`CheckpointStore.snapshot()` stores pre-edit state using text content encoded as Base64. `rewind()` restores individual paths, retains failed checkpoints and returns restored/failed counts. It avoids recursively deleting agent-created directories containing untracked files.

Positive findings:

- Earliest per-turn snapshots are retained.
- Failed restores are represented explicitly.
- Current files are backed up before consuming original checkpoints.

Limits:

- Text snapshots are not binary-safe whole-workspace backups.
- Directory restoration does not imply restoration of every previous child.
- Multi-file restore is not a transactional filesystem operation.
- Shell-created changes and external effects are not automatically covered by file-tool snapshots.

`RunManager` marks normal completion from agent events and error state. That is not an independent proof that required builds/tests passed.

HMX should distinguish `agent_finished` from `verification_passed`, and attach command, exit code, workspace revision, test summary and artifact hash to verification results.

## 6. App build configuration and remote CI readiness

These are source-declared values, not verified installed artifacts or a passing build matrix.

| Component | Observed configuration |
|---|---|
| Application framework | Kotlin and Jetpack Compose |
| Modules | `:app`; `:local_gpu` enabled for Play distribution |
| AGP | 9.3.1 in version catalog read from `main` |
| Kotlin | 2.4.10 in version catalog read from `main` |
| KSP | 2.3.11 in version catalog read from `main` |
| Gradle wrapper | 9.7.0 at pinned revision |
| README build JDK | 17 |
| Upstream nightly CI JDK | Temurin 21 |
| Java/Kotlin bytecode target | 17 |
| Minimum Android API | 26 |
| Target Android API | 36 |
| Compile Android API | 37 |
| NDK | 27.2.12479018 |
| CMake | 3.22.1 |
| Native shader build dependency | Host `glslc` |
| Declared application ABIs | armeabi-v7a, arm64-v8a, x86, x86_64 |

Use JDK 21 as the starting remote CI host based on upstream workflow evidence; Java bytecode target 17 is a separate setting. Actual compatibility must be established remotely.

### Native component findings

`app/src/main/cpp/CMakeLists.txt` builds PTY, device and ADB pairing libraries. Local inference uses llama.cpp pinned to `19cce6919992fdffd92e0ac152caaf1e2b8f254e` with an archive SHA-256. Vulkan and SPIR-V header downloads also have hashes.

The CMake file provides unavailable stubs for local inference on 32-bit ABIs. The Gradle script conditionally skips native configuration when the pinned NDK is absent. A build that succeeds without those native components would not establish complete functionality.

### Upstream CI issues to adapt

The inspected `.github/workflows/nightly.yml`:

- Uses `ubuntu-latest`, JDK 21 and the pinned NDK.
- Installs `glslc`.
- Runs unit tests, then assembles a debug APK.
- Uploads APK and GPU runtime artifacts.
- Optionally publishes GPU runtime packages to **`Sanuu7/AH-GPU-Runtime`** using a secret.
- Grants `contents: write`.
- Triggers push builds for `app/**` and the workflow itself; root build/version files are not included in that push filter.

For HMX, prepare an artifact-only workflow with read-only repository permissions, explicit SDK/CMake provisioning, a complete build-input trigger policy, and no upstream publishing step. A manually dispatched initial workflow avoids accidental builds while source import is being reviewed.

The upstream wrapper file has `validateDistributionUrl=true` but no `distributionSha256Sum` in the inspected content. Add a checksum verified from the selected Gradle distribution when preparing HMX CI.

### Release configuration findings

The inspected application build script uses debug signing for `release` and sets `lint.checkReleaseBuilds = false`, with a comment describing a lint tooling failure. Neither should become an unnoticed HMX production default.

Release readiness requires HMX-owned signing and a functioning lint gate or a narrowly documented upstream-tool exception with compensating checks.

## 7. Licensing and redistribution

AndroidHarness's root LICENSE is MIT, allowing commercial use, modification and redistribution with notice preservation.

That license does not automatically cover every downloaded package, native dependency, font, model or SDK archive. The source tree and build dependencies include Sora Editor, Termux-derived packages, Shizuku, libsu, BoringSSL, llama.cpp and Vulkan-related components.

The complete resolved dependency/license inventory is **UNKNOWN**. In particular, independently determine obligations for editor integration and any redistributed GPL/LGPL packages. Do not assume the whole resulting APK can be proprietary solely because the app's root license is MIT.

The Gradle packaging block excludes several `META-INF/LICENSE` and `NOTICE` resources. Separate bundled notices may compensate, but completeness was not verified. HMX needs an explicit notices manifest mapping included components to license texts, source locations and relevant redistribution obligations.

Branding, service entitlements, signing keys, OAuth clients and upstream publishing credentials are not transferred by the MIT license.

## 8. Android integration observations

The manifest declares foreground services, wakelock, broad storage access, Shizuku integration, package installation and local network permissions. It also permits cleartext traffic globally.

For HMX:

- Keep privileged device access optional and separate from ordinary local workspace tools.
- Report the actual execution backend and UID/capability to the user and agent.
- Scope cleartext networking to explicit local endpoints if supported; cloud credentials should use authenticated HTTPS.
- Preserve credential exclusions in backup policies. The inspected backup XML excludes API-key preferences, ADB credentials, MCP configuration and selected runtime/configuration paths.
- Audit custom URI schemes and provider authorities when renaming the package.
- Test foreground-service behavior on supported Android versions; manifest declarations alone do not establish device or store-policy acceptance.

## 9. Adoption priorities

| Priority | Work | Reason |
|---|---|---|
| P0 | Pin and import source with provenance | Reproducible starting point |
| P0 | Remove unintended upstream release/publishing targets from HMX CI | Avoid distributing under upstream infrastructure |
| P0 | Inventory runtime downloads and component licenses | Determine distribution requirements |
| P0 | Configure HMX provider defaults explicitly | Avoid dependence on upstream free-service assumptions |
| P0 | Prepare remote build environment | Honor the no-local-build constraint |
| P1 | Validate local package execution and project storage | Core native-agent promise |
| P1 | Separate agent completion from verification | Prevent false success claims |
| P1 | Test checkpoint and process-death recovery | Protect ongoing work |
| P1 | Validate Java/Android and Kotlin/Compose independently | Avoid inflated language claims |
| P2 | Establish Flutter/Dart execution profile | No complete verified path yet |
| P2 | Improve multi-agent isolation and merge behavior | Parallel tasks need stronger ownership |
| P2 | Refactor oversized runtime classes incrementally | Maintainability after baseline validation |

## 10. Remote-only validation plan

This is a proposed plan. No remote build was dispatched.

1. Prepare a clean Linux CI runner with a pinned source revision, JDK 21, the selected Gradle wrapper, SDK platform 37, required build-tools, NDK 27.2.12479018, CMake 3.22.1 and `glslc`.
2. Resolve dependencies and record their versions, licenses and download origins. Validate artifact availability rather than assuming every declared version exists.
3. Run upstream unit tests and lint remotely; retain full reports.
4. Assemble a debug APK and matching native runtime artifacts remotely. Record checksums and the source revision.
5. Keep artifacts private or scoped to the chosen repository; do not publish a release automatically.
6. On a user-selected test device, evaluate the scenarios below when device testing is initiated.

| Scenario | Required evidence | Current status |
|---|---|---|
| Provider round trip | Streaming output, tool-call schema and error handling | NOT RUN |
| Native local repair | Multi-file change, failing test, fix and passing rerun | NOT RUN |
| Plan mode | No unapproved project mutation | NOT RUN |
| Tool cancellation | Process outcome and no misleading success | NOT RUN |
| Network interruption | Explicit state and safe continuation | NOT RUN |
| Process death | Preserved history and reconciled in-flight operation | NOT RUN |
| Undo | Correct restoration and visible partial failure | NOT RUN |
| Basic Android APK | Tool versions, logs and resulting artifact | NOT RUN |
| Gradle/Compose | Representative project build/test logs | NOT RUN |
| Flutter | SDK validation, analysis, tests and APK build | NOT RUN |
| No elevated access | Useful app-private project workflow | NOT RUN |
| SSH execution | Native engine receives results from selected host | NOT RUN |

## 11. Source references

The following links use the pinned audit revision:

- [AgentEngine.kt](https://github.com/Sanuu7/AndroidHarness/blob/28ceacb04ee4ea4117cc46d417e49be3304dc085/app/src/main/java/com/androidharness/app/agent/AgentEngine.kt)
- [RunManager.kt](https://github.com/Sanuu7/AndroidHarness/blob/28ceacb04ee4ea4117cc46d417e49be3304dc085/app/src/main/java/com/androidharness/app/agent/RunManager.kt)
- [LinuxEnvironment.kt](https://github.com/Sanuu7/AndroidHarness/blob/28ceacb04ee4ea4117cc46d417e49be3304dc085/app/src/main/java/com/androidharness/app/data/env/LinuxEnvironment.kt)
- [ShellTierRouter.kt](https://github.com/Sanuu7/AndroidHarness/blob/28ceacb04ee4ea4117cc46d417e49be3304dc085/app/src/main/java/com/androidharness/app/data/env/ShellTierRouter.kt)
- [AndroidSdkTools.kt](https://github.com/Sanuu7/AndroidHarness/blob/28ceacb04ee4ea4117cc46d417e49be3304dc085/app/src/main/java/com/androidharness/app/tools/AndroidSdkTools.kt)
- [AndroidSdk.kt](https://github.com/Sanuu7/AndroidHarness/blob/28ceacb04ee4ea4117cc46d417e49be3304dc085/app/src/main/java/com/androidharness/app/data/env/AndroidSdk.kt)
- [AndroidBuildToolchain.kt](https://github.com/Sanuu7/AndroidHarness/blob/28ceacb04ee4ea4117cc46d417e49be3304dc085/app/src/main/java/com/androidharness/app/data/env/AndroidBuildToolchain.kt)
- [Llm.kt](https://github.com/Sanuu7/AndroidHarness/blob/28ceacb04ee4ea4117cc46d417e49be3304dc085/app/src/main/java/com/androidharness/app/llm/Llm.kt)
- [HarnessProvider.kt](https://github.com/Sanuu7/AndroidHarness/blob/28ceacb04ee4ea4117cc46d417e49be3304dc085/app/src/main/java/com/androidharness/app/llm/HarnessProvider.kt)
- [CheckpointStore.kt](https://github.com/Sanuu7/AndroidHarness/blob/28ceacb04ee4ea4117cc46d417e49be3304dc085/app/src/main/java/com/androidharness/app/data/CheckpointStore.kt)
- [CMakeLists.txt](https://github.com/Sanuu7/AndroidHarness/blob/28ceacb04ee4ea4117cc46d417e49be3304dc085/app/src/main/cpp/CMakeLists.txt)
- [Gradle wrapper](https://github.com/Sanuu7/AndroidHarness/blob/28ceacb04ee4ea4117cc46d417e49be3304dc085/gradle/wrapper/gradle-wrapper.properties)
- [Nightly workflow](https://github.com/Sanuu7/AndroidHarness/blob/28ceacb04ee4ea4117cc46d417e49be3304dc085/.github/workflows/nightly.yml)

Moving-branch files inspected for initial configuration:

- [App build configuration](https://github.com/Sanuu7/AndroidHarness/blob/main/app/build.gradle.kts)
- [Version catalog](https://github.com/Sanuu7/AndroidHarness/blob/main/gradle/libs.versions.toml)
- [Manifest](https://github.com/Sanuu7/AndroidHarness/blob/main/app/src/main/AndroidManifest.xml)
- [License](https://github.com/Sanuu7/AndroidHarness/blob/main/LICENSE)

## Outcome

The static review supports the native Android architecture and identifies substantial reusable implementation. Build success, device reliability, universal language support and full redistribution compliance remain open validation items. The immediate implementation milestone is a pinned source import plus HMX-owned configuration and remote-only CI preparation, followed by validation of a small local repair task.
