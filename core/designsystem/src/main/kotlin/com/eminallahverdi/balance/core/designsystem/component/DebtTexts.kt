package com.eminallahverdi.balance.core.designsystem.component

import com.eminallahverdi.balance.core.common.UiText
import com.eminallahverdi.balance.core.common.rawText
import com.eminallahverdi.balance.core.common.uiText
import com.eminallahverdi.balance.core.designsystem.R
import com.eminallahverdi.balance.core.model.DebtEntryRole
import com.eminallahverdi.balance.core.model.PAID_BACK_TO

/** "Borrowed from Sam" and the like, for any list that shows a debt's entries. */
fun DebtEntryRole.label(counterparty: String): UiText = when (this) {
    DebtEntryRole.BORROWED_FROM -> uiText(R.string.ds_borrowed_from, counterparty)
    DebtEntryRole.PAID_BACK_TO -> uiText(R.string.ds_paid_back_to, counterparty)
    DebtEntryRole.LENT_TO -> uiText(R.string.ds_lent_to, counterparty)
    DebtEntryRole.PAID_BACK_BY -> uiText(R.string.ds_paid_back_by, counterparty)
    DebtEntryRole.YOUR_SHARE -> uiText(R.string.ds_your_share)
    DebtEntryRole.PAID_FOR -> uiText(R.string.ds_paid_for, counterparty)
}

/** A debt entry's note. "Paid back to Sam" is one the app wrote, so it's said in the language showing; the user's own stay as typed. */
fun debtNoteText(note: String): UiText =
    if (note.startsWith(PAID_BACK_TO)) DebtEntryRole.PAID_BACK_TO.label(note.removePrefix(PAID_BACK_TO)) else rawText(note)
