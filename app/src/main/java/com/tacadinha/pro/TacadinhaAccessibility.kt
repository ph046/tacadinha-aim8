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

    companion object {
        var instance: TacadinhaAccessibility? = null
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        try { instance = this } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun performShot(cueX: Float, cueY: Float, angleRad: Double, power: Float = 0.75f) {
        try {
            val cosA = cos(angleRad).toFloat()
            val sinA = sin(angleRad).toFloat()
            val pullDist = (100 + power * 220).toInt()

            val aimPath = Path().apply {
                moveTo(cueX - cosA * 100f, cueY - sinA * 100f)
                lineTo(cueX + cosA * 60f, cueY + sinA * 60f)
            }
            val aimGesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(aimPath, 0L, 280L))
                .build()

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
                    handler.postDelayed({
                        try { dispatchGesture(shootGesture, null, null) } catch (e: Exception) { e.printStackTrace() }
                    }, 400L)
                }
                override fun onCancelled(g: GestureDescription) {
                    handler.postDelayed({
                        try { dispatchGesture(shootGesture, null, null) } catch (e: Exception) { e.printStackTrace() }
                    }, 400L)
                }
            }, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
