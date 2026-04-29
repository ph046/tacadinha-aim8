package com.tacadinha.pro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import kotlin.math.cos
import kotlin.math.sin

class TacadinhaAccessibility : AccessibilityService() {
    companion object { var instance: TacadinhaAccessibility? = null }
    private val handler = Handler(Looper.getMainLooper())
    override fun onServiceConnected() { instance = this }
    override fun onInterrupt() {}
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onDestroy() { instance = null; super.onDestroy() }

    fun performShot(cueX: Float, cueY: Float, angleRad: Double, power: Float = 0.75f) {
        val cosA = cos(angleRad).toFloat()
        val sinA = sin(angleRad).toFloat()
        val aimPath = Path().apply {
            moveTo(cueX - cosA * 100, cueY - sinA * 100)
            lineTo(cueX + cosA * 60, cueY + sinA * 60)
        }
        val aimGesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(aimPath, 0L, 280L)).build()
        val pullDist = (100 + power * 220).toInt()
        val pullPath = Path().apply {
            moveTo(cueX - cosA * 30, cueY - sinA * 30)
            lineTo(cueX - cosA * pullDist, cueY - sinA * pullDist)
        }
        val shootPath = Path().apply {
            moveTo(cueX - cosA * pullDist, cueY - sinA * pullDist)
            lineTo(cueX + cosA * 50, cueY + sinA * 50)
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
