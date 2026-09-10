package com.melox.player.memory

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class FairMemoryManager(private val trimCaches: () -> Unit) {
    @Volatile
    var savePlayback: (suspend () -> Boolean)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val working = AtomicBoolean()
    private val handler = Handler(HandlerThread("FairMemory").apply { start() }.looper)

    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(FAIR_MEMORY_TRIM)
            addAction(FAIR_MEMORY_KILL)
        }
        // The vendor protocol requires cross-process delivery and documents no permission.
        ContextCompat.registerReceiver(
            context, receiver, filter, null, handler, ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val deadline = SystemClock.uptimeMillis() + WORK_BUDGET_MS
            val notification = runCatching { FairMemoryNotification.from(intent) }
                .getOrNull() ?: return
            if (!working.compareAndSet(false, true)) {
                notification.reply(1)
                return
            }
            val pending = goAsync()
            val completion = FairMemoryCompletion(deadline)
            fun finish(success: Boolean) {
                val result = completion.complete(success, SystemClock.uptimeMillis()) ?: return
                try {
                    notification.reply(result)
                } finally {
                    pending.finish()
                }
            }

            // The watchdog never shares a thread with disk IO or the player main thread.
            val job = scope.launch(start = CoroutineStart.LAZY) {
                val success = runCatching {
                    when (notification.action) {
                        FAIR_MEMORY_TRIM -> {
                            trimCaches()
                            true
                        }
                        else -> savePlayback?.invoke() ?: true
                    }
                }.getOrDefault(false)
                handler.post { finish(success) }
            }
            val timeout = Runnable {
                finish(false)
                job.cancel()
            }
            job.invokeOnCompletion {
                working.set(false)
                // Keep the watchdog until the result has actually reached the handler.
                handler.post {
                    finish(false)
                    handler.removeCallbacks(timeout)
                }
            }
            handler.postAtTime(timeout, deadline)
            job.start()
        }
    }

    private companion object {
        const val WORK_BUDGET_MS = 2_500L
    }
}

internal data class FairMemoryNotification(
    val action: String,
    val notifyType: Int,
    val notifyId: Int,
    val callback: IBinder,
) {
    fun reply(result: Int) {
        val data = Parcel.obtain()
        try {
            data.writeInt(notifyType)
            data.writeInt(notifyId)
            data.writeInt(result)
            data.writeBundle(Bundle())
            // A one-way transaction has no reply Parcel to read.
            if (!callback.transact(IBinder.FIRST_CALL_TRANSACTION, data, null, IBinder.FLAG_ONEWAY)) {
                Log.w("FairMemory", "Callback transaction was rejected")
            }
        } catch (exception: Exception) {
            Log.w("FairMemory", "Callback failed", exception)
        } finally {
            data.recycle()
        }
    }

    companion object {
        @Suppress("DEPRECATION")
        fun from(intent: Intent): FairMemoryNotification? {
            val common = intent.getBundleExtra("common") ?: return null
            val notifyId = common.get("notifyId") as? Int ?: return null
            val type = common.getInt("notifyType", -1)
            if (!isValidFairMemoryNotification(
                    intent.action,
                    common.getString("action"),
                    type,
                    common.containsKey("notifyId"),
                )
            ) return null
            val callback = common.getBinder("callback") ?: return null
            return FairMemoryNotification(
                action = requireNotNull(intent.action),
                notifyType = type,
                notifyId = notifyId,
                callback = callback,
            )
        }
    }
}
