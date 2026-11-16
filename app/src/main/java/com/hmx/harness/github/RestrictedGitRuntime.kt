package com.hmx.harness.github

import java.io.File
import org.eclipse.jgit.lib.Config
import org.eclipse.jgit.storage.file.FileBasedConfig
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.util.SystemReader

/** JGit is used only by the restricted executor: never import shell/user Git config. */
internal object RestrictedGitRuntime {
    @Synchronized fun initialize() {
        if (initialized) return
        val original = SystemReader.getInstance()
        SystemReader.setInstance(object : SystemReader() {
            override fun getHostname() = "localhost"
            override fun getenv(variable: String): String? = null
            override fun getProperty(key: String): String? = original.getProperty(key)
            override fun getCurrentTime() = System.currentTimeMillis()
            override fun getTimezone(whenMillis: Long) = 0
            override fun openUserConfig(parent: Config?, fs: FS) = empty(fs)
            override fun openSystemConfig(parent: Config?, fs: FS) = empty(fs)
            override fun openJGitConfig(parent: Config?, fs: FS) = empty(fs)
            private fun empty(fs: FS) = object : FileBasedConfig(null as File?, fs) {
                override fun load() {
                    clear()
                    // Monitoring.registerMBean returns before touching desktop JMX APIs.
                    setBoolean("jmx", null, "WindowCacheStats", false)
                }
                override fun save() { }
                override fun isOutdated() = false
            }
        })
        initialized = true
    }
    private var initialized = false
}
