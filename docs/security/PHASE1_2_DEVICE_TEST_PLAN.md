# Phase 1.2 OPPO A38 / Android 15 plan

All items NOT RUN. Use verified signed APK after CI; use disposable test state for corruption/invalidation. Never uninstall or clear the user's app to test recovery.

- Verify package com.hmx.harness, signing certificate and increasing release versionCode.
- Upgrade existing installation; compare chats/projects/memory/settings counts and representative content.
- Connect provider, GitHub and MCP; force-stop/restart and reboot; confirm credentials remain readable.
- Test logout with synthetic legacy files; restart and deploy privileged toolchain; no automatic reauthentication.
- Test fixed local/Shizuku legacy recovery, interrupted writes, conflicting tokens and retry; old source survives failures.
- Corrupt test ciphertext or remove only disposable test keys; verify sanitized recovery UI.
- Confirm re-entry dialog preserves old files, creates new active generation, and reconnects services after restart. Verify projects/conversations unaffected.
- GitHub REST account/repositories/PRs, revoked token and token replacement.
- Private Git clone/fetch/push: BLOCKED until restricted executor is implemented. Do not mark passed using generic shell credentials.
- Verify Termux/SSH public Git and configured remote credentials; app token must not appear in env, argv, diagnostics, hooks or child processes.
- Test Shizuku if available; record unavailable tiers explicitly.
- MCP HTTPS/OAuth and existing AI/voice connections; no HTTP token transmission.
- Measure startup and operation RAM on this device; no always-on broker is currently introduced.

Record commit/APK hash, Android build, expected/actual results and sanitized logs. Device tests remain separate from emulator/JVM results.
