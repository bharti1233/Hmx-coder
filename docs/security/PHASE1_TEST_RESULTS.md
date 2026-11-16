# Phase 1 test results

## Phase 1.1 continuation

Starting commit `aea32c0e7753b1191b8b4bc83f59fd827557a561`, verified clean on `security/phase-1-privacy-hardening`.
`GIT_TERMINAL_PROMPT=0 git push --dry-run origin security/phase-1-privacy-hardening` failed with exit 128: no HTTPS GitHub username/credential configured. This was a dry run, not a push. No prior exposed token was reused.

Added `.github/workflows/security-device-tests.yml`: API 26/35 emulators, full `connectedDebugAndroidTest` execution, no ignore/continue-on-error on tests, JUnit XML and failure logs uploaded with `always()`. This workflow has NOT run. Runner SDK command availability, KVM and API/system-image installation must be confirmed by its first run.

New endpoint policy and GitHub credential-boundary tests have NOT run. Kotlin compilation, unit suite, lint, instrumented compilation/execution and APK/signature checks remain NOT EXECUTED. No fresh passing test count is claimed.

Executed in this continuation: `git diff --check` passed; Python XML assertions passed for disabled manifest backup/cleartext, all three complete backup-rule blocks and exact loopback domain set. A source assertion confirmed neither general process environment calls `GitHubProvision.processEnvironment`. This is limited static evidence, not proof that arbitrary app-UID code cannot obtain a credential.

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
