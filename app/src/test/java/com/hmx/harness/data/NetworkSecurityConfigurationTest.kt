package com.hmx.harness.data

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class NetworkSecurityConfigurationTest {
    @Test fun onlyExactLoopbackHostsPermitHttpAndOnlySystemCertificatesAreTrusted() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/xml/network_security_config.xml"))
        val base = doc.getElementsByTagName("base-config").item(0) as Element
        assertEquals("false", base.getAttribute("cleartextTrafficPermitted"))
        val nodes = doc.getElementsByTagName("domain")
        val domains = (0 until nodes.length).map { nodes.item(it) as Element }
        assertEquals(setOf("localhost", "127.0.0.1", "::1"), domains.map { it.textContent.trim() }.toSet())
        assertTrue(domains.all { it.getAttribute("includeSubdomains") == "false" })
        val certs = doc.getElementsByTagName("certificates")
        assertEquals(1, certs.length)
        assertEquals("system", (certs.item(0) as Element).getAttribute("src"))
    }

    @Test fun credentialHeaderAndQueryNamesAreRecognized() {
        listOf("Authorization", "Proxy-Authorization", "x-api-key", "api_key", "access_token",
            "refresh_token", "Cookie", "password", "key").forEach { assertTrue(it, EndpointSecurity.sensitiveName(it)) }
        assertFalse(EndpointSecurity.sensitiveName("Content-Type"))
    }
}
