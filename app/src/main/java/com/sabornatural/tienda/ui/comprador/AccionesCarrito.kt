package com.sabornatural.tienda.ui.comprador

import android.content.Intent
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.Carrito
import com.sabornatural.tienda.datos.Sesion
import com.sabornatural.tienda.datos.modelo.Producto
import com.sabornatural.tienda.ui.auth.IngresoActivity
import com.sabornatural.tienda.ui.comun.navegar

/** Claves de los argumentos que se pasan entre pantallas (Bundle). */
object Argumentos {
    const val PRODUCTO_ID = "producto_id"
    const val PEDIDO_ID = "pedido_id"
    const val CATEGORIA = "categoria"
    const val ENFOCAR_BUSQUEDA = "enfocar_busqueda"
    const val MODO = "modo"
    const val USUARIO_ID = "usuario_id"
    const val ESTADO = "estado"
}

/**
 * Agrega un producto al carrito desde cualquier pantalla del comprador y resuelve los casos:
 *  - invitado sin cuenta: se le pide ingresar (RF-INI-02: para comprar se exige sesión);
 *  - producto de otro productor: se pide confirmar el cambio de carrito (RF-CAR-05);
 *  - agregado: aviso con acceso directo al carrito.
 */
fun Fragment.agregarAlCarrito(producto: Producto, cantidad: Int = 1, alAgregar: () -> Unit = {}) {
    if (Sesion(requireContext()).usuarioActual == null) {
        pedirIngreso()
        return
    }
    when (Carrito.agregar(producto, cantidad)) {
        Carrito.Resultado.AGREGADO -> {
            alAgregar()
            avisarAgregado()
        }
        Carrito.Resultado.SIN_EXISTENCIAS -> Snackbar.make(requireView(), R.string.carrito_sin_existencias, Snackbar.LENGTH_SHORT).show()
        Carrito.Resultado.OTRO_VENDEDOR -> MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.carrito_otro_vendedor_titulo)
            .setMessage(getString(R.string.carrito_otro_vendedor_texto, Carrito.vendedorNombre, producto.vendedorNombre))
            .setPositiveButton(R.string.carrito_otro_vendedor_si) { _, _ ->
                Carrito.reemplazarCon(producto, cantidad)
                alAgregar()
                avisarAgregado()
            }
            .setNegativeButton(R.string.accion_cancelar, null)
            .show()
    }
}

private fun Fragment.avisarAgregado() {
    Snackbar.make(requireView(), R.string.detalle_agregado, Snackbar.LENGTH_SHORT)
        .setAction(R.string.detalle_ver_carrito) { navegar(R.id.carritoFragment) }
        .show()
}

/** Diálogo para invitados: ingresar o crear cuenta. */
fun Fragment.pedirIngreso() {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.vacio_invitado)
        .setMessage(R.string.vacio_invitado_texto)
        .setPositiveButton(R.string.bienvenida_ingresar) { _, _ ->
            startActivity(Intent(requireContext(), IngresoActivity::class.java))
        }
        .setNegativeButton(R.string.accion_cancelar, null)
        .show()
}
