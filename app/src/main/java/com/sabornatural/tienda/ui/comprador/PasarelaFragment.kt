package com.sabornatural.tienda.ui.comprador

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.Carrito
import com.sabornatural.tienda.datos.RepositorioPedidos
import com.sabornatural.tienda.datos.Sesion
import com.sabornatural.tienda.datos.Ubicacion
import com.sabornatural.tienda.datos.modelo.EstadoPago
import com.sabornatural.tienda.datos.modelo.LineaPedido
import com.sabornatural.tienda.datos.modelo.MedioPago
import com.sabornatural.tienda.datos.modelo.Pedido
import com.sabornatural.tienda.datos.modelo.TipoEntrega
import com.sabornatural.tienda.datos.modelo.EstadoPedido
import com.sabornatural.tienda.datos.pagos.PasarelaWompi
import com.sabornatural.tienda.datos.remoto.Errores
import com.sabornatural.tienda.databinding.FragmentPasarelaBinding
import com.sabornatural.tienda.databinding.IncludeOpcionPagoBinding
import com.sabornatural.tienda.ui.comun.Formatos
import com.sabornatural.tienda.ui.comun.FragmentoBase
import com.sabornatural.tienda.ui.comun.configurar
import com.sabornatural.tienda.ui.comun.dialogoCargando
import com.sabornatural.tienda.ui.comun.ejecutar
import com.sabornatural.tienda.ui.comun.ocultarTeclado
import com.sabornatural.tienda.ui.comun.rellenarBarraDeNavegacion
import com.sabornatural.tienda.ui.comun.texto
import com.sabornatural.tienda.ui.comun.validar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Pasarela de pagos (mockups B-04 y B-05): entrega, medio de pago y facturación.
 *
 * Flujo completo de la compra:
 *  1. Dirección de entrega: se llena con la ubicación del teléfono (permiso en tiempo de
 *     ejecución, FusedLocation + Geocoder); si el usuario niega el permiso, la escribe a mano.
 *  2. Al pagar se crea el pedido en Firestore (PAGO_PENDIENTE) con la dirección y las coordenadas.
 *  3. La Cloud Function crearPago recalcula el total en el servidor y devuelve la referencia y la
 *     firma de integridad; se abre el Web Checkout de Wompi en sandbox (PagoWompiActivity). Los
 *     datos de la tarjeta los escribe el comprador en la página de Wompi; la app nunca los ve (RNF-09).
 *  4. Con el id de transacción que devuelve Wompi, la Cloud Function verificarPago consulta el
 *     estado real y lo guarda en el pedido (aprobada, rechazada o pendiente, reintentando mientras
 *     siga pendiente) ANTES de mostrar la confirmación.
 */
class PasarelaFragment : FragmentoBase<FragmentPasarelaBinding>(FragmentPasarelaBinding::inflate) {

    private lateinit var opciones: Map<MedioPago, IncludeOpcionPagoBinding>
    private var medioPago = MedioPago.TARJETA

    /** Coordenadas de la última ubicación capturada; se borran si el usuario edita la dirección a mano. */
    private var latitud: Double? = null
    private var longitud: Double? = null
    private var direccionDeUbicacion: String? = null

    /** Pedido creado que está esperando el resultado de Wompi (se conserva si Android recrea la pantalla). */
    private var pedidoEnPago: String? = null

    private val tipoEntrega: TipoEntrega
        get() = if (binding.grupoEntrega.checkedButtonId == binding.opcionRecoger.id) TipoEntrega.RECOGER else TipoEntrega.DOMICILIO

    // Permiso de ubicación en tiempo de ejecución (se registra al crear el Fragment)
    private val pedirPermisoUbicacion = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { respuesta ->
        if (respuesta.values.any { it }) {
            usarMiUbicacion()
        } else {
            binding.campoDireccion.helperText = getString(R.string.ubicacion_permiso_negado)
            binding.campoDireccion.editText?.requestFocus()
        }
    }

    // Checkout de Wompi: devuelve el id de la transacción o se cancela
    private val abrirCheckout = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
        val pedidoId = pedidoEnPago ?: return@registerForActivityResult
        val transaccion = resultado.data?.getStringExtra(PagoWompiActivity.RESULTADO_TRANSACCION)
        when {
            resultado.resultCode != Activity.RESULT_OK -> pagoCancelado(pedidoId)
            transaccion.isNullOrBlank() -> {
                // Volvió sin id: el pedido queda pendiente para revisarlo desde Mis pedidos
                pedidoEnPago = null
                Snackbar.make(binding.root, R.string.pago_sin_transaccion, Snackbar.LENGTH_LONG).show()
            }
            else -> confirmarPago(pedidoId, transaccion)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pedidoEnPago = savedInstanceState?.getString(ESTADO_PEDIDO)
        latitud = savedInstanceState?.getDouble(ESTADO_LATITUD)?.takeIf { savedInstanceState.containsKey(ESTADO_LATITUD) }
        longitud = savedInstanceState?.getDouble(ESTADO_LONGITUD)?.takeIf { savedInstanceState.containsKey(ESTADO_LONGITUD) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pedidoEnPago?.let { outState.putString(ESTADO_PEDIDO, it) }
        latitud?.let { outState.putDouble(ESTADO_LATITUD, it) }
        longitud?.let { outState.putDouble(ESTADO_LONGITUD, it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val usuario = Sesion(requireContext()).usuarioActual
        if (usuario == null || (Carrito.unidades == 0 && pedidoEnPago == null)) {
            findNavController().navigateUp()
            return
        }
        binding.barra.configurar(this, getString(R.string.pago_titulo), principal = false)
        binding.barraAccion.rellenarBarraDeNavegacion()
        pintarPasos()

        binding.campoNombreFactura.editText?.setText(usuario.nombreCompleto)
        binding.avisoRecoger.text = getString(R.string.pago_recoger_en, Carrito.vendedorNombre)
        configurarUbicacion()

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
        /** Pinta un paso como activo (verde) o pendiente (gris). */
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

    // ------------------------------------------------------------ Ubicación

    /**
     * Ícono "mi ubicación" al final del campo de dirección. Si el permiso ya estaba concedido, la
     * ubicación se toma sola al abrir la pantalla; si no, se pide al tocar el ícono.
     */
    private fun configurarUbicacion() = with(binding.campoDireccion) {
        endIconMode = TextInputLayout.END_ICON_CUSTOM
        setEndIconDrawable(R.drawable.ic_ubicacion)
        endIconContentDescription = getString(R.string.ubicacion_usar_mia)
        setEndIconOnClickListener { solicitarUbicacion() }
        helperText = getString(R.string.ubicacion_ayuda)
        // Si el usuario cambia a mano la dirección que dio el GPS, las coordenadas ya no corresponden
        editText?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                if (direccionDeUbicacion != null && s?.toString() != direccionDeUbicacion) {
                    latitud = null
                    longitud = null
                    direccionDeUbicacion = null
                }
            }
        })
        if (texto.isEmpty() && Ubicacion.tienePermiso(requireContext())) usarMiUbicacion()
    }

    /** Pide el permiso de ubicación si falta (explicando para qué) o usa la ubicación si ya está. */
    private fun solicitarUbicacion() {
        when {
            Ubicacion.tienePermiso(requireContext()) -> usarMiUbicacion()
            shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_FINE_LOCATION) ->
                MaterialAlertDialogBuilder(requireContext())
                    .setIcon(R.drawable.ic_ubicacion)
                    .setTitle(R.string.ubicacion_permiso_titulo)
                    .setMessage(R.string.ubicacion_permiso_texto)
                    .setPositiveButton(R.string.ubicacion_permitir) { _, _ -> pedirPermisoUbicacion.launch(Ubicacion.PERMISOS) }
                    .setNegativeButton(R.string.ubicacion_escribir_mano, null)
                    .show()
            else -> pedirPermisoUbicacion.launch(Ubicacion.PERMISOS)
        }
    }

    /** Captura la ubicación y llena la dirección (o deja las coordenadas si no hay dirección legible). */
    private fun usarMiUbicacion() {
        binding.campoDireccion.helperText = getString(R.string.ubicacion_buscando)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resultado = Ubicacion.obtener(requireContext())
                latitud = resultado.latitud
                longitud = resultado.longitud
                if (resultado.direccion != null) {
                    direccionDeUbicacion = resultado.direccion
                    binding.campoDireccion.editText?.setText(resultado.direccion)
                    binding.campoDireccion.helperText = getString(R.string.ubicacion_lista)
                } else {
                    binding.campoDireccion.helperText = getString(R.string.ubicacion_sin_direccion)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                binding.campoDireccion.helperText = getString(Errores.mensaje(e))
            }
        }
    }

    // ------------------------------------------------------------ Pago

    /** Marca el medio de pago elegido (preferencia que se guarda en el pedido). */
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

    /** Valida, crea el pedido en Firestore, pide la firma al servidor y abre el checkout de Wompi. */
    private fun pagar() {
        val obligatorio = getString(R.string.error_campo_obligatorio)
        val validos = listOf(
            tipoEntrega == TipoEntrega.RECOGER || binding.campoDireccion.validar(binding.campoDireccion.texto.length >= 8, obligatorio),
            binding.campoNombreFactura.validar(binding.campoNombreFactura.texto.isNotEmpty(), obligatorio),
            binding.campoDocumento.validar(Regex("^\\d{6,10}$").matches(binding.campoDocumento.texto), getString(R.string.error_documento)),
        ).all { it }
        if (!validos) {
            mostrarPrimerError()
            return
        }
        requireActivity().ocultarTeclado()

        // Sin la llave pública de Wompi no se puede cobrar: se explica cómo configurarla
        if (!PasarelaWompi.configurada) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.wompi_sin_configurar_titulo)
                .setMessage(R.string.wompi_sin_configurar_texto)
                .setPositiveButton(R.string.registro_entendido, null)
                .show()
            return
        }

        val usuario = Sesion(requireContext()).usuarioActual ?: return
        val vendedorId = Carrito.vendedorId ?: return
        val domicilio = tipoEntrega == TipoEntrega.DOMICILIO
        val lineas = Carrito.lineas.value.orEmpty().map {
            LineaPedido(it.producto.id, it.producto.nombre, it.producto.categoria, it.cantidad, it.producto.precio)
        }
        val pagador = PasarelaWompi.Pagador(
            nombre = binding.campoNombreFactura.texto,
            documento = binding.campoDocumento.texto,
            correo = usuario.correo,
            telefono = usuario.telefono,
        )

        ejecutar(R.string.pago_creando_pedido) {
            val pedido = RepositorioPedidos.crear(
                comprador = usuario,
                vendedorId = vendedorId,
                vendedorNombre = Carrito.vendedorNombre.orEmpty(),
                lineas = lineas,
                medioPago = medioPago,
                tipoEntrega = tipoEntrega,
                direccion = if (domicilio) binding.campoDireccion.texto else getString(R.string.entrega_recoger),
                latitud = if (domicilio) latitud else null,
                longitud = if (domicilio) longitud else null,
            )
            // Referencia, monto y firma los calcula el servidor; si falla, el pedido no queda colgado
            val datos = try {
                PasarelaWompi.crearPago(pedido.id)
            } catch (e: Exception) {
                runCatching { RepositorioPedidos.cambiarEstado(pedido, EstadoPedido.CANCELADO) }
                throw e
            }
            pedidoEnPago = pedido.id
            abrirCheckout.launch(PagoWompiActivity.intent(requireContext(), PasarelaWompi.urlCheckout(datos, pagador)))
        }
    }

    /**
     * Lleva la pantalla al primer campo con error (el documento queda al final y no se veía) y lo
     * enfoca, con un aviso para que el comprador sepa por qué no avanzó.
     */
    private fun mostrarPrimerError() {
        val campos = listOf(binding.campoDireccion, binding.campoNombreFactura, binding.campoDocumento)
        val primero = campos.firstOrNull { it.isShown && it.error != null } ?: return
        binding.desplazable.post {
            val zona = android.graphics.Rect()
            primero.getDrawingRect(zona)
            binding.desplazable.offsetDescendantRectToMyCoords(primero, zona)
            binding.desplazable.smoothScrollTo(0, (zona.top - resources.getDimensionPixelSize(R.dimen.espacio_xl)).coerceAtLeast(0))
            primero.editText?.requestFocus()
        }
        Snackbar.make(binding.root, R.string.pago_revisa_datos, Snackbar.LENGTH_LONG)
            .setAnchorView(binding.barraAccion)
            .show()
    }

    /**
     * Wompi devolvió una transacción: el servidor la verifica (reintentando mientras siga
     * pendiente) y guarda el resultado en el pedido; luego se muestra. Si fue aprobada o quedó
     * pendiente, se vacía el carrito; si fue rechazada, se conserva para intentar de nuevo.
     */
    private fun confirmarPago(pedidoId: String, transaccionId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val dialogo = requireContext().dialogoCargando(R.string.pago_consultando)
            try {
                val actualizado = RepositorioPedidos.confirmarPago(pedidoId, transaccionId)
                pedidoEnPago = null
                if (actualizado.estadoPago != EstadoPago.RECHAZADO) Carrito.vaciar()
                abrirConfirmacion(actualizado)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Snackbar.make(binding.root, Errores.texto(requireContext(), e), Snackbar.LENGTH_LONG).show()
            } finally {
                dialogo.dismiss()
            }
        }
    }

    /** El comprador cerró el checkout sin pagar: el pedido se cancela y el carrito se conserva. */
    private fun pagoCancelado(pedidoId: String) {
        ejecutar {
            val pedido = RepositorioPedidos.obtener(pedidoId) ?: return@ejecutar
            if (pedido.estado == EstadoPedido.PAGO_PENDIENTE) RepositorioPedidos.cambiarEstado(pedido, EstadoPedido.CANCELADO)
            pedidoEnPago = null
            Snackbar.make(binding.root, R.string.pago_cancelado_usuario, Snackbar.LENGTH_LONG).show()
        }
    }

    /** Abre el resultado; al volver desde allí se llega al inicio, no a la pasarela. */
    private fun abrirConfirmacion(pedido: Pedido) {
        val opciones = NavOptions.Builder()
            .setPopUpTo(R.id.inicioFragment, false)
            .setEnterAnim(R.anim.entrar_derecha)
            .setExitAnim(R.anim.salir_izquierda)
            .build()
        findNavController().navigate(R.id.confirmacionPagoFragment, bundleOf(Argumentos.PEDIDO_ID to pedido.id), opciones)
    }

    private companion object {
        const val ESTADO_PEDIDO = "pedido_en_pago"
        const val ESTADO_LATITUD = "latitud"
        const val ESTADO_LONGITUD = "longitud"
    }
}
