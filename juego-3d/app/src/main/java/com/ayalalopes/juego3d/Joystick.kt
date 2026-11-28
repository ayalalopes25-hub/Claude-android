package com.ayalalopes.juego3d

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * Joystick con bolita: arrastra en cualquier direccion.
 * Informa (x, y) entre -1 y 1; x derecha+, y arriba (adelante)+.
 */
class Joystick(ctx: Context, private val onMove: (Float, Float) -> Unit) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var kx = 0f
    private var ky = 0f
    private var active = false

    override fun onDraw(canvas: Canvas) {
        val s = width.toFloat()
        val c = s / 2f
        val r = c - 4f
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(60, 255, 255, 255)
        canvas.drawCircle(c, c, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.argb(170, 255, 255, 255)
        canvas.drawCircle(c, c, r, paint)
        // marcas de las cuatro direcciones
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(120, 255, 255, 255)
        canvas.drawCircle(c, c - r * 0.82f, 4f, paint)
        canvas.drawCircle(c, c + r * 0.82f, 4f, paint)
        canvas.drawCircle(c - r * 0.82f, c, 4f, paint)
        canvas.drawCircle(c + r * 0.82f, c, 4f, paint)
        // la bolita
        val br = r * 0.38f
        paint.style = Paint.Style.FILL
        paint.color = if (active) Color.argb(240, 255, 255, 255) else Color.argb(180, 255, 255, 255)
        canvas.drawCircle(c + kx, c + ky, br, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.argb(255, 110, 110, 110)
        canvas.drawCircle(c + kx, c + ky, br, paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val s = width.toFloat()
        val c = s / 2f
        val lim = s * 0.30f
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                var dx = e.x - c
                var dy = e.y - c
                val len = hypot(dx, dy)
                if (len > lim) {
                    dx *= lim / len
                    dy *= lim / len
                }
                kx = dx
                ky = dy
                active = true
                onMove(dx / lim, -dy / lim)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                kx = 0f
                ky = 0f
                active = false
                onMove(0f, 0f)
            }
        }
        invalidate()
        return true
    }
}
