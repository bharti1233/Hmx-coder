# Phase 1 remaining risks

Status: incomplete implementation and verification. Do not release on the basis of these documents.

Phase 1.1: ambient GitHub token injection has been removed. Private Git/gh subprocess operations are currently not authenticated by the app; a restricted broker/executor with safe Git config and child-process boundaries is required. No command parser/allowlist is claimed implemented. This is an explicit functional gap and keeps acceptance INCOMPLETE.

Privileged Shizuku/ADB fixed-path recovery now persists and verifies before cleanup, but root-only orphan recovery and deployment-order edge cases are unverified. Master-key preflight now refuses key regeneration with existing ciphertext and authenticates stored values. Recovery UI still needs a safe archive/reconnect action, and background entry points need startup-failure handling.

- No fresh CI unit/lint/APK/signature results; no instrumentation execution.
- Keystore loss must be tested across all eager AppContainer credential readers: preserving ciphertext must not introduce a startup crash loop.
- Global MCP migration is implemented; workspace/plugin configurations can still contain credentials. Those paths need separate review and a compatibility-preserving storage design.
- GitHub process environment must be traced through every launcher, background process record and privileged IPC path before claiming absence of plaintext credential files. Existing terminal sessions retain old environment until restarted.
- Local legacy credentials are recovered from `.gh-token`, `hosts.yml`, or token URL rewrites, rejecting conflicting copies. Privileged-prefix-only orphaned credentials still require a recovery path before declaring migration coverage complete.
- Endpoint checks currently cover common header/query credential names on provider/MCP clients. Arbitrary custom headers, form bodies, voice clients, redirects and FTP need further review. Android network configuration does not constrain arbitrary native tools.
- Device-to-device backup behavior depends on OEM implementation. Rules are configured but not tested on hardware; old backups and independently copied shared-storage files remain outside their control.
- Credentials must exist in authorized process memory. Same-UID untrusted code/root compromise is not prevented by Keystore storage.
- Full logging/clipboard/intent/export review and instrumentation cryptography tests remain outstanding.
- Version remains 1.0.0/code 1 during development. A distributed update must use a greater versionCode and corresponding existing CI assertions/metadata; no release is published here.
