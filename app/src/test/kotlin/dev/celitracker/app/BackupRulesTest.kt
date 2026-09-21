package dev.celitracker.app

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class BackupRulesTest {

    private fun includes(resource: String, section: String?): List<Pair<String, String>> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/$resource"))
        val root = if (section == null) document.documentElement else document.getElementsByTagName(section).item(0) as Element
        val nodes = root.getElementsByTagName("include")
        return (0 until nodes.length).map { (nodes.item(it) as Element).let { include -> include.getAttribute("domain") to include.getAttribute("path") } }
    }

    private val database = "file" to DATABASE_FILE_NAME

    @Test
    fun `the API 30 rules back up the database file and nothing else`() {
        assertEquals(listOf(database), includes("backup_rules.xml", section = null))
    }

    @Test
    fun `cloud backup includes the database file and nothing else`() {
        assertEquals(listOf(database), includes("data_extraction_rules.xml", section = "cloud-backup"))
    }

    @Test
    fun `device transfer includes the database file and nothing else`() {
        assertEquals(listOf(database), includes("data_extraction_rules.xml", section = "device-transfer"))
    }
}
