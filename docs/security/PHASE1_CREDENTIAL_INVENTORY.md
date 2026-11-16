# Phase 1.1 credential inventory

Source root below: `app/src/main/java/com/hmx/harness/`. Status: source review, no new runtime verification.

| Credential | Storage / cryptography | Retrieval and network use | Exposure / migration |
|---|---|---|---|
| AI keys and pools | `data/KeyStoreManager.kt`: EncryptedSharedPreferences, Keystore-wrapped keyset, AES256-GCM values | ProviderRepository / ProviderNetwork to selected provider | Custom headers and bodies need full review; global HTTP blocked |
| GitHub PAT/OAuth/refresh | Same store, github_pat/github_connection | GitHubAuth and REST tools; GitHub HTTPS | Legacy local/privileged files migrate with read-back; generic shell inheritance removed |
| Global MCP command/env/headers | Versioned mcp_configuration_v1 in same encrypted store | McpManager, local child or configured HTTP service | Global JSON migration implemented; workspace/plugin files remain user-controlled plaintext |
| MCP OAuth tokens/client secret | Same store, per-server OAuth record | McpOAuth token/refresh endpoint | Token exchange now requires HTTPS; redirect/discovery regression pending |
| ChatGPT OAuth | Same store, chatgpt_accounts | ChatGptAccounts/ChatGptProvider | Device key recovery and restart unverified |
| Voice keys | Same store, per-provider voice slots | VoiceGateway/audio clients | Network guard added; live voice tests pending |
| Search keys | Same store, search key/pool slots | SearchBackends HTTPS | Existing legacy-key migration; logging audit pending |
| Telegram bot token | Same store, telegram_connection | TelegramBotClient HTTPS path | URLs themselves contain secrets; raw exceptions must not be emitted |
| SSH/sync passwords/private keys | Same store via localSecret | JSch/transfer clients | FTP transmits credentials unencrypted; must not claim all transports secure |
| Wireless ADB pairing key | Separate wireless_adb preference path | Local ADB protocol | Not covered by the MCP/GitHub encrypted preference migration; further review required |
| User-requested settings export | Password-derived PBKDF2 + AES-GCM (`data/BackupEncryption.kt`) | User-selected document provider | Credentials optional; passphrase/key lifecycle separate from Android Keystore |
| Chats, exports and command logs | Room/files, not blanket encrypted | UI/tools/manual exports | May contain user-entered secrets; exclusion from backup is not encryption |

## Recovery states

- No credential: legitimate absent slot; not a decryption failure.
- Legacy plaintext: only accepted after validation and successful encrypted persistence/read-back.
- Missing master key with existing records: fail before MasterKey creation; never silently create a replacement for old ciphertext.
- Invalidated key: report invalidation when directly surfaced by Android; preserve ciphertext.
- Corrupt record/unavailable key: startup now decrypts all stored entries, not just `contains()`. Wrapped Android exceptions may remain classified as unreadable rather than distinguishable corruption/invalidation.
- Process restart: storage is persistent by construction; device restart test evidence remains absent.

The startup error screen explains preservation but still lacks a complete user-controlled archive/reconnect workflow. Background component access to an uninitialized container must be checked. These are open acceptance items, not completed recovery.
