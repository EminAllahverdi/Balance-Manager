package com.eminallahverdi.balance.core.backup

import com.eminallahverdi.balance.core.model.DriveBackup
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val API = "https://www.googleapis.com/drive/v3/files"
private const val UPLOAD_API = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
private const val LIST_QUERY = "?spaces=appDataFolder&pageSize=100&fields=files(id,createdTime,appProperties)"
private const val BOUNDARY = "balance-backup-boundary"
private const val ENTRIES_PROPERTY = "entries"
private const val CONNECT_MILLIS = 20_000
private const val READ_MILLIS = 60_000

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class FileList(val files: List<RemoteFile> = emptyList())

@Serializable
private data class RemoteFile(val id: String, val createdTime: String, val appProperties: Map<String, String> = emptyMap())

@Serializable
private data class NewFile(val name: String, val parents: List<String>, val appProperties: Map<String, String>)

internal fun parseBackups(body: String): List<DriveBackup> = json.decodeFromString<FileList>(body).files.map {
    DriveBackup(it.id, Instant.parse(it.createdTime), it.appProperties[ENTRIES_PROPERTY]?.toIntOrNull())
}

/** The part of a multipart upload that comes before the file. The folder is Drive's hidden one, which only this app can see. */
internal fun uploadHead(name: String, entries: Int): String {
    val metadata = json.encodeToString(NewFile(name, listOf("appDataFolder"), mapOf(ENTRIES_PROPERTY to entries.toString())))
    return "--$BOUNDARY\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$BOUNDARY\r\nContent-Type: application/zip\r\n\r\n"
}

internal fun uploadTail(): String = "\r\n--$BOUNDARY--\r\n"

/** Google Drive's REST API, which is all this needs. Its client library would add megabytes to do the same four calls. */
@Singleton
class DriveFiles @Inject constructor() {
    fun list(token: String): List<DriveBackup> = connect("$API$LIST_QUERY", token).readAll().let(::parseBackups)

    fun upload(token: String, file: File, name: String, entries: Int) {
        val head = uploadHead(name, entries).toByteArray()
        val tail = uploadTail().toByteArray()
        val connection = connect(UPLOAD_API, token, "POST")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=$BOUNDARY")
        connection.setFixedLengthStreamingMode(head.size + file.length() + tail.size)
        connection.outputStream.use { out ->
            out.write(head)
            file.inputStream().use { it.copyTo(out) }
            out.write(tail)
        }
        connection.readAll()
    }

    fun download(token: String, id: String, target: File) {
        val connection = connect("$API/$id?alt=media", token)
        connection.ensureOk()
        connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
    }

    fun delete(token: String, id: String) {
        connect("$API/$id", token, "DELETE").ensureOk()
    }

    private fun connect(url: String, token: String, method: String = "GET") = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = CONNECT_MILLIS
        readTimeout = READ_MILLIS
        setRequestProperty("Authorization", "Bearer $token")
    }

    private fun HttpURLConnection.ensureOk() {
        if (responseCode !in 200..299) throw IOException("Drive said $responseCode")
    }

    private fun HttpURLConnection.readAll(): String {
        ensureOk()
        return inputStream.use { it.readBytes().decodeToString() }
    }
}
