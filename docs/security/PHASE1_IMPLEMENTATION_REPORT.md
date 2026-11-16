# Phase 1 implementation report

## Phase 1.1 continuation checkpoint

Status: **INCOMPLETE**. Continued the existing branch at `aea32c0`, without uncommitted user changes. Removed ambient GitHub token injection from both process environments; private shell authentication requires a restricted executor and is not complete. Added fixed-path privileged legacy recovery with read-back verification; logout bypasses recovery. Added missing-master-key preflight, all-value decryption during startup checks, distinct sanitized recovery messages, loopback connected-address validation and HTTP form-token rejection. Added network/boundary tests and a full-suite emulator workflow. These are source changes, not runtime-verified fixes.

GitHub publication dry-run failed because authentication is unavailable. No fresh Gradle/Actions/emulator or device evidence exists. See the credential inventory, GitHub auth matrix and OPPO A38 test plan for current boundaries and explicit pending tests.

HMXHARNESS / HMXPANEL (`com.hmx.harness`). Branch `security/phase-1-privacy-hardening`, baseline `fd9ee02`. **IN PROGRESS / NOT ACCEPTED FOR RELEASE.**

## Implemented so far

- Preserved the baseline and ignored audit documents; added security documentation tracking.
- Global MCP configuration uses existing Android Keystore-backed EncryptedSharedPreferences (AES-256-GCM values, library-managed random nonces/authenticated ciphertext), with an application record version. No duplicate cryptographic key store or heavy dependency introduced.
- Added validation/write/read-back/cleanup migration sequence, safe failure message in MCP settings, and removal of silent persistence failure handling. Legacy records survive failed validation or encryption.
- Removed automatic deletion of unreadable encrypted preferences. Recovery behavior across eager callers still requires work and testing.
- Replaced GitHub token-bearing config generation with process environment authentication and legacy file cleanup. Migration from orphaned plaintext-only records and execution-tier regression review remain unfinished.
- Disabled backup and excluded all private app storage domains in both legacy and Android 12+ cloud/device-transfer rule sets.
- Default-deny Android cleartext policy with exact loopback exceptions; preview mixed content disabled; provider/MCP network checks reject common credential headers/query values over HTTP and disable cross-protocol redirects.
- Updated current privacy behavior, project identity, reporting links and security source links.
- Added JVM tests for migration ordering/failure/retry, backup rules and network configuration. Updated GitHub provisioning expectations.
- Added instrumentation tests for actual encrypted preferences round-trip, randomized ciphertext, tamper/invalid-data rejection and non-exportable Keystore keys. Added key availability preflight and a startup error screen that preserves data. Removed credential prefixes from debug headers and added short-secret redaction tests. MCP OAuth token exchange now requires HTTPS and omits server-supplied error details.

## Verification and compatibility

See `PHASE1_TEST_RESULTS.md` for exact executed checks and unexecuted tasks. Fresh CI execution is blocked by missing configured GitHub authentication and CLI. No local Android build, app execution, secret/signing-key change, Room schema/data reset, or DataStore replacement occurred.

Remote/LAN HTTP callers now require TLS or a loopback tunnel; this is an intentional compatibility change. Local unauthenticated inference/router/preview endpoints retain exact loopback exceptions. User-requested exports remain available. Full regression compatibility is not established.

Uninstall or lost Keystore material can make encrypted credentials unrecoverable. Preserve records, report errors and require reconnection; do not pretend encrypted backups restore keys. No broad promise is made that all user files or network transports are encrypted.

## Acceptance status

All acceptance criteria remain pending final review and test evidence. In particular, no fresh debug/signed release builds exist and not all credential/network paths are audited. A final commit and complete modified-file inventory must be recorded after implementation is finished. See `PHASE1_REMAINING_RISKS.md`.
