package com.sabornatural.tienda.datos

import android.content.Context
import androidx.core.content.edit
import com.sabornatural.tienda.datos.modelo.Usuario

/**
 * Sesión del usuario, guardada en SharedPreferences.
 *
 * Solo se guarda el identificador del usuario y la preferencia de huella, nunca la contraseña.
 * En la versión conectada a la API aquí se guardará el token cifrado con una clave del
 * Android Keystore (documento técnico, RF-AUT-05 y RF-AUT-07).
 */
class Sesion(contexto: Context) {

    private val preferencias = contexto.getSharedPreferences("sesion", Context.MODE_PRIVATE)

    /** Usuario con sesión abierta, o null si no hay sesión. */
    val usuarioActual: Usuario?
        get() = preferencias.getInt(CLAVE_USUARIO, SIN_USUARIO)
            .takeIf { it != SIN_USUARIO }
            ?.let { RepositorioUsuarios.buscarPorId(it) }

    /** Si el usuario activó el ingreso con huella para desbloquear su sesión. */
    var huellaHabilitada: Boolean
        get() = preferencias.getBoolean(CLAVE_HUELLA, false)
        set(valor) = preferencias.edit { putBoolean(CLAVE_HUELLA, valor) }

    fun iniciar(usuario: Usuario) = preferencias.edit { putInt(CLAVE_USUARIO, usuario.id) }

    /** Cierra la sesión: borra el usuario y la preferencia de huella (RF-AUT-07). */
    fun cerrar() = preferencias.edit { clear() }

    private companion object {
        const val CLAVE_USUARIO = "usuario_id"
        const val CLAVE_HUELLA = "huella_habilitada"
        const val SIN_USUARIO = -1
    }
}
