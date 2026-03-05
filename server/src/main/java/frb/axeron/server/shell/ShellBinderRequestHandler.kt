package xyz.lazyghosty.phant0m.server.shell

import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Parcel
import frb.phant0m.api.Phant0m
import frb.phant0m.server.ServerConstants
import frb.phant0m.shared.Phant0mApiConstant

object ShellBinderRequestHandler {

    fun handleRequest(context: Context, intent: Intent): Boolean {
        if (intent.action != ServerConstants.REQUEST_BINDER_AXRUNTIME) {
            return false
        }

        val binder = intent.getBundleExtra("data")?.getBinder("binder") ?: return false
        val phant0mBinder = Phant0m.getBinder()
//        if (phant0mBinder == null) {
//            LOGGER.w("Binder not received or Phant0m service not running")
//        }

        val data = Parcel.obtain()
        return try {
            data.writeStrongBinder(phant0mBinder)
            data.writeLong(Phant0mApiConstant.server.VERSION_CODE)
            data.writeString(context.applicationInfo.nativeLibraryDir)
            binder.transact(1, data, null, IBinder.FLAG_ONEWAY)
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        } finally {
            data.recycle()
        }
    }
}
