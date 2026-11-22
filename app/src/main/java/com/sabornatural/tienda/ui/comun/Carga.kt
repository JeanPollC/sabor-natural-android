package com.sabornatural.tienda.ui.comun

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.remoto.Errores
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * Ejecución de operaciones contra Firebase desde las pantallas, con corrutinas.
 *
 * Las escrituras (guardar, eliminar, pagar...) son funciones suspend de los repositorios. Estas
 * funciones las lanzan en el lifecycleScope de la pantalla (se cancelan solas si la pantalla se
 * destruye), muestran un diálogo "Guardando…" mientras tanto y, si algo falla, un aviso con el
 * error traducido a español.
 */

/** Diálogo modal con indicador circular y un texto (dialogo_cargando.xml). */
fun Context.dialogoCargando(@StringRes mensaje: Int): AlertDialog {
    val vista = LayoutInflater.from(this).inflate(R.layout.dialogo_cargando, null)
    vista.findViewById<TextView>(R.id.mensaje).setText(mensaje)
    return MaterialAlertDialogBuilder(this).setView(vista).setCancelable(false).show()
}

/** Lógica común: diálogo de carga, ejecución y manejo de errores. */
private fun ejecutarEn(
    alcance: LifecycleCoroutineScope,
    contexto: () -> Context?,
    vista: () -> View?,
    @StringRes mensaje: Int,
    alFallar: ((Throwable) -> Unit)?,
    accion: suspend () -> Unit,
): Job = alcance.launch {
    val dialogo = contexto()?.dialogoCargando(mensaje)
    try {
        accion()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (alFallar != null) {
            alFallar(e)
        } else {
            vista()?.let { Snackbar.make(it, Errores.texto(it.context, e), Snackbar.LENGTH_LONG).show() }
        }
    } finally {
        dialogo?.dismiss()
    }
}

/**
 * Ejecuta [accion] (que llama a Firebase) desde un Fragment mostrando "[mensaje]…".
 * @param alFallar manejo propio del error; por defecto se muestra un Snackbar.
 */
fun Fragment.ejecutar(
    @StringRes mensaje: Int = R.string.carga_guardando,
    alFallar: ((Throwable) -> Unit)? = null,
    accion: suspend () -> Unit,
): Job = ejecutarEn(viewLifecycleOwner.lifecycleScope, { context }, { view }, mensaje, alFallar, accion)

/** Igual que [Fragment.ejecutar], para las Activities. */
fun AppCompatActivity.ejecutar(
    @StringRes mensaje: Int = R.string.carga_guardando,
    alFallar: ((Throwable) -> Unit)? = null,
    accion: suspend () -> Unit,
): Job = ejecutarEn(lifecycleScope, { this }, { findViewById(android.R.id.content) }, mensaje, alFallar, accion)
