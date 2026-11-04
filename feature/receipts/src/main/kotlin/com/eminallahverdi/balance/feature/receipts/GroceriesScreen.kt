package com.eminallahverdi.balance.feature.receipts

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.RiceBowl
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.data.ReceiptRepository
import com.eminallahverdi.balance.core.designsystem.component.BtCard
import com.eminallahverdi.balance.core.designsystem.component.BtIconButton
import com.eminallahverdi.balance.core.designsystem.component.BtTextField
import com.eminallahverdi.balance.core.designsystem.component.ChoiceSheet
import com.eminallahverdi.balance.core.designsystem.component.FittedAmount
import com.eminallahverdi.balance.core.designsystem.component.FloatingAddButton
import com.eminallahverdi.balance.core.designsystem.component.OptionRow
import com.eminallahverdi.balance.core.designsystem.component.Overline
import com.eminallahverdi.balance.core.designsystem.component.PrimaryButton
import com.eminallahverdi.balance.core.designsystem.component.toColor
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.designsystem.theme.CategoryPalette
import com.eminallahverdi.balance.core.designsystem.theme.TILE_TINT
import com.eminallahverdi.balance.core.model.GroceryReport
import com.eminallahverdi.balance.core.model.ItemGroup
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.REPORT_SPANS
import com.eminallahverdi.balance.core.model.Receipt
import com.eminallahverdi.balance.core.model.ReceiptItem
import com.eminallahverdi.balance.core.model.percentChange
import com.eminallahverdi.balance.core.model.sum
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

/**
 * What groceries came to this month, against the months before, and what they went on, from the receipts scanned.
 * [onAddExpense] opens quick add filled in with a receipt the entries don't have yet.
 */
@Composable
fun GroceriesRoute(onBack: () -> Unit, onAddExpense: (ReceiptEntry) -> Unit, viewModel: GroceriesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = BtTheme.colors
    Box(Modifier.fillMaxSize().background(c.bgCanvas)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BtIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                Text("Groceries", style = BtTheme.type.titleLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
            }
            MonthSwitcher(state.month, onChange = viewModel::showMonth)
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val report = state.report
                if (!state.loading && report != null) {
                    item(key = "spent") { SpentCard(report, state.category?.name) }
                    if (report.receipts == 0) {
                        item(key = "hint") { HintCard() }
                    } else {
                        item(key = "groups") { GroupsCard(report) }
                        item(key = "items") { TopItemsCard(report) }
                    }
                    if (state.receipts.isNotEmpty()) {
                        item(key = "receipts") { Overline("Receipts", Modifier.padding(start = 4.dp, top = 4.dp)) }
                        items(state.receipts, key = { it.id }) { receipt -> ReceiptRow(receipt) { viewModel.openReceipt(receipt) } }
                    }
                }
            }
        }
        FloatingAddButton("Add a receipt", viewModel::startScan, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp))
    }
    ScanFlow(state.scan, viewModel, onAddExpense)
    state.open?.let { ReceiptSheet(it, onClose = { viewModel.openReceipt(null) }, onDelete = { viewModel.delete(it) }) }
}

@Composable
private fun MonthSwitcher(month: YearMonth, onChange: (YearMonth) -> Unit) {
    val c = BtTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(BtShapes.full).clickable { onChange(month.minusMonths(1)) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ChevronLeft, "Month before", tint = c.textSecondary)
        }
        Text(month.format(MONTH), style = BtTheme.type.bodyStrong, color = c.textPrimary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        val canGoOn = month.isBefore(YearMonth.now())
        Box(Modifier.size(44.dp).clip(BtShapes.full).clickable(enabled = canGoOn) { onChange(month.plusMonths(1)) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ChevronRight, "Month after", tint = if (canGoOn) c.textSecondary else c.textMuted.copy(alpha = 0.3f))
        }
    }
}

/** +12% in the spending colour when it's more, −8% in the income colour when it's less. */
@Composable
private fun Change(now: Money, before: Money?) {
    val c = BtTheme.colors
    val change = percentChange(now, before) ?: return Text("–", style = BtTheme.type.caption, color = c.textMuted)
    Text(
        when {
            change > 0 -> "+$change%"
            change < 0 -> "−${-change}%"
            else -> "same"
        },
        style = BtTheme.type.caption,
        color = when {
            change > 0 -> c.moneyExpense
            change < 0 -> c.moneyIncome
            else -> c.textSecondary
        },
    )
}

@Composable
private fun SpentCard(report: GroceryReport, categoryName: String?) {
    val c = BtTheme.colors
    BtCard(bordered = true) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Overline(categoryName?.let { "Spent on $it" } ?: "No groceries category found")
            FittedAmount(MoneyFormat.format(report.spent), BtTheme.type.amountLarge)
            CompareRow("Last month", report.lastMonth, report.spent)
            REPORT_SPANS.forEach { span -> CompareRow("$span-month average", report.averages[span], report.spent) }
        }
    }
}

@Composable
private fun CompareRow(label: String, before: Money?, now: Money) {
    val c = BtTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = BtTheme.type.body, color = c.textSecondary, modifier = Modifier.weight(1f))
        Text(before?.let(MoneyFormat::format) ?: "–", style = BtTheme.type.amountSmall, color = c.textPrimary, modifier = Modifier.padding(end = 12.dp))
        Box(Modifier.size(width = 52.dp, height = 20.dp), contentAlignment = Alignment.CenterEnd) { Change(now, before) }
    }
}

@Composable
private fun HintCard() {
    val c = BtTheme.colors
    BtCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("What did it go on?", style = BtTheme.type.bodyStrong, color = c.textPrimary)
            Text("Add the receipts from your grocery shopping, and this shows what you spent most on, by item and by kind, against the months before.", style = BtTheme.type.caption, color = c.textSecondary)
        }
    }
}

@Composable
private fun GroupsCard(report: GroceryReport) {
    val c = BtTheme.colors
    val largest = report.groups.maxOfOrNull { it.amount.minor }?.takeIf { it > 0 } ?: 1L
    BtCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Overline("${MoneyFormat.format(report.itemsTotal)} from ${if (report.receipts == 1) "1 receipt" else "${report.receipts} receipts"}")
            report.groups.forEach { spend ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GroupTile(spend.group)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(spend.group.label, style = BtTheme.type.body, color = c.textPrimary, modifier = Modifier.weight(1f))
                            Text(MoneyFormat.format(spend.amount), style = BtTheme.type.amountSmall, color = c.textPrimary)
                        }
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(BtShapes.full).background(c.bgSurfaceHigh)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(spend.amount.minor.toFloat() / largest).clip(BtShapes.full).background(spend.group.colour.toColor()))
                        }
                    }
                    Box(Modifier.size(width = 48.dp, height = 20.dp), contentAlignment = Alignment.CenterEnd) { Change(spend.amount, spend.before) }
                }
            }
        }
    }
}

@Composable
private fun TopItemsCard(report: GroceryReport) {
    val c = BtTheme.colors
    BtCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Overline("Most spent on")
            report.topItems.forEachIndexed { i, item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${i + 1}", style = BtTheme.type.caption, color = c.textMuted, modifier = Modifier.size(width = 18.dp, height = 18.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.name, style = BtTheme.type.body, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("×${quantityLabel(item.quantityMilli)} · ${if (item.times == 1) "once" else "${item.times} times"}", style = BtTheme.type.caption, color = c.textSecondary)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(MoneyFormat.format(item.amount), style = BtTheme.type.amountSmall, color = c.textPrimary)
                        Change(item.amount, item.before)
                    }
                }
            }
        }
    }
}

/** 3 for pieces, 2.45 for what's weighed. */
private fun quantityLabel(milli: Long): String =
    if (milli % 1000 == 0L) "${milli / 1000}" else java.math.BigDecimal(milli).movePointLeft(3).stripTrailingZeros().toPlainString()

private val ItemGroup.icon: ImageVector
    get() = when (this) {
        ItemGroup.DAIRY -> Icons.Rounded.Egg
        ItemGroup.MEAT_FISH -> Icons.Rounded.SetMeal
        ItemGroup.BREAD -> Icons.Rounded.BakeryDining
        ItemGroup.FRUIT_VEG -> Icons.Rounded.Eco
        ItemGroup.STAPLES -> Icons.Rounded.RiceBowl
        ItemGroup.DRINKS -> Icons.Rounded.LocalDrink
        ItemGroup.SWEETS -> Icons.Rounded.Cake
        ItemGroup.HOUSEHOLD -> Icons.Rounded.CleaningServices
        ItemGroup.OTHER -> Icons.Rounded.ShoppingBasket
    }

private val ItemGroup.colour: Long get() = CategoryPalette[(ordinal * 3) % CategoryPalette.size]

@Composable
private fun GroupTile(group: ItemGroup) {
    val hue = group.colour.toColor()
    Box(Modifier.size(36.dp).clip(BtShapes.md).background(hue.copy(alpha = TILE_TINT)), contentAlignment = Alignment.Center) {
        Icon(group.icon, null, tint = hue, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ReceiptRow(receipt: Receipt, onClick: () -> Unit) {
    val c = BtTheme.colors
    BtCard {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GroupTile(ItemGroup.OTHER)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(receipt.store, style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${DateLabels.relative(receipt.date)} · ${if (receipt.items.size == 1) "1 item" else "${receipt.items.size} items"}", style = BtTheme.type.caption, color = c.textSecondary)
            }
            Text(MoneyFormat.format(receipt.total), style = BtTheme.type.amountSmall, color = c.textPrimary)
        }
    }
}

@Composable
private fun ItemLine(item: ReceiptItem, onRemove: (() -> Unit)? = null) {
    val c = BtTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GroupTile(item.group)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.name, style = BtTheme.type.body, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${quantityLabel(item.quantityMilli)} × ${MoneyFormat.format(item.price)}", style = BtTheme.type.caption, color = c.textSecondary)
        }
        Text(MoneyFormat.format(item.total), style = BtTheme.type.amountSmall, color = c.textPrimary)
        if (onRemove != null) {
            Box(Modifier.size(36.dp).clip(BtShapes.full).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, "Leave out ${item.name}", tint = c.textMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- adding a receipt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanFlow(scan: ScanState, vm: GroceriesViewModel, onAddExpense: (ReceiptEntry) -> Unit) {
    val context = LocalContext.current
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.readPhoto(uri) else vm.closeScan() }
    fun scanCode() {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { code -> vm.lookUp(code.rawValue.orEmpty()) }
            .addOnCanceledListener { vm.closeScan() }
            .addOnFailureListener { vm.typeId() }
    }
    fun openCheckPage(fiscalId: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ReceiptRepository.checkPage(fiscalId))))
        vm.closeScan()
    }
    when (scan) {
        ScanState.Idle -> Unit
        ScanState.Choosing -> ChoiceSheet("Add a receipt", onClose = vm::closeScan) { _ ->
            OptionRow("Scan its QR code", selected = false, onClick = ::scanCode, caption = "Gets the receipt from the tax service, every item on it")
            OptionRow("Type its fiscal ID", selected = false, onClick = vm::typeId, caption = "The 12 letters and digits under the QR code")
            OptionRow("Read a photo of it", selected = false, onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, caption = "For one the tax service doesn't have yet")
        }
        ScanState.TypingId -> {
            var typed by rememberSaveable { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = vm::closeScan,
                title = { Text("Fiscal ID") },
                text = { BtTextField(typed, "12 letters and digits", { typed = it.trim() }) },
                confirmButton = { TextButton(onClick = { vm.lookUp(typed) }, enabled = typed.length >= 12) { Text("Look up") } },
                dismissButton = { TextButton(onClick = vm::closeScan) { Text("Cancel") } },
            )
        }
        is ScanState.Working -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = BtTheme.colors.accentFill, strokeWidth = 3.dp)
                    Text(scan.message)
                }
            },
        )
        is ScanState.Problem -> AlertDialog(
            onDismissRequest = vm::closeScan,
            title = { Text("Couldn't add it") },
            text = { Text(scan.message) },
            confirmButton = {
                val fiscalId = scan.fiscalId
                if (fiscalId != null) TextButton(onClick = { openCheckPage(fiscalId) }) { Text("Open its page") } else TextButton(onClick = vm::closeScan) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Use a photo") } },
        )
        is ScanState.Review -> ReviewSheet(scan, vm)
        is ScanState.Saved -> if (scan.needsEntry) {
            val receipt = scan.receipt
            AlertDialog(
                onDismissRequest = vm::closeScan,
                title = { Text("Add it as an expense?") },
                text = { Text("${MoneyFormat.format(receipt.total)} at ${receipt.store}, ${DateLabels.relative(receipt.date).inSentence()}, isn't in your entries yet.") },
                confirmButton = { TextButton(onClick = { onAddExpense(vm.entryFor(receipt)); vm.closeScan() }) { Text("Add expense") } },
                dismissButton = { TextButton(onClick = vm::closeScan) { Text("Not now") } },
            )
        } else {
            // Already in the entries: nothing to ask.
            LaunchedEffect(scan) { vm.closeScan() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewSheet(review: ScanState.Review, vm: GroceriesViewModel) {
    val c = BtTheme.colors
    val parsed = review.parsed
    val itemsTotal = parsed.items.map { it.total }.sum()
    ModalBottomSheet(onDismissRequest = vm::closeScan, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bgSurface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Check the receipt", style = BtTheme.type.titleMedium, color = c.textPrimary)
            review.picture?.let { picture ->
                Image(picture.asImageBitmap(), "The receipt", Modifier.fillMaxWidth().height(140.dp).clip(BtShapes.md), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
            }
            BtTextField(review.store, "Shop", vm::renameStore)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(parsed.date?.let { DateLabels.relative(it) } ?: "Today", style = BtTheme.type.body, color = c.textSecondary, modifier = Modifier.weight(1f))
                Text(MoneyFormat.format(parsed.total ?: itemsTotal), style = BtTheme.type.amountMedium, color = c.textPrimary)
            }
            if (!parsed.isComplete) {
                Text(
                    if (parsed.total == null) "No total was found, so it's what the items add up to." else "The items come to ${MoneyFormat.format(itemsTotal)}, not ${MoneyFormat.format(parsed.total!!)}. Something may have been misread or missed, like a discount.",
                    style = BtTheme.type.caption,
                    color = c.statusWarning,
                )
            }
            Overline(if (parsed.items.size == 1) "1 item" else "${parsed.items.size} items")
            parsed.items.forEachIndexed { i, item -> ItemLine(item, onRemove = { vm.removeItem(i) }) }
            PrimaryButton("Save receipt", vm::save, enabled = parsed.items.isNotEmpty())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptSheet(receipt: Receipt, onClose: () -> Unit, onDelete: () -> Unit) {
    val c = BtTheme.colors
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bgSurface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(receipt.store, style = BtTheme.type.titleMedium, color = c.textPrimary)
                    Text(DateLabels.relative(receipt.date), style = BtTheme.type.caption, color = c.textSecondary)
                }
                Text(MoneyFormat.format(receipt.total), style = BtTheme.type.amountMedium, color = c.textPrimary)
            }
            receipt.items.forEach { ItemLine(it) }
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(BtShapes.full).clickable { confirmDelete = true }, contentAlignment = Alignment.Center) {
                Text("Delete receipt", style = BtTheme.type.label, color = c.moneyExpense)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this receipt?") },
            text = { Text("Its items leave the report. Your entries stay as they are.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** "Today" and "Yesterday" in lower case, for the middle of a sentence. Dates stay as they are. */
private fun String.inSentence(): String = if (this == "Today" || this == "Yesterday") lowercase() else this
