package com.sabornatural.tienda.ui.auth

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.RepositorioUsuarios
import com.sabornatural.tienda.datos.Sesion
import com.sabornatural.tienda.datos.modelo.Rol
import com.sabornatural.tienda.databinding.ActivityRegistroBinding
import com.sabornatural.tienda.ui.comun.Navegador
import com.sabornatural.tienda.ui.comun.PantallaBase
import com.sabornatural.tienda.ui.comun.Validaciones
import com.sabornatural.tienda.ui.comun.igualarAltoBarraDeEstado
import com.sabornatural.tienda.ui.comun.ocultarTeclado
import com.sabornatural.tienda.ui.comun.rellenarBarraDeEstado
import com.sabornatural.tienda.ui.comun.rellenarBarraDeNavegacion
import com.sabornatural.tienda.ui.comun.texto
import com.sabornatural.tienda.ui.comun.validar

/**
 * Crear cuenta (mockup C-04). El comprador queda activo de inmediato; el vendedor queda
 * pendiente hasta que un administrador apruebe su comercio (RF-AUT-01 y RF-AUT-02).
 */
class RegistroActivity : PantallaBase() {

    private lateinit var binding: ActivityRegistroBinding

    private val rolElegido: Rol
        get() = if (binding.grupoRol.checkedButtonId == binding.opcionVendedor.id) Rol.VENDEDOR else Rol.COMPRADOR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        with(binding.encabezado) {
            root.rellenarBarraDeEstado()
            titulo.setText(R.string.registro_titulo_barra)
            subtitulo.setText(R.string.eslogan)
            botonVolver.setOnClickListener { finish() }
        }
        binding.desplazable.rellenarBarraDeNavegacion()
        binding.franjaBarraEstado.igualarAltoBarraDeEstado()

        binding.grupoRol.addOnButtonCheckedListener { _, _, _ ->
            binding.avisoVendedor.visibility = if (rolElegido == Rol.VENDEDOR) View.VISIBLE else View.GONE
        }
        binding.textoClave.doAfterTextChanged { actualizarFortaleza(it?.toString().orEmpty()) }
        actualizarFortaleza("")

        binding.botonCrear.setOnClickListener { crearCuenta() }
        binding.botonYaTengoCuenta.setOnClickListener {
            startActivity(Intent(this, IngresoActivity::class.java))
            finish()
        }
    }

    /** Barra de 0 a 4 niveles con color rojo, ámbar o lima según la fortaleza. */
    private fun actualizarFortaleza(clave: String) {
        val nivel = Validaciones.fortalezaClave(clave)
        val (color, etiqueta) = when {
            nivel <= 1 -> R.color.rojo to R.string.registro_fortaleza_debil
            nivel <= 3 -> R.color.ambar to R.string.registro_fortaleza_media
            else -> R.color.lima to R.string.registro_fortaleza_fuerte
        }
        binding.indicadorFortaleza.setProgressCompat(nivel, true)
        binding.indicadorFortaleza.setIndicatorColor(ContextCompat.getColor(this, color))
        binding.etiquetaFortaleza.text = getString(R.string.registro_fortaleza, getString(etiqueta))
    }

    /** Lleva la pantalla al primer campo con error, para que el usuario vea qué corregir. */
    private fun desplazarAlPrimerError() = with(binding) {
        val campos = listOf(campoNombre, campoApellido, campoCorreo, campoTelefono, campoClave, campoConfirmarClave)
        val primero: View = campos.firstOrNull { it.error != null }
            ?: avisoConsentimientos.takeIf { it.visibility == View.VISIBLE }
            ?: return@with
        val zona = Rect()
        primero.getDrawingRect(zona)
        desplazable.offsetDescendantRectToMyCoords(primero, zona)
        desplazable.smoothScrollTo(0, zona.top - resources.getDimensionPixelSize(R.dimen.espacio_xl))
    }

    private fun crearCuenta() {
        val obligatorio = getString(R.string.error_campo_obligatorio)
        val nombre = binding.campoNombre.texto
        val apellido = binding.campoApellido.texto
        val correo = binding.campoCorreo.texto
        val telefono = binding.campoTelefono.texto
        val clave = binding.campoClave.editText?.text?.toString().orEmpty()
        val confirmacion = binding.campoConfirmarClave.editText?.text?.toString().orEmpty()

        // Se validan todos los campos (no se detiene en el primero) para mostrar todos los errores a la vez
        val validos = listOf(
            binding.campoNombre.validar(nombre.isNotEmpty(), obligatorio),
            binding.campoApellido.validar(apellido.isNotEmpty(), obligatorio),
            binding.campoCorreo.validar(Validaciones.esCorreoValido(correo), getString(R.string.error_correo_invalido)) &&
                binding.campoCorreo.validar(!RepositorioUsuarios.existeCorreo(correo), getString(R.string.error_correo_registrado)),
            binding.campoTelefono.validar(Validaciones.esTelefonoValido(telefono), getString(R.string.error_telefono_invalido)),
            binding.campoClave.validar(Validaciones.esClaveValida(clave), getString(R.string.error_clave_debil)),
            binding.campoConfirmarClave.validar(clave == confirmacion && confirmacion.isNotEmpty(), getString(R.string.error_claves_distintas)),
        ).all { it }

        // Las tres autorizaciones obligatorias; promociones es opcional
        val consentimientos = binding.casillaTerminos.isChecked &&
            binding.casillaDatos.isChecked &&
            binding.casillaMayorEdad.isChecked
        binding.avisoConsentimientos.visibility = if (consentimientos) View.GONE else View.VISIBLE

        if (!validos || !consentimientos) {
            desplazarAlPrimerError()
            return
        }
        ocultarTeclado()

        val usuario = RepositorioUsuarios.registrar(nombre, apellido, correo, telefono, clave, rolElegido)
        if (usuario.rol == Rol.VENDEDOR) {
            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_check_circulo)
                .setTitle(R.string.registro_vendedor_enviado_titulo)
                .setMessage(R.string.registro_vendedor_enviado_texto)
                .setPositiveButton(R.string.registro_entendido) { _, _ -> finish() }
                .setCancelable(false)
                .show()
        } else {
            Sesion(this).iniciar(usuario)
            Navegador.abrirPanel(this, usuario)
        }
    }
}
