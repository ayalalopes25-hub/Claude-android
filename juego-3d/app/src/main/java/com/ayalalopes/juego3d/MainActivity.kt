package com.ayalalopes.juego3d

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
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
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {

    private val game = Game()
    private lateinit var glView: GLSurfaceView
    private lateinit var minimap: MiniMap
    private lateinit var joystick: Joystick
    private lateinit var moneyTv: TextView
    private lateinit var starsTv: TextView
    private lateinit var hpTv: TextView
    private lateinit var weaponTv: TextView
    private lateinit var luciaTv: TextView
    private lateinit var missionTv: TextView
    private lateinit var msgTv: TextView
    private lateinit var enterBtn: TextView
    private lateinit var fireBtn: TextView
    private lateinit var weaponBtn: TextView
    private lateinit var introView: FrameLayout
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
        glView.setEGLConfigChooser(8, 8, 8, 0, 24, 0)
        glView.setRenderer(Renderer(game))
        // arrastrar sobre la pantalla = mirar a todos lados (camara libre)
        var lx = 0f
        var ly = 0f
        glView.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lx = e.x; ly = e.y; game.lookActive = true }
                MotionEvent.ACTION_MOVE -> {
                    game.lookDX += e.x - lx
                    game.lookDY += e.y - ly
                    lx = e.x
                    ly = e.y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> game.lookActive = false
            }
            true
        }
        root.addView(glView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // ----- textos del HUD -----
        moneyTv = hudText(26f, Color.rgb(124, 252, 138))
        root.addView(moneyTv, corner(Gravity.TOP or Gravity.START, 16, 6, 0, 0))
        hpTv = hudText(15f, Color.rgb(255, 120, 120))
        root.addView(hpTv, corner(Gravity.TOP or Gravity.START, 16, 44, 0, 0))
        starsTv = hudText(24f, Color.rgb(255, 212, 0))
        root.addView(starsTv, corner(Gravity.TOP or Gravity.START, 16, 64, 0, 0))
        weaponTv = hudText(15f, Color.rgb(255, 255, 255))
        root.addView(weaponTv, corner(Gravity.TOP or Gravity.START, 16, 96, 0, 0))
        luciaTv = hudText(15f, Color.rgb(255, 130, 175))
        root.addView(luciaTv, corner(Gravity.TOP or Gravity.START, 16, 118, 0, 0))
        missionTv = hudText(15f, Color.rgb(255, 212, 0))
        root.addView(missionTv, corner(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0))
        msgTv = hudText(20f, Color.WHITE)
        msgTv.gravity = Gravity.CENTER
        root.addView(msgTv, corner(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 40, 0, 0))

        minimap = MiniMap(this, game)
        val mm = FrameLayout.LayoutParams(dp(130), dp(130), Gravity.TOP or Gravity.END)
        mm.setMargins(0, dp(8), dp(12), 0)
        root.addView(minimap, mm)

        // ----- bolita (joystick) a la izquierda -----
        joystick = Joystick(this) { x, y -> game.joyX = x; game.joyY = y }
        root.addView(joystick, corner(Gravity.BOTTOM or Gravity.START, 28, 0, 0, 28, 150, 150))

        // ----- botones de accion a la derecha -----
        fireBtn = padButton("FUEGO", 15f) { game.inFire = it }
        enterBtn = padButton("SUBIR", 14f) { if (it) game.actEnter = true }
        weaponBtn = padButton("ARMA", 13f) { if (it) game.actWeapon = true }
        root.addView(fireBtn, corner(Gravity.BOTTOM or Gravity.END, 0, 0, 28, 28, 96, 96))
        root.addView(enterBtn, corner(Gravity.BOTTOM or Gravity.END, 0, 0, 140, 28, 80, 80))
        root.addView(weaponBtn, corner(Gravity.BOTTOM or Gravity.END, 0, 0, 36, 136, 66, 66))

        // ----- pantalla de inicio con la pareja protagonista -----
        introView = FrameLayout(this)
        introView.isClickable = true
        try {
            val iv = ImageView(this)
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP)
            val st = assets.open("pareja.jpg")
            iv.setImageBitmap(BitmapFactory.decodeStream(st))
            st.close()
            introView.addView(iv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        } catch (e: Exception) {
            // sin imagen: se ve la escena 3D de fondo
        }
        val title = hudText(40f, Color.WHITE)
        title.text = "JUEGO 3D"
        root.addView(introView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        introView.addView(title, corner(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0))
        val sub = hudText(18f, Color.rgb(255, 150, 190))
        sub.text = "Lucía y tú ♥"
        introView.addView(sub, corner(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 70, 0, 0))
        val hint = hudText(13f, Color.WHITE)
        hint.text = "Bolita: caminar o conducir · Arrastra la pantalla: mirar a todos lados"
        introView.addView(hint, corner(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 0, 0, 90))
        val play = TextView(this)
        play.text = "JUGAR"
        play.textSize = 22f
        play.typeface = Typeface.DEFAULT_BOLD
        play.setTextColor(Color.WHITE)
        play.gravity = Gravity.CENTER
        val pbg = GradientDrawable()
        pbg.setShape(GradientDrawable.RECTANGLE)
        pbg.setCornerRadius(dp(28).toFloat())
        pbg.setColor(Color.argb(230, 220, 40, 90))
        pbg.setStroke(dp(2), Color.WHITE)
        play.background = pbg
        play.setOnClickListener { startGame() }
        introView.addView(play, corner(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 0, 0, 24, 200, 56))

        setContentView(root)
        showControls(false)
    }

    private fun showControls(on: Boolean) {
        val vis = if (on) View.VISIBLE else View.INVISIBLE
        val all = arrayOf<View>(minimap, joystick, moneyTv, starsTv, hpTv, weaponTv, luciaTv, missionTv, msgTv, enterBtn, fireBtn, weaponBtn)
        for (v in all) v.visibility = vis
    }

    private fun startGame() {
        game.start()
        introView.visibility = View.GONE
        showControls(true)
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
        tv.typeface = Typeface.DEFAULT_BOLD
        return tv
    }

    private fun padButton(label: String, size: Float, onChange: (Boolean) -> Unit): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.textSize = size
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.setTextColor(Color.WHITE)
        tv.gravity = Gravity.CENTER
        val bg = GradientDrawable()
        bg.setShape(GradientDrawable.OVAL)
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
        if (game.intro) return
        val onFoot = game.pcar == null
        moneyTv.text = "\$" + game.money
        val lvl = min(5, game.heat.toInt())
        starsTv.text = "★".repeat(lvl) + "☆".repeat(5 - lvl)
        val car = game.pcar
        hpTv.text = "Vida: " + max(0, game.hp.toInt()) + "%" + if (car != null) "   Coche: " + max(0, car.hp.toInt()) + "%" else ""
        val w = WEAPONS[game.curW]
        weaponTv.text = w.name + ": " + game.ammo[game.curW]
        luciaTv.text = if (game.lucia.inCar) "Lucía ♥ en el coche" else "Lucía ♥ contigo"
        enterBtn.text = if (onFoot) "SUBIR" else "BAJAR"
        val dx = game.missionX - game.player.x
        val dz = game.missionZ - game.player.z
        val d = Math.sqrt((dx * dx + dz * dz).toDouble()).toInt()
        missionTv.text = "Misión: llega al punto amarillo ($d m)"
        msgTv.text = if (game.wastedT > 0f) "¡MUERTO!" else if (game.msgT > 0f) game.msg else ""
        minimap.invalidate()
    }

    // ----- teclado (para probar en emulador / con mando) -----
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (key(keyCode, true)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (key(keyCode, false)) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun key(code: Int, down: Boolean): Boolean {
        when (code) {
            KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_DPAD_UP -> game.inGas = down
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_DPAD_DOWN -> game.inBrake = down
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_DPAD_LEFT -> game.inLeft = down
            KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_DPAD_RIGHT -> game.inRight = down
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_F -> game.inFire = down
            KeyEvent.KEYCODE_E -> { if (down) game.actEnter = true }
            KeyEvent.KEYCODE_Q -> { if (down) game.actWeapon = true }
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
        game.inLeft = false; game.inRight = false; game.inGas = false
        game.inBrake = false; game.inFire = false
        game.joyX = 0f; game.joyY = 0f; game.lookActive = false
    }
}
