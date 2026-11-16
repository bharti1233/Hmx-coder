# Phase 1 test results

Status: INCOMPLETE. No fresh Android/JVM Gradle results or signed APK builds available yet.

Baseline `fd9ee02`: historical Actions run `43788648255` reports 2,247 passing unit tests, debug lint and signed/debug APK builds. This is not a pre-change rerun. Instrumentation was not executed in that run.

Current environment: GitHub CLI missing; GH_TOKEN and GITHUB_TOKEN not configured. Builds and Android execution must remain in GitHub Actions. No local Gradle/APK build or device execution was attempted. The exposed credential from an earlier conversation is not reused.

Executed during implementation: `git diff --check` passed. Python ElementTree validation passed for manifest backup/cleartext flags, all three complete backup-exclusion blocks and the exact-loopback network domain set. These are static configuration checks, not Gradle or Android runtime tests. Added JVM and instrumentation tests are not yet executed.

Required Actions sequence:

1. Rerun baseline tests at `fd9ee02` to establish a fresh control.
2. Run `:app:testDebugUnitTest` and `:app:lintDebug` on the final branch commit.
3. Run `:app:assembleDebug`, `:app:assembleRelease` with existing CI signing secrets.
4. Run existing `apksigner`/`aapt2` identity, version and signature checks. Inspect merged backup/network configuration.
5. Compile instrumentation with `:app:assembleDebugAndroidTest`.
6. Use a separate Actions emulator job (API 26 and 35/36, JDK 21, matching SDK/NDK) to run security tests and core non-privileged flows. Existing build workflow has no emulator; root/Shizuku/ADB shell tests require a dedicated hardware lane.
7. Publish exact reports and test counts. Do not equate XML validation with device backup validation or JVM tests with Keystore execution.

Regression priorities: existing Room migration/data preservation, DataStore/provider settings, MCP stdio and HTTP, GitHub REST/git/gh in app and privileged tiers, local endpoints, OAuth callback, TLS rejection, updater install/signature continuity.
