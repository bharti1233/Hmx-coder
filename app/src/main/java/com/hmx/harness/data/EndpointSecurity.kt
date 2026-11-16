package com.hmx.harness.data

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/** Also runs on redirects; never changes the TLS trust manager or hostname verifier. */
class EndpointSecurity : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        validate(request)
        if (!request.url.isHttps) {
            val connectedAddress = chain.connection()?.route()?.socketAddress?.address
            if (connectedAddress != null && !connectedAddress.isLoopbackAddress) {
                throw IOException("Local HTTP endpoint resolved outside loopback. Configure HTTPS instead.")
            }
        }
        return chain.proceed(request)
    }

    companion object {
        internal fun validate(request: okhttp3.Request) {
            if (!request.url.isHttps) {
                val loopback = request.url.host in setOf("localhost", "127.0.0.1", "::1")
                val credentials = request.url.username.isNotEmpty() || request.url.password.isNotEmpty() ||
                    request.headers.names().any { sensitiveName(it) } ||
                    request.url.queryParameterNames.any { sensitiveName(it) } ||
                    (request.body as? okhttp3.FormBody)?.let { form ->
                        (0 until form.size).any { sensitiveName(form.name(it)) }
                    } == true
                if (!loopback || credentials) throw IOException(
                    "This endpoint requires HTTPS. HTTP is available only for local endpoints without credentials.",
                )
            }
        }
        internal fun sensitiveName(name: String): Boolean {
            val normalized = name.lowercase().replace("-", "").replace("_", "")
            return listOf("authorization", "apikey", "token", "secret", "password", "cookie").any { it in normalized } ||
                normalized.endsWith("key") ||
                normalized == "key"
        }
    }
}
