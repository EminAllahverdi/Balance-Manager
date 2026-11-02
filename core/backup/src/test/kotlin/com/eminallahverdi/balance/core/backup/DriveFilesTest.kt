package com.eminallahverdi.balance.core.backup

import com.eminallahverdi.balance.core.model.AppEnvironment
import com.eminallahverdi.balance.core.model.DriveBackup
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class DriveFilesTest {
    @Test fun `reads Drive's file list, with or without a count`() {
        val body = """
            { "files": [
              { "id": "a", "createdTime": "2026-11-02T03:00:12.000Z", "appProperties": { "entries": "42" }, "extra": 1 },
              { "id": "b", "createdTime": "2026-11-01T03:00:00.000Z" }
            ] }
        """
        assertThat(parseBackups(body)).containsExactly(
            DriveBackup("a", Instant.parse("2026-11-02T03:00:12Z"), 42),
            DriveBackup("b", Instant.parse("2026-11-01T03:00:00Z"), null),
        ).inOrder()
    }

    @Test fun `an empty folder is an empty list`() {
        assertThat(parseBackups("{}")).isEmpty()
    }

    @Test fun `an upload goes in the hidden app folder and carries its count`() {
        val head = uploadHead("balance.zip", 7, AppEnvironment.PROD)
        assertThat(head).contains(""""parents":["appDataFolder"]""")
        assertThat(head).contains(""""entries":"7"""")
        assertThat(head).endsWith("Content-Type: application/zip\r\n\r\n")
    }

    @Test fun `an upload says which app made it, and reads back the same`() {
        assertThat(uploadHead("balance.zip", 7, AppEnvironment.DEV)).contains(""""environment":"DEV"""")
        val body = """{ "files": [ { "id": "a", "createdTime": "2026-11-02T03:00:00Z", "appProperties": { "environment": "DEV" } } ] }"""
        assertThat(parseBackups(body).single().environment).isEqualTo(AppEnvironment.DEV)
    }

    @Test fun `a backup from before Dev existed counts as the real data`() {
        val body = """{ "files": [ { "id": "a", "createdTime": "2026-11-02T03:00:00Z", "appProperties": { "entries": "42" } } ] }"""
        assertThat(parseBackups(body).single().environment).isEqualTo(AppEnvironment.PROD)
    }
}
