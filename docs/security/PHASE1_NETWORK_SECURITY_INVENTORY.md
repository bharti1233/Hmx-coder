# Phase 1 network inventory

Status: implementation in progress; no live endpoint tests performed.

Phase 1.1 regression matrix: exact localhost/127.0.0.1/::1, LAN/remote/lookalike rejection, header/query/form credential rejection now have JVM test source (`EndpointSecurityTest`) but are NOT EXECUTED. Network interceptors check the connected address is loopback for HTTP. TLS certificate/hostname, live MCP OAuth/refresh, provider streaming, voice and WebView regression remain NOT EXECUTED. No certificate validator or hostname verifier was replaced. API 26/35 emulator workflow is prepared but not run.

| Connection | Source under `app/src/main/java/com/hmx/harness` | Transport / policy |
|---|---|---|
| External AI providers | `llm/Llm.kt:210`, `llm/ProviderNetwork.kt:20` | OkHttp, default certificate/hostname verification; remote HTTP blocked by Android policy |
| Local Ollama / LM Studio | `ui/settings/ProviderSheet.kt:112-113` | Explicit 127.0.0.1 HTTP presets; unauthenticated local inference must remain available |
| 9Router | `router/NineRouterControl.kt:36` | Explicit 127.0.0.1:20128 HTTP |
| MCP HTTP/SSE and OAuth | `tools/mcp/McpTransport.kt:334`, `McpManager.kt:47` | OkHttp; exact loopback HTTP only; authenticated requests require additional application enforcement (pending) |
| ChatGPT OAuth callback | `chatgpt/ChatGptProtocol.kt:180` | Incoming loopback HTTP callback, ephemeral port; authorization/token exchanges use HTTPS |
| GitHub REST and updates | `github/GitHubAuth.kt:36`, `data/update/UpdateRepository.kt:24` | HTTPS; existing update signer/package/hash policy preserved |
| Voice providers | `data/audio/*Client.kt` | OkHttp HTTPS or custom endpoint; credential-on-HTTP enforcement pending |
| Search/web downloads | `tools/`, `local/`, `plugins/` | OkHttp/system clients; remote cleartext rejected; third-party redirects require review |
| Preview/browser | `ui/chat/components/WebPreviewSheet.kt:1092`, `browser/BrowserController.kt` | WebView follows app policy; preview mixed content disabled |
| Artifacts | `artifacts/ArtifactWebView.kt:42,70` | Mixed content disabled and SSL errors cancelled |
| SSH/SFTP | `workspace/SshConnections.kt` | SSH host-key checks, independent of Android HTTP policy |
| FTP | `data/transfer/` | Unencrypted protocol; outside Android HTTP policy; unresolved compatibility/security constraint |
| User shell tools | `data/env/LinuxEnvironment.kt:1007` | Native TLS CA bundle provided; arbitrary programs do not necessarily obey Android Network Security Configuration |

## Compatibility change

Remote and LAN HTTP endpoints no longer qualify for the app's cleartext exception. Configure HTTPS with a valid certificate or use a loopback tunnel. There is no automatic HTTPS-to-HTTP downgrade. Exact localhost, 127.0.0.1 and ::1 exceptions support explicit local development/inference; no wildcard domain, private CIDR, or DNS resolution-based exception is introduced.

This configuration does not prove all traffic encrypted: FTP, user-run native programs, and local HTTP remain exceptions. Credential transport checks and regression execution are not complete. OAuth loopback reception is distinct from sending tokens to a remote HTTP server.
