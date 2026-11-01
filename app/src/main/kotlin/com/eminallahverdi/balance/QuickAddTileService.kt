package com.eminallahverdi.balance

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.eminallahverdi.balance.core.model.TransactionKind
import com.eminallahverdi.balance.feature.transaction.QuickAddViewModel

/** A Quick Settings tile that adds an expense from anywhere. The system only runs it while the panel is open, so it costs nothing otherwise. */
class QuickAddTileService : TileService() {
    /** It's an action, not a switch, so it never shows as on. */
    override fun onStartListening() {
        qsTile?.run {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        if (isLocked) unlockAndRun { openQuickAdd() } else openQuickAdd()
    }

    private fun openQuickAdd() {
        val add = Intent(this, QuickAddActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(QuickAddViewModel.ARG_KIND, TransactionKind.EXPENSE.name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, add, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(add)
        }
    }
}
