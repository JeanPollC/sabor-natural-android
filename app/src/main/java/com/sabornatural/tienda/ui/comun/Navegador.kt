package com.sabornatural.tienda.ui.comun

import android.app.Activity
import android.content.Intent
import com.sabornatural.tienda.datos.modelo.Rol
import com.sabornatural.tienda.datos.modelo.Usuario
import com.sabornatural.tienda.ui.admin.AdminActivity
import com.sabornatural.tienda.ui.comprador.CompradorActivity
import com.sabornatural.tienda.ui.inicio.BienvenidaActivity
import com.sabornatural.tienda.ui.vendedor.VendedorActivity

/**
 * Punto único de navegación entre los grandes bloques de la app. Cuando la sesión es válida,
 * el rol del usuario decide qué conjunto de pantallas se abre (documento técnico, sección 5.2).
 */
object Navegador {

    /** Abre las pantallas del rol y borra la pila: "atrás" no regresa al ingreso. */
    fun abrirPanel(origen: Activity, usuario: Usuario) {
        val destino = when (usuario.rol) {
            Rol.COMPRADOR -> CompradorActivity::class.java
            Rol.VENDEDOR -> VendedorActivity::class.java
            Rol.ADMINISTRADOR -> AdminActivity::class.java
        }
        iniciarLimpio(origen, Intent(origen, destino))
    }

    /** "Explorar sin cuenta": el catálogo del comprador, sin sesión. */
    fun explorarComoInvitado(origen: Activity) {
        origen.startActivity(Intent(origen, CompradorActivity::class.java))
    }

    /** Vuelve a la bienvenida después de cerrar sesión. */
    fun abrirBienvenida(origen: Activity) = iniciarLimpio(origen, Intent(origen, BienvenidaActivity::class.java))

    private fun iniciarLimpio(origen: Activity, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        origen.startActivity(intent)
        origen.finish()
    }
}
