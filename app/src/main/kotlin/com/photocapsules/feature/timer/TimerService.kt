package com.photocapsules.feature.timer

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.photocapsules.MainActivity
import com.photocapsules.PhotoCapsulesApp
import kotlinx.coroutines.flow.MutableStateFlow

/** Global timer state observed by the UI. */
object TimerState {
    data class State(val running: Boolean = false, val endMillis: Long = 0L, val totalMillis: Long = 0L, val finished: Boolean = false)
    val state = MutableStateFlow(State())
}

/** Foreground countdown so the timer keeps running with the screen off; alerts when finished. */
class TimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private val finish = Runnable { onFinished() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopTimer(); return START_NOT_STICKY }
            ACTION_START -> {
                val total = intent.getLongExtra(EXTRA_MILLIS, 0L).coerceAtLeast(1000L)
                val end = System.currentTimeMillis() + total
                TimerState.state.value = TimerState.State(true, end, total, false)
                val n = ongoing(end)
                ServiceCompat.startForeground(this, NOTIF_ID, n,
                    if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
                wakeLock?.let { if (it.isHeld) it.release() }
                wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhotoCapsules:timer").apply { acquire(total + 60_000L) }
                handler.removeCallbacks(finish)
                handler.postDelayed(finish, total)
            }
        }
        return START_NOT_STICKY
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun ongoing(end: Long): Notification {
        val stop = PendingIntent.getService(this, 1, Intent(this, TimerService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, PhotoCapsulesApp.CHANNEL_TIMER)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Exposure timer running")
            .setWhen(end).setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true)
            .setOngoing(true).setContentIntent(contentIntent())
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun onFinished() {
        TimerState.state.value = TimerState.state.value.copy(running = false, finished = true)
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_DONE_ID, NotificationCompat.Builder(this, PhotoCapsulesApp.CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Time's up!").setContentText("Exposure / shooting time finished")
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true).setContentIntent(contentIntent())
            .setDefaults(NotificationCompat.DEFAULT_SOUND).build())
        runCatching {
            val v = getSystemService(Vibrator::class.java)
            v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 800), -1))
        }
        stopTimer(keepFinished = true)
    }

    private fun stopTimer(keepFinished: Boolean = false) {
        handler.removeCallbacks(finish)
        if (!keepFinished) TimerState.state.value = TimerState.State()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(finish)
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.photocapsules.timer.START"
        const val ACTION_STOP = "com.photocapsules.timer.STOP"
        const val EXTRA_MILLIS = "millis"
        private const val NOTIF_ID = 42
        private const val NOTIF_DONE_ID = 43

        fun start(ctx: Context, millis: Long) =
            ContextCompat.startForegroundService(ctx, Intent(ctx, TimerService::class.java).setAction(ACTION_START).putExtra(EXTRA_MILLIS, millis))

        fun stop(ctx: Context) = ctx.startService(Intent(ctx, TimerService::class.java).setAction(ACTION_STOP))
    }
}
