package ani.saikou.neo.shared.sources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceListTest {
    private val starter = ListedSource("com.example.starter", "Starter source", "1.2.0", 1, "https://example.com/starter.apk")

    private fun entry(
        id: String = "\"com.example.starter\"",
        name: String = "\"Starter source\"",
        version: String = "\"1.2.0\"",
        api: String = "1",
        apk: String = "\"https://example.com/starter.apk\"",
    ) = """{ "id": $id, "name": $name, "version": $version, "api": $api, "apk": $apk }"""

    private fun list(vararg entries: String) = """{ "sources": [ ${entries.joinToString()} ] }""".encodeToByteArray()

    @Test
    fun readsEachEntry() {
        assertEquals(listOf(starter), readSourceList(list(entry())))
    }

    @Test
    fun ignoresFieldsItDoesNotKnow() {
        val text = """{ "name": "My sources", "sources": [ { "icon": { "x": 1 }, ${entry().drop(1)} ] }"""

        assertEquals(listOf(starter), readSourceList(text.encodeToByteArray()))
    }

    @Test
    fun skipsEntriesWithAMissingOrWrongField() {
        val entries =
            list(
                """{ "id": "com.example.a", "name": "A", "version": "1.0.0", "apk": "https://example.com/a.apk" }""",
                entry(id = "\"not a package\""),
                entry(name = "\"  \""),
                entry(api = "\"one\""),
                entry(apk = "\"http://example.com/starter.apk\""),
                entry(apk = "\"ftp://example.com/starter.apk\""),
                entry(),
            )

        assertEquals(listOf(starter), readSourceList(entries))
    }

    @Test
    fun keepsTheFirstEntryForAnId() {
        val again = entry(name = "\"Another\"")

        assertEquals(listOf(starter), readSourceList(list(entry(), again)))
    }

    @Test
    fun allowsPlainHttpAtHome() {
        val home = readSourceList(list(entry(apk = "\"http://192.168.1.20:8000/starter.apk\"")))

        assertEquals("http://192.168.1.20:8000/starter.apk", home?.single()?.apk)
    }

    @Test
    fun readsAtMostFiftyEntries() {
        val entries = (1..60).map { entry(id = "\"com.example.s$it\"") }.toTypedArray()

        assertEquals(50, readSourceList(list(*entries))?.size)
    }

    @Test
    fun shortensLongNamesAndVersions() {
        val long = "x".repeat(200)
        val read = readSourceList(list(entry(name = "\"$long\"", version = "\"$long\"")))!!.single()

        assertEquals(60, read.name.length)
        assertEquals(60, read.version.length)
    }

    @Test
    fun aListWithNoGoodEntriesIsEmpty() {
        assertEquals(emptyList(), readSourceList(list()))
        assertEquals(emptyList(), readSourceList(list(entry(id = "\"x\""))))
    }

    @Test
    fun aBrokenListIsNull() {
        assertNull(readSourceList("{ \"sources\": [ { \"id\": ".encodeToByteArray()))
        assertNull(readSourceList("{ \"sources\": \"none\" }".encodeToByteArray()))
        assertNull(readSourceList("{ \"other\": [] }".encodeToByteArray()))
        assertNull(readSourceList("[ ]".encodeToByteArray()))
    }

    @Test
    fun aFileStartingWithABraceIsAList() {
        assertTrue(isSourceList("  \n{ \"sources\": [] }".encodeToByteArray()))
        assertTrue(isSourceList("﻿{}".encodeToByteArray()))
        assertFalse(isSourceList(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertFalse(isSourceList("<html>".encodeToByteArray()))
        assertFalse(isSourceList(ByteArray(0)))
    }
}
