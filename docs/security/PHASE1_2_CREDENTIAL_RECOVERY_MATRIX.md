# Phase 1.2 credential recovery matrix

| State / credential | Implemented behavior | Verification |
|---|---|---|
| Available credential | Existing EncryptedSharedPreferences; startup decrypts values | Existing device test source, not run |
| Missing credential | Null slot; distinct from exception | Not executed |
| Missing master key with ciphertext | Reject before creating key; retain records | Not executed |
| Invalidated key | Sanitized error when directly surfaced; wrapped errors remain unreadable category | Not executed |
| Corrupted ciphertext | Startup error; no automatic reset | Not executed |
| Explicit re-entry | New generation and Keystore alias; read-back then switch nonsecret selector; old records and keys retained | Not executed |
| GitHub legacy | Fixed-source recovery; synchronized migration checks existing credential and durable logout marker | Not executed |
| GitHub logout | Atomic encrypted tombstone and credential removal; migration cannot clear marker | Device regression added, not run |
| MCP legacy | Verified encrypted read-back before cleanup; re-entry store preserves old legacy files | Not executed |
| Provider/voice/search/ChatGPT/Telegram/SSH credentials | Existing KeyStoreManager slots, selected generation; reconnect after re-entry | No live regression |
| Process restart | New manager reads active generation selector | Device regression added, not run |
| Backup/restore | All private domains excluded; retained ciphertext cannot recover without original key | Static configuration only |

Re-entry is a user-confirmed recovery action, not a claim of cryptographic recovery. It resets active connections only; databases, projects and conversations are unchanged. Old encrypted files remain private and backup-excluded. Selector commit occurs after writing/verifying the new store; interruption before selection leaves the old store active. Generation cleanup is intentionally not automatic.

The implementation has sanitized error categories, not a complete observable eleven-state migration state machine. Cross-instance/process serialization, background startup handling, user-approved key availability retry and granular corrupted-entry recovery remain open. Secrets must never be included in state descriptions or logs.
