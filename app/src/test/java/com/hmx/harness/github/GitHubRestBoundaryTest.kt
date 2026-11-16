package com.hmx.harness.github

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class GitHubRestBoundaryTest {
    @Test fun githubCredentialStringNeverContainsTokens() {
        val credential = GitHubCredential("synthetic-access", "alice", refreshToken = "synthetic-refresh")
        assertFalse(credential.toString().contains("synthetic"))
    }

    @Test fun apiUsesApprovedOriginAndCurrentToken() = runBlocking {
        val headers = mutableListOf<String?>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("https", chain.request().url.scheme)
            assertEquals("api.github.com", chain.request().url.host)
            headers += chain.request().header("Authorization")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body("{}".toResponseBody()).build()
        }.build()
        val api = GitHubApi(client)
        api.get("/user", null); api.get("/user", "synthetic-one"); api.get("/user", "synthetic-two")
        assertEquals(listOf(null, "Bearer synthetic-one", "Bearer synthetic-two"), headers)
    }

    @Test fun redirectsAndRejectedCredentialsReturnSanitizedErrors() = runBlocking {
        for (code in listOf(302, 401, 403)) {
            var calls = 0
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                calls++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code)
                    .message("Rejected").header("Location", "https://unapproved.example/")
                    .body("""{"message":"synthetic-secret"}""".toResponseBody()).build()
            }.build()
            try { GitHubApi(client).get("/user", "synthetic-secret"); fail("accepted rejection") }
            catch (error: GitHubHttpError) { assertFalse(error.message.orEmpty().contains("synthetic-secret")) }
            assertEquals(1, calls)
        }
    }

    @Test fun malformedPathCannotChangeOrigin() = runBlocking {
        var called = false
        val client = OkHttpClient.Builder().addInterceptor { called = true; error("unexpected network") }.build()
        try { GitHubApi(client).get("@unapproved.example/user", "synthetic"); fail("accepted origin") }
        catch (_: IllegalArgumentException) { }
        assertFalse(called)
    }
}
