# Phase 1 security baseline

Project: HMXHARNESS / HMXPANEL (`com.hmx.harness`). Inspected baseline: `fd9ee02`, initially clean tracked tree. Work branch: `security/phase-1-privacy-hardening`. Existing ignored audit reports are preserved.

This is an implementation working record, not a completion certificate. The earlier Actions run `43788648255` reported 2,247 passing JVM tests; it is historical evidence, not a fresh run. This environment has no GitHub CLI or configured GitHub API token. Android builds and execution remain CI-only.

| Finding | Severity | Source evidence and existing behavior | Attack scenario | Mitigation and compatibility risk | Status |
|---|---|---|---|---|---|
| Plaintext toolchain GitHub credentials | High | `app/src/main/java/com/hmx/harness/data/env/GitHubProvision.kt:40` writes a token URL rewrite, `.gh-token`, and `hosts.yml`; `LinuxEnvironment.kt:1116` copies them to the privileged prefix | A program reading those files obtains the saved token; command errors can expose credential-bearing URLs | Replace disk copies with process-scoped authentication; preserve app/privileged shell and gh behavior, verify encrypted master before removing legacy files | Open |
| Plaintext global MCP configuration | High | `app/src/main/java/com/hmx/harness/tools/mcp/McpManager.kt:116` loads JSON with arguments, environment, headers; `:140` suppresses persistence errors | File disclosure reveals configured credentials; unsuccessful saves appear successful | Move the entire configuration into existing Keystore-backed credential storage; verify migration before removal and fail visibly on errors | Open |
| Destructive credential recovery | High | `app/src/main/java/com/hmx/harness/data/KeyStoreManager.kt:27` deletes encrypted preferences after a security exception | Transient key failures become permanent credential loss | Preserve encrypted records; surface actionable recovery instead of resetting | Open |
| Broad OS backup eligibility | High | `app/src/main/AndroidManifest.xml:69`; `app/src/main/res/xml/backup_rules.xml`; `data_extraction_rules.xml` exclude selected locations only | Chats, image attachments, database sidecars and workspace snapshots can enter OS backup/transfer | Exclude all app data domains in both rule generations; no changes to local data or manual export | Open |
| Global HTTP allowance | Medium | `app/src/main/AndroidManifest.xml:76`; provider presets at `ui/settings/ProviderSheet.kt:112` and router at `router/NineRouterControl.kt:36` | Custom remote HTTP endpoints expose requests and credentials | HTTPS default with exact loopback exceptions; LAN HTTP endpoints need HTTPS or a tunnel, not a broad IP exception | Open |
| Mixed content in preview | Medium | `app/src/main/java/com/hmx/harness/ui/chat/components/WebPreviewSheet.kt:1092` permits mixed content | HTTPS previews load unencrypted active content | Disallow mixed content; plain loopback development pages remain possible | Open |

Existing controls: encrypted provider/GitHub/OAuth master records use Android Keystore-backed EncryptedSharedPreferences (AES-256-GCM values); artifacts cancel SSL errors and disallow mixed content (`artifacts/ArtifactWebView.kt:42,70`). No custom trust-all TLS override was found in the initial source survey. Full client, export, intent and logging review is still in progress.

Identity, Room schema/data, DataStore, signing key and release secrets must remain unchanged. Findings describe confirmed source behavior, not exploits tested on hardware.

## Implementation checkpoint

Backup eligibility and global cleartext policy are addressed in source; XML parsing and explicit-domain checks passed. Global MCP persistence now uses encrypted preferences and verified migration, with JVM/device tests added but unexecuted. GitHub config generation no longer emits token files or token URLs; encrypted-master migration and process environment delivery are implemented but execution-tier checks remain pending. Unreadable credential storage is preserved; startup presents an error rather than resetting credentials. Preview mixed content is disabled. None of these changes has fresh Gradle/Actions or device verification, so findings remain open for acceptance.
