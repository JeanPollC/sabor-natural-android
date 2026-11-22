package com.sabornatural.tienda.datos

import android.content.Context
import androidx.core.content.edit
import com.sabornatural.tienda.datos.modelo.EstadoCuenta
import com.sabornatural.tienda.datos.modelo.Rol
import com.sabornatural.tienda.datos.modelo.Usuario
import com.sabornatural.tienda.datos.remoto.Nube
import org.json.JSONObject

/**
 * Sesión del usuario en este teléfono.
 *
 * La sesión real la guarda Firebase Authentication (un token que se renueva solo y que Firebase
 * almacena en privado). Esta clase solo agrega:
 *  - una copia del perfil (nombre, rol, estado...) en SharedPreferences, para poder mostrar la
 *    pantalla del rol aunque Android haya cerrado la app en segundo plano;
 *  - la preferencia de ingreso con huella y el dato cifrado que la acompaña (ver Biometria.kt).
 *
 * Nunca se guarda la contraseña.
 */
class Sesion(contexto: Context) {

    private val preferencias = contexto.applicationContext.getSharedPreferences("sesion", Context.MODE_PRIVATE)

    /**
     * Usuario con sesión abierta, o null si no hay sesión de Firebase. Se toma el perfil en vivo
     * de Firestore y, mientras llega, la copia guardada (solo si es del mismo uid).
     */
    val usuarioActual: Usuario?
        get() {
            val uid = Nube.uidActual ?: return null
            return RepositorioUsuarios.actual.value?.takeIf { it.id == uid }
                ?: leerCopia()?.takeIf { it.id == uid }
        }

    /**
     * Si el usuario activó el ingreso con huella para desbloquear su sesión. Solo es true si
     * además existe el dato cifrado con la llave del Keystore: una preferencia suelta (por ejemplo,
     * de una versión anterior de la app) no cuenta como huella activada.
     */
    var huellaHabilitada: Boolean
        get() = preferencias.getBoolean(CLAVE_HUELLA, false) &&
            preferencias.getString(CLAVE_HUELLA_DATO, null) != null &&
            preferencias.getString(CLAVE_HUELLA_IV, null) != null
        set(valor) = preferencias.edit { putBoolean(CLAVE_HUELLA, valor) }

    /** Dato cifrado con la llave de huella del Android Keystore (Base64) y su vector inicial. */
    var huellaCifrado: String?
        get() = preferencias.getString(CLAVE_HUELLA_DATO, null)
        set(valor) = preferencias.edit { putString(CLAVE_HUELLA_DATO, valor) }

    var huellaIv: String?
        get() = preferencias.getString(CLAVE_HUELLA_IV, null)
        set(valor) = preferencias.edit { putString(CLAVE_HUELLA_IV, valor) }

    /** Registra el perfil del usuario que acaba de ingresar y lo deja disponible para toda la app. */
    fun iniciar(usuario: Usuario) {
        guardarCopia(usuario)
        RepositorioUsuarios.establecerActual(usuario)
    }

    /** Actualiza la copia guardada cuando el perfil cambia en Firestore. */
    fun guardarCopia(usuario: Usuario) = preferencias.edit {
        putString(
            CLAVE_PERFIL,
            JSONObject()
                .put("id", usuario.id)
                .put("nombre", usuario.nombre)
                .put("apellido", usuario.apellido)
                .put("correo", usuario.correo)
                .put("telefono", usuario.telefono)
                .put("rol", usuario.rol.name)
                .put("estado", usuario.estado.name)
                .put("fechaRegistro", usuario.fechaRegistro)
                .put("fotoUrl", usuario.fotoUrl)
                .toString(),
        )
    }

    /** Lee la copia del perfil guardada en SharedPreferences (null si no hay o está dañada). */
    private fun leerCopia(): Usuario? = runCatching {
        val j = JSONObject(preferencias.getString(CLAVE_PERFIL, null) ?: return null)
        Usuario(
            id = j.getString("id"),
            nombre = j.optString("nombre"),
            apellido = j.optString("apellido"),
            correo = j.optString("correo"),
            telefono = j.optString("telefono"),
            rol = Rol.valueOf(j.getString("rol")),
            estado = EstadoCuenta.valueOf(j.getString("estado")),
            fechaRegistro = j.optString("fechaRegistro"),
            fotoUrl = j.optString("fotoUrl"),
        )
    }.getOrNull()

    /**
     * Cierra la sesión (RF-AUT-07): detiene la sincronización, cierra la sesión de Firebase y
     * borra el perfil guardado, la huella y el carrito de este teléfono. El carrito del comprador
     * sigue guardado en Firestore y vuelve al ingresar de nuevo.
     */
    fun cerrar() {
        Sincronizacion.detener()
        Carrito.vaciarLocal()
        if (Nube.configurado) Nube.auth.signOut()
        preferencias.edit { clear() }
    }

    private companion object {
        const val CLAVE_PERFIL = "perfil"
        const val CLAVE_HUELLA = "huella_habilitada"
        const val CLAVE_HUELLA_DATO = "huella_dato"
        const val CLAVE_HUELLA_IV = "huella_iv"
    }
}
