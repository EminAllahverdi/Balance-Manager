package com.eminallahverdi.balance.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.designsystem.R
import com.eminallahverdi.balance.core.model.GuessReason

/**
 * Why a bank payment was filled in as it was, in a few words beside the suggestion: "Usual at Bravo", "Like your other
 * ₼4.50 payments around 09:00". The same on To confirm and in quick add, so it's read the same way in both.
 */
@Composable
fun GuessReason.text(): String = when (this) {
    is GuessReason.UsualAt -> if (times > 1) stringResource(R.string.ds_reason_usual_at, shop) else stringResource(R.string.ds_reason_last_at, shop)
    is GuessReason.LikeAmount -> minuteOfDay?.let { stringResource(R.string.ds_reason_like_amount_at, MoneyFormat.format(amount), DateLabels.timeOfDay(it)) }
        ?: stringResource(R.string.ds_reason_like_amount, MoneyFormat.format(amount))
    is GuessReason.Recurring -> stringResource(R.string.ds_reason_recurring, name)
    GuessReason.Words -> stringResource(R.string.ds_reason_words)
}
