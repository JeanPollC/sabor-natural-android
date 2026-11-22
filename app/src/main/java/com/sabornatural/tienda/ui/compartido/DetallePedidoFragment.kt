package com.sabornatural.tienda.ui.compartido

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.RepositorioPedidos
import com.sabornatural.tienda.datos.modelo.EstadoPago
import com.sabornatural.tienda.datos.modelo.EstadoPedido
import com.sabornatural.tienda.datos.modelo.Pedido
import com.sabornatural.tienda.datos.modelo.TipoEntrega
import com.sabornatural.tienda.databinding.FragmentDetallePedidoBinding
import com.sabornatural.tienda.databinding.ItemLineaPedidoBinding
import com.sabornatural.tienda.databinding.ItemLineaTiempoBinding
import com.sabornatural.tienda.ui.comprador.Argumentos
import com.sabornatural.tienda.ui.comprador.mostrarTotales
import com.sabornatural.tienda.ui.comun.Formatos
import com.sabornatural.tienda.ui.comun.FragmentoBase
import com.sabornatural.tienda.ui.comun.configurar
import com.sabornatural.tienda.ui.comun.confirmar
import com.sabornatural.tienda.ui.comun.ejecutar
import com.sabornatural.tienda.ui.comun.mostrarEtiqueta
import com.sabornatural.tienda.ui.comun.rellenarBarraDeNavegacion
import com.sabornatural.tienda.ui.comun.tono

/**
 * Detalle del pedido. Comprador (B-08): seguimiento y cancelación antes del despacho (RF-PED-06).
 * Vendedor (V-06): avanza el estado en la secuencia permitida o rechaza un pedido nuevo (RF-PED-07).
 *
 * El pedido se lee de la copia en vivo de Firestore: si el vendedor cambia el estado, el comprador
 * lo ve actualizarse sin recargar. Un pedido con el pago pendiente permite al comprador consultar
 * de nuevo el estado de la transacción en Wompi (lo verifica la Cloud Function verificarPago).
 */
class DetallePedidoFragment : FragmentoBase<FragmentDetallePedidoBinding>(FragmentDetallePedidoBinding::inflate) {

    private val modo: ModoPedidos
        get() = ModoPedidos.valueOf(requireArguments().getString(Argumentos.MODO) ?: ModoPedidos.COMPRADOR.name)

    private val pedidoId: String get() = requireArguments().getString(Argumentos.PEDIDO_ID).orEmpty()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.barraAccion.rellenarBarraDeNavegacion()
        observar(RepositorioPedidos.cambios) { mostrar() }
    }

    /** Pinta el pedido; si ya no está en la lista (por ejemplo, otro rol), vuelve atrás. */
    private fun mostrar() {
        if (RepositorioPedidos.estado.value == com.sabornatural.tienda.datos.EstadoCarga.Cargando) return
        val pedido = RepositorioPedidos.buscarPorId(pedidoId) ?: run {
            findNavController().navigateUp()
            return
        }
        binding.barra.configurar(this, pedido.codigo, principal = false)
        val esVendedor = modo == ModoPedidos.VENDEDOR
        val verComprador = modo != ModoPedidos.COMPRADOR // vendedor y administrador ven al comprador

        with(binding) {
            estado.mostrarEtiqueta(getString(pedido.estado.nombre), pedido.estado.tono)
            fecha.text = Formatos.fechaHora(pedido.fecha)
            pintarLineaDeTiempo(pedido)

            etiquetaContraparte.setText(if (verComprador) R.string.detalle_pedido_comprador else R.string.detalle_pedido_productor)
            contraparte.text = when (modo) {
                ModoPedidos.COMPRADOR -> pedido.vendedorNombre
                ModoPedidos.VENDEDOR -> pedido.compradorNombre
                // El administrador ve quién compró y a quién
                ModoPedidos.ADMINISTRADOR -> getString(R.string.formato_pedido_resumen, pedido.compradorNombre, pedido.vendedorNombre)
            }
            // El vendedor ve el contacto del comprador solo mientras el pedido está activo (RF-PED-07)
            telefono.visibility = if (esVendedor && pedido.estado.enCurso) View.VISIBLE else View.GONE
            telefono.text = getString(R.string.formato_telefono, pedido.compradorTelefono)
            entrega.text = if (pedido.tipoEntrega == TipoEntrega.DOMICILIO) {
                getString(R.string.formato_pedido_resumen, getString(R.string.entrega_domicilio), pedido.direccion)
            } else {
                getString(R.string.detalle_pedido_recoge)
            }

            listaLineas.removeAllViews()
            pedido.lineas.forEach { linea ->
                val fila = ItemLineaPedidoBinding.inflate(layoutInflater, listaLineas, true)
                fila.descripcion.text = getString(R.string.formato_linea_pedido, linea.cantidad, linea.nombre)
                fila.subtotal.text = Formatos.precio(linea.subtotal)
            }
            mostrarTotales(totales, pedido.subtotal, pedido.domicilio)
            medio.setText(pedido.medioPago.nombre)
            // Transacción de Wompi y resultado del pago
            val idPago = pedido.transaccionId.ifBlank { pedido.referenciaPago }
            referencia.text = if (idPago.isBlank()) {
                getString(pedido.estadoPago.nombre)
            } else {
                getString(R.string.formato_pedido_resumen, idPago, getString(pedido.estadoPago.nombre))
            }
        }
        if (modo == ModoPedidos.ADMINISTRADOR) {
            binding.barraAccion.visibility = View.GONE // el administrador solo consulta
        } else {
            configurarAcciones(pedido, esVendedor)
        }
    }

    /** Estados de la secuencia con su marca: cumplido (check verde) o pendiente (círculo gris). */
    private fun pintarLineaDeTiempo(pedido: Pedido) {
        binding.lineaTiempo.removeAllViews()
        val secuencia = listOf(EstadoPedido.CONFIRMADO, EstadoPedido.EN_PREPARACION, EstadoPedido.DESPACHADO, EstadoPedido.ENTREGADO)
        val pasos = if (pedido.estado == EstadoPedido.CANCELADO) listOf(EstadoPedido.CONFIRMADO, EstadoPedido.CANCELADO) else secuencia
        val alcanzado = pasos.indexOf(pedido.estado)
        pasos.forEachIndexed { i, estado ->
            val fila = ItemLineaTiempoBinding.inflate(layoutInflater, binding.lineaTiempo, true)
            val cumplido = i <= alcanzado
            fila.icono.setImageResource(if (cumplido) R.drawable.ic_check_circulo else R.drawable.ic_circulo_vacio)
            val color = when {
                estado == EstadoPedido.CANCELADO -> R.color.rojo
                cumplido -> R.color.verde_hoja
                else -> R.color.gris_suave
            }
            fila.icono.imageTintList = ContextCompat.getColorStateList(requireContext(), color)
            // En la línea de tiempo el primer paso se lee "Pedido confirmado" (en las listas es "Nuevo")
            fila.nombre.setText(if (estado == EstadoPedido.CONFIRMADO) R.string.pedido_confirmado_linea else estado.nombre)
            fila.nombre.setTextColor(ContextCompat.getColor(requireContext(), if (cumplido) R.color.texto_principal else R.color.texto_secundario))
        }
    }

    /** Botones según rol y estado: avanzar, consultar el pago, rechazar o cancelar. */
    private fun configurarAcciones(pedido: Pedido, esVendedor: Boolean) = with(binding) {
        val siguiente = pedido.estado.siguiente
        // Comprador con pago pendiente y transacción conocida: puede volver a consultar el pago en Wompi
        val consultarPago = !esVendedor && pedido.estado == EstadoPedido.PAGO_PENDIENTE && pedido.transaccionId.isNotBlank()
        botonAvanzar.visibility = if ((esVendedor && siguiente != null) || consultarPago) View.VISIBLE else View.GONE
        if (consultarPago) {
            botonAvanzar.setText(R.string.detalle_pedido_consultar_pago)
            botonAvanzar.setOnClickListener { consultarPago(pedido) }
        } else {
            siguiente?.let { botonAvanzar.text = getString(R.string.detalle_pedido_avanzar, getString(it.nombre).lowercase()) }
            botonAvanzar.setOnClickListener { siguiente?.let { cambiarEstado(pedido, it) } }
        }

        // Rechazar (vendedor) o cancelar (comprador): solo antes del despacho
        val puedeCancelar = if (esVendedor) {
            pedido.estado == EstadoPedido.CONFIRMADO
        } else {
            pedido.estado == EstadoPedido.CONFIRMADO || pedido.estado == EstadoPedido.EN_PREPARACION ||
                (pedido.estado == EstadoPedido.PAGO_PENDIENTE && pedido.transaccionId.isBlank())
        }
        botonCancelar.visibility = if (puedeCancelar) View.VISIBLE else View.GONE
        val textoBoton = if (esVendedor) R.string.detalle_pedido_rechazar else R.string.detalle_pedido_cancelar
        val textoConfirmar = if (esVendedor) R.string.detalle_pedido_rechazar_confirmar else R.string.detalle_pedido_cancelar_confirmar
        botonCancelar.setText(textoBoton)
        botonCancelar.setOnClickListener {
            confirmar(getString(textoBoton), getString(textoConfirmar), getString(textoBoton)) { cambiarEstado(pedido, EstadoPedido.CANCELADO) }
        }
        barraAccion.visibility = if (botonAvanzar.visibility == View.VISIBLE || puedeCancelar) View.VISIBLE else View.GONE
    }

    /** Guarda el nuevo estado en Firestore; la pantalla se redibuja al llegar el cambio. */
    private fun cambiarEstado(pedido: Pedido, nuevo: EstadoPedido) {
        ejecutar {
            RepositorioPedidos.cambiarEstado(pedido, nuevo)
            Snackbar.make(binding.root, getString(R.string.detalle_pedido_actualizado, getString(nuevo.nombre)), Snackbar.LENGTH_SHORT)
                .setAnchorView(binding.barraAccion.takeIf { it.visibility == View.VISIBLE })
                .show()
        }
    }

    /** Pide al servidor consultar otra vez en Wompi una transacción que quedó pendiente. */
    private fun consultarPago(pedido: Pedido) {
        ejecutar(R.string.pago_consultando) {
            val actualizado = RepositorioPedidos.confirmarPago(pedido.id, pedido.transaccionId)
            val mensaje = when (actualizado.estadoPago) {
                EstadoPago.APROBADO -> R.string.pago_estado_aprobado
                EstadoPago.PENDIENTE -> R.string.pago_sigue_pendiente
                EstadoPago.RECHAZADO -> R.string.pago_estado_rechazado
            }
            Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_LONG).show()
        }
    }
}
