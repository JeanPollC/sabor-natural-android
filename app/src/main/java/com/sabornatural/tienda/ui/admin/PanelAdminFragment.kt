package com.sabornatural.tienda.ui.admin

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.RepositorioPedidos
import com.sabornatural.tienda.datos.RepositorioProductos
import com.sabornatural.tienda.datos.RepositorioUsuarios
import com.sabornatural.tienda.datos.Sesion
import com.sabornatural.tienda.datos.modelo.EstadoCuenta
import com.sabornatural.tienda.datos.modelo.EstadoPedido
import com.sabornatural.tienda.datos.modelo.EstadoPublicacion
import com.sabornatural.tienda.datos.modelo.Rol
import com.sabornatural.tienda.databinding.FragmentPanelAdminBinding
import com.sabornatural.tienda.databinding.ItemBarraProgresoBinding
import com.sabornatural.tienda.ui.comprador.Argumentos
import com.sabornatural.tienda.ui.comun.ConMenuLateral
import com.sabornatural.tienda.ui.comun.Formatos
import com.sabornatural.tienda.ui.comun.FragmentoBase
import com.sabornatural.tienda.ui.comun.igualarAltoBarraDeEstado
import com.sabornatural.tienda.ui.comun.navegar
import com.sabornatural.tienda.ui.comun.rellenarBarraDeEstado
import com.sabornatural.tienda.ui.vendedor.mostrar
import java.time.LocalDate

/** Panel de administración (mockup A-01): indicadores, asuntos pendientes y usuarios por rol. */
class PanelAdminFragment : FragmentoBase<FragmentPanelAdminBinding>(FragmentPanelAdminBinding::inflate) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.encabezado.root.rellenarBarraDeEstado()
        binding.franjaBarraEstado.igualarAltoBarraDeEstado()
        binding.encabezado.botonMenu.setOnClickListener { (activity as? ConMenuLateral)?.abrirMenuLateral() }
        binding.encabezado.subtitulo.setText(R.string.panel_admin_subtitulo)

        binding.filaSolicitudes.setOnClickListener { navegar(R.id.usuariosFragment, bundleOf(Argumentos.ESTADO to EstadoCuenta.PENDIENTE.name)) }
        binding.filaRevision.setOnClickListener { navegar(R.id.productosAdminFragment) }
        binding.filaReporte.setOnClickListener { navegar(R.id.reporteVentasFragment) }
    }

    override fun onResume() {
        super.onResume()
        val admin = Sesion(requireContext()).usuarioActual ?: return
        binding.encabezado.saludo.text = getString(R.string.inicio_saludo, admin.nombre)
        binding.encabezado.avatar.text = admin.iniciales

        val usuarios = RepositorioUsuarios.todos()
        val productos = RepositorioProductos.todos()
        val mes = LocalDate.now().toString().substring(0, 7)
        val pedidosMes = RepositorioPedidos.todos().filter { it.fecha.startsWith(mes) }
        val ventasMes = pedidosMes.filter { it.estado != EstadoPedido.CANCELADO }.sumOf { it.total }
        val enRevision = productos.count { it.estado == EstadoPublicacion.EN_REVISION }
        val solicitudes = usuarios.count { it.rol == Rol.VENDEDOR && it.estado == EstadoCuenta.PENDIENTE }

        binding.indicador1.mostrar(R.drawable.ic_usuarios, usuarios.count { it.estado == EstadoCuenta.ACTIVO }.toString(), getString(R.string.indicador_usuarios_activos))
        binding.indicador2.mostrar(R.drawable.ic_inventario, enRevision.toString(), getString(R.string.indicador_en_revision))
        binding.indicador3.mostrar(R.drawable.ic_pedidos, pedidosMes.size.toString(), getString(R.string.indicador_pedidos_mes))
        binding.indicador4.mostrar(R.drawable.ic_dinero, Formatos.precio(ventasMes), getString(R.string.indicador_ventas_mes))

        binding.filaSolicitudes.text = resources.getQuantityString(R.plurals.panel_admin_solicitudes, solicitudes, solicitudes)
        binding.filaRevision.text = resources.getQuantityString(R.plurals.panel_admin_productos_revision, enRevision, enRevision)

        // Barras de usuarios por rol: el ancho es la proporción sobre el total
        binding.listaRoles.removeAllViews()
        Rol.entries.forEach { rol ->
            val cantidad = usuarios.count { it.rol == rol }
            val fila = ItemBarraProgresoBinding.inflate(layoutInflater, binding.listaRoles, true)
            fila.nombre.setText(rol.nombre)
            fila.valor.text = cantidad.toString()
            fila.barra.max = usuarios.size.coerceAtLeast(1)
            fila.barra.setProgressCompat(cantidad, true)
        }
    }
}
