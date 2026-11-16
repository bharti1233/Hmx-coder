# Phase 1.1 GitHub authentication matrix

Continuation from `aea32c0`. Status: **INCOMPLETE**. No shell-tier runtime tests executed.

## Confirmed WIP defect and correction

The initial Phase 1 commit injected GH_TOKEN and Git HTTP authorization into `processEnv()` and `tmpProcessEnv()`. Every terminal, build, MCP child and background job using those environments inherited credentials. Those two injection sites have been removed. The helper that constructs credentials is currently unused in production; this is containment, not completed authenticated subprocess support.

| Actual execution path | Boundary | Current automatic GitHub auth | Operations / evidence | Runtime result |
|---|---|---|---|---|
| APP_LINUX | App UID / Bash / linker | None in general process environment | Public Git and local repo commands; private Git/gh need a restricted executor | Not executed |
| TOYBOX | Android shell under app UID | None | Basic shell; tools depend on installation | Not executed |
| ROOT | Root service process | None from app processEnv | Local/public Git if installed; hooks/config can execute privileged children | Not executed |
| PRIVILEGED (Shizuku or wireless ADB) | Binder/ADB service, shell or root UID | None from tmpProcessEnv | Public/local Git; private auth incomplete | Not executed |
| TERMUX_SSH / remote SSH workspace | JSch channel to remote host | No local GitHub token forwarding | Remote host manages its own credentials | Not executed |
| Dhizuku | Explicit device-owner command route | No app GitHub token injection | Not a general GitHub transport | Not executed |
| GitHub REST tools / app API | OkHttp in app process | Existing encrypted token supplier | Account checks, repository/API operations | Not executed |

## Recovery

Local legacy `.gh-token`, `hosts.yml` and token URL rewrite candidates are compared; conflicting values fail without deletion. Privileged recovery reads only these three fixed paths under the app-derived deployed prefix with direct `test` and `cat` argv. It does not evaluate supplied shell commands. Output is used internally, not surfaced as a tool result. Recovered tokens are synchronously encrypted and read back before cleanup. Logout explicitly disables legacy recovery to avoid resurrecting removed credentials. Root-only orphan recovery and process-tier execution tests remain outstanding.

## Required restricted executor

Do not authorize credential injection by checking `startsWith("git")` or by merely parsing argv. Git can execute hooks, credential helpers, filters, custom transports and configured commands; gh can execute extensions/aliases. An approved operation must capture executable identity, validated GitHub HTTPS remote, exact arguments, safe config, disabled hooks/helpers and a bounded process scope. A credential broker that serves only the vetted HTTPS operation avoids placing reusable tokens in a parent shell environment. This executor is not implemented yet.

Until it exists, chained commands, pipes, redirections, substitutions, nested shells, arbitrary commands and interactive terminals receive no application GitHub token. This intentionally restricts private-repository shell operations. Existing tests for generated config are not evidence of live clone/fetch/push compatibility.
