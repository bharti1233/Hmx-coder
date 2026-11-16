# OPPO A38 / Android 15 verification plan

No tests below have been executed. Run only after CI produces a verified signed APK. Preserve the installed app and export recoverable data before upgrade; do not clear storage or uninstall the user's installation to simulate key loss.

Record APK SHA-256, signing-certificate SHA-256, commit, versionCode, Android build and timestamp for every run. Never attach tokens, raw prompt logs or private project contents.

- [ ] Verify existing package is com.hmx.harness and certificate matches the candidate. Candidate versionCode must exceed installed version for release distribution.
- [ ] Upgrade in place; verify conversation/project counts, recent messages, memory and files. No database reset.
- [ ] Force-stop/relaunch: settings, providers, GitHub and MCP survive.
- [ ] On a separate disposable installation/profile, test fresh setup and permissions.
- [ ] With synthetic legacy fixtures, test migration, interrupted cleanup, storage-full failure and retry. Ensure original remains on failure and no plaintext remains after success.
- [ ] On disposable test state only, remove/invalidate a test Keystore key; verify visible recovery and preserved encrypted files. Do not manipulate production keys.
- [ ] External AI provider: model list, streaming turn, tool call and cancellation.
- [ ] MCP: stdio, HTTPS transport, OAuth authorization, token refresh and rejected HTTP token exchange.
- [ ] GitHub REST: account, repository browse, invalid/replaced/expired token. Private shell clone/fetch/push are blocked until the restricted executor is complete.
- [ ] Termux/SSH: public clone, local Git operations, server-side credentials; no forwarding of app credentials.
- [ ] Shizuku (if installed and authorized): commands, legacy recovery, logout cleanup, reconnect; repeat for wireless ADB/root only where actually available.
- [ ] Voice: microphone permission, transcription, TTS and cancellation for selected providers.
- [ ] Local HTTP: explicit localhost/127.0.0.1/IPv6 ::1 fixture succeeds without credentials. LAN/public HTTP, spoofed hostname and token-bearing HTTP fail.
- [ ] HTTPS: valid certificate succeeds; invalid certificate/hostname fails with no fallback.
- [ ] Preview: local development page, HTTPS page, mixed-content rejection.
- [ ] Inspect backup rules in packaged APK. Use a disposable backup/transfer test to check OEM behavior; absence of cloud UI evidence is not proof of exclusion.
- [ ] Export/import and deletion: user-requested files retain existing behavior; no credential copies in diagnostics or clipboard without explicit user action.

For each item record PASS/FAIL/NOT RUN, expected/actual behavior and sanitized evidence. Unit/emulator results do not replace these device observations.
