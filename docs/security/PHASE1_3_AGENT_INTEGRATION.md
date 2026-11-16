# Production agent integration

`ToolRegistry.default` registers `RestrictedGitHubTool` as `github_git` when supplied the encrypted GitHubAuth token callback from AppContainer. Existing `git_*` tools remain unauthenticated public/local shell operations; this is a separate restricted capability rather than a duplicate tool name.

Model input: operation, canonical repository URL, relative directory, exact target. No token, executable, shell string or approval boolean. Paths are resolved within the routed workspace; SSH/SAF rejected. Plan mode excludes this modifying tool conservatively.

AgentEngine injects an engine-owned approval callback into ToolContext. Every request displays repository, operation, branch/path/message, directory, expected HEAD, remote-change flag and destructive flag in the existing approval card. The callback waits for the user; the normal remembered/full-auto shortcuts do not replace this confirmation. Each tool call gets a new confirmation and a five-minute wait limit.

The capability gateway revalidates scope/permission before execution. Controlled writes are permitted only after exact approval. Advanced operations additionally require the existing user-controlled full-access mode. A separate persisted three-mode GitHub settings panel is not implemented. All operations currently ask rather than retaining repository grants for read-only operations.

Credentials are retrieved after approval through GitHubAuth and the current Keystore-backed generation. Logout tombstones and recovery failures remain authoritative. Generic tool output redaction and exact-token scrubbing apply. The executor does not return raw exceptions or authentication headers.
