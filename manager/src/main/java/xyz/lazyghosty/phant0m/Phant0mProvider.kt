package xyz.lazyghosty.phant0m.manager

import android.os.Build
import android.os.Bundle
import androidx.core.os.bundleOf
import frb.phant0m.api.Phant0m
import frb.phant0m.ktx.workerHandler
import frb.phant0m.provider.Phant0mProvider
import frb.phant0m.server.util.Logger
import frb.phant0m.shared.ShizukuApiConstant.USER_SERVICE_ARG_PGID
import frb.phant0m.shared.ShizukuApiConstant.USER_SERVICE_ARG_TOKEN
import moe.shizuku.api.BinderContainer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class Phant0mProvider : Phant0mProvider() {

    companion object {
        private const val EXTRA_BINDER = "moe.shizuku.privileged.api.intent.extra.BINDER"
        private const val METHOD_SEND_USER_SERVICE = "sendUserService"
        private val LOGGER = Logger("Phant0mProvider")
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (extras == null) return null

        return if (method == METHOD_SEND_USER_SERVICE) {
            LOGGER.d("sendUserService")
            try {
                extras.classLoader = BinderContainer::class.java.classLoader

                val token = extras.getString(USER_SERVICE_ARG_TOKEN) ?: return null
                val pgid = extras.getInt(USER_SERVICE_ARG_PGID)
                val binder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    extras.getParcelable(EXTRA_BINDER,  BinderContainer::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    extras.getParcelable(EXTRA_BINDER)
                }?.binder ?: return null
//                val binder = extras.getParcelableCompat(EXTRA_BINDER, BinderContainer::class.java)?.binder ?: return null

                val countDownLatch = CountDownLatch(1)
                var reply: Bundle? = Bundle()

                val listener = object : Phant0m.OnBinderReceivedListener {

                    override fun onBinderReceived() {
                        try {
                            Phant0m.attachUserService(binder, bundleOf(
                                USER_SERVICE_ARG_TOKEN to token,
                                USER_SERVICE_ARG_PGID to pgid
                            )
                            )
                            reply!!.putParcelable(EXTRA_BINDER,
                                BinderContainer(Phant0m.getShizukuService().asBinder())
                            )
                        } catch (e: Throwable) {
                            LOGGER.e(e, "attachUserService $token")
                            reply = null
                        }

                        Phant0m.removeBinderReceivedListener(this)

                        countDownLatch.countDown()
                    }
                }

                Phant0m.addBinderReceivedListenerSticky(listener, workerHandler)

                return try {
                    countDownLatch.await(5, TimeUnit.SECONDS)
                    reply
                } catch (e: TimeoutException) {
                    LOGGER.e(e, "Binder not received in 5s")
                    null
                }
            } catch (e: Throwable) {
                LOGGER.e(e, "sendUserService")
                null
            }
        } else {
            super.call(method, arg, extras)
        }
    }
}