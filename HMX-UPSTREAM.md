# HMX source provenance

HMX Coder AI derives from https://github.com/Sanuu7/AndroidHarness at commit
`28ceacb04ee4ea4117cc46d417e49be3304dc085`.

The upstream MIT license and copyright notice are retained in LICENSE.
Third-party component licenses remain applicable. This import is a debug development
baseline, not a completed production rebrand or licensing certification.

HMX application ID: `com.hmx.coder` (`com.hmx.coder.debug` for debug).
Internal Kotlin namespace remains `com.androidharness.app` to minimize migration risk.
MCP callback scheme: `hmxcoder`.

Builds are intended to execute only in GitHub Actions. No local Gradle, APK build,
emulator or application execution has been performed.

Known remaining upstream integrations include optional GPU runtime download sources,
free inference gateways and author/about links. HMX's debug CI does not publish to
upstream repositories. Debug APKs are development artifacts and not production releases.
