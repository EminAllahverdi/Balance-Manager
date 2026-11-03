package com.eminallahverdi.balance

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.eminallahverdi.balance.core.data.EXTRA_OPEN_CONFIRM
import com.eminallahverdi.balance.core.model.TransactionKind
import com.eminallahverdi.balance.feature.transaction.QuickAddViewModel

/** The long-press menu on the launcher icon. Published from code because the debug build has its own package name, which res/xml/shortcuts.xml would have to hard-code. */
fun publishAppShortcuts(context: Context) {
    ShortcutManagerCompat.setDynamicShortcuts(
        context,
        listOf(
            shortcut(context, "add_expense", "Add expense", R.drawable.ic_shortcut_expense, TransactionKind.EXPENSE, rank = 0),
            ShortcutInfoCompat.Builder(context, "add_by_voice")
                .setShortLabel("Add by voice")
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_voice))
                .setIntent(Intent(context, VoiceEntryActivity::class.java).setAction(Intent.ACTION_VIEW))
                .setRank(1)
                .build(),
            shortcut(context, "add_income", "Add income", R.drawable.ic_shortcut_income, TransactionKind.INCOME, rank = 2),
            shortcut(context, "transfer", "Transfer", R.drawable.ic_shortcut_transfer, TransactionKind.TRANSFER, rank = 3),
            ShortcutInfoCompat.Builder(context, "to_confirm")
                .setShortLabel("To confirm")
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_confirm))
                .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(EXTRA_OPEN_CONFIRM, true))
                .setRank(4)
                .build(),
        ),
    )
}

private fun shortcut(context: Context, id: String, label: String, @DrawableRes icon: Int, kind: TransactionKind, rank: Int): ShortcutInfoCompat =
    ShortcutInfoCompat.Builder(context, id)
        .setShortLabel(label)
        .setIcon(IconCompat.createWithResource(context, icon))
        .setIntent(Intent(context, QuickAddActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(QuickAddViewModel.ARG_KIND, kind.name))
        .setRank(rank)
        .build()
