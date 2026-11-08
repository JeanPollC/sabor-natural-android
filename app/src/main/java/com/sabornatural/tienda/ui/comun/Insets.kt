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

/** Igual que [rellenarBarraDeEstado], pero para la barra de navegación del sistema (abajo). */
fun View.rellenarBarraDeNavegacion() {
    val rellenoOriginal = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { vista, insets ->
        val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        vista.updatePadding(bottom = rellenoOriginal + barras.bottom)
        insets
    }
}
