package com.tacadinha.pro

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat

class CaptureService : Service() {
    companion object {
        const val CH="tac_pro"; const val NID=77
        const val EXTRA_CODE="code"; const val EXTRA_DATA="data"; const val EXTRA_MODE="mode"
    }
    private var projection: MediaProjection?=null; private var reader: ImageReader?=null
    private var vDisplay: android.hardware.display.VirtualDisplay?=null
    private lateinit var wm: WindowManager; private var overlay: View?=null
    private var sw=0; private var sh=0; private var dpi=0; private var playerMode=0
    private var autoRepeat=false; private val handler=Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate(); wm=getSystemService(WINDOW_SERVICE) as WindowManager
        val m=DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getMetrics(m)
        sw=m.widthPixels; sh=m.heightPixels; dpi=m.densityDpi; createChannel()
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        val code=intent?.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED)?:return START_NOT_STICKY
        val data: Intent=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU)
            intent.getParcelableExtra(EXTRA_DATA,Intent::class.java)!!
        else intent.getParcelableExtra(EXTRA_DATA)!!
        playerMode=intent.getIntExtra(EXTRA_MODE,2)

        val notif = buildNotif()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NID, notif)
        }

        val pm=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection=pm.getMediaProjection(code,data)
        reader=ImageReader.newInstance(sw,sh,PixelFormat.RGBA_8888,2)
        vDisplay=projection!!.createVirtualDisplay("TacPro",sw,sh,dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,null)
        addOverlay(); return START_STICKY
    }

    private fun addOverlay() {
        val layout=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(200,0,0,0)); setPadding(12,12,12,12)
        }
        val btnShot=Button(this).apply {
            text="TACAR"; setTextColor(Color.BLACK)
            backgroundTintList=android.content.res.ColorStateList.valueOf(Color.argb(255,0,220,80))
        }
        val switchAuto=ToggleButton(this).apply {
            textOff="AUTO: OFF"; textOn="AUTO: ON"; isChecked=false; setTextColor(Color.WHITE)
            backgroundTintList=android.content.res.ColorStateList.valueOf(Color.argb(200,50,50,50))
        }
        layout.addView(btnShot,LinearLayout.LayoutParams(-1,-2))
        layout.addView(switchAuto,LinearLayout.LayoutParams(-1,-2).also{it.topMargin=8})
        val lp=WindowManager.LayoutParams(280,WindowManager.LayoutParams.WRAP_CONTENT,
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT
        ).apply{gravity=Gravity.TOP or Gravity.END;x=8;y=180}
        var dX=0f;var dY=0f;var sX=0f;var sY=0f
        layout.setOnTouchListener{v,e->
            when(e.action){
                MotionEvent.ACTION_DOWN->{sX=e.rawX;sY=e.rawY;dX=lp.x.toFloat();dY=lp.y.toFloat();false}
                MotionEvent.ACTION_MOVE->{lp.x=(dX+(sX-e.rawX)).toInt();lp.y=(dY+(e.rawY-sY)).toInt();wm.updateViewLayout(v,lp);true}
                else->false
            }
        }
        btnShot.setOnClickListener{triggerShot()}
        switchAuto.setOnCheckedChangeListener{_,checked->autoRepeat=checked;if(checked)scheduleAutoShot()}
        wm.addView(layout,lp); overlay=layout
    }

    private fun scheduleAutoShot() {
        if(!autoRepeat) return
        handler.postDelayed({triggerShot();scheduleAutoShot()},3000L)
    }

    private fun triggerShot() {
        val svc=TacadinhaAccessibility.instance?:return
        val image=reader?.acquireLatestImage()?:return
        try {
            val plane=image.planes[0]; val bw=plane.rowStride/plane.pixelStride
            val bmp=Bitmap.createBitmap(bw,sh,Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(plane.buffer)
            val final=if(bw!=sw) Bitmap.createBitmap(bmp,0,0,sw,sh).also{bmp.recycle()} else bmp
            handler.post {
                val state=Detector.analyze(final,playerMode); final.recycle()
                val shot=ShotCalculator.bestShot(state)?:return@post
                handler.postDelayed({svc.performShot(state.cueBall!!.x,state.cueBall.y,shot.angleRad,shot.power)},200L)
            }
        } catch(e: Exception){e.printStackTrace()} finally{image.close()}
    }

    override fun onDestroy() {
        autoRepeat=false; handler.removeCallbacksAndMessages(null)
        try{overlay?.let{wm.removeView(it)}}catch(_: Exception){}
        vDisplay?.release();projection?.stop();reader?.close();super.onDestroy()
    }
    override fun onBind(i: Intent?)=null
    private fun createChannel() {
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CH,"Tacadinha Pro",NotificationManager.IMPORTANCE_LOW))
    }
    private fun buildNotif()=NotificationCompat.Builder(this,CH)
        .setContentTitle("Tacadinha Pro").setContentText("Jogando automaticamente...")
        .setSmallIcon(android.R.drawable.ic_menu_compass).setPriority(NotificationCompat.PRIORITY_LOW).build()
}
