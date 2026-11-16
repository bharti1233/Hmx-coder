package com.hmx.harness.data

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/** Also runs on redirects; never changes the TLS trust manager or hostname verifier. */
class EndpointSecurity : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.isHttps) {
            val loopback = request.url.host in setOf("localhost", "127.0.0.1", "::1")
            val credentials = request.url.username.isNotEmpty() || request.url.password.isNotEmpty() ||
                request.headers.names().any { sensitiveName(it) } ||
                request.url.queryParameterNames.any { sensitiveName(it) }
            if (!loopback || credentials) throw IOException(
                "This endpoint requires HTTPS. HTTP is available only for local endpoints without credentials.",
            )
        }
        return chain.proceed(request)
    }

    companion object {
        internal fun sensitiveName(name: String): Boolean {
            val normalized = name.lowercase().replace("-", "").replace("_", "")
            return listOf("authorization", "apikey", "token", "secret", "password", "cookie").any { it in normalized } ||
                normalized.endsWith("key") ||
                normalized == "key"
        }
    }
}
