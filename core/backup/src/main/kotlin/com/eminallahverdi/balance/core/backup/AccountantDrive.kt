package com.eminallahverdi.balance.core.backup

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val FILES = "https://www.googleapis.com/drive/v3/files"
private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
private const val BOUNDARY = "cibim-accountant-boundary"
private const val TAG_KEY = "cibim"
private const val CONNECT_MILLIS = 20_000
private const val READ_MILLIS = 60_000

internal const val FOLDER_TYPE = "application/vnd.google-apps.folder"
internal const val DOC_TYPE = "application/vnd.google-apps.document"
internal const val SHEET_TYPE = "application/vnd.google-apps.spreadsheet"

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class Found(val files: List<FoundFile> = emptyList())

@Serializable
private data class FoundFile(val id: String)

@Serializable
private data class Created(val id: String)

@Serializable
private data class NewItem(val name: String, val mimeType: String, val parents: List<String>? = null, val appProperties: Map<String, String>)

/**
 * Where a file of the accountant's copy is found again: by a tag it was made with, not its name, so renaming it in Drive
 * doesn't lose it. One in the bin counts as gone, and is made again.
 */
internal fun findQuery(tag: String): String =
    "?q=" + URLEncoder.encode("appProperties has { key='$TAG_KEY' and value='$tag' } and trashed=false", "UTF-8") + "&spaces=drive&fields=files(id)"

internal fun newItemJson(name: String, mimeType: String, parent: String?, tag: String): String =
    json.encodeToString(NewItem(name, mimeType, parent?.let { listOf(it) }, mapOf(TAG_KEY to tag)))

internal fun parseFound(body: String): String? = json.decodeFromString<Found>(body).files.firstOrNull()?.id

/**
 * The visible part of the user's Drive that the accountant reads, through the `drive.file` scope: it lets the app see only
 * files it made itself, never the rest of the Drive. A page is uploaded as a Google Doc, and comma-separated rows as a
 * Google Sheet, which Drive converts on upload and again on each update, keeping the same file and link.
 */
@Singleton
class AccountantDrive @Inject constructor() {
    fun find(token: String, tag: String): String? = parseFound(connect("$FILES${findQuery(tag)}", token).readAll())

    fun createFolder(token: String, name: String, tag: String): String {
        val connection = connect(FILES, token, "POST")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        connection.outputStream.use { it.write(newItemJson(name, FOLDER_TYPE, null, tag).toByteArray()) }
        return json.decodeFromString<Created>(connection.readAll()).id
    }

    /** A new Doc or Sheet in [folder], from [content] of [contentType] (text/html or text/csv). */
    fun createConverted(token: String, name: String, googleType: String, folder: String, tag: String, contentType: String, content: ByteArray): String {
        val head = "--$BOUNDARY\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${newItemJson(name, googleType, folder, tag)}\r\n--$BOUNDARY\r\nContent-Type: $contentType\r\n\r\n".toByteArray()
        val tail = "\r\n--$BOUNDARY--\r\n".toByteArray()
        val connection = connect("$UPLOAD?uploadType=multipart&fields=id", token, "POST")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=$BOUNDARY")
        connection.setFixedLengthStreamingMode(head.size + content.size + tail.size)
        connection.outputStream.use { out ->
            out.write(head)
            out.write(content)
            out.write(tail)
        }
        return json.decodeFromString<Created>(connection.readAll()).id
    }

    /** New content for a Doc or Sheet made by [createConverted]: the same file, so its link and place stay. */
    fun replaceContent(token: String, id: String, contentType: String, content: ByteArray) {
        // HttpURLConnection can't send PATCH, and Google's APIs take it as a POST that says so.
        val connection = connect("$UPLOAD/$id?uploadType=media&fields=id", token, "POST")
        connection.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", contentType)
        connection.setFixedLengthStreamingMode(content.size)
        connection.outputStream.use { it.write(content) }
        connection.readAll()
    }

    private fun connect(url: String, token: String, method: String = "GET") = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = CONNECT_MILLIS
        readTimeout = READ_MILLIS
        setRequestProperty("Authorization", "Bearer $token")
    }

    private fun HttpURLConnection.readAll(): String {
        if (responseCode !in 200..299) throw IOException("Drive said $responseCode")
        return inputStream.use { it.readBytes().decodeToString() }
    }
}
