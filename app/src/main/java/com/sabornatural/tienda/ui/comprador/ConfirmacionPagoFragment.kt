package com.sabornatural.tienda.ui.comprador

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.activity.OnBackPressedCallback
import androidx.navigation.fragment.findNavController
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.RepositorioPedidos
import com.sabornatural.tienda.datos.Sesion
import com.sabornatural.tienda.databinding.FragmentConfirmacionPagoBinding
import com.sabornatural.tienda.ui.comun.Formatos
import com.sabornatural.tienda.ui.comun.FragmentoBase
import com.sabornatural.tienda.ui.comun.iconosBarraDeEstadoOscuros
import com.sabornatural.tienda.ui.comun.navegar
import com.sabornatural.tienda.ui.comun.rellenarBarrasDelSistema

/** Comprobante del pago aprobado (mockup B-06). */
class ConfirmacionPagoFragment : FragmentoBase<FragmentConfirmacionPagoBinding>(FragmentConfirmacionPagoBinding::inflate) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.raiz.rellenarBarrasDelSistema()
        val pedido = RepositorioPedidos.buscarPorId(requireArguments().getInt(Argumentos.PEDIDO_ID))
        if (pedido == null) {
            volverAlInicio()
            return
        }

        binding.iconoExito.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.logo_entrada))
        binding.texto.text = getString(R.string.confirmacion_texto, pedido.vendedorNombre)
        binding.codigo.text = pedido.codigo
        binding.referencia.text = pedido.referenciaPago
        binding.medio.setText(pedido.medioPago.nombre)
        binding.fecha.text = Formatos.fechaHora(pedido.fecha)
        binding.total.text = Formatos.precio(pedido.total)
        binding.correo.text = getString(R.string.confirmacion_correo, Sesion(requireContext()).usuarioActual?.correo.orEmpty())

        binding.botonVerPedidos.setOnClickListener { navegar(R.id.misPedidosFragment) }
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

    private fun volverAlInicio() {
        findNavController().popBackStack(R.id.inicioFragment, false)
    }
}
