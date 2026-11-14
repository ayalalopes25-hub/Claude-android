package com.ayalalopes.bitacorademo

import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Llamada a la API de Gemini usada por el modo en vivo. */
/** Opciones de tiempo de expiración de la operación (en segundos). */
object Expiry {
    val OPTIONS = listOf(5, 15, 60, 180)
    const val DEFAULT = 60
    fun short(s: Int) = if (s < 60) "${s}s" else "${s / 60}m"
    fun long(s: Int) = when (s) {
        5 -> "5 segundos"
        15 -> "15 segundos"
        60 -> "1 minuto"
        180 -> "3 minutos"
        else -> "$s segundos"
    }
}

object Gemini {
    const val DEFAULT_MODEL = "gemini-3.6-flash"

    const val MODE_CLASSIC = "clasico"
    const val MODE_STRICT = "estricto"

    /** Modo anterior (se conserva): señal rápida BUY/SELL con patrón y riesgo. */
    const val PROMPT_CLASSIC = "Eres un analista de acción del precio. Recibes una captura de un gráfico de velas " +
                "de opciones binarias. Observa solo las últimas 4 a 6 velas del extremo derecho. " +
                "Evalúa cuerpos, mechas, fuerza inmediata y patrón (pin bar, envolvente, continuación, giro). " +
                "Se te indica el tiempo de expiración de la operación: a 5 o 15 segundos pesa solo el impulso inmediato de las últimas velas; a 1 o 3 minutos pesa más la tendencia reciente. " +
                "Responde SOLO con JSON: {\"prob\": entero entre 50 y 75, \"accion\": \"BUY\" o \"SELL\", " +
                "\"patron\": \"nombre corto\", \"riesgo\": \"LOW\" o \"MEDIUM\" o \"HIGH\"}. " +
                "prob es tu confianza subjetiva, no una probabilidad calculada: sé conservador y casi " +
                "nunca superes 65. Si la imagen no es un gráfico de velas legible, responde " +
                "{\"error\": \"motivo breve\"}."

    /** Modo estricto: 3 filtros y umbral de paciencia. */
    const val PROMPT_STRICT =
        "Eres un trader institucional experto en acción del precio y microestructura para operaciones " +
            "de importe fijo de corto plazo. Analiza la captura del gráfico aplicando 3 filtros. " +
            "1) Últimas 3 velas cerradas: compara cuerpos y mechas; identifica dominancia clara o indecisión. " +
            "Si la vela previa tiene una mecha larga de rechazo en la dirección de la posible señal, " +
            "reduce esa probabilidad un 25%. " +
            "2) Tendencia: indica si la señal va a favor o en contra del impulso visible; en rango lateral " +
            "estrecho o consolidación usa EVITAR. " +
            "3) Soportes y resistencias: espacio libre favorece la continuidad; chocar contra un nivel donde " +
            "el precio ya rebotó o frenó reduce la probabilidad. " +
            "Umbral estricto: responde OPERAR solo si la confluencia es alta, el precio está en el punto de " +
            "entrada óptimo y las velas previas eliminan la ambigüedad. Ante indecisión, mechas de rechazo " +
            "excesivas, movimiento ya muy extendido o consolidación, responde ESPERAR o EVITAR. " +
            "Se te indica el tiempo de expiración: a 5 o 15 segundos pesa solo el impulso inmediato; " +
            "a 1 o 3 minutos pesa más la tendencia reciente. " +
            "Responde SOLO con JSON: {\"alcista\": entero 0-100, \"bajista\": entero 0-100 (deben sumar 100), " +
            "\"veredicto\": \"OPERAR\" o \"ESPERAR\" o \"EVITAR\", " +
            "\"diagnostico\": \"máximo 2 líneas sobre cuerpos, mechas de las últimas 3 velas y zona del precio\"}. " +
            "Son estimaciones subjetivas: nunca pongas más de 70 en un lado. " +
            "Si la imagen no es un gráfico de velas legible, responde {\"error\": \"motivo breve\"}."

    data class Display(val big: String, val line2: String, val line2Color: Int, val detail: String, val sell: Boolean)

    /** Convierte la respuesta de cualquiera de los dos modos en algo mostrable. */
    fun format(json: JSONObject): Display {
        val green = Color.parseColor("#2ECC71")
        val red = Color.parseColor("#E74C3C")
        val amber = Color.parseColor("#F5B041")
        if (json.has("veredicto")) {
            val up = json.optInt("alcista", 50).coerceIn(0, 100)
            val down = json.optInt("bajista", 100 - up).coerceIn(0, 100)
            val v = json.optString("veredicto", "ESPERAR").uppercase()
            val color = when (v) {
                "OPERAR" -> if (up >= down) green else red
                "EVITAR" -> red
                else -> amber
            }
            return Display(
                v, "$up% alcista / $down% bajista", color,
                json.optString("diagnostico", "") + "\nEstimación de la IA, no un cálculo", down > up
            )
        }
        val prob = json.optInt("prob", 50).coerceIn(50, 75)
        val sell = json.optString("accion").uppercase() == "SELL"
        val pattern = json.optString("patron", "—")
        val risk = json.optString("riesgo", "—").uppercase()
        return Display(
            "$prob%", if (sell) "SELL" else "BUY", if (sell) red else green,
            "$pattern · Riesgo $risk\nEstimación de la IA, no un cálculo", sell
        )
    }


    fun analyze(
        key: String, model: String, imageB64: String, expirySec: Int, mode: String
    ): JSONObject {
        val image = JSONObject().put(
            "inline_data",
            JSONObject().put("mime_type", "image/jpeg").put("data", imageB64)
        )
        val text = JSONObject().put("text", "Analiza esta captura. La operación expira en ${Expiry.long(expirySec)}.")
        val body = JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", if (mode == MODE_CLASSIC) PROMPT_CLASSIC else PROMPT_STRICT)))
            )
            .put(
                "contents",
                JSONArray().put(JSONObject().put("parts", JSONArray().put(image).put(text)))
            )
            .put(
                "generationConfig",
                JSONObject().put("responseMimeType", "application/json").put("maxOutputTokens", 1024)
            )

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.doOutput = true
            conn.setRequestProperty("x-goog-api-key", key)
            conn.setRequestProperty("content-type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = try {
                    JSONObject(raw).getJSONObject("error").getString("message")
                } catch (e: Exception) {
                    raw.take(150)
                }
                throw RuntimeException("Error $code: $msg")
            }
            val parts = JSONObject(raw).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text", ""))
            val answer = sb.toString()

            val start = answer.indexOf('{')
            val end = answer.lastIndexOf('}')
            if (start < 0 || end <= start) throw RuntimeException("Respuesta inesperada de la IA.")
            val json = JSONObject(answer.substring(start, end + 1))
            if (json.has("error")) throw RuntimeException(json.optString("error", "No se pudo analizar."))
            return json
        } finally {
            conn.disconnect()
        }
    }
}
