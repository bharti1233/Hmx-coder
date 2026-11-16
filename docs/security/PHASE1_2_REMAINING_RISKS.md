# Phase 1.2 remaining risks

- Primary requirement, restricted authenticated Git executor, is not implemented. Authorization component alone is not an isolation boundary and is not wired into agent requests.
- No proven safe askpass IPC broker exists. Same-UID hooks/children or root can defeat naive socket secrets/process ancestry checks. Authenticated Git is therefore unsupported in all subprocess tiers pending isolation.
- REST origin/redirect hardening applies to GitHubApi; other independent REST/tool/download clients require full review. Internal loopback fixtures are permitted for fake-credential unit tests, not user endpoint configuration.
- Recovery generation switching preserves old ciphertext but needs process-restart, interruption and concurrency tests. No automatic cleanup of retained keys/files.
- Re-entry UI requires manual restart; background components may still assume AppContainer is initialized during recovery. This must be resolved before release.
- Revocation marker prevents stale recovery after normal logout; cross-process/in-flight refresh races need further testing.
- Privileged legacy file reading is fixed-path but symlink/content-change races and root-only orphan recovery require review.
- No fresh CI/device results. Do not distribute this branch as verified.
