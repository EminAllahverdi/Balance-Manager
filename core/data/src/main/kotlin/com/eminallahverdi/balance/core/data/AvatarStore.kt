package com.eminallahverdi.balance.core.data

import android.content.Context
import android.net.Uri
import com.eminallahverdi.balance.core.common.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** People's photos. Copied into app storage because the Photo Picker's access doesn't outlive the app process. */
@Singleton
class AvatarStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val dir: File get() = File(context.filesDir, "avatars").apply { mkdirs() }

    /** Returns the stored copy's URI, or null if the picture can't be read. */
    suspend fun copy(source: Uri): String? = withContext(io) {
        val target = File(dir, UUID.randomUUID().toString())
        runCatching {
            val input = context.contentResolver.openInputStream(source) ?: error("Unreadable: $source")
            input.use { i -> target.outputStream().use { o -> i.copyTo(o) } }
            Uri.fromFile(target).toString()
        }.onFailure { target.delete() }.getOrNull()
    }

    suspend fun delete(uri: String) = withContext(io) {
        Uri.parse(uri).path?.let(::File)?.takeIf { it.parentFile == dir }?.delete()
        Unit
    }
}
