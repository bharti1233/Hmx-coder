# Phase 1.3 restricted Git executor

Implementation uses JGit 5.13.5 (Java 8-compatible maintenance release), in-process HTTPS transport and an operation-scoped credentials callback. No askpass service, token files, token argv or token environment. JGit core is an added dependency; final APK/RAM cost requires CI/device measurement.

`RestrictedGitExecutor` supports clone, fetch, fast-forward pull, status, file-summary diff, branch listing, recent commit inspection, branch creation, explicit-path staging, commit, push, force push and remote branch deletion. Remote URL modification is deliberately unsupported. Clone initializes a new folder, fetches an explicit branch, then checks it out; failure can leave a partial folder for inspection and never removes an existing directory.

Only canonical GitHub HTTPS origins are accepted. Repository helper/filter/include/URL/HTTP/SSH/path overrides are rejected; metadata symlinks and alternates are rejected. A custom filesystem refuses shell execution and reports hooks absent. User/system Git configuration is replaced with empty in-memory configuration for JGit only. No recursive submodules. TLS verification is retained; HTTP redirects disabled in the operation's in-memory config. Credential callback accepts only username/password requests for the approved URL; no trust-certificate confirmation is answered.

Supported execution: app-process locally accessible workspace. Root/Shizuku/ADB/SSH/SAF authentication is unsupported; public generic git tools retain their existing paths. Same-UID malicious code/root compromise is not isolated by in-process storage. Concurrent external workspace mutation remains a risk; head is checked before operations but no OS filesystem sandbox is claimed.

Cancellation uses coroutine interruption, progress monitor and 30-second transport timeout; task deadline is 180 seconds. Mutation outcome can be uncertain after interruption: no automatic retry/reset. Library/Android compatibility and private network tests remain unverified until CI runs.
