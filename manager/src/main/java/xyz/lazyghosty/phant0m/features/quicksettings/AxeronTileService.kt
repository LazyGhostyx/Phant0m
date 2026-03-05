package xyz.lazyghosty.phant0m.manager.features.quicksettings

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.widget.Toast
import xyz.lazyghosty.phant0m.R

class Phant0mTileService : android.service.quicksettings.TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile.apply {
            label = "AxTest"
            icon = Icon.createWithResource(this@Phant0mTileService, R.drawable.ic_phant0m)
            state = Tile.STATE_INACTIVE
            updateTile()
        }


    }

    override fun onClick() {
        super.onClick()
        Toast.makeText(this, "Clicked", Toast.LENGTH_SHORT).show()
    }


}