package com.hmx.harness.data

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class BackupProtectionTest {
    private fun xml(name: String) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File("src/main/$name"))
    private val domains = setOf("root", "file", "database", "sharedpref", "external",
        "device_root", "device_file", "device_database", "device_sharedpref")

    private fun assertExcluded(element: Element) {
        val exclusions = element.getElementsByTagName("exclude")
        val actual = (0 until exclusions.length).map { exclusions.item(it) as Element }
        assertEquals(domains, actual.map { it.getAttribute("domain") }.toSet())
        assertTrue(actual.all { it.getAttribute("path") == "." })
        assertEquals(0, element.getElementsByTagName("include").length)
    }

    @Test fun manifestDisablesBackupAndReferencesBothRuleGenerations() {
        val app = xml("AndroidManifest.xml").getElementsByTagName("application").item(0) as Element
        val ns = "http://schemas.android.com/apk/res/android"
        assertEquals("false", app.getAttributeNS(ns, "allowBackup"))
        assertEquals("@xml/backup_rules", app.getAttributeNS(ns, "fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", app.getAttributeNS(ns, "dataExtractionRules"))
    }

    @Test fun legacyExcludesDatabaseSidecarsAttachmentsAndWorkspaces() {
        assertExcluded(xml("res/xml/backup_rules.xml").documentElement)
    }

    @Test fun cloudAndDeviceTransferExcludeEveryPrivateStorageDomain() {
        val rules = xml("res/xml/data_extraction_rules.xml")
        for (tag in listOf("cloud-backup", "device-transfer")) {
            assertEquals(1, rules.getElementsByTagName(tag).length)
            assertExcluded(rules.getElementsByTagName(tag).item(0) as Element)
        }
    }
}
