package xyz.lazyghosty.phant0m.manager.ui.viewmodel

import android.app.AppOpsManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import frb.phant0m.adb.AdbPairingService
import frb.phant0m.adb.util.AdbEnvironment
import frb.phant0m.api.Phant0m
import frb.phant0m.api.Phant0mCommandSession
import frb.phant0m.api.Phant0mInfo
import frb.phant0m.api.core.Phant0mSettings
import frb.phant0m.api.core.Starter
import xyz.lazyghosty.phant0m.adb.AdbStarter
import xyz.lazyghosty.phant0m.adb.AdbStarter.stopTcp
import xyz.lazyghosty.phant0m.adb.AdbStateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ActivateViewModel : ViewModel() {

    companion object {
        const val TAG = "AdbViewModel"
        const val ACTIVATE_FAILED = -1
        const val ACTIVATE_PROCESS = 0
        const val ACTIVATE_SUCCESS = 1

    }

    var activateStatus by mutableStateOf<ActivateStatus>(run {
        if (Phant0m.pingBinder() && Phant0m.getPhant0mInfo().isNeedUpdate()) {
            ActivateStatus.Updating(Phant0m.getPhant0mInfo())
        }
        ActivateStatus.Disable
    })
        private set

    var phant0mInfo by mutableStateOf(Phant0mInfo())
        private set

    var isShizukuActive by mutableStateOf(
        Phant0m.getShizukuService() != null
    )
        private set

    fun setShizukuIntercept(enable: Boolean) {
        viewModelScope.launch(Dispatchers.Main) {
            isShizukuActive = enable
            Phant0m.enableShizukuService(enable)
        }
    }

    fun checkShizukuIntercept() {
        viewModelScope.launch(Dispatchers.Main) {
            isShizukuActive = Phant0m.pingBinder() && Phant0m.getShizukuService() != null
        }
    }

    var isNotificationEnabled by mutableStateOf(false)
        private set

    var devSettings by mutableStateOf(false)
        private set

    fun setLaunchDevSettings(launch: Boolean) {
        viewModelScope.launch(Dispatchers.Main) {
            devSettings = launch
        }
    }

    var tryActivate by mutableStateOf(false)
        private set

    fun setTryToActivate(activate: Boolean) {
        viewModelScope.launch(Dispatchers.Main) {
            tryActivate = activate
        }
    }


    sealed class ActivateStatus {
        object Disable : ActivateStatus()
        object NeedExtraStep : ActivateStatus()
        class Updating(val phant0mInfo: Phant0mInfo) : ActivateStatus()
        class Running(val phant0mInfo: Phant0mInfo) : ActivateStatus()
    }

    fun phant0mObserve(): Flow<ActivateStatus> = callbackFlow {
        if (Phant0m.pingBinder()) {
            Log.i("Phant0mBinder", "binderHasReceived")
            val phant0mInfo = Phant0m.getPhant0mInfo()
            when {
                phant0mInfo.isNeedUpdate() -> {
                    trySend(ActivateStatus.Updating(phant0mInfo))
                    setTryToActivate(true)
                    Phant0m.newProcess(
                        Phant0mCommandSession.getQuickCmd(
                            Starter.internalCommand,
                            true,
                            false
                        ),
                        null,
                        null
                    )
                }

                phant0mInfo.isRunning() -> {
                    trySend(ActivateStatus.Running(phant0mInfo))
                }

                phant0mInfo.isNeedExtraStep() -> {
                    trySend(ActivateStatus.NeedExtraStep)
                }
            }
        }
        val receivedListener = Phant0m.OnBinderReceivedListener {
            Log.i("Phant0mBinder", "onBinderReceived")
            val phant0mInfo = Phant0m.getPhant0mInfo()
            when {
                phant0mInfo.isRunning() -> {
                    trySend(ActivateStatus.Running(phant0mInfo))
                }

                phant0mInfo.isNeedExtraStep() -> {
                    trySend(ActivateStatus.NeedExtraStep)
                }
            }
        }
        val deadListener = Phant0m.OnBinderDeadListener {
            Log.i("Phant0mBinder", "onBinderDead")
            trySend(ActivateStatus.Disable)
        }
        Phant0m.addBinderReceivedListener(receivedListener)
        Phant0m.addBinderDeadListener(deadListener)
        awaitClose {
            Phant0m.removeBinderReceivedListener(receivedListener)
            Phant0m.removeBinderDeadListener(deadListener)
        }
    }

    init {
        viewModelScope.launch {
            phant0mObserve().collect { status ->
                val isStillUpdating =
                    status is ActivateStatus.Disable && activateStatus is ActivateStatus.Updating
                phant0mInfo = when (status) {
                    is ActivateStatus.Running -> {
                        checkShizukuIntercept()
                        status.phant0mInfo
                    }

                    is ActivateStatus.Updating -> {
                        status.phant0mInfo
                    }

                    else -> {
                        if (isStillUpdating) {
                            (activateStatus as ActivateStatus.Updating).phant0mInfo
                        } else {
                            Phant0mInfo()
                        }
                    }
                }
                if (isStillUpdating) return@collect
                Log.i("Phant0mBinder", "status: $status")
                activateStatus = status
                setTryToActivate(false)
            }
        }
    }

    fun startRoot(result: (Int) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (tryActivate) return@launch result(ACTIVATE_PROCESS)
                setTryToActivate(true)

                if (!Shell.getShell().isRoot) {
                    Shell.getCachedShell()?.close()
                    result(ACTIVATE_FAILED)
                    return@launch
                }

                Shell.cmd(Starter.internalCommand).submit {
                    if (it.isSuccess) {
                        Phant0mSettings.setLastLaunchMode(Phant0mSettings.LaunchMethod.ROOT)
                        result(ACTIVATE_SUCCESS)
                    } else {
                        result(ACTIVATE_FAILED)
                    }
                    Shell.getCachedShell()?.close()
                }
            }.onFailure {
                it.printStackTrace()
                result(ACTIVATE_FAILED)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun updateNotificationState(context: Context) {
        viewModelScope.launch {
            isNotificationEnabled = checkNotificationEnabled(context)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun startAdbWireless(
        context: Context, result: (AdbStateInfo) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (AdbEnvironment.isWifiRequired() && !isWifiEnabled(context)) return@launch requestEnableWifi(context)
            if (tryActivate) return@launch result(AdbStateInfo.Process("Trying to activate"))
            setTryToActivate(true)

            AdbStarter.startAdbWireless(context, result)
        }
    }

    fun startAdbTcp(
        context: Context, result: (AdbStateInfo) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (tryActivate) return@launch result(AdbStateInfo.Process("Trying to activate"))
            setTryToActivate(true)

            val tcpPort = AdbEnvironment.getAdbTcpPort()

            AdbStarter.startAdbClient(context, tcpPort, result)
        }
    }

    fun stopAdbTcp(
        context: Context, result: (AdbStateInfo) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (tryActivate) return@launch result(AdbStateInfo.Process("Trying to activate"))
            setTryToActivate(true)

            val tcpPort = AdbEnvironment.getAdbTcpPort()
            if (tcpPort > 0 && !Phant0mSettings.getTcpMode()) {
                stopTcp(context, tcpPort)
            }
        }
    }

    fun isWifiEnabled(context: Context): Boolean {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.isWifiEnabled
    }

    fun requestEnableWifi(context: Context) {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }
        context.startActivity(intent)
    }



    @RequiresApi(Build.VERSION_CODES.R)
    fun startPairingService(context: Context) = runBlocking(Dispatchers.IO) {
        if (!isNotificationEnabled) return@runBlocking
        setLaunchDevSettings(true)

        val intent = AdbPairingService.startIntent(context)
        try {
            context.startForegroundService(intent)
        } catch (e: Throwable) {
            Log.e("Phant0m", "startForegroundService", e)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && e is ForegroundServiceStartNotAllowedException
            ) {
                val mode = context.getSystemService(AppOpsManager::class.java)
                    .noteOpNoThrow(
                        "android:start_foreground",
                        android.os.Process.myUid(),
                        context.packageName,
                        null,
                        null
                    )
                if (mode == AppOpsManager.MODE_ERRORED) {
                    Toast.makeText(
                        context,
                        "OP_START_FOREGROUND is denied. What are you doing?",
                        Toast.LENGTH_LONG
                    ).show()
                }
                context.startService(intent)
            }
        }
    }


    /**
     * Cek notifikasi aktif atau tidak
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun checkNotificationEnabled(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = nm.getNotificationChannel(AdbPairingService.NOTIFICATION_CHANNEL)
        return nm.areNotificationsEnabled() &&
                (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)
    }
}