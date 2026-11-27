package com.ayalalopes.juego3d

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** Minimapa con el norte arriba, centrado en el jugador. */
class MiniMap(ctx: Context, private val game: Game) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val s = width.toFloat()
        val range = 170f
        val k = s / (2f * range)
        val p = game.player
        val px = p.x
        val pz = p.z

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(200, 30, 33, 38)
        canvas.drawRect(0f, 0f, s, s, paint)

        canvas.save()
        canvas.clipRect(0f, 0f, s, s)

        val bs = CELL - ROAD
        for (i in 0 until NB) for (j in 0 until NB) {
            val x0 = i * CELL + ROAD / 2f
            val z0 = j * CELL + ROAD / 2f
            paint.color = if (isPark(i, j)) Color.rgb(45, 90, 43) else Color.rgb(96, 99, 106)
            canvas.drawRect(
                s / 2f + (x0 - px) * k, s / 2f + (z0 - pz) * k,
                s / 2f + (x0 + bs - px) * k, s / 2f + (z0 + bs - pz) * k, paint
            )
        }

        // policias (parpadean rojo/azul)
        val cops = game.mapCops
        paint.color = if ((game.time * 6f).toInt() % 2 == 0) Color.rgb(255, 60, 60) else Color.rgb(60, 110, 255)
        var n = 0
        while (n + 1 < cops.size) {
            canvas.drawCircle(s / 2f + (cops[n] - px) * k, s / 2f + (cops[n + 1] - pz) * k, 4f, paint)
            n += 2
        }

        // cajas de armas
        paint.color = Color.rgb(80, 160, 255)
        val cr = game.mapCrates
        var m = 0
        while (m + 1 < cr.size) {
            val cx = s / 2f + (cr[m] - px) * k
            val cz = s / 2f + (cr[m + 1] - pz) * k
            canvas.drawRect(cx - 3f, cz - 3f, cx + 3f, cz + 3f, paint)
            m += 2
        }

        // Lucia
        paint.color = Color.rgb(255, 105, 160)
        canvas.drawCircle(s / 2f + (game.lucia.x - px) * k, s / 2f + (game.lucia.z - pz) * k, 4.5f, paint)

        // mision (se pega al borde si queda fuera)
        var mx = s / 2f + (game.missionX - px) * k
        var mz = s / 2f + (game.missionZ - pz) * k
        mx = mx.coerceIn(6f, s - 6f)
        mz = mz.coerceIn(6f, s - 6f)
        paint.color = Color.rgb(255, 215, 0)
        canvas.drawCircle(mx, mz, 6f, paint)

        // jugador: triangulo orientado
        val a = p.a
        val c = s / 2f
        path.reset()
        path.moveTo(c + cos(a) * 9f, c + sin(a) * 9f)
        path.lineTo(c + cos(a + 2.5f) * 7f, c + sin(a + 2.5f) * 7f)
        path.lineTo(c + cos(a - 2.5f) * 7f, c + sin(a - 2.5f) * 7f)
        path.close()
        paint.color = Color.WHITE
        canvas.drawPath(path, paint)

        canvas.restore()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.WHITE
        canvas.drawRect(0f, 0f, s, s, paint)
    }
}
