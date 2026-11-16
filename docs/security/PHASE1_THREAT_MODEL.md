# Phase 1 threat model

HMXHARNESS / HMXPANEL, baseline `fd9ee02`. Status: implementation in progress.

## Assets and boundaries

Assets: provider keys, GitHub PAT/OAuth tokens, MCP environment/header/command secrets, refresh tokens, SSH credentials, conversations, prompts, workspace content, attachments, history and signed update identity.

Boundaries: Android app sandbox; Keystore; app-spawned processes; privileged root/Shizuku/ADB execution; remote HTTPS services; local HTTP development endpoints; WebView; Android backup and device transfer; user-requested exports.

Keystore protects keys at rest, not against arbitrary code executing as the application UID or a compromised OS. Process authentication necessarily makes credentials available to the authorized process in memory. This phase must remove routine plaintext credential files without claiming an OS sandbox around trusted user tools.

## Threats and controls

Phase 1.1 clarification: environment-only authentication is not a sufficient boundary. Any shell inheriting a token can dump it or pass it to unrelated children; Git hooks/helpers and gh extensions bypass simplistic command-name allowlists. Ambient injection was removed. A constrained operation-specific broker/executor is still required before authenticated subprocess support can be approved.

| Threat | Severity | Evidence | Control | Compatibility risk / validation |
|---|---|---|---|---|
| Disk credential disclosure | High | `data/env/GitHubProvision.kt:40-92`; `tools/mcp/McpManager.kt:116-156` under `app/src/main/java/com/hmx/harness` | Encrypted verified persistence; process-scoped GitHub auth | Test MCP restart and git/gh in every supported execution tier |
| Lost/interrupted migration | High | MCP saves currently ignore exceptions; `data/KeyStoreManager.kt:27-34` resets unreadable storage | Persist, decrypt and compare before legacy deletion; fail without deleting source; retry idempotently | Key unavailable, interrupted write, cleanup failure and replacement tests |
| Backup bypass of local-only expectations | High | Manifest enables backup with selective exclusions | Disable backup and exclude all private domains for both rule generations | Android 8–11 and 12+ rules; explicit manual exports remain available |
| Network interception | Medium | Global cleartext flag, remote custom endpoints | System TLS trust, HTTPS default, exact loopback exceptions, no credentials on HTTP | Local Ollama/LM Studio/router and OAuth loopback callbacks must survive; LAN users need TLS/tunneling |
| Secrets in diagnostics | High if confirmed | Audit pending across logs, command output and HTTP debug capture | Redaction before persistence/display; local opt-in content capture | Preserve useful diagnostics; test synthetic tokens without real secrets |
| Malicious web content | Medium | Preview mixed content enabled at `ui/chat/components/WebPreviewSheet.kt:1092` | Disallow mixed content; preserve SSL validation | Verify localhost previews and OAuth redirects |

## Lifecycle and exclusions

Keys may be unavailable temporarily or permanently invalidated. Preserve ciphertext and legacy migration sources on errors; never treat an error as an empty successful read. After uninstall or device migration, Keystore material may be irrecoverable: users must reconnect affected services. Ordinary Room data must not be reset as a recovery mechanism.

User-authored workspace config and arbitrary shell scripts can contain secrets; the application cannot promise to sanitize every user file or prevent deliberately authorized export. Document such boundaries explicitly. Root compromise, malicious trusted plugins, and third-party service retention remain outside at-rest encryption guarantees.

No threat is marked resolved until implementation and its verification evidence are recorded in the final report.

Checkpoint controls: encrypted MCP migration with read-back, GitHub in-memory process authentication, legacy token recovery, startup key-availability error screen, full backup exclusion, exact-loopback cleartext exceptions, HTTPS-only MCP OAuth token exchange, and removal of credential prefixes from diagnostic captures are implemented. Static backup/network checks passed. Runtime threats remain unverified until CI and instrumentation execute.
