# Phase 1.2 test results

Fresh Gradle unit tests, Kotlin compilation, lint, instrumented compilation/execution, debug/release APK builds, APK signing/package verification: **NOT EXECUTED**.

Executed: `git diff --check` passed. Added test sources: GitHubCapabilityGatewayTest (scope/mode/approval/revocation/shell rejection), GitHubRestBoundaryTest (origin/current token/redirect rejection/redaction), GitHubLogoutRecoveryDeviceTest (isolated preference names, manager reload, logout tombstone, replacement). These tests have not executed; interceptor fixtures are not live TLS tests, and manager reconstruction is not a killed-process/reboot test.

Build policy remains GitHub Actions-only. Prior continuation confirmed missing GitHub authentication; no push or publication is authorized for Phase 1.2. Historical passing counts are not reused as current verification. Policy, REST and credential recovery tests are added/updated as source only until CI runs.

Manual remote verification after owner authorization:

1. Configure GitHub authentication using the local credential manager, not a token in chat or source.
2. Review local branch and explicitly authorize/push `security/phase-1-privacy-hardening`.
3. Run `.github/workflows/build.yml` on that branch; collect unit/lint/instrumentation-compilation and signed APK artifacts.
4. Manually dispatch `.github/workflows/security-device-tests.yml` for full API 26/35 emulator suite; preserve failures.
5. Verify APK certificate equals existing pinned release identity, package com.hmx.harness, and merged manifest backup/network configuration.
6. Execute OPPO plan only with reviewed APK. Private Git tests cannot pass until executor implementation exists.
