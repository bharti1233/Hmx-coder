# Phase 1.3 security test evidence

Added JVM test source `RestrictedGitExecutorTest`: local init/status/stage/commit/branch/log operations, malicious pre-commit hook nonexecution, and credential-helper configuration rejection. Existing gateway tests cover scope, approval binding and revoked permission; REST tests cover host/redirection/error redaction.

Not yet executed: Gradle JVM suite, Kotlin compilation, lint, instrumentation, APK builds or signature checks. No private/public network clone, fetch/pull/push, live token revocation, or OPPO execution is claimed. No real credential is embedded in fixtures.

Executed isolated JVM probe: Kotlin 2.2.0 compiler compiled the actual RestrictedGitExecutor, RestrictedGitRuntime, GitHubCapabilityGateway and their two test classes against pinned JGit 5.13.5. The unrelated app dependencies were avoided by a temporary copy of the repository URL validator in /tmp/opencode. JUnit 4.13.2 result: **OK (6 tests), 7.894 seconds**. This includes actual local repository mutations and hook nonexecution, not private-network authentication. Full application Kotlin compilation remains unexecuted. SLF4J used its no-operation logger in this probe.

Source review confirmed production registry wiring, engine approval callback and absence of token-bearing subprocess creation in the restricted executor. Static checks alone do not establish Android compatibility or full isolation. Review JGit dependency advisories and final APK footprint in CI before release.
