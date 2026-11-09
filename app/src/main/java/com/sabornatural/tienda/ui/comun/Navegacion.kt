package com.sabornatural.tienda.ui.comun

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sabornatural.tienda.R
import com.sabornatural.tienda.databinding.IncludeBarraSuperiorBinding

/** Lo implementan las Activities de rol para que sus Fragments puedan abrir el menú lateral. */
interface ConMenuLateral {
    fun abrirMenuLateral()
}

/** Animaciones de entrada y salida de las pantallas (res/anim). */
private val animaciones = NavOptions.Builder()
    .setEnterAnim(R.anim.entrar_derecha)
    .setExitAnim(R.anim.salir_izquierda)
    .setPopEnterAnim(R.anim.entrar_izquierda)
    .setPopExitAnim(R.anim.salir_derecha)
    .setLaunchSingleTop(true)
    .build()

/** Navega a otro destino del grafo, con argumentos opcionales y animación. */
fun Fragment.navegar(@IdRes destino: Int, argumentos: Bundle? = null) {
    findNavController().navigate(destino, argumentos, animaciones)
}

/**
 * Configura la barra superior de una pantalla.
 * @param principal true en los destinos de la barra inferior (muestra ☰ para abrir el menú
 *                  lateral); false en las pantallas de detalle (muestra la flecha para volver).
 */
fun IncludeBarraSuperiorBinding.configurar(fragmento: Fragment, titulo: CharSequence, principal: Boolean) {
    root.rellenarBarraDeEstado()
    barraSuperior.title = titulo
    if (principal) {
        barraSuperior.setNavigationIcon(R.drawable.ic_menu)
        barraSuperior.setNavigationContentDescription(R.string.menu_abrir)
        barraSuperior.setNavigationOnClickListener { (fragmento.activity as? ConMenuLateral)?.abrirMenuLateral() }
    } else {
        barraSuperior.setNavigationIcon(R.drawable.ic_flecha_atras)
        barraSuperior.setNavigationContentDescription(R.string.accion_volver)
        barraSuperior.setNavigationOnClickListener { fragmento.findNavController().navigateUp() }
    }
}

/**
 * Íconos de la barra de estado oscuros (true) para pantallas de fondo claro arriba, como el
 * detalle del producto o el comprobante; claros (false) para el resto, que empiezan en verde.
 */
fun Fragment.iconosBarraDeEstadoOscuros(oscuros: Boolean) {
    val ventana = requireActivity().window
    WindowCompat.getInsetsController(ventana, ventana.decorView).isAppearanceLightStatusBars = oscuros
}

/** Diálogo de confirmación con el estilo de la app. */
fun Fragment.confirmar(titulo: CharSequence, mensaje: CharSequence, accion: CharSequence, alConfirmar: () -> Unit) {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(titulo)
        .setMessage(mensaje)
        .setPositiveButton(accion) { _, _ -> alConfirmar() }
        .setNegativeButton(R.string.accion_cancelar, null)
        .show()
}
