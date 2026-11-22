package com.sabornatural.tienda.ui.comun

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.annotation.MenuRes
import androidx.annotation.NavigationRes
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.RepositorioUsuarios
import com.sabornatural.tienda.datos.Sesion
import com.sabornatural.tienda.datos.Sincronizacion
import com.sabornatural.tienda.datos.modelo.Usuario
import com.sabornatural.tienda.databinding.ActivityPanelRolBinding
import com.sabornatural.tienda.databinding.EncabezadoMenuLateralBinding
import kotlinx.coroutines.launch

/**
 * Base de las Activities de los tres roles. Cada rol solo indica su grafo de navegación, sus
 * menús y qué destinos muestran la barra inferior; esta clase conecta:
 *   NavHostFragment  ↔  BottomNavigationView  ↔  NavigationView (menú lateral)
 * de modo que tocar un destino en cualquiera de los dos menús abre el mismo Fragment.
 *
 * Además arranca la sincronización con Firestore para el usuario de la sesión (o el invitado) y
 * cierra la sesión si el administrador suspende o elimina la cuenta mientras se usa.
 */
abstract class PanelRolActivity : PantallaBase(), ConMenuLateral {

    @get:NavigationRes protected abstract val grafo: Int
    @get:MenuRes protected abstract val menuInferior: Int
    @get:MenuRes protected abstract val menuLateral: Int

    /** Destinos con barra inferior y menú lateral; en los demás (detalles) se ocultan. */
    protected abstract val destinosPrincipales: Set<Int>

    protected lateinit var binding: ActivityPanelRolBinding
    protected lateinit var navController: NavController
    protected val usuario: Usuario? get() = Sesion(this).usuarioActual

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPanelRolBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // NavHostFragment: el "marco" donde se muestran los Fragments del grafo del rol
        val host = supportFragmentManager.findFragmentById(R.id.contenedor) as? NavHostFragment
            ?: NavHostFragment.create(grafo).also {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.contenedor, it)
                    .setPrimaryNavigationFragment(it)
                    .commitNow()
            }
        navController = host.navController

        // Barra inferior: Navigation la sincroniza con el destino actual
        binding.barraInferior.inflateMenu(menuInferior)
        binding.barraInferior.setupWithNavController(navController)

        configurarMenuLateral()

        // Datos en vivo desde Firestore según el rol (idempotente: no duplica escuchas)
        Sincronizacion.iniciar(usuario)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { RepositorioUsuarios.actual.collect { llenarEncabezadoMenu() } }
                launch { RepositorioUsuarios.sesionInvalida.collect { mensaje -> expulsar(mensaje) } }
            }
        }

        navController.addOnDestinationChangedListener { _, destino, _ ->
            val principal = destino.id in destinosPrincipales
            binding.barraInferior.visibility = if (principal) View.VISIBLE else View.GONE
            binding.cajon.setDrawerLockMode(
                if (principal) DrawerLayout.LOCK_MODE_UNLOCKED else DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
            )
            binding.menuLateral.setCheckedItem(destino.id)
        }

        // Con el menú lateral abierto, "atrás" lo cierra en lugar de salir de la pantalla
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.cajon.isDrawerOpen(GravityCompat.START)) {
                    binding.cajon.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        llenarEncabezadoMenu()
    }

    /** Abre el menú lateral (lo piden los Fragments con el botón ☰). */
    override fun abrirMenuLateral() = binding.cajon.openDrawer(GravityCompat.START)

    /** Opciones del menú lateral: navegar o cerrar sesión. */
    private fun configurarMenuLateral() {
        binding.menuLateral.inflateMenu(menuLateral)
        binding.menuLateral.setNavigationItemSelectedListener { opcion ->
            binding.cajon.closeDrawer(GravityCompat.START)
            if (opcion.itemId == R.id.menu_cerrar_sesion) {
                confirmarCierreDeSesion()
                false
            } else {
                NavigationUI.onNavDestinationSelected(opcion, navController)
            }
        }
    }

    /** Nombre, correo y rol del usuario en la cabecera del menú lateral. */
    private fun llenarEncabezadoMenu() {
        val encabezado = EncabezadoMenuLateralBinding.bind(binding.menuLateral.getHeaderView(0))
        val actual = usuario
        encabezado.avatar.text = actual?.iniciales ?: "SN"
        encabezado.nombre.text = actual?.nombreCompleto ?: getString(R.string.app_name)
        encabezado.correo.text = actual?.correo ?: getString(R.string.eslogan)
        encabezado.rol.visibility = if (actual == null) View.GONE else View.VISIBLE
        actual?.let { encabezado.rol.text = getString(it.rol.nombre) }
        binding.menuLateral.menu.findItem(R.id.menu_cerrar_sesion)?.isVisible = actual != null
    }

    /** La cuenta dejó de ser válida (suspendida o eliminada): aviso, cierre de sesión y bienvenida. */
    private fun expulsar(mensaje: Int) {
        if (isFinishing) return
        Sesion(this).cerrar()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sesion_cerrada_titulo)
            .setMessage(mensaje)
            .setPositiveButton(R.string.registro_entendido) { _, _ -> Navegador.abrirBienvenida(this) }
            .setCancelable(false)
            .show()
    }

    /** Pregunta y, si el usuario confirma, cierra la sesión de Firebase y vuelve a la bienvenida. */
    fun confirmarCierreDeSesion() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.panel_cerrar_sesion)
            .setMessage(R.string.panel_cerrar_sesion_confirmar)
            .setPositiveButton(R.string.panel_cerrar_sesion) { _, _ ->
                Sesion(this).cerrar()
                Navegador.abrirBienvenida(this)
            }
            .setNegativeButton(R.string.accion_cancelar, null)
            .show()
    }
}
