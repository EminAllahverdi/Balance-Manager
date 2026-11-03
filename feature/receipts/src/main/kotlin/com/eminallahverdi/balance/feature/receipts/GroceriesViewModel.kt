package com.eminallahverdi.balance.feature.receipts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.ReceiptLookup
import com.eminallahverdi.balance.core.data.ReceiptRepository
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.CategoryType
import com.eminallahverdi.balance.core.model.DateRange
import com.eminallahverdi.balance.core.model.GroceryReport
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.ParsedReceipt
import com.eminallahverdi.balance.core.model.Receipt
import com.eminallahverdi.balance.core.model.TextPiece
import com.eminallahverdi.balance.core.model.TransactionKind
import com.eminallahverdi.balance.core.model.fiscalIdFrom
import com.eminallahverdi.balance.core.model.groceryReport
import com.eminallahverdi.balance.core.model.parseReceiptText
import com.eminallahverdi.balance.core.model.rowsFrom
import com.eminallahverdi.balance.core.model.sum
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** Where adding a receipt stands. Each step says what's happening, and a problem says what to do about it. */
sealed interface ScanState {
    data object Idle : ScanState
    data object Choosing : ScanState
    data object TypingId : ScanState
    data class Working(val message: String) : ScanState
    data class Review(val parsed: ParsedReceipt, val store: String, val fiscalId: String?, val picture: Bitmap?) : ScanState
    data class Problem(val message: String, val fiscalId: String? = null) : ScanState

    /** Saved. [needsEntry] when no grocery entry has its total yet, so adding one is offered. */
    data class Saved(val receipt: Receipt, val needsEntry: Boolean) : ScanState
}

data class GroceriesUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val category: Category? = null,
    val report: GroceryReport? = null,
    val receipts: List<Receipt> = emptyList(),
    val scan: ScanState = ScanState.Idle,
    val open: Receipt? = null,
)

private val GROCERY_NAMES = setOf("groceries", "grocery", "market", "supermarket", "ərzaq", "erzaq", "продукты")

/** The category the user keeps groceries in: by its name, or by the shopping cart the template gives it. */
internal fun groceriesCategory(categories: List<Category>): Category? {
    val top = categories.filter { it.parentId == null && it.type == CategoryType.EXPENSE && !it.isArchived }
    return top.firstOrNull { it.name.trim().lowercase() in GROCERY_NAMES } ?: top.firstOrNull { it.icon == "shopping_cart" }
}

private const val MONTHS_COMPARED = 12L

@HiltViewModel
class GroceriesViewModel @Inject constructor(
    private val receipts: ReceiptRepository,
    private val transactions: TransactionRepository,
    catalog: CatalogRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val scan = MutableStateFlow<ScanState>(ScanState.Idle)
    private val open = MutableStateFlow<Receipt?>(null)

    private val entries = month.flatMapLatest { m -> transactions.observeDetails(DateRange(m.minusMonths(MONTHS_COMPARED).atDay(1), m.atEndOfMonth())) }

    val state: StateFlow<GroceriesUiState> = combine(
        combine(month, catalog.categories, entries) { m, c, e -> Triple(m, c, e) },
        receipts.receipts,
        scan,
        open,
    ) { (m, categories, rows), all, scanState, opened ->
        val groceries = groceriesCategory(categories)
        // Sub-categories count towards their parent, as they do everywhere else.
        val spent = rows.filter { row ->
            row.transaction.kind == TransactionKind.EXPENSE && groceries != null && (row.category?.id == groceries.id || row.parentCategory?.id == groceries.id)
        }.groupBy { YearMonth.from(it.transaction.date) }.mapValues { (_, list) -> list.map { it.transaction.amount }.sum() }
        GroceriesUiState(
            loading = false,
            month = m,
            category = groceries,
            report = groceryReport(m, spent, all),
            receipts = all.filter { YearMonth.from(it.date) == m },
            scan = scanState,
            open = opened,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroceriesUiState())

    fun showMonth(next: YearMonth) {
        if (!next.isAfter(YearMonth.now())) month.value = next
    }

    fun startScan() = scan.update { ScanState.Choosing }
    fun typeId() = scan.update { ScanState.TypingId }
    fun closeScan() = scan.update { ScanState.Idle }
    fun openReceipt(receipt: Receipt?) = open.update { receipt }

    /** What the QR code held, or what was typed: the fiscal ID, alone or in the tax service's link. */
    fun lookUp(scannedOrTyped: String) {
        val fiscalId = fiscalIdFrom(scannedOrTyped)
        if (fiscalId == null) {
            scan.value = ScanState.Problem("That isn't an e-kassa receipt's code. Its fiscal ID has 12 letters and digits.")
            return
        }
        viewModelScope.launch {
            if (receipts.exists(fiscalId)) {
                scan.value = ScanState.Problem("This receipt is saved already.")
                return@launch
            }
            scan.value = ScanState.Working("Asking the tax service for the receipt…")
            scan.value = when (val found = receipts.lookUp(fiscalId)) {
                is ReceiptLookup.Found -> read(BitmapFactory.decodeByteArray(found.image, 0, found.image.size), fiscalId)
                ReceiptLookup.NotFound -> ScanState.Problem("The tax service doesn't have this receipt yet. A receipt can take up to a week to get there.", fiscalId)
                ReceiptLookup.Refused -> ScanState.Problem("The tax service wants this checked on its own page.", fiscalId)
                ReceiptLookup.Offline -> ScanState.Problem("Couldn't reach the tax service. Check the connection and try again.", fiscalId)
            }
        }
    }

    /** A photo or screenshot of the receipt, read on the phone. */
    fun readPhoto(uri: Uri) {
        viewModelScope.launch {
            scan.value = ScanState.Working("Reading the receipt…")
            val bitmap = runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } }.getOrNull()
            scan.value = if (bitmap == null) ScanState.Problem("Couldn't open that picture.") else read(bitmap, fiscalId = null)
        }
    }

    private suspend fun read(bitmap: Bitmap?, fiscalId: String?): ScanState {
        if (bitmap == null) return ScanState.Problem("The receipt's picture couldn't be opened.", fiscalId)
        scan.value = ScanState.Working("Reading the receipt…")
        val pieces = runCatching { recognise(bitmap) }.getOrElse { return ScanState.Problem("Couldn't read the receipt on this phone. Google Play services may still be getting the text reader.", fiscalId) }
        val parsed = parseReceiptText(rowsFrom(pieces))
        if (parsed.items.isEmpty()) return ScanState.Problem("No items could be read from this receipt.", fiscalId ?: parsed.fiscalId)
        return ScanState.Review(parsed, parsed.store.orEmpty(), fiscalId ?: parsed.fiscalId, bitmap)
    }

    /** Text recognition on the phone, line by line with where each line sits, so columns can be put back into rows. */
    private suspend fun recognise(bitmap: Bitmap): List<TextPiece> = suspendCancellableCoroutine { done ->
        val reader = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        reader.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text ->
                done.resume(text.textBlocks.flatMap { it.lines }.mapNotNull { line -> line.boundingBox?.let { TextPiece(it.left, it.top, it.right, it.bottom, line.text) } })
                reader.close()
            }
            .addOnFailureListener {
                done.resumeWithException(it)
                reader.close()
            }
    }

    fun renameStore(name: String) = scan.update { (it as? ScanState.Review)?.copy(store = name) ?: it }

    fun removeItem(index: Int) = scan.update { s ->
        (s as? ScanState.Review)?.let { review -> review.copy(parsed = review.parsed.copy(items = review.parsed.items.filterIndexed { i, _ -> i != index })) } ?: s
    }

    fun save() {
        val review = scan.value as? ScanState.Review ?: return
        val parsed = review.parsed
        val receipt = Receipt(
            id = 0,
            store = review.store.trim().ifEmpty { "Shop" },
            date = parsed.date ?: LocalDate.now(),
            total = parsed.total ?: parsed.items.map { it.total }.sum(),
            fiscalId = review.fiscalId,
            items = parsed.items,
        )
        viewModelScope.launch {
            val id = receipts.save(receipt)
            scan.value = ScanState.Saved(receipt.copy(id = id), needsEntry = !hasEntryFor(receipt))
        }
    }

    /** An expense for the receipt's total within a day of it, in any category: then it's been logged already. */
    private suspend fun hasEntryFor(receipt: Receipt): Boolean =
        transactions.observeDetails(DateRange(receipt.date.minusDays(1), receipt.date.plusDays(1))).first()
            .any { it.transaction.kind == TransactionKind.EXPENSE && it.transaction.amount == receipt.total }

    fun delete(receipt: Receipt) {
        open.value = null
        viewModelScope.launch { receipts.delete(receipt.id) }
    }

    /** The receipt's total as it would go into quick add. */
    fun entryFor(receipt: Receipt): ReceiptEntry = ReceiptEntry(receipt.total, receipt.store, receipt.date, state.value.category?.id)
}

/** What quick add opens with for a receipt: nothing is saved until the user presses Save there. */
data class ReceiptEntry(val total: Money, val store: String, val date: LocalDate, val categoryId: Long?)
