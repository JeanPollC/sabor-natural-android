package com.sabornatural.tienda.ui.comun

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Desde Android 15 las apps dibujan de borde a borde: el contenido pasa por debajo de la barra
 * de estado. Esta función suma la altura de esa barra al relleno superior de la vista, para que
 * los encabezados de color la cubran pero sus textos no queden tapados.
 */
fun View.rellenarBarraDeEstado() {
    val rellenoOriginal = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { vista, insets ->
        val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        vista.updatePadding(top = rellenoOriginal + barras.top)
        insets
    }
}

/** Da a la vista exactamente el alto de la barra de estado (franja de color fija detrás de ella). */
fun View.igualarAltoBarraDeEstado() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { vista, insets ->
        vista.layoutParams = vista.layoutParams.apply {
            height = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        }
        insets
    }
}

/** Combina las dos anteriores para pantallas de fondo completo (splash, bienvenida). */
fun View.rellenarBarrasDelSistema() {
    val arribaOriginal = paddingTop
    val abajoOriginal = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { vista, insets ->
        val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        vista.updatePadding(top = arribaOriginal + barras.top, bottom = abajoOriginal + barras.bottom)
        insets
    }
}

/**
 * Suma al relleno inferior la barra de navegación del sistema o, si está abierto, el teclado
 * (el mayor de los dos). Así los botones del final de un formulario nunca quedan tapados.
 */
fun View.rellenarBarraDeNavegacion() {
    val rellenoOriginal = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { vista, insets ->
        val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val teclado = insets.getInsets(WindowInsetsCompat.Type.ime())
        vista.updatePadding(bottom = rellenoOriginal + maxOf(barras.bottom, teclado.bottom))
        insets
    }
}
