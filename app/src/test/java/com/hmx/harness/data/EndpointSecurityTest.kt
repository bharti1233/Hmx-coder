package com.hmx.harness.data

import java.io.IOException
import okhttp3.FormBody
import okhttp3.Request
import org.junit.Assert.assertThrows
import org.junit.Test

class EndpointSecurityTest {
    @Test fun exactLoopbacksAndHttpsPassPolicy() {
        listOf("http://localhost:3000/", "http://127.0.0.1:11434/", "http://[::1]:3000/",
            "https://api.github.com/user").forEach { EndpointSecurity.validate(Request.Builder().url(it).build()) }
    }

    @Test fun remoteLanAndLoopbackLookalikesFail() {
        listOf("http://192.168.1.2/", "http://example.com/", "http://localhost.example.com/",
            "http://127.0.0.1.example.com/", "http://localhost@evil.example/", "http://127.0.0.2/").forEach {
            assertThrows(IOException::class.java) { EndpointSecurity.validate(Request.Builder().url(it).build()) }
        }
    }

    @Test fun credentialsFailEvenOnLoopback() {
        for (name in listOf("Authorization", "x-api-key", "Cookie", "x-custom-secret")) {
            assertThrows(IOException::class.java) {
                EndpointSecurity.validate(Request.Builder().url("http://localhost/").header(name, "synthetic").build())
            }
        }
        assertThrows(IOException::class.java) {
            EndpointSecurity.validate(Request.Builder().url("http://localhost/?access_token=synthetic").build())
        }
        assertThrows(IOException::class.java) {
            EndpointSecurity.validate(Request.Builder().url("http://localhost/")
                .post(FormBody.Builder().add("refresh_token", "synthetic").build()).build())
        }
    }
}
