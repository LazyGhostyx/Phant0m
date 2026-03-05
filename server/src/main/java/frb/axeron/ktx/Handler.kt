package xyz.lazyghosty.phant0m.ktx

import android.os.Handler
import android.os.HandlerThread

private val workerThread by lazy(LazyThreadSafetyMode.NONE) {
    HandlerThread("Worker").apply { start() }
}

val workerHandler by lazy {
    Handler(workerThread.looper)
}
