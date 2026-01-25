package com.mepapp.mobile.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.mepapp.mobile.R

class CallOverlayService : Service() {
    
    private val TAG = "CallOverlayService"
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action")
        
        when (action) {
            "show" -> showOverlay()
            "hide" -> hideOverlay()
        }
        
        return START_NOT_STICKY
    }
    
    private fun showOverlay() {
        try {
            if (overlayView != null) {
                Log.d(TAG, "Overlay already showing")
                return
            }
            
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            // Create overlay view
            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            overlayView = inflater.inflate(R.layout.call_overlay, null)
            
            // Configure layout parameters
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 100 // 100px from top
            }
            
            // Add view to window
            windowManager?.addView(overlayView, params)
            Log.d(TAG, "✅ Overlay view added to WindowManager successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing overlay in windowManager", e)
            e.printStackTrace()
        }
    }
    
    private fun hideOverlay() {
        try {
            if (overlayView != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
                Log.d(TAG, "Overlay hidden")
            }
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding overlay", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
    }
    
    companion object {
        fun show(context: Context) {
            val intent = Intent(context, CallOverlayService::class.java).apply {
                putExtra("action", "show")
            }
            context.startService(intent)
        }
        
        fun hide(context: Context) {
            val intent = Intent(context, CallOverlayService::class.java).apply {
                putExtra("action", "hide")
            }
            context.startService(intent)
        }
    }
}
