package com.ayalalopes.holamundo

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val texto = TextView(this).apply {
            text = "¡Hola Mundo!\nCompilado con GitHub Actions"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
        }
        setContentView(texto)
    }
}
