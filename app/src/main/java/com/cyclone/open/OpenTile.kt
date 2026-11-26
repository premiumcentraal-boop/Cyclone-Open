package com.cyclone.open

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class OpenTile : TileService() {
    override fun onStartListening() { super.onStartListening(); qsTile?.apply { state = Tile.STATE_ACTIVE; updateTile() } }
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else launchOnAndroid13(intent)
    }

    // The Intent overload is supported on API 33 only; API 34+ always uses PendingIntent.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun launchOnAndroid13(intent: Intent) {
        check(Build.VERSION.SDK_INT == 33)
        startActivityAndCollapse(intent)
    }
}
