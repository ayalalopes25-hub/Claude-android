package com.ayalalopes.bitacorademo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Modo en vivo: captura la pantalla con MediaProjection y muestra un panel flotante
 * que envía el fotograma a Gemini cuando lo tocas (o cada 20 s en modo automático).
 */
class ScreenService : Service() {

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val CHANNEL = "screen"
        const val AUTO_MS = 20000L
        val ACCENT = Color.parseColor("#3DDBB0")
        val GREEN = Color.parseColor("#2ECC71")
        val RED = Color.parseColor("#E74C3C")
        val BG = Color.parseColor("#0B1512")
        val MUTED = Color.parseColor("#9DB0A9")
        val TEXT = Color.parseColor("#E8F3EF")
    }

    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var vDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private lateinit var wm: WindowManager
    private var panel: LinearLayout? = null
    private lateinit var probText: TextView
    private lateinit var actionText: TextView
    private lateinit var detailText: TextView
    private lateinit var autoBtn: Button
    private var auto = false
    private var busy = false
    private var stopped = false
    private var width = 0
    private var height = 0
    private var density = 0

    private val autoRunnable = object : Runnable {
        override fun run() {
            if (auto && !stopped) {
                scan()
                main.postDelayed(this, AUTO_MS)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (projection != null) return START_NOT_STICKY
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        try {
            wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val proj = mpm.getMediaProjection(code, data)
            projection = proj
            proj.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    main.post { stopAll() }
                }
            }, main)
            readSize()
            val r = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            reader = r
            vDisplay = proj.createVirtualDisplay(
                "bitacora-scan", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null
            )
            buildOverlay()
        } catch (e: Exception) {
            stopAll()
        }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder: Notification.Builder
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Análisis de pantalla", NotificationManager.IMPORTANCE_LOW)
            )
            builder = Notification.Builder(this, CHANNEL)
        } else {
            builder = Notification.Builder(this)
        }
        val n = builder
            .setContentTitle("Bitácora IA activa")
            .setContentText("Usa el panel flotante para analizar la pantalla")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, n)
        }
    }

    @Suppress("DEPRECATION")
    private fun readSize() {
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        width = dm.widthPixels
        height = dm.heightPixels
        density = dm.densityDpi
    }

    /** Si el usuario giró la pantalla, ajusta el tamaño de la captura. */
    private fun ensureSize() {
        val old = Triple(width, height, density)
        readSize()
        if (Triple(width, height, density) == old) return
        val r = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        vDisplay?.resize(width, height, density)
        vDisplay?.surface = r.surface
        reader?.close()
        reader = r
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun smallButton(label: String, bg: Int, fg: Int, onClick: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(fg)
        typeface = Typeface.DEFAULT_BOLD
        textSize = 12f
        isAllCaps = false
        minimumWidth = 0
        minWidth = 0
        setPadding(dp(10), 0, dp(10), 0)
        background = GradientDrawable().apply {
            setColor(bg)
            cornerRadius = dp(10).toFloat()
        }
        setOnClickListener { onClick() }
    }

    private fun buildOverlay() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            minimumWidth = dp(200)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE111F1B"))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), ACCENT)
            }
        }

        val title = TextView(this).apply {
            text = "≡  BITÁCORA IA  (arrastra)"
            textSize = 11f
            setTextColor(MUTED)
            typeface = Typeface.DEFAULT_BOLD
        }
        box.addView(title)

        probText = TextView(this).apply {
            text = "—"
            textSize = 34f
            setTextColor(TEXT)
            typeface = Typeface.DEFAULT_BOLD
        }
        box.addView(probText)
        actionText = TextView(this).apply {
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        box.addView(actionText)
        detailText = TextView(this).apply {
            text = "Toca ANALIZAR"
            textSize = 12f
            setTextColor(TEXT)
        }
        box.addView(detailText)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        val lp = { LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(4) } }
        row.addView(smallButton("ANALIZAR", ACCENT, BG) { scan() }, lp())
        autoBtn = smallButton("AUTO", BG, ACCENT) { toggleAuto() }
        row.addView(autoBtn, lp())
        row.addView(smallButton("✕", BG, RED) { stopAll() }, LinearLayout.LayoutParams(dp(40), dp(40)))
        box.addView(row)

        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(120)
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        title.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchX = e.rawX; touchY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - touchX).toInt()
                    params.y = startY + (e.rawY - touchY).toInt()
                    wm.updateViewLayout(box, params)
                }
            }
            true
        }

        wm.addView(box, params)
        panel = box
    }

    private fun toggleAuto() {
        auto = !auto
        autoBtn.text = if (auto) "AUTO ●" else "AUTO"
        autoBtn.setTextColor(if (auto) BG else ACCENT)
        (autoBtn.background as GradientDrawable).setColor(if (auto) ACCENT else BG)
        main.removeCallbacks(autoRunnable)
        if (auto) {
            detailText.text = "Auto: analiza cada ${AUTO_MS / 1000} s"
            main.post(autoRunnable)
        }
    }

    private fun show(prob: String, action: String, detail: String, color: Int = TEXT) {
        probText.text = prob
        actionText.text = action
        actionText.setTextColor(color)
        detailText.text = detail
    }

    private fun scan() {
        if (busy || stopped) return
        val prefs = getSharedPreferences("bitacora", MODE_PRIVATE)
        val key = prefs.getString("geminikey", "").orEmpty()
        if (key.isEmpty()) {
            show("—", "", "Guarda tu clave de Gemini en la app")
            return
        }
        val model = prefs.getString("model", Gemini.DEFAULT_MODEL).orEmpty()
            .ifEmpty { Gemini.DEFAULT_MODEL }
        busy = true
        show("…", "", "Capturando…")
        // Oculta el panel para que no salga en la captura
        panel?.visibility = View.INVISIBLE
        main.postDelayed({
            if (stopped) return@postDelayed
            ensureSize()
            val r = reader
            Thread {
                var result: Any
                try {
                    val bmp = grabFrame(r) ?: throw RuntimeException(
                        "No llegó imagen. Mueve el gráfico o inténtalo de nuevo."
                    )
                    main.post { panel?.visibility = View.VISIBLE; show("…", "", "Analizando…") }
                    if (isBlack(bmp)) throw RuntimeException(
                        "Pantalla negra: la app bloquea la captura. Usa Pocket Option desde el navegador."
                    )
                    result = Gemini.analyze(key, model, encode(bmp))
                } catch (e: Exception) {
                    result = e
                }
                main.post {
                    panel?.visibility = View.VISIBLE
                    busy = false
                    when (result) {
                        is JSONObject -> showResult(result as JSONObject)
                        is Exception -> show("—", "", (result as Exception).message ?: "Error")
                    }
                }
            }.start()
        }, 250)
    }

    private fun showResult(json: JSONObject) {
        val prob = json.optInt("prob", 50).coerceIn(50, 75)
        val sell = json.optString("accion").uppercase() == "SELL"
        val pattern = json.optString("patron", "—")
        val risk = json.optString("riesgo", "—").uppercase()
        show(
            "$prob%", if (sell) "SELL" else "BUY",
            "$pattern · Riesgo $risk\nEstimación de la IA, no un cálculo",
            if (sell) RED else GREEN
        )
    }

    /** Descarta fotogramas viejos y espera uno nuevo (el gráfico en vivo los genera sin parar). */
    private fun grabFrame(r: ImageReader?): Bitmap? {
        if (r == null) return null
        try {
            r.acquireLatestImage()?.close()
        } catch (e: Exception) {
            // ignorar
        }
        repeat(14) {
            val img: Image? = try {
                r.acquireLatestImage()
            } catch (e: Exception) {
                null
            }
            if (img != null) {
                try {
                    return toBitmap(img)
                } finally {
                    img.close()
                }
            }
            Thread.sleep(150)
        }
        return null
    }

    private fun toBitmap(img: Image): Bitmap {
        val plane = img.planes[0]
        val pixelStride = plane.pixelStride
        val rowPadding = plane.rowStride - pixelStride * img.width
        val wide = Bitmap.createBitmap(
            img.width + rowPadding / pixelStride, img.height, Bitmap.Config.ARGB_8888
        )
        wide.copyPixelsFromBuffer(plane.buffer)
        return Bitmap.createBitmap(wide, 0, 0, img.width, img.height)
    }

    private fun isBlack(bmp: Bitmap): Boolean {
        val stepX = maxOf(1, bmp.width / 20)
        val stepY = maxOf(1, bmp.height / 20)
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val p = bmp.getPixel(x, y)
                if (Color.red(p) + Color.green(p) + Color.blue(p) > 24) return false
                x += stepX
            }
            y += stepY
        }
        return true
    }

    private fun encode(bmp: Bitmap): String {
        val longSide = maxOf(bmp.width, bmp.height)
        val scaled = if (longSide > 1600) {
            val f = 1600f / longSide
            Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true)
        } else bmp
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun stopAll() {
        if (stopped) return
        stopped = true
        auto = false
        main.removeCallbacksAndMessages(null)
        try { panel?.let { wm.removeView(it) } } catch (e: Exception) { }
        panel = null
        try { vDisplay?.release() } catch (e: Exception) { }
        try { reader?.close() } catch (e: Exception) { }
        try { projection?.stop() } catch (e: Exception) { }
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        stopAll()
        super.onDestroy()
    }
}
