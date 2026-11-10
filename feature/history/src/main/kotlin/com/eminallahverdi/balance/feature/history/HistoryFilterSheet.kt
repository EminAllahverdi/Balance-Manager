package com.eminallahverdi.balance.feature.history

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.ChoiceSheet
import com.eminallahverdi.balance.core.designsystem.component.MonthPicker
import com.eminallahverdi.balance.core.designsystem.component.OptionRow
import com.eminallahverdi.balance.core.designsystem.component.Overline
import com.eminallahverdi.balance.core.designsystem.component.PersonDot
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.CategoryType

private const val CATEGORIES_PER_ROW = 4

internal enum class FilterSheet(@StringRes val title: Int) {
    MONTH(R.string.history_month),
    PERSON(R.string.history_person),
    CATEGORY(R.string.history_category),
    ACCOUNT(R.string.history_account),
}

/** Picks one filter value. Grids keep the longer choices on one screen, where a menu would run off it. */
@Composable
internal fun HistoryFilterSheet(sheet: FilterSheet, state: HistoryUiState, vm: HistoryViewModel, onClose: () -> Unit) {
    val filter = state.filter
    ChoiceSheet(stringResource(sheet.title), onClose) { choose ->
        when (sheet) {
            FilterSheet.MONTH -> {
                OptionRow(stringResource(R.string.history_all_time), filter.month == null, onClick = { choose { vm.setMonth(null) } })
                MonthPicker(filter.month) { month -> choose { vm.setMonth(month) } }
            }
            FilterSheet.CATEGORY -> CategoryPicker(state.categories, filter.categoryId) { id -> choose { vm.setCategory(id) } }
            FilterSheet.PERSON -> {
                OptionRow(stringResource(R.string.history_everyone), filter.personId == null, onClick = { choose { vm.setPerson(null) } })
                state.people.forEach { person ->
                    OptionRow(person.name, person.id == filter.personId, onClick = { choose { vm.setPerson(person.id) } }, leading = { PersonDot(person.color, 10.dp) })
                }
            }
            FilterSheet.ACCOUNT -> {
                OptionRow(stringResource(R.string.history_all_accounts), filter.accountId == null, onClick = { choose { vm.setAccount(null) } })
                state.accounts.forEach { account ->
                    OptionRow(
                        text = account.name,
                        selected = account.id == filter.accountId,
                        onClick = { choose { vm.setAccount(account.id) } },
                        leading = { CategoryTile(account.icon, account.color, size = 32.dp, iconSize = 18.dp, corner = 10.dp) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryPicker(categories: List<Category>, selected: Long?, onSelect: (Long?) -> Unit) {
    OptionRow(stringResource(R.string.history_all_categories), selected == null, onClick = { onSelect(null) })
    listOf(CategoryType.EXPENSE to stringResource(R.string.history_expense), CategoryType.INCOME to stringResource(R.string.history_income)).forEach { (type, title) ->
        val group = categories.filter { it.type == type }
        if (group.isNotEmpty()) {
            Overline(title, Modifier.padding(top = 8.dp, start = 4.dp))
            group.chunked(CATEGORIES_PER_ROW).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { category -> CategoryCell(category, category.id == selected, onClick = { onSelect(category.id) }, modifier = Modifier.weight(1f)) }
                    repeat(CATEGORIES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CategoryCell(category: Category, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = BtTheme.colors
    Column(
        modifier.clip(BtShapes.sm).clickable(role = Role.RadioButton, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(if (selected) Modifier.border(2.dp, c.accentFill, RoundedCornerShape(19.dp)).padding(3.dp) else Modifier.padding(3.dp)) {
            CategoryTile(category.icon, category.color, size = 48.dp, iconSize = 24.dp, corner = 16.dp)
        }
        Text(
            category.name,
            style = BtTheme.type.caption.copy(fontSize = 11.sp, lineHeight = 14.sp),
            color = if (selected) c.textPrimary else c.textMuted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
