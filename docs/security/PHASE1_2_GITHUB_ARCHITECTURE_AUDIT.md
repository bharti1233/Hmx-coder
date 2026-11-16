# Phase 1.2 GitHub architecture audit

Inspected clean branch `security/phase-1-privacy-hardening` at `6ec49d44250fff29ea0a3970f282a8781f82fb90`. The supplied `ce6c...` hash was incorrect. No push/publication authorized or performed.

## Existing implementation

`github/GitHubAuth.kt` owns device OAuth, PAT verification, refresh and direct REST. `KeyStoreManager` stores GitHub records in Keystore-backed encrypted preferences. `GitHubRepository.kt:69` accepts structured argv through an injected runner, but `gitCommand` renders those arguments into shell syntax. `tools/GitTools.kt` also uses shell commands. `GitHubWorkspaces` supplies workspace runners. No native Git library or direct credential callback was found; current Git is the installed Termux-derived toolchain.

Actual execution tiers: APP_LINUX, TOYBOX, ROOT, PRIVILEGED (Shizuku/wireless ADB), and TERMUX_SSH/remote workspace. Dhizuku is an explicit device-owner command feature, not a Git transport. General process environments no longer receive app GitHub credentials. Local/public Git operations still use existing executors; private authenticated subprocess operations remain unsupported pending isolation.

## Credential boundaries

REST credentials are supplied directly to OkHttp. Redirect following is now disabled in GitHubApi; request origins must match the selected API/auth origin. Production defaults are HTTPS api.github.com and github.com; internal constructor overrides support loopback unit fixtures. No trust manager changes.

Legacy recovery reads local and privileged fixed `.gh-token`, `hosts.yml`, `etc/gitconfig` sources, compares candidates and verifies encrypted read-back. Durable encrypted logout tombstone now prevents migration from resurrecting a revoked token. Same-UID or root hostile code is outside at-rest storage isolation.

## Missing executor

The new structured capability gateway validates canonical repository scope, permission level and exact push/advanced approval. It is not connected to the app/agent or to a token-bearing executor. A local socket askpass helper alone does not isolate same-UID hooks/children; PID ancestry or an environment session secret alone is insufficient. No insecure broker was introduced.

No new native dependency was introduced: libgit2 would require native build/ABI integration and callback review; JGit adds substantial runtime/dependency surface. Neither option has measured size/RAM evidence in this environment. Existing subprocess tiers must remain unauthenticated until a secure isolated execution boundary is implemented and tested.
