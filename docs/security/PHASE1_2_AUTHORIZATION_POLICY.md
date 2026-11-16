# Phase 1.2 authorization policy

Implemented policy component: `github/GitHubCapabilityGateway.kt`. **Not wired into production execution yet.**

| Mode | Allowed operations |
|---|---|
| READ_ONLY | clone, fetch, pull, status, diff, branch listing, commit inspection |
| CONTROLLED_WRITE | read operations plus create branch, stage, commit, push |
| ADVANCED | controlled writes plus force push, delete remote branch, change remote |

Push and every advanced operation request a new approval for the immutable request (canonical repository, operation, target, expected head). Scope/permission is checked again after approval. There is no model-provided approval boolean and no cached reusable approval. Credentials are not part of the gateway input or output.

The gateway accepts no shell command/executable field. It cannot itself prove a repository is unchanged: a production executor must revalidate head, target, worktree, configuration and remote immediately before dispatch. User permission persistence/UI, local permission-change audit, agent integration, and safe native/process executor remain incomplete. Existing app permission checks have not been replaced or weakened.
