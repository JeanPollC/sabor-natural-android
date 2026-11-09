package com.sabornatural.tienda.ui.vendedor

import com.sabornatural.tienda.R
import com.sabornatural.tienda.ui.comun.PanelRolActivity

/** Pantallas del vendedor (mockups V-01 a V-06): panel, productos, pedidos y perfil. */
class VendedorActivity : PanelRolActivity() {
    override val grafo = R.navigation.nav_vendedor
    override val menuInferior = R.menu.menu_vendedor_inferior
    override val menuLateral = R.menu.menu_vendedor_lateral
    override val destinosPrincipales = setOf(
        R.id.panelVendedorFragment, R.id.productosVendedorFragment, R.id.pedidosVendedorFragment, R.id.perfilFragment,
    )
}
