# Privacy Policy for HMXHARNESS

Last updated: November 16, 2026 (Phase 1 implementation; device verification pending)

HMXHARNESS, the HMXPANEL Android application (`com.hmx.harness`), runs on your device. It connects to services you
choose and configure yourself. I built it so that your data stays between you
and those services. This page explains what the app handles and what it does
with it.

## What the app stores on your device

Everything the app needs to work is stored locally: your chats and agent
history, files you open or edit through the file manager, your settings, and
the credentials you enter. Credentials such as API keys, GitHub tokens, and
sign-in tokens for MCP servers are kept in encrypted storage that uses the
Android Keystore. Chats and files remain local unless you use a feature that sends them to a service as described below.

## What the app sends, and where

When you use the agent, the app sends your prompts and the context needed to answer
them (which can include file contents and command output from your device) to
the AI provider you configured, for example OpenAI, Anthropic, or any other
OpenAI-compatible endpoint you entered. Web search queries go to the search
service you configured, or to public search engines when no API key is set.
If you connect a GitHub account, you create a personal access token on
GitHub and paste it into the app. The app checks the token by calling
GitHub's API with it and saves it only after GitHub confirms it belongs to
you. The token is sent only to GitHub: for the check, for repository
requests, and for Git operations. There is no separate login server between
you and GitHub. If you add an MCP server, the app talks to that server directly, and
any OAuth sign-in happens between you and that server.

Which services receive data, and what keys they use, is entirely up to you.
If you remove or change a provider, server, or key in settings, the app stops
using it.

## What I collect

The app has no analytics, advertising, crash reporting, or telemetry.

## Backups

The application disables Android backup and explicitly excludes all app-managed
data domains from legacy cloud backup, Android 12+ cloud backup, and device
transfer. This includes database sidecars, attachments, workspace history,
credentials and preferences. These rules do not erase existing backups or
control backups made independently by other apps of shared-storage files.
OEM backup and transfer behavior has not yet been verified on devices.

Manual exports remain available: settings exports are password-encrypted and
include credentials only when selected. Chat exports contain readable conversation
content and should be handled as sensitive files. Clearing chats or memories in
the app does not erase copies previously exported or sent to external services.

## Credential recovery and local networking

Global MCP configurations migrate into existing Android Keystore-backed encrypted
preferences. Legacy files are removed only after encrypted read-back succeeds.
Failures preserve original data and block configuration replacement with an
actionable error. User-authored workspace configuration can still contain secrets.
GitHub toolchain authentication uses process environment values instead of saved
token files; programs running with those credentials can access them in memory.

Keystore invalidation, uninstall or device migration may make credentials
unrecoverable. The app does not automatically delete unreadable encrypted records;
affected services must be reconnected when recovery is impossible. Chats and
workspace files are not protected by blanket application-level encryption.

Android HTTP policy requires HTTPS except exact loopback development endpoints
(`localhost`, `127.0.0.1`, `::1`). LAN HTTP services require HTTPS or a local tunnel.
FTP and user-run native programs have separate network behavior; not every
connection is encrypted. External voice services receive audio or read-aloud text,
including Microsoft when Edge TTS is selected. Keyless AI/search services also
receive request content. Local inference does not make unrelated tools offline.

## Children

The app is a developer and power-user tool and is not directed at children
under 13.

## Changes

If the app's data handling ever changes, I will update this page and the
change will be reflected in the app's store listing.

## Contact

Questions or concerns: open an issue at
https://github.com/bharti1233/Hmx-coder/issues
