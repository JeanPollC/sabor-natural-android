package com.sabornatural.tienda.ui.comun

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.sabornatural.tienda.R

/*
 * Desde Android 15 las apps dibujan de borde a borde: el contenido pasa por debajo de la barra
 * de estado (arriba) y de la barra de navegación (abajo). Estas funciones de extensión ajustan
 * el relleno de una vista con el tamaño de esas barras ("insets"), para que nada quede tapado.
 */

/** Suma la altura de la barra de estado al relleno superior (encabezados de color). */
fun View.rellenarBarraDeEstado() = alRecibirInsets { vista, insets, arriba, _ ->
    vista.updatePadding(top = arriba + insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
}

/**
 * Suma al relleno inferior la barra de navegación del sistema o, si está abierto, el teclado
 * (el mayor de los dos). Así los botones del final de un formulario nunca quedan tapados.
 */
fun View.rellenarBarraDeNavegacion() = alRecibirInsets { vista, insets, _, abajo ->
    val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
    val teclado = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
    vista.updatePadding(bottom = abajo + maxOf(barras, teclado))
}

/** Combina las dos anteriores para pantallas de fondo completo (splash, bienvenida). */
fun View.rellenarBarrasDelSistema() = alRecibirInsets { vista, insets, arriba, abajo ->
    val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
    vista.updatePadding(top = arriba + barras.top, bottom = abajo + barras.bottom)
}

/** Da a la vista exactamente el alto de la barra de estado (franja de color fija detrás de ella). */
fun View.igualarAltoBarraDeEstado() = alRecibirInsets { vista, insets, _, _ ->
    vista.layoutParams = vista.layoutParams.apply {
        height = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
    }
}

/**
 * Registra el ajuste y pide al sistema que envíe los insets otra vez: las vistas de un Fragment
 * se agregan después de que la ventana ya repartió los insets, y sin esto no los recibirían.
 */
private fun View.alRecibirInsets(ajuste: (View, WindowInsetsCompat, Int, Int) -> Unit) {
    // Se guarda el relleno original la primera vez, para no sumar la barra dos veces si la
    // función se vuelve a llamar sobre la misma vista (por ejemplo, al refrescar una pantalla)
    val original = getTag(R.id.relleno_original) as? IntArray
        ?: intArrayOf(paddingTop, paddingBottom).also { setTag(R.id.relleno_original, it) }
    val arribaOriginal = original[0]
    val abajoOriginal = original[1]
    ViewCompat.setOnApplyWindowInsetsListener(this) { vista, insets ->
        ajuste(vista, insets, arribaOriginal, abajoOriginal)
        insets
    }
    if (isAttachedToWindow) {
        ViewCompat.requestApplyInsets(this)
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                ViewCompat.requestApplyInsets(v)
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }
}
