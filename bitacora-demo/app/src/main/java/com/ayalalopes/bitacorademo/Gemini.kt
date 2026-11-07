package com.ayalalopes.bitacorademo

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

    const val SYSTEM_PROMPT =
        "Eres un analista de acción del precio. Recibes una captura de un gráfico de velas " +
            "de opciones binarias. Observa solo las últimas 4 a 6 velas del extremo derecho. " +
            "Evalúa cuerpos, mechas, fuerza inmediata y patrón (pin bar, envolvente, continuación, giro). " +
            "Se te indica el tiempo de expiración de la operación: a 5 o 15 segundos pesa solo el impulso inmediato de las últimas velas; a 1 o 3 minutos pesa más la tendencia reciente. " +
            "Responde SOLO con JSON: {\"prob\": entero entre 50 y 75, \"accion\": \"BUY\" o \"SELL\", " +
            "\"patron\": \"nombre corto\", \"riesgo\": \"LOW\" o \"MEDIUM\" o \"HIGH\"}. " +
            "prob es tu confianza subjetiva, no una probabilidad calculada: sé conservador y casi " +
            "nunca superes 65. Si la imagen no es un gráfico de velas legible, responde " +
            "{\"error\": \"motivo breve\"}."

    fun analyze(key: String, model: String, imageB64: String, expirySec: Int): JSONObject {
        val image = JSONObject().put(
            "inline_data",
            JSONObject().put("mime_type", "image/jpeg").put("data", imageB64)
        )
        val text = JSONObject().put("text", "Analiza esta captura. La operación expira en ${Expiry.long(expirySec)}.")
        val body = JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
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
