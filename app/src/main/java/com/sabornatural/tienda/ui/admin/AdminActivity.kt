package com.sabornatural.tienda.ui.admin

import com.sabornatural.tienda.R
import com.sabornatural.tienda.ui.comun.PanelRolActivity

/** Pantallas del administrador (mockups A-01 a A-06): panel, usuarios, productos, ventas y perfil. */
class AdminActivity : PanelRolActivity() {
    override val grafo = R.navigation.nav_admin
    override val menuInferior = R.menu.menu_admin_inferior
    override val menuLateral = R.menu.menu_admin_lateral
    override val destinosPrincipales = setOf(
        R.id.panelAdminFragment, R.id.usuariosFragment, R.id.productosAdminFragment, R.id.reporteVentasFragment, R.id.perfilFragment,
    )
}
