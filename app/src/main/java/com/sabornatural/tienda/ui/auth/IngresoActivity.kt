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
import androidx.lifecycle.lifecycleScope
import com.sabornatural.tienda.datos.RepositorioUsuarios
import com.sabornatural.tienda.datos.Sesion
import com.sabornatural.tienda.datos.modelo.Usuario
import com.sabornatural.tienda.datos.remoto.Errores
import com.sabornatural.tienda.datos.remoto.Nube
import com.sabornatural.tienda.ui.inicio.SplashActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
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
 *
 * La verificación de la contraseña la hace Firebase Authentication; luego se lee el perfil en
 * Firestore para conocer el rol y el estado (pendiente o suspendida no pueden entrar).
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

    /** Título y botón volver; al desbloquear con huella no hay pantalla anterior. */
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

    /**
     * Desbloqueo con huella: el sistema verifica la huella, la llave del Keystore descifra el uid
     * guardado y, si coincide con la sesión de Firebase de este teléfono, se reabre esa sesión
     * (se confirma el perfil en Firestore y se abre el panel del rol).
     */
    private fun pedirHuella() {
        Biometria.desbloquear(
            actividad = this,
            alExito = { uid ->
                if (uid != Nube.uidActual) {
                    mostrarAviso(getString(R.string.huella_sesion_vencida))
                    return@desbloquear
                }
                lifecycleScope.launch {
                    val usuario = SplashActivity.cargarPerfilVigente(this@IngresoActivity)
                    if (usuario == null) {
                        configurarHuella()
                        mostrarAviso(getString(R.string.huella_sesion_vencida))
                    } else {
                        Navegador.abrirPanel(this@IngresoActivity, usuario)
                    }
                }
            },
            alUsarClave = { binding.campoClave.requestFocus() },
            alError = { mensaje ->
                configurarHuella()
                mostrarAviso(mensaje.toString())
            },
        )
    }

    /** Valida el formulario e ingresa con Firebase Authentication. */
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
        binding.botonIngresar.isEnabled = false
        binding.botonIngresar.setText(R.string.ingreso_verificando)
        lifecycleScope.launch {
            try {
                // Si había otra sesión guardada (por ejemplo, al desbloquear se eligió la contraseña), se cierra antes
                if (Nube.uidActual != null) sesion.cerrar()
                val usuario = RepositorioUsuarios.autenticar(correo, clave)
                ingresoExitoso(usuario)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                when {
                    Errores.esCredencialInvalida(e) -> registrarIntentoFallido()
                    e is com.sabornatural.tienda.datos.ErrorNegocio && e.mensaje == R.string.ingreso_cuenta_pendiente ->
                        mostrarAviso(getString(R.string.ingreso_cuenta_pendiente), pendiente = true)
                    else -> mostrarAviso(getString(Errores.mensaje(e)))
                }
            } finally {
                binding.botonIngresar.setText(R.string.ingreso_boton)
                if (temporizadorBloqueo == null) binding.botonIngresar.isEnabled = true
            }
        }
    }

    /** Cuenta un intento fallido y bloquea el ingreso al llegar a 5. */
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
                temporizadorBloqueo = null
                intentosFallidos = 0
                binding.botonIngresar.isEnabled = true
                ocultarAviso()
            }
        }.start()
    }

    /**
     * Abre la sesión y, si el equipo tiene huella y aún no se activó, ofrece activarla. Activarla
     * pide la huella una vez para crear la llave del Keystore (ver Biometria.activar).
     */
    private fun ingresoExitoso(usuario: Usuario) {
        sesion.iniciar(usuario)
        if (Biometria.disponible(this) && !sesion.huellaHabilitada) {
            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_huella)
                .setTitle(R.string.ingreso_activar_huella_titulo)
                .setMessage(R.string.ingreso_activar_huella_texto)
                .setPositiveButton(R.string.ingreso_activar_huella_si) { _, _ ->
                    Biometria.activar(
                        actividad = this,
                        uid = usuario.id,
                        alExito = {
                            Snackbar.make(binding.root, R.string.ingreso_huella_activada, Snackbar.LENGTH_SHORT).show()
                            binding.root.postDelayed({ Navegador.abrirPanel(this, usuario) }, 900)
                        },
                        alCancelar = { Navegador.abrirPanel(this, usuario) },
                        alError = { Navegador.abrirPanel(this, usuario) },
                    )
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

    /** Oculta el aviso de error o de estado. */
    private fun ocultarAviso() {
        binding.aviso.visibility = View.GONE
    }

    companion object {
        const val EXTRA_DESBLOQUEAR_CON_HUELLA = "desbloquear_con_huella"
        private const val MAX_INTENTOS = 5
        private const val BLOQUEO_MS = 30_000L
    }
}
