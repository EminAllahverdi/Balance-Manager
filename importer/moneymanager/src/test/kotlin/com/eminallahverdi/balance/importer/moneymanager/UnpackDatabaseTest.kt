package com.eminallahverdi.balance.importer.moneymanager

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertThrows
import org.junit.Test

class UnpackDatabaseTest {
    private fun zip(vararg files: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            files.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun unpack(bytes: ByteArray) = ByteArrayOutputStream().also { unpackDatabase(ByteArrayInputStream(bytes), it) }.toString()

    @Test fun `finds the database behind the bytes in front of the zip`() {
        val backup = ByteArray(8) { 8 } + zip("MyFinance.db" to "the database", "backup_meta" to "{}")
        assertThat(unpack(backup)).isEqualTo("the database")
    }

    @Test fun `finds it wherever it sits in the zip`() {
        assertThat(unpack(zip("backup_meta" to "{}", "MyFinance.db" to "the database"))).isEqualTo("the database")
    }

    @Test fun `a file that isn't a backup is refused`() {
        assertThrows(IOException::class.java) { unpack("just some text".toByteArray()) }
    }

    @Test fun `a zip with no database is refused`() {
        assertThrows(IOException::class.java) { unpack(zip("notes.txt" to "hello")) }
    }
}
