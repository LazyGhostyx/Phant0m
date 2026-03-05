package xyz.lazyghosty.phant0m.manager

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import coil.Coil
import coil.ImageLoader
import com.topjohnwu.superuser.Shell
import frb.phant0m.Axerish
import frb.phant0m.api.core.Phant0mSettings
import frb.phant0m.api.core.Engine
import xyz.lazyghosty.phant0m.ui.util.createShellBuilder
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File
import java.util.Locale

class Phant0mApplication : Engine() {
    companion object {
        lateinit var phant0mApp: Phant0mApplication

        init {
//            logd("ShizukuApplication", "init")
            Axerish.initialize(BuildConfig.APPLICATION_ID)
            Shell.setDefaultBuilder(createShellBuilder())
            Shell.enableLegacyStderrRedirection = true
            Shell.enableVerboseLogging = BuildConfig.DEBUG

            if (Build.VERSION.SDK_INT >= 28) {
                HiddenApiBypass.setHiddenApiExemptions("")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                System.loadLibrary("adb")
            }
        }
    }

    lateinit var okhttpClient: OkHttpClient

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        phant0mApp = this
        Phant0mSettings.initialize(phant0mApp)
    }

    @SuppressLint("ResourceType")
    override fun onCreate() {
        super.onCreate()

        val context = this
        val iconSize = resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        Coil.setImageLoader(
            ImageLoader.Builder(context)
                .components {
                    add(AppIconKeyer())
                    add(AppIconFetcher.Factory(iconSize, false, context))
                }
                .build()
        )


        okhttpClient =
            OkHttpClient.Builder().cache(Cache(File(cacheDir, "okhttp"), 10 * 1024 * 1024))
                .addInterceptor { block ->
                    block.proceed(
                        block.request().newBuilder()
                            .header("User-Agent", "Phant0m/${BuildConfig.VERSION_CODE}")
                            .header("Accept-Language", Locale.getDefault().toLanguageTag()).build()
                    )
                }.build()
    }
}