package com.sabornatural.tienda.ui.comprador

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.Carrito
import com.sabornatural.tienda.datos.RepositorioPedidos
import com.sabornatural.tienda.datos.Sesion
import com.sabornatural.tienda.datos.modelo.LineaPedido
import com.sabornatural.tienda.datos.modelo.MedioPago
import com.sabornatural.tienda.datos.modelo.TipoEntrega
import com.sabornatural.tienda.databinding.FragmentPasarelaBinding
import com.sabornatural.tienda.databinding.IncludeOpcionPagoBinding
import com.sabornatural.tienda.ui.comun.Formatos
import com.sabornatural.tienda.ui.comun.FragmentoBase
import com.sabornatural.tienda.ui.comun.configurar
import com.sabornatural.tienda.ui.comun.ocultarTeclado
import com.sabornatural.tienda.ui.comun.rellenarBarraDeNavegacion
import com.sabornatural.tienda.ui.comun.texto
import com.sabornatural.tienda.ui.comun.validar

/**
 * Pasarela de pagos (mockups B-04 y B-05). Reúne entrega, medio de pago y facturación, y simula
 * la respuesta de la pasarela. En la versión conectada, el formulario de la tarjeta lo muestra
 * la pasarela (Wompi) y la app solo recibe la referencia y el estado del pago (RNF-09).
 */
class PasarelaFragment : FragmentoBase<FragmentPasarelaBinding>(FragmentPasarelaBinding::inflate) {

    private lateinit var opciones: Map<MedioPago, IncludeOpcionPagoBinding>
    private var medioPago = MedioPago.TARJETA

    private val tipoEntrega: TipoEntrega
        get() = if (binding.grupoEntrega.checkedButtonId == binding.opcionRecoger.id) TipoEntrega.RECOGER else TipoEntrega.DOMICILIO

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val usuario = Sesion(requireContext()).usuarioActual
        if (usuario == null || Carrito.unidades == 0) {
            findNavController().navigateUp()
            return
        }
        binding.barra.configurar(this, getString(R.string.pago_titulo), principal = false)
        binding.barraAccion.rellenarBarraDeNavegacion()
        pintarPasos()

        // Valores iniciales: dirección guardada y nombre del usuario
        binding.campoDireccion.editText?.setText(getString(R.string.perfil_direccion_predeterminada))
        binding.campoNombreFactura.editText?.setText(usuario.nombreCompleto)
        binding.avisoRecoger.text = getString(R.string.pago_recoger_en, Carrito.vendedorNombre)

        binding.grupoEntrega.addOnButtonCheckedListener { _, _, _ -> actualizarEntrega() }

        opciones = mapOf(
            MedioPago.TARJETA to binding.opcionTarjeta,
            MedioPago.PSE to binding.opcionPse,
            MedioPago.NEQUI to binding.opcionNequi,
        )
        val detalles = mapOf(
            MedioPago.TARJETA to R.string.pago_tarjeta_detalle,
            MedioPago.PSE to R.string.pago_pse_detalle,
            MedioPago.NEQUI to R.string.pago_nequi_detalle,
        )
        opciones.forEach { (medio, opcion) ->
            opcion.radio.isSaveEnabled = false // mismo id en las tres tarjetas: el estado lo decide elegirMedio()
            opcion.icono.setImageResource(medio.icono)
            opcion.nombre.setText(medio.nombre)
            opcion.detalle.setText(detalles.getValue(medio))
            opcion.root.setOnClickListener { elegirMedio(medio) }
        }
        elegirMedio(MedioPago.TARJETA)
        actualizarEntrega()

        binding.botonPagar.setOnClickListener { pagar() }
    }

    /** Paso 1 (entrega) en curso junto con el 2 (pago); el 3 se completa al aprobar el pago. */
    private fun pintarPasos() = with(binding.pasos) {
        fun estilo(paso: TextView, etiqueta: TextView, activo: Boolean) {
            val fondo = if (activo) R.color.verde_bosque else R.color.crema_oscuro
            val texto = if (activo) R.color.blanco else R.color.texto_secundario
            paso.backgroundTintList = ContextCompat.getColorStateList(requireContext(), fondo)
            paso.setTextColor(ContextCompat.getColor(requireContext(), texto))
            etiqueta.setTextColor(ContextCompat.getColor(requireContext(), if (activo) R.color.verde_bosque else R.color.texto_secundario))
        }
        estilo(paso1, etiqueta1, true)
        estilo(paso2, etiqueta2, true)
        estilo(paso3, etiqueta3, false)
        linea1.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.verde_bosque))
    }

    private fun elegirMedio(medio: MedioPago) {
        medioPago = medio
        opciones.forEach { (m, opcion) ->
            opcion.root.isChecked = m == medio
            opcion.radio.isChecked = m == medio
        }
    }

    /** Domicilio: pide dirección y cobra el envío. Recoger: sin dirección ni costo de envío. */
    private fun actualizarEntrega() {
        val domicilio = tipoEntrega == TipoEntrega.DOMICILIO
        binding.datosDomicilio.visibility = if (domicilio) View.VISIBLE else View.GONE
        binding.avisoRecoger.visibility = if (domicilio) View.GONE else View.VISIBLE
        val valorDomicilio = if (domicilio) RepositorioPedidos.VALOR_DOMICILIO else 0L
        mostrarTotales(binding.totales, Carrito.subtotal, valorDomicilio)
        binding.totales.etiquetaSubtotal.text = resources.getQuantityString(R.plurals.formato_productos, Carrito.unidades, Carrito.unidades)
        binding.botonPagar.text = getString(R.string.pago_boton, Formatos.precio(Carrito.subtotal + valorDomicilio))
    }

    private fun pagar() {
        val obligatorio = getString(R.string.error_campo_obligatorio)
        val validos = listOf(
            tipoEntrega == TipoEntrega.RECOGER || binding.campoDireccion.validar(binding.campoDireccion.texto.length >= 8, obligatorio),
            binding.campoNombreFactura.validar(binding.campoNombreFactura.texto.isNotEmpty(), obligatorio),
            binding.campoDocumento.validar(Regex("^\\d{6,10}$").matches(binding.campoDocumento.texto), getString(R.string.error_documento)),
        ).all { it }
        if (!validos) return
        requireActivity().ocultarTeclado()

        // Simulación de la pasarela: diálogo de espera y aprobación
        val procesando = MaterialAlertDialogBuilder(requireContext())
            .setView(R.layout.dialogo_procesando)
            .setCancelable(false)
            .show()
        binding.root.postDelayed({
            procesando.dismiss()
            if (vistaActiva()) confirmarPedido()
        }, 1800)
    }

    private fun vistaActiva() = view != null && isAdded

    /** Crea el pedido con el precio del momento, vacía el carrito y abre la confirmación (B-06). */
    private fun confirmarPedido() {
        val usuario = Sesion(requireContext()).usuarioActual ?: return
        val lineas = Carrito.lineas.value.orEmpty().map {
            LineaPedido(it.producto.id, it.producto.nombre, it.producto.categoria, it.cantidad, it.producto.precio)
        }
        val pedido = RepositorioPedidos.crear(
            comprador = usuario,
            vendedorId = Carrito.vendedorId ?: return,
            vendedorNombre = Carrito.vendedorNombre.orEmpty(),
            lineas = lineas,
            medioPago = medioPago,
            tipoEntrega = tipoEntrega,
            direccion = if (tipoEntrega == TipoEntrega.DOMICILIO) binding.campoDireccion.texto else getString(R.string.entrega_recoger),
        )
        Carrito.vaciar()

        // Al volver desde la confirmación se llega al inicio, no al carrito ya pagado
        val opciones = NavOptions.Builder()
            .setPopUpTo(R.id.inicioFragment, false)
            .setEnterAnim(R.anim.entrar_derecha)
            .setExitAnim(R.anim.salir_izquierda)
            .build()
        findNavController().navigate(R.id.confirmacionPagoFragment, bundleOf(Argumentos.PEDIDO_ID to pedido.id), opciones)
    }
}
