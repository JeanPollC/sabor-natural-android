package com.sabornatural.tienda.ui.comun

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity base de todas las pantallas. Activa el modo de borde a borde con íconos claros en la
 * barra de estado, porque todas las pantallas empiezan con un encabezado verde.
 */
abstract class PantallaBase : AppCompatActivity() {

    /** true en pantallas con fondo verde de arriba abajo (splash, bienvenida). */
    protected open val fondoOscuroCompleto: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = if (fondoOscuroCompleto) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            },
        )
        super.onCreate(savedInstanceState)
    }
}
