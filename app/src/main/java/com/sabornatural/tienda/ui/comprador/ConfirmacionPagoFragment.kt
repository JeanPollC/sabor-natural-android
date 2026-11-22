package com.sabornatural.tienda.ui.comprador

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.RepositorioPedidos
import com.sabornatural.tienda.datos.Sesion
import com.sabornatural.tienda.datos.modelo.EstadoPago
import com.sabornatural.tienda.databinding.FragmentConfirmacionPagoBinding
import com.sabornatural.tienda.ui.comun.Formatos
import com.sabornatural.tienda.ui.comun.FragmentoBase
import com.sabornatural.tienda.ui.comun.iconosBarraDeEstadoOscuros
import com.sabornatural.tienda.ui.comun.navegar
import com.sabornatural.tienda.ui.comun.rellenarBarrasDelSistema

/**
 * Resultado del pago (mockup B-06). Se muestra después de guardar en el pedido lo que respondió
 * Wompi: aprobado (comprobante), pendiente de confirmación o rechazado (con opción de volver al
 * carrito, que se conserva).
 */
class ConfirmacionPagoFragment : FragmentoBase<FragmentConfirmacionPagoBinding>(FragmentConfirmacionPagoBinding::inflate) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.raiz.rellenarBarrasDelSistema()
        val pedido = RepositorioPedidos.buscarPorId(requireArguments().getString(Argumentos.PEDIDO_ID).orEmpty())
        if (pedido == null) {
            volverAlInicio()
            return
        }

        binding.iconoExito.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.logo_entrada))
        // Título, ícono y texto según el resultado del pago
        val (icono, color, titulo) = when (pedido.estadoPago) {
            EstadoPago.APROBADO -> Triple(R.drawable.ic_check_circulo, R.color.verde_bosque, R.string.confirmacion_titulo)
            EstadoPago.PENDIENTE -> Triple(R.drawable.ic_info, R.color.ambar, R.string.confirmacion_pendiente_titulo)
            EstadoPago.RECHAZADO -> Triple(R.drawable.ic_alerta, R.color.rojo, R.string.confirmacion_rechazado_titulo)
        }
        binding.iconoExito.setImageResource(icono)
        binding.iconoExito.imageTintList = ContextCompat.getColorStateList(requireContext(), color)
        binding.titulo.setText(titulo)
        binding.texto.text = when (pedido.estadoPago) {
            EstadoPago.APROBADO -> getString(R.string.confirmacion_texto, pedido.vendedorNombre)
            EstadoPago.PENDIENTE -> getString(R.string.confirmacion_pendiente_texto)
            EstadoPago.RECHAZADO -> getString(R.string.confirmacion_rechazado_texto, pedido.detallePago.ifBlank { getString(R.string.pago_estado_rechazado) })
        }
        binding.codigo.text = pedido.codigo
        binding.referencia.text = pedido.referenciaPago.ifBlank { "—" }
        binding.medio.setText(pedido.medioPago.nombre)
        binding.fecha.text = Formatos.fechaHora(pedido.fecha)
        binding.total.text = Formatos.precio(pedido.total)
        binding.correo.text = getString(R.string.confirmacion_correo, Sesion(requireContext()).usuarioActual?.correo.orEmpty())
        binding.correo.visibility = if (pedido.estadoPago == EstadoPago.APROBADO) View.VISIBLE else View.GONE

        if (pedido.estadoPago == EstadoPago.RECHAZADO) {
            // El carrito no se vació: se puede intentar de nuevo
            binding.botonVerPedidos.setText(R.string.confirmacion_volver_carrito)
            binding.botonVerPedidos.setOnClickListener { navegar(R.id.carritoFragment) }
        } else {
            binding.botonVerPedidos.setOnClickListener { navegar(R.id.misPedidosFragment) }
        }
        binding.botonSeguir.setOnClickListener { volverAlInicio() }

        // "Atrás" desde el comprobante lleva al inicio (el carrito ya fue pagado)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = volverAlInicio()
        })
    }

    // Fondo crema arriba: íconos de la barra de estado oscuros mientras se ve el comprobante
    override fun onResume() {
        super.onResume()
        iconosBarraDeEstadoOscuros(true)
    }

    override fun onPause() {
        iconosBarraDeEstadoOscuros(false)
        super.onPause()
    }

    /** Vuelve al inicio del comprador (el pago ya terminó). */
    private fun volverAlInicio() {
        findNavController().popBackStack(R.id.inicioFragment, false)
    }
}
