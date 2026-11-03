package com.eminallahverdi.balance.core.data

import androidx.room.withTransaction
import com.eminallahverdi.balance.core.common.IoDispatcher
import com.eminallahverdi.balance.core.database.BalanceDatabase
import com.eminallahverdi.balance.core.database.dao.ReceiptDao
import com.eminallahverdi.balance.core.database.entity.ReceiptEntity
import com.eminallahverdi.balance.core.database.entity.ReceiptItemEntity
import com.eminallahverdi.balance.core.database.entity.ReceiptWithItems
import com.eminallahverdi.balance.core.model.ItemGroup
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.Receipt
import com.eminallahverdi.balance.core.model.ReceiptItem
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** What the tax service said about a fiscal ID. */
sealed interface ReceiptLookup {
    /** The receipt as the tax service draws it: a picture, to be read on the phone. */
    class Found(val image: ByteArray) : ReceiptLookup

    /** Not in the tax service's records yet. A receipt can take up to a week to get there. */
    data object NotFound : ReceiptLookup

    /** The tax service wants a person to prove themselves on its own page. */
    data object Refused : ReceiptLookup

    data object Offline : ReceiptLookup
}

@Singleton
class ReceiptRepository @Inject constructor(
    private val db: BalanceDatabase,
    private val dao: ReceiptDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    val receipts: Flow<List<Receipt>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun exists(fiscalId: String): Boolean = dao.exists(fiscalId)

    /** Saves a receipt and its lines together. */
    suspend fun save(receipt: Receipt): Long = db.withTransaction {
        val id = dao.insert(ReceiptEntity(0, receipt.store, receipt.date.toEpochDay(), receipt.total.minor, receipt.fiscalId, Instant.now().toEpochMilli()))
        dao.insertItems(
            receipt.items.mapIndexed { i, item ->
                ReceiptItemEntity(0, id, item.name, item.key, item.quantityMilli, item.price.minor, item.total.minor, item.group.name, i)
            },
        )
        id
    }

    suspend fun delete(id: Long) = dao.delete(id)

    /**
     * Asks the tax service's public receipt check for [fiscalId], the same request its own page makes. It answers with a
     * picture: the receipt when it has it, or a notice when it hasn't. Nothing about the user is sent.
     */
    suspend fun lookUp(fiscalId: String): ReceiptLookup = withContext(io) {
        try {
            val connection = URL("$EKASSA_DOCUMENTS$fiscalId").openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            try {
                val status = connection.responseCode
                val isImage = connection.contentType.orEmpty().startsWith("image/")
                when {
                    status == HttpURLConnection.HTTP_OK && isImage -> ReceiptLookup.Found(connection.inputStream.use { it.readBytes() })
                    // Its page shows a "not found" picture for these.
                    status == NOT_FOUND_STATUS || status == HttpURLConnection.HTTP_NOT_FOUND -> ReceiptLookup.NotFound
                    else -> ReceiptLookup.Refused
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: IOException) {
            ReceiptLookup.Offline
        }
    }

    companion object {
        private const val EKASSA_DOCUMENTS = "https://monitoring.e-kassa.gov.az/pks-monitoring/2.0.0/documents/"
        private const val TIMEOUT_MS = 15_000
        private const val NOT_FOUND_STATUS = 209

        /** The tax service's own check page for a receipt, for when the picture can't be fetched here. */
        fun checkPage(fiscalId: String): String = "https://monitoring.e-kassa.gov.az/#/index?doc=$fiscalId"
    }
}

private fun ReceiptWithItems.toModel() = Receipt(
    id = receipt.id,
    store = receipt.store,
    date = LocalDate.ofEpochDay(receipt.date),
    total = Money(receipt.totalMinor),
    fiscalId = receipt.fiscalId,
    items = items.sortedBy { it.position }.map {
        ReceiptItem(it.name, it.quantityMilli, Money(it.priceMinor), Money(it.totalMinor), runCatching { ItemGroup.valueOf(it.group) }.getOrDefault(ItemGroup.OTHER))
    },
)
