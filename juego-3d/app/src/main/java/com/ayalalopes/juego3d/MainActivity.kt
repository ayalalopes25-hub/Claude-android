package com.ayalalopes.juego3d

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {

    private val game = Game()
    private lateinit var glView: GLSurfaceView
    private lateinit var minimap: MiniMap
    private lateinit var moneyTv: TextView
    private lateinit var starsTv: TextView
    private lateinit var hpTv: TextView
    private lateinit var missionTv: TextView
    private lateinit var msgTv: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            refreshHud()
            handler.postDelayed(this, 100)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this)

        glView = GLSurfaceView(this)
        glView.setEGLContextClientVersion(2)
        glView.setEGLConfigChooser(8, 8, 8, 0, 16, 0)
        glView.setRenderer(Renderer(game))
        root.addView(glView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // ----- textos del HUD -----
        moneyTv = hudText(26f, Color.rgb(124, 252, 138))
        root.addView(moneyTv, corner(Gravity.TOP or Gravity.START, 16, 10, 0, 0))
        hpTv = hudText(16f, Color.rgb(255, 120, 120))
        root.addView(hpTv, corner(Gravity.TOP or Gravity.START, 16, 50, 0, 0))
        starsTv = hudText(26f, Color.rgb(255, 212, 0))
        root.addView(starsTv, corner(Gravity.TOP or Gravity.START, 16, 74, 0, 0))
        missionTv = hudText(16f, Color.rgb(255, 212, 0))
        root.addView(missionTv, corner(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 10, 0, 0))
        msgTv = hudText(22f, Color.WHITE)
        root.addView(msgTv, corner(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 44, 0, 0))

        minimap = MiniMap(this, game)
        val mm = FrameLayout.LayoutParams(dp(150), dp(150), Gravity.TOP or Gravity.END)
        mm.setMargins(0, dp(10), dp(12), 0)
        root.addView(minimap, mm)

        // ----- controles tactiles -----
        val left = padButton("◀") { game.inLeft = it }
        val right = padButton("▶") { game.inRight = it }
        val gas = padButton("▲") { game.inGas = it }
        val brake = padButton("▼") { game.inBrake = it }
        root.addView(left, corner(Gravity.BOTTOM or Gravity.START, 24, 0, 0, 24, 88, 88))
        root.addView(right, corner(Gravity.BOTTOM or Gravity.START, 128, 0, 0, 24, 88, 88))
        root.addView(brake, corner(Gravity.BOTTOM or Gravity.END, 0, 0, 128, 24, 88, 88))
        root.addView(gas, corner(Gravity.BOTTOM or Gravity.END, 0, 0, 24, 24, 88, 88))

        setContentView(root)
    }

    private fun corner(g: Int, l: Int, t: Int, r: Int, b: Int, w: Int = -2, h: Int = -2): FrameLayout.LayoutParams {
        val lp = FrameLayout.LayoutParams(if (w > 0) dp(w) else w, if (h > 0) dp(h) else h, g)
        lp.setMargins(dp(l), dp(t), dp(r), dp(b))
        return lp
    }

    private fun hudText(size: Float, color: Int): TextView {
        val tv = TextView(this)
        tv.textSize = size
        tv.setTextColor(color)
        tv.setShadowLayer(5f, 2f, 2f, Color.BLACK)
        tv.typeface = android.graphics.Typeface.DEFAULT_BOLD
        return tv
    }

    private fun padButton(label: String, onChange: (Boolean) -> Unit): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.textSize = 30f
        tv.setTextColor(Color.WHITE)
        tv.gravity = Gravity.CENTER
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Color.argb(90, 255, 255, 255))
        bg.setStroke(dp(2), Color.argb(200, 255, 255, 255))
        tv.background = bg
        tv.alpha = 0.6f
        tv.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { v.alpha = 1f; onChange(true) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.alpha = 0.6f; onChange(false) }
            }
            true
        }
        return tv
    }

    private fun refreshHud() {
        moneyTv.text = "\$" + game.money
        val lvl = min(5, game.heat.toInt())
        starsTv.text = "★".repeat(lvl) + "☆".repeat(5 - lvl)
        hpTv.text = "Coche: " + max(0, game.player.hp.toInt()) + "%"
        val m = game.missionLen
        val dx = game.missionX - game.player.x
        val dz = game.missionZ - game.player.z
        val d = Math.sqrt((dx * dx + dz * dz).toDouble()).toInt()
        missionTv.text = "Misión: llega al punto amarillo ($d m)"
        msgTv.text = if (game.wastedT > 0f) "¡DESTRUIDO!" else if (game.msgT > 0f) game.msg else ""
        minimap.invalidate()
    }

    // ----- teclado (para probar en emulador / con mando) -----
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyDown(keyCode, true)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyDown(keyCode, false)) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun keyDown(code: Int, down: Boolean): Boolean {
        when (code) {
            KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_DPAD_UP -> game.inGas = down
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_DPAD_DOWN -> game.inBrake = down
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_DPAD_LEFT -> game.inLeft = down
            KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_DPAD_RIGHT -> game.inRight = down
            else -> return false
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
        glView.onPause()
        game.inLeft = false; game.inRight = false; game.inGas = false; game.inBrake = false
    }
}
