package com.sabornatural.tienda.ui.comprador

import android.os.Bundle
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.Carrito
import com.sabornatural.tienda.ui.comun.PanelRolActivity

/**
 * Pantallas del comprador. También la usa el invitado que entra con "Explorar sin cuenta":
 * puede ver el catálogo, pero para comprar se le pide ingresar.
 */
class CompradorActivity : PanelRolActivity() {

    override val grafo = R.navigation.nav_comprador
    override val menuInferior = R.menu.menu_comprador_inferior
    override val menuLateral = R.menu.menu_comprador_lateral
    override val destinosPrincipales = setOf(
        R.id.inicioFragment, R.id.catalogoFragment, R.id.carritoFragment, R.id.perfilFragment,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Contador del carrito en la barra inferior: se actualiza solo al observar el carrito
        Carrito.lineas.observe(this) {
            val unidades = Carrito.unidades
            val insignia = binding.barraInferior.getOrCreateBadge(R.id.carritoFragment)
            insignia.isVisible = unidades > 0
            insignia.number = unidades
            insignia.backgroundColor = getColor(R.color.dorado_tierra)
            insignia.badgeTextColor = getColor(R.color.blanco)
        }
    }
}
