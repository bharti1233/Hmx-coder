package com.hmx.harness.data.env

import java.io.File

/** Encrypted preferences own the credential; toolchains receive it only in process memory. */
object GitHubProvision {
    const val TOKEN_FILE = "home/.gh-token"
    const val GH_HOSTS_FILE = "home/.config/gh/hosts.yml"
    fun hasToken(token: String?): Boolean = !token.isNullOrBlank()

    /** Recover only the exact formats emitted by older HMX builds; ambiguity fails closed. */
    fun legacyToken(prefix: File): String? {
        return legacyToken(
            File(prefix, TOKEN_FILE).takeIf { it.isFile }?.readText(),
            File(prefix, GH_HOSTS_FILE).takeIf { it.isFile }?.readText(),
            File(prefix, "etc/gitconfig").takeIf { it.isFile }?.readText(),
        )
    }

    fun legacyToken(tokenFile: String?, hostsFile: String?, gitConfig: String?): String? {
        val candidates = buildSet {
            tokenFile?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
            hostsFile?.lineSequence()?.forEach { line ->
                if (line.trimStart().startsWith("oauth_token:")) line.substringAfter("oauth_token:").trim().takeIf { it.isNotBlank() }?.let(::add)
            }
            gitConfig?.let { text ->
                Regex("https://x-access-token:([^@\\s]+)@github\\.com/").findAll(text).forEach { add(it.groupValues[1]) }
            }
        }
        check(candidates.size <= 1) { "Legacy GitHub credentials disagree. Reconnect GitHub; original files were preserved." }
        return candidates.singleOrNull()
    }

    @Suppress("UNUSED_PARAMETER")
    fun gitConfigBody(token: String?): String = "[safe]\n\tdirectory = *\n[user]\n\tname = Android Harness\n\temail = harness@android.local\n[init]\n\tdefaultBranch = main\n"

    fun processEnvironment(token: String?): Map<String, String> {
        if (!hasToken(token)) return emptyMap()
        val clean = token!!.trim()
        require(clean.none { it == '\n' || it == '\r' || it == '\u0000' }) { "Invalid GitHub credential. Reconnect GitHub." }
        val basic = java.util.Base64.getEncoder().encodeToString("x-access-token:$clean".toByteArray(Charsets.UTF_8))
        return mapOf("GH_TOKEN" to clean, "GIT_CONFIG_COUNT" to "2",
            "GIT_CONFIG_KEY_0" to "http.https://github.com/.extraHeader",
            "GIT_CONFIG_VALUE_0" to "Authorization: Basic $basic",
            "GIT_CONFIG_KEY_1" to "http.https://github.com/.followRedirects",
            "GIT_CONFIG_VALUE_1" to "false")
    }

    @Suppress("UNUSED_PARAMETER")
    fun materializeTokenFile(prefix: File, token: String?) = removeLegacy(File(prefix, TOKEN_FILE))

    @Suppress("UNUSED_PARAMETER")
    fun ghHostsYaml(token: String?): String? = null

    @Suppress("UNUSED_PARAMETER")
    fun materializeGhHosts(prefix: File, token: String?) = removeLegacy(File(prefix, GH_HOSTS_FILE))

    @Suppress("UNUSED_PARAMETER")
    fun checkMaterialized(prefix: File, token: String?) {
        check(!File(prefix, TOKEN_FILE).exists() && !File(prefix, GH_HOSTS_FILE).exists()) {
            "Could not remove legacy GitHub credentials. Check storage and reconnect GitHub."
        }
    }

    private fun removeLegacy(file: File) {
        check(!file.exists() || file.delete()) { "Could not remove legacy GitHub credentials. Check storage and retry." }
    }
}
