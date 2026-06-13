package com.yourpackage.clicker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.view.Choreographer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat

class FloatingClickerService : Service() {
    private lateinit var windowManager: WindowManager
    private var buttonView: View? = null
    private var panelView: View? = null
    private var buttonParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var clickerThread: HandlerThread? = null
    @Volatile private var isRunning = false
    private lateinit var etTime: EditText
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvCountdown: TextView

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, createNotification())
        showFloatingButton()
        showControlPanel()
    }

    private fun showFloatingButton() {
        buttonView = LayoutInflater.from(this).inflate(R.layout.floating_button, null)
        buttonParams = WindowManager.LayoutParams(
            80, 80,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 400
        }
        buttonView?.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0
            var initialY = 0
            var touchX = 0f
            var touchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = buttonParams!!.x
                        initialY = buttonParams!!.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        buttonParams!!.x = initialX + (event.rawX - touchX).toInt()
                        buttonParams!!.y = initialY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(buttonView, buttonParams)
                        return true
                    }
                }
                return false
            }
        })
        windowManager.addView(buttonView, buttonParams)
    }

    private fun showControlPanel() {
        panelView = LayoutInflater.from(this).inflate(R.layout.floating_panel, null)
        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }
        etTime = panelView!!.findViewById(R.id.et_time)
        btnStart = panelView!!.findViewById(R.id.btn_start)
        btnStop = panelView!!.findViewById(R.id.btn_stop)
        tvCountdown = panelView!!.findViewById(R.id.tv_countdown)
        btnStart.setOnClickListener { startClicking() }
        btnStop.setOnClickListener { stopClicking() }
        val header = panelView!!.findViewById<View>(R.id.panel_header)
        header.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0
            var initialY = 0
            var touchX = 0f
            var touchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = panelParams!!.x
                        initialY = panelParams!!.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        panelParams!!.x = initialX + (event.rawX - touchX).toInt()
                        panelParams!!.y = initialY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(panelView, panelParams)
                        return true
                    }
                }
                return false
            }
        })
        windowManager.addView(panelView, panelParams)
    }

    private fun getButtonCenter(): Pair<Float, Float> {
        val location = IntArray(2)
        buttonView?.getLocationOnScreen(location)
        val x = location[0] + (buttonView?.width ?: 80) / 2f
        val y = location[1] + (buttonView?.height ?: 80) / 2f
        return Pair(x, y)
    }

    private fun startClicking() {
        val timeStr = etTime.text.toString()
        val intervalSec = timeStr.toDoubleOrNull()
        if (intervalSec == null || intervalSec < 0.0 || intervalSec > 31.0) {
            Toast.makeText(this, "Enter 0.000 - 31.000", Toast.LENGTH_SHORT).show()
            return
        }
        val service = ClickerAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, "Enable Accessibility Service first", Toast.LENGTH_LONG).show()
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return
        }
        val intervalNanos = (intervalSec * 1_000_000_000L).toLong()

        // 1ST TAP — immediate
        val (x1, y1) = getButtonCenter()
        service.dispatchTap(x1, y1)

        if (intervalNanos <= 0) return

        isRunning = true
        clickerThread = HandlerThread("PrecisionClicker", Process.THREAD_PRIORITY_URGENT_DISPLAY)
        clickerThread?.start()
        val handler = Handler(clickerThread!!.looper)
        handler.post {
            val targetTime = System.nanoTime() + intervalNanos

            // Sleep until 3ms before target
            while (System.nanoTime() < targetTime - 3_000_000L) {
                if (!isRunning) return@post
                try { Thread.sleep(1) } catch (e: InterruptedException) { return@post }
            }

            // Busy-wait the final 3ms
            while (System.nanoTime() < targetTime) {
                if (!isRunning) return@post
                Thread.yield()
            }

            if (!isRunning) return@post

            // 2ND TAP — frame-locked on main thread
            val (finalX, finalY) = getButtonCenter()
            Handler(Looper.getMainLooper()).post {
                Choreographer.getInstance().postFrameCallback {
                    if (!isRunning) return@postFrameCallback
                    ClickerAccessibilityService.instance?.dispatchTap(finalX, finalY)
                    isRunning = false
                    tvCountdown.text = "DONE"
                }
            }
            // Thread ends naturally here. NO while loop.
        }
        startCountdownUI(intervalSec)
    }

    private fun startCountdownUI(totalSeconds: Double) {
        val mainHandler = Handler(Looper.getMainLooper())
        val startTime = System.currentTimeMillis()
        val totalMillis = (totalSeconds * 1000).toLong()
        val runnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                val elapsed = System.currentTimeMillis() - startTime
                val totalElapsedMillis = (System.currentTimeMillis() - startTime)
                val remaining = totalMillis - totalElapsedMillis
                if (remaining > 0) {
                    val secs = remaining / 1000
                    val millis = remaining % 1000
                    tvCountdown.text = String.format("%d.%03d", secs, millis)
                    mainHandler.postDelayed(this, 50)
                } else {
                    tvCountdown.text = "0.000"
                }
            }
        }
        mainHandler.post(runnable)
    }

    private fun stopClicking() {
        isRunning = false
        clickerThread?.interrupt()
        clickerThread?.quitSafely()
        tvCountdown.text = "STOPPED"
    }

    private fun createNotification(): android.app.Notification {
        val channelId = "clicker_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Auto Clicker", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Auto Clicker")
            .setContentText("Running...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        buttonView?.let { windowManager.removeView(it) }
        panelView?.let { windowManager.removeView(it) }
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1
    }
}
