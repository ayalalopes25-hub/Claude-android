package com.ayalalopes.bitacorademo

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.util.Base64
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    data class Trade(val time: Long, val pair: String, val dir: String, val win: Boolean)

    private companion object {
        val BG = Color.parseColor("#0B1512")
        val CARD = Color.parseColor("#111F1B")
        val ACCENT = Color.parseColor("#3DDBB0")
        val GREEN = Color.parseColor("#2ECC71")
        val RED = Color.parseColor("#E74C3C")
        val MUTED = Color.parseColor("#7C8F89")
        val TEXT = Color.parseColor("#E8F3EF")
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val REQ_PICK = 1
        const val REQ_CAPTURE = 2
        const val DEFAULT_MODEL = "gemini-3.6-flash"
    }

    private lateinit var prefs: SharedPreferences
    private val trades = mutableListOf<Trade>()
    private var dir = "BUY"

    private lateinit var rateText: TextView
    private lateinit var rateBar: ProgressBar
    private lateinit var countsText: TextView
    private lateinit var streakText: TextView
    private lateinit var breakEvenText: TextView
    private lateinit var verdictText: TextView
    private lateinit var payoutInput: EditText
    private lateinit var pairInput: EditText
    private lateinit var buyBtn: Button
    private lateinit var sellBtn: Button
    private lateinit var history: LinearLayout
    private lateinit var aiProb: TextView
    private lateinit var aiAction: TextView
    private lateinit var aiPattern: TextView
    private lateinit var aiStatus: TextView
    private lateinit var keyInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var keyStatus: TextView
    private lateinit var liveStatus: TextView
    private val expiryBtns = mutableMapOf<Int, Button>()
    private val modeBtns = mutableMapOf<String, Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("bitacora", MODE_PRIVATE)
        prefs.edit().remove("apikey").apply()
        load()
        window.statusBarColor = BG
        window.navigationBarColor = BG
        buildUi()
        refresh()
        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    // ---------- Análisis con IA ----------

    @Suppress("DEPRECATION")
    private fun handleShare(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND && i.type?.startsWith("image/") == true) {
            val uri = i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) analyze(uri)
        }
    }

    private fun startLive() {
        if (prefs.getString("geminikey", "").isNullOrEmpty()) {
            liveStatus.text = "Primero guarda tu clave de Gemini (sección de abajo)."
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            liveStatus.text = "Activa \"Mostrar sobre otras apps\" para esta app y vuelve a pulsar el botón."
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    private fun pickImage() {
        val i = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        startActivityForResult(Intent.createChooser(i, "Elegir captura"), REQ_PICK)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK && resultCode == RESULT_OK) {
            data?.data?.let { analyze(it) }
        }
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                val svc = Intent(this, ScreenService::class.java)
                    .putExtra(ScreenService.EXTRA_CODE, resultCode)
                    .putExtra(ScreenService.EXTRA_DATA, data)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc) else startService(svc)
                liveStatus.text = "Activo. Abre Pocket Option: verás el panel flotante encima."
                moveTaskToBack(true)
            } else {
                liveStatus.text = "Se canceló el permiso de captura."
            }
        }
    }

    private fun analyze(uri: Uri) {
        val key = prefs.getString("geminikey", "").orEmpty()
        if (key.isEmpty()) {
            aiStatus.text = "Primero guarda tu clave API en la sección de abajo."
            return
        }
        aiProb.text = "…"
        aiAction.text = ""
        aiPattern.text = ""
        aiStatus.text = "Analizando captura…"
        Thread {
            val result: Any = try {
                Gemini.analyze(
                    key,
                    prefs.getString("model", DEFAULT_MODEL).orEmpty().ifEmpty { DEFAULT_MODEL },
                    encodeImage(uri),
                    prefs.getInt("expiry", Expiry.DEFAULT),
                    prefs.getString("mode", Gemini.MODE_STRICT).orEmpty()
                )
            } catch (e: Exception) {
                e
            }
            runOnUiThread {
                when (result) {
                    is JSONObject -> showResult(result)
                    is Exception -> {
                        aiProb.text = "—"
                        aiStatus.text = result.message ?: "Error desconocido"
                    }
                }
            }
        }.start()
    }

    private fun encodeImage(uri: Uri): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 2000) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw RuntimeException("No se pudo leer la imagen.")
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun showResult(json: JSONObject) {
        val d = Gemini.format(json)
        aiProb.text = d.big
        aiAction.text = d.line2
        aiAction.setTextColor(d.line2Color)
        aiPattern.text = d.detail
        aiStatus.text = "Regístrala abajo y compárala con tu acierto real."
        dir = if (d.sell) "SELL" else "BUY"
        updateDirButtons()
    }

    // ---------- Datos ----------

    private fun load() {
        trades.clear()
        val raw = prefs.getString("trades", "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                trades.add(
                    Trade(o.getLong("t"), o.getString("p"), o.getString("d"), o.getBoolean("w"))
                )
            }
        } catch (e: Exception) {
            trades.clear()
        }
    }

    private fun save() {
        val arr = JSONArray()
        trades.forEach {
            arr.put(
                JSONObject().put("t", it.time).put("p", it.pair).put("d", it.dir).put("w", it.win)
            )
        }
        prefs.edit().putString("trades", arr.toString()).apply()
    }

    private fun payout(): Int = payoutInput.text.toString().toIntOrNull()?.coerceIn(1, 500) ?: 92

    private fun addTrade(win: Boolean) {
        val pair = pairInput.text.toString().trim().ifEmpty { "Sin par" }
        trades.add(Trade(System.currentTimeMillis(), pair, dir, win))
        save()
        refresh()
    }

    // ---------- Interfaz ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun round(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun tv(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = round(CARD, 16)
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
    }

    private fun button(label: String, bg: Int, fg: Int, onClick: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(fg)
        typeface = Typeface.DEFAULT_BOLD
        background = round(bg, 12)
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun weighted() = LinearLayout.LayoutParams(0, dp(52), 1f).apply {
        marginStart = dp(4)
        marginEnd = dp(4)
    }

    private fun input(hint: String, type: Int) = EditText(this).apply {
        setHint(hint)
        setHintTextColor(MUTED)
        setTextColor(TEXT)
        inputType = type
        setSingleLine()
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = round(BG, 10)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(32))
        }

        root.addView(tv("BITÁCORA DEMO", 22f, TEXT, true))
        root.addView(tv("Práctica con dinero virtual", 13f, MUTED))

        // Temporalidad de la entrada
        val tf = card()
        tf.addView(tv("TEMPORALIDAD DE LA ENTRADA", 11f, MUTED, true))
        tf.addView(
            tv(
                "Tiempo de expiración de la operación. Se envía a la IA y también puedes cambiarlo desde el panel flotante.",
                12f, MUTED
            ).apply { setPadding(0, dp(6), 0, 0) }
        )
        val tfRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) }
        }
        Expiry.OPTIONS.forEach { sec ->
            val b = button(Expiry.short(sec), ACCENT, BG) {
                prefs.edit().putInt("expiry", sec).apply()
                updateExpiryButtons()
            }
            expiryBtns[sec] = b
            tfRow.addView(b, weighted())
        }
        tf.addView(tfRow)
        root.addView(tf)
        updateExpiryButtons()

        // Modo de análisis
        val mc = card()
        mc.addView(tv("MODO DE ANÁLISIS", 11f, MUTED, true))
        mc.addView(
            tv(
                "Estricto: 3 filtros y filtro de paciencia, con veredicto OPERAR / ESPERAR / EVITAR " +
                    "(la mayoría saldrán en ESPERAR o EVITAR). Clásico: el análisis anterior, con BUY o SELL, " +
                    "patrón y riesgo.",
                12f, MUTED
            ).apply { setPadding(0, dp(6), 0, 0) }
        )
        val mcRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) }
        }
        listOf(Gemini.MODE_STRICT to "ESTRICTO", Gemini.MODE_CLASSIC to "CLÁSICO").forEach { (id, label) ->
            val b = button(label, ACCENT, BG) {
                prefs.edit().putString("mode", id).apply()
                updateModeButtons()
            }
            modeBtns[id] = b
            mcRow.addView(b, weighted())
        }
        mc.addView(mcRow)
        root.addView(mc)
        updateModeButtons()

        // Panel de análisis con IA
        val ai = card()
        ai.addView(tv("ANÁLISIS IA DE LA CAPTURA", 11f, MUTED, true))
        aiProb = tv("—", 56f, TEXT, true)
        ai.addView(aiProb)
        aiAction = tv("", 22f, GREEN, true)
        ai.addView(aiAction)
        aiPattern = tv("", 14f, TEXT)
        ai.addView(aiPattern)
        ai.addView(button("ELEGIR CAPTURA Y ANALIZAR", ACCENT, BG) { pickImage() }.apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(52)).apply { topMargin = dp(12) }
        })
        aiStatus = tv(
            "Toma una captura del gráfico y compártela a esta app, o elígela aquí.",
            12f, MUTED
        ).apply { setPadding(0, dp(10), 0, 0) }
        ai.addView(aiStatus)
        root.addView(ai)

        // Modo en vivo
        val live = card()
        live.addView(tv("MODO PANTALLA EN VIVO", 11f, MUTED, true))
        live.addView(
            tv(
                "Muestra un panel flotante encima de Pocket Option. Tócalo para analizar la pantalla " +
                    "al momento, o activa AUTO para que lo haga cada 20 s.",
                13f, TEXT
            ).apply { setPadding(0, dp(6), 0, 0) }
        )
        live.addView(button("ACTIVAR PANEL FLOTANTE", ACCENT, BG) { startLive() }.apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(52)).apply { topMargin = dp(12) }
        })
        liveStatus = tv(
            "Necesita permiso para mostrar sobre otras apps y para capturar la pantalla.",
            12f, MUTED
        ).apply { setPadding(0, dp(10), 0, 0) }
        live.addView(liveStatus)
        root.addView(live)

        // Panel de estadísticas
        val stats = card()
        stats.addView(tv("ACIERTO REAL", 11f, MUTED, true))
        rateText = tv("0%", 56f, TEXT, true)
        stats.addView(rateText)
        rateBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(ACCENT)
            progressBackgroundTintList = ColorStateList.valueOf(BG)
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(10))
        }
        stats.addView(rateBar)
        countsText = tv("", 14f, TEXT).apply { setPadding(0, dp(12), 0, 0) }
        stats.addView(countsText)
        streakText = tv("", 14f, TEXT)
        stats.addView(streakText)
        breakEvenText = tv("", 14f, TEXT)
        stats.addView(breakEvenText)
        verdictText = tv("", 13f, ACCENT).apply { setPadding(0, dp(10), 0, 0) }
        stats.addView(verdictText)
        root.addView(stats)

        // Registrar operación
        val form = card()
        form.addView(tv("REGISTRAR OPERACIÓN", 11f, MUTED, true))

        pairInput = input("Par o activo (ej. BTC OTC)", InputType.TYPE_CLASS_TEXT).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) }
        }
        form.addView(pairInput)

        val payoutRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) }
        }
        payoutRow.addView(tv("Pago del broker (%)", 14f, TEXT).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        payoutInput = input("92", InputType.TYPE_CLASS_NUMBER).apply {
            setText(prefs.getInt("payout", 92).toString())
            layoutParams = LinearLayout.LayoutParams(dp(90), WRAP)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    s.toString().toIntOrNull()?.let { prefs.edit().putInt("payout", it).apply() }
                    if (::history.isInitialized) refresh()
                }
            })
        }
        payoutRow.addView(payoutInput)
        form.addView(payoutRow)

        val dirRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
        }
        buyBtn = button("BUY", GREEN, BG) { dir = "BUY"; updateDirButtons() }
        sellBtn = button("SELL", RED, BG) { dir = "SELL"; updateDirButtons() }
        dirRow.addView(buyBtn, weighted())
        dirRow.addView(sellBtn, weighted())
        form.addView(dirRow)
        updateDirButtons()

        val resultRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) }
        }
        resultRow.addView(button("✔ GANÓ", GREEN, BG) { addTrade(true) }, weighted())
        resultRow.addView(button("✘ PERDIÓ", RED, BG) { addTrade(false) }, weighted())
        form.addView(resultRow)
        root.addView(form)

        // Historial
        val hist = card()
        hist.addView(tv("ÚLTIMAS OPERACIONES", 11f, MUTED, true))
        history = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        hist.addView(history)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
        }
        actions.addView(button("Borrar última", BG, TEXT) {
            if (trades.isNotEmpty()) {
                trades.removeAt(trades.size - 1)
                save()
                refresh()
            }
        }, weighted())
        actions.addView(button("Borrar todo", BG, RED) {
            AlertDialog.Builder(this)
                .setTitle("Borrar todo")
                .setMessage("Se eliminarán todas las operaciones registradas.")
                .setPositiveButton("Borrar") { _, _ ->
                    trades.clear()
                    save()
                    refresh()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }, weighted())
        hist.addView(actions)
        root.addView(hist)

        // Configuración de la clave API
        val cfg = card()
        cfg.addView(tv("CLAVE API (GOOGLE GEMINI)", 11f, MUTED, true))
        cfg.addView(
            tv(
                "Gratis en aistudio.google.com (Get API key). Se guarda solo en este celular. " +
                    "En el nivel gratis, Google puede usar tus capturas para mejorar sus productos.",
                12f, MUTED
            ).apply { setPadding(0, dp(6), 0, 0) }
        )
        keyInput = input(
            "Pega tu clave aquí",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        ).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) }
        }
        cfg.addView(keyInput)
        cfg.addView(tv("Modelo (cámbialo si Google lo renombra)", 12f, MUTED).apply {
            setPadding(0, dp(10), 0, 0)
        })
        modelInput = input("gemini-3.6-flash", InputType.TYPE_CLASS_TEXT).apply {
            setText(prefs.getString("model", DEFAULT_MODEL))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(4) }
        }
        cfg.addView(modelInput)
        keyStatus = tv(
            if (prefs.getString("geminikey", "").isNullOrEmpty()) "Sin clave guardada." else "Clave guardada ✔",
            12f, ACCENT
        ).apply { setPadding(0, dp(8), 0, 0) }
        cfg.addView(keyStatus)
        val keyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) }
        }
        keyRow.addView(button("Guardar", BG, ACCENT) {
            val k = keyInput.text.toString().trim()
            val m = modelInput.text.toString().trim().ifEmpty { DEFAULT_MODEL }
            prefs.edit().putString("model", m).apply()
            if (k.isNotEmpty()) {
                prefs.edit().putString("geminikey", k).apply()
                keyInput.setText("")
            }
            keyStatus.text = if (prefs.getString("geminikey", "").isNullOrEmpty())
                "Sin clave guardada." else "Clave guardada ✔"
        }, weighted())
        keyRow.addView(button("Quitar clave", BG, RED) {
            prefs.edit().remove("geminikey").apply()
            keyStatus.text = "Sin clave guardada."
        }, weighted())
        cfg.addView(keyRow)
        root.addView(cfg)

        root.addView(
            tv(
                "Solo para práctica y registro. No es una recomendación de inversión.",
                11f, MUTED
            ).apply { setPadding(0, dp(16), 0, 0) }
        )

        setContentView(ScrollView(this).apply {
            setBackgroundColor(BG)
            isFillViewport = true
            addView(root)
        })
    }

    private fun updateModeButtons() {
        val current = prefs.getString("mode", Gemini.MODE_STRICT)
        modeBtns.forEach { (id, b) -> b.alpha = if (id == current) 1f else 0.35f }
    }

    private fun updateExpiryButtons() {
        val current = prefs.getInt("expiry", Expiry.DEFAULT)
        expiryBtns.forEach { (sec, b) -> b.alpha = if (sec == current) 1f else 0.35f }
    }

    private fun updateDirButtons() {
        buyBtn.alpha = if (dir == "BUY") 1f else 0.35f
        sellBtn.alpha = if (dir == "SELL") 1f else 0.35f
    }

    private fun refresh() {
        val n = trades.size
        val wins = trades.count { it.win }
        val rate = if (n > 0) wins * 100.0 / n else 0.0
        val breakEven = 10000.0 / (100 + payout())

        rateText.text = "${rate.toInt()}%"
        rateBar.progress = rate.toInt()
        countsText.text = "Operaciones: $n  ·  Ganadas: $wins  ·  Perdidas: ${n - wins}"

        if (n == 0) {
            streakText.text = "Racha actual: —"
        } else {
            val last = trades.last().win
            var streak = 0
            for (t in trades.asReversed()) {
                if (t.win == last) streak++ else break
            }
            streakText.text = "Racha actual: $streak ${if (last) "ganadas" else "perdidas"}"
        }

        breakEvenText.text = "Necesitas acertar más de %.1f%% para no perder".format(breakEven)

        verdictText.text = when {
            n < 30 -> "Muestra pequeña: registra al menos 100 operaciones antes de sacar conclusiones."
            rate > breakEven && n < 100 -> "Por encima del equilibrio, pero la muestra aún es corta."
            rate > breakEven -> "Por encima del equilibrio en esta muestra."
            else -> "Por debajo del equilibrio en esta muestra."
        }

        history.removeAllViews()
        if (n == 0) {
            history.addView(tv("Aún no hay operaciones.", 14f, MUTED).apply {
                setPadding(0, dp(8), 0, 0)
            })
        } else {
            val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            trades.takeLast(20).asReversed().forEach { t ->
                val row = tv(
                    "${fmt.format(Date(t.time))}  ·  ${t.pair}  ·  ${t.dir}  ·  " +
                        if (t.win) "✔ Ganó" else "✘ Perdió",
                    14f,
                    if (t.win) GREEN else RED
                ).apply { setPadding(0, dp(8), 0, 0) }
                history.addView(row)
            }
        }
    }
}
