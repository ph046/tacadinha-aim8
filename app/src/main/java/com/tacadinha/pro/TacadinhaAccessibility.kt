package com.tacadinha.pro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import kotlin.math.cos
import kotlin.math.sin

class TacadinhaAccessibility : AccessibilityService() {

    companion object {
        var instance: TacadinhaAccessibility? = null
        const val CH = "tac_acc"
        const val NID = 99
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        instance = this
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        }
        serviceInfo = info
        startForegroundIfNeeded()
    }

    private fun startForegroundIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CH) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CH, "Tacadinha Ativo", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        val notif = NotificationCompat.Builder(this, CH)
            .setContentTitle("Tacadinha Pro")
            .setContentText("Servico ativo")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NID, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_ACCESSIBILITY)
            } else {
                startForeground(NID, notif)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onInterrupt() {}
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onDestroy() { instance = null; super.onDestroy() }

    fun performShot(cueX: Float, cueY: Float, angleRad: Double, power: Float = 0.75f) {
        val cosA = cos(angleRad).toFloat()
        val sinA = sin(angleRad).toFloat()
        val pullDist = (100 + power * 220).toInt()
        val aimPath = Path().apply {
            moveTo(cueX - cosA * 100f, cueY - sinA * 100f)
            lineTo(cueX + cosA * 60f, cueY + sinA * 60f)
        }
        val aimGesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(aimPath, 0L, 280L)).build()
        val pullPath = Path().apply {
            moveTo(cueX - cosA * 30f, cueY - sinA * 30f)
            lineTo(cueX - cosA * pullDist, cueY - sinA * pullDist)
        }
        val shootPath = Path().apply {
            moveTo(cueX - cosA * pullDist, cueY - sinA * pullDist)
            lineTo(cueX + cosA * 50f, cueY + sinA * 50f)
        }
        val pullStroke = GestureDescription.StrokeDescription(pullPath, 0L, 250L, true)
        val shootStroke = pullStroke.continueStroke(shootPath, 0L, 100L, false)
        val shootGesture = GestureDescription.Builder().addStroke(shootStroke).build()
        dispatchGesture(aimGesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) {
                handler.postDelayed({ dispatchGesture(shootGesture, null, null) }, 400L)
            }
            override fun onCancelled(g: GestureDescription) {
                handler.postDelayed({ dispatchGesture(shootGesture, null, null) }, 400L)
            }
        }, null)
    }
}
