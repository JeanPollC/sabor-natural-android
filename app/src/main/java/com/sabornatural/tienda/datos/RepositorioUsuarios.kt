package com.sabornatural.tienda.datos

import android.content.Context
import com.sabornatural.tienda.datos.modelo.EstadoCuenta
import com.sabornatural.tienda.datos.modelo.Rol
import com.sabornatural.tienda.datos.modelo.Usuario
import org.json.JSONObject
import java.security.MessageDigest
import java.time.LocalDate

/**
 * Fuente de datos de usuarios para la demostración del frontend.
 *
 * En esta entrega la aplicación no se conecta a la API: los usuarios se leen de
 * assets/datos_demo/usuarios.json y se mantienen en memoria mientras la app está abierta.
 * Cuando se integre el backend, solo cambia esta clase; las pantallas no se enteran.
 */
object RepositorioUsuarios {

    private val usuarios = mutableListOf<Usuario>()

    /** Carga los datos de prueba una sola vez (se llama desde el splash). */
    fun inicializar(contexto: Context) {
        if (usuarios.isNotEmpty()) return
        val texto = contexto.assets.open("datos_demo/usuarios.json").bufferedReader().use { it.readText() }
        val lista = JSONObject(texto).getJSONArray("usuarios")
        for (i in 0 until lista.length()) {
            val u = lista.getJSONObject(i)
            usuarios += Usuario(
                id = u.getInt("id"),
                nombre = u.getString("nombre"),
                apellido = u.getString("apellido"),
                correo = u.getString("correo"),
                telefono = u.getString("telefono"),
                rol = Rol.valueOf(u.getString("rol")),
                estado = EstadoCuenta.valueOf(u.getString("estado")),
                claveHash = u.getString("claveHash"),
                fechaRegistro = u.getString("fechaRegistro"),
            )
        }
    }

    fun buscarPorId(id: Int): Usuario? = usuarios.find { it.id == id }

    fun existeCorreo(correo: String): Boolean = usuarios.any { it.correo.equals(correo.trim(), ignoreCase = true) }

    /**
     * Verifica correo y contraseña comparando hashes. Devuelve null si alguno no coincide,
     * sin indicar cuál, para no revelar si el correo existe (RF-AUT-03).
     */
    fun autenticar(correo: String, clave: String): Usuario? {
        val hash = hashClave(clave)
        return usuarios.find { it.correo.equals(correo.trim(), ignoreCase = true) && it.claveHash == hash }
    }

    /** Registra una cuenta nueva: el comprador queda activo y el vendedor pendiente de aprobación (RF-AUT-02). */
    fun registrar(nombre: String, apellido: String, correo: String, telefono: String, clave: String, rol: Rol): Usuario {
        val nuevo = Usuario(
            id = (usuarios.maxOfOrNull { it.id } ?: 0) + 1,
            nombre = nombre.trim(),
            apellido = apellido.trim(),
            correo = correo.trim().lowercase(),
            telefono = telefono.trim(),
            rol = rol,
            estado = if (rol == Rol.VENDEDOR) EstadoCuenta.PENDIENTE else EstadoCuenta.ACTIVO,
            claveHash = hashClave(clave),
            fechaRegistro = LocalDate.now().toString(),
        )
        usuarios += nuevo
        return nuevo
    }

    private fun hashClave(clave: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(clave.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
