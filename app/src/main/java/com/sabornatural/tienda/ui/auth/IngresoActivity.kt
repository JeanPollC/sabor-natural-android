package com.sabornatural.tienda.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.RepositorioUsuarios
import com.sabornatural.tienda.datos.Sesion
import com.sabornatural.tienda.datos.modelo.EstadoCuenta
import com.sabornatural.tienda.datos.modelo.Usuario
import com.sabornatural.tienda.databinding.ActivityIngresoBinding
import com.sabornatural.tienda.ui.comun.Biometria
import com.sabornatural.tienda.ui.comun.Navegador
import com.sabornatural.tienda.ui.comun.PantallaBase
import com.sabornatural.tienda.ui.comun.Validaciones
import com.sabornatural.tienda.ui.comun.ocultarTeclado
import com.sabornatural.tienda.ui.comun.rellenarBarraDeEstado
import com.sabornatural.tienda.ui.comun.rellenarBarraDeNavegacion
import com.sabornatural.tienda.ui.comun.texto
import com.sabornatural.tienda.ui.comun.validar

/**
 * Ingreso con correo y contraseña o con huella (mockups C-02 y C-03).
 *
 * Reglas aplicadas (documento técnico, RF-AUT-03 y RF-AUT-05):
 *  - el mensaje de error es genérico: no dice si falló el correo o la contraseña;
 *  - tras 5 intentos fallidos el ingreso se bloquea 30 segundos;
 *  - la huella solo desbloquea una sesión guardada y siempre se ofrece la contraseña como alternativa.
 */
class IngresoActivity : PantallaBase() {

    private lateinit var binding: ActivityIngresoBinding
    private lateinit var sesion: Sesion

    private var intentosFallidos = 0
    private var temporizadorBloqueo: CountDownTimer? = null

    /** true cuando se abrió desde el splash para desbloquear una sesión guardada. */
    private val modoDesbloqueo: Boolean get() = intent.getBooleanExtra(EXTRA_DESBLOQUEAR_CON_HUELLA, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIngresoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sesion = Sesion(this)
        RepositorioUsuarios.inicializar(this)

        configurarEncabezado()
        binding.desplazable.rellenarBarraDeNavegacion()

        binding.botonIngresar.setOnClickListener { ingresarConClave() }
        binding.textoClave.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_DONE) ingresarConClave()
            false
        }
        binding.botonOlvideClave.setOnClickListener {
            startActivity(
                Intent(this, RecuperarClaveActivity::class.java)
                    .putExtra(RecuperarClaveActivity.EXTRA_CORREO, binding.campoCorreo.texto),
            )
        }
        binding.botonCrearCuenta.setOnClickListener {
            startActivity(Intent(this, RegistroActivity::class.java))
        }
        binding.botonHuella.setOnClickListener { pedirHuella() }

        configurarHuella()
        if (modoDesbloqueo && savedInstanceState == null) pedirHuella()
    }

    override fun onDestroy() {
        temporizadorBloqueo?.cancel()
        super.onDestroy()
    }

    private fun configurarEncabezado() = with(binding.encabezado) {
        root.rellenarBarraDeEstado()
        titulo.setText(R.string.ingreso_titulo)
        subtitulo.setText(if (modoDesbloqueo) R.string.ingreso_huella_desbloquear else R.string.ingreso_subtitulo)
        // Al desbloquear no hay pantalla anterior a la que volver
        botonVolver.visibility = if (modoDesbloqueo) View.INVISIBLE else View.VISIBLE
        botonVolver.setOnClickListener { finish() }
    }

    /** El botón de huella solo aparece si hay una sesión guardada con la huella activada. */
    private fun configurarHuella() {
        val usuarioGuardado = sesion.usuarioActual
        val mostrar = sesion.huellaHabilitada && usuarioGuardado != null && Biometria.disponible(this)
        binding.botonHuella.visibility = if (mostrar) View.VISIBLE else View.GONE
        binding.separador.visibility = binding.botonHuella.visibility
        if (mostrar) binding.campoCorreo.editText?.setText(usuarioGuardado?.correo)
    }

    private fun pedirHuella() {
        Biometria.autenticar(
            actividad = this,
            alExito = { sesion.usuarioActual?.let { Navegador.abrirPanel(this, it) } },
            alUsarClave = { binding.campoClave.requestFocus() },
            alError = { mensaje -> mostrarAviso(mensaje.toString()) },
        )
    }

    private fun ingresarConClave() {
        ocultarAviso()
        val correo = binding.campoCorreo.texto
        val clave = binding.campoClave.editText?.text?.toString().orEmpty()

        // Validación en el cliente: campos completos y correo con formato válido
        val correoValido = binding.campoCorreo.validar(
            correo.isNotEmpty() && Validaciones.esCorreoValido(correo),
            getString(if (correo.isEmpty()) R.string.error_campo_obligatorio else R.string.error_correo_invalido),
        )
        val claveValida = binding.campoClave.validar(clave.isNotEmpty(), getString(R.string.error_campo_obligatorio))
        if (!correoValido || !claveValida) return

        ocultarTeclado()
        val usuario = RepositorioUsuarios.autenticar(correo, clave)
        when {
            usuario == null -> registrarIntentoFallido()
            usuario.estado == EstadoCuenta.PENDIENTE -> mostrarAviso(getString(R.string.ingreso_cuenta_pendiente), pendiente = true)
            usuario.estado == EstadoCuenta.SUSPENDIDO -> mostrarAviso(getString(R.string.ingreso_cuenta_suspendida))
            else -> ingresoExitoso(usuario)
        }
    }

    private fun registrarIntentoFallido() {
        intentosFallidos++
        binding.campoClave.editText?.text?.clear()
        if (intentosFallidos >= MAX_INTENTOS) bloquearIngreso() else mostrarAviso(getString(R.string.ingreso_error_credenciales))
    }

    /** Bloqueo temporal tras cinco intentos fallidos, con cuenta regresiva visible. */
    private fun bloquearIngreso() {
        binding.botonIngresar.isEnabled = false
        temporizadorBloqueo = object : CountDownTimer(BLOQUEO_MS, 1000) {
            override fun onTick(restante: Long) {
                val segundos = (restante / 1000 + 1).toInt()
                mostrarAviso(resources.getQuantityString(R.plurals.ingreso_bloqueado, segundos, segundos))
            }

            override fun onFinish() {
                intentosFallidos = 0
                binding.botonIngresar.isEnabled = true
                ocultarAviso()
            }
        }.start()
    }

    /** Abre la sesión y, si el equipo tiene huella y aún no se activó, ofrece activarla. */
    private fun ingresoExitoso(usuario: Usuario) {
        sesion.iniciar(usuario)
        if (Biometria.disponible(this) && !sesion.huellaHabilitada) {
            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_huella)
                .setTitle(R.string.ingreso_activar_huella_titulo)
                .setMessage(R.string.ingreso_activar_huella_texto)
                .setPositiveButton(R.string.ingreso_activar_huella_si) { _, _ ->
                    sesion.huellaHabilitada = true
                    Snackbar.make(binding.root, R.string.ingreso_huella_activada, Snackbar.LENGTH_SHORT).show()
                    binding.root.postDelayed({ Navegador.abrirPanel(this, usuario) }, 900)
                }
                .setNegativeButton(R.string.ingreso_activar_huella_no) { _, _ -> Navegador.abrirPanel(this, usuario) }
                .setCancelable(false)
                .show()
        } else {
            Navegador.abrirPanel(this, usuario)
        }
    }

    /** Muestra el aviso en rojo (error) o en ámbar (estado pendiente), según el caso. */
    private fun mostrarAviso(mensaje: String, pendiente: Boolean = false) {
        val (fondo, texto) = if (pendiente) {
            R.color.estado_pendiente_fondo to R.color.estado_pendiente_texto
        } else {
            R.color.estado_error_fondo to R.color.estado_error_texto
        }
        binding.aviso.backgroundTintList = ContextCompat.getColorStateList(this, fondo)
        binding.aviso.setTextColor(ContextCompat.getColor(this, texto))
        binding.aviso.text = mensaje
        binding.aviso.visibility = View.VISIBLE
    }

    private fun ocultarAviso() {
        binding.aviso.visibility = View.GONE
    }

    companion object {
        const val EXTRA_DESBLOQUEAR_CON_HUELLA = "desbloquear_con_huella"
        private const val MAX_INTENTOS = 5
        private const val BLOQUEO_MS = 30_000L
    }
}
