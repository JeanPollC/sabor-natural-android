package com.sabornatural.tienda.datos

import com.sabornatural.tienda.datos.modelo.EstadoCuenta
import com.sabornatural.tienda.datos.modelo.Rol
import com.sabornatural.tienda.datos.modelo.Usuario
import java.security.MessageDigest
import java.time.LocalDate

/**
 * Usuarios de la tienda, en memoria mientras la app está abierta (datos de assets/datos_demo).
 * Cuando se integre el backend, solo cambia esta clase; las pantallas no se enteran.
 */
object RepositorioUsuarios {

    private val usuarios = mutableListOf<Usuario>()

    fun reemplazarTodos(lista: List<Usuario>) {
        usuarios.clear()
        usuarios += lista
    }

    fun todos(): List<Usuario> = usuarios.toList()

    fun buscarPorId(id: Int): Usuario? = usuarios.find { it.id == id }

    fun existeCorreo(correo: String, exceptoId: Int? = null): Boolean =
        usuarios.any { it.correo.equals(correo.trim(), ignoreCase = true) && it.id != exceptoId }

    /**
     * Verifica correo y contraseña comparando hashes. Devuelve null si alguno no coincide,
     * sin indicar cuál, para no revelar si el correo existe (RF-AUT-03).
     */
    fun autenticar(correo: String, clave: String): Usuario? {
        val hash = hashClave(clave)
        return usuarios.find { it.correo.equals(correo.trim(), ignoreCase = true) && it.claveHash == hash }
    }

    /** Registro desde la app: el comprador queda activo y el vendedor pendiente de aprobación (RF-AUT-02). */
    fun registrar(nombre: String, apellido: String, correo: String, telefono: String, clave: String, rol: Rol): Usuario =
        crear(nombre, apellido, correo, telefono, rol,
            if (rol == Rol.VENDEDOR) EstadoCuenta.PENDIENTE else EstadoCuenta.ACTIVO, hashClave(clave))

    /**
     * Alta hecha por el administrador (RF-ADU-02). El administrador no asigna contraseñas:
     * el titular recibe un enlace para crearla, por eso la cuenta nace sin clave.
     */
    fun crearDesdeAdministracion(nombre: String, apellido: String, correo: String, telefono: String, rol: Rol, estado: EstadoCuenta): Usuario =
        crear(nombre, apellido, correo, telefono, rol, estado, claveHash = "")

    private fun crear(nombre: String, apellido: String, correo: String, telefono: String, rol: Rol, estado: EstadoCuenta, claveHash: String): Usuario {
        val nuevo = Usuario(
            id = (usuarios.maxOfOrNull { it.id } ?: 0) + 1,
            nombre = nombre.trim(),
            apellido = apellido.trim(),
            correo = correo.trim().lowercase(),
            telefono = telefono.trim(),
            rol = rol,
            estado = estado,
            claveHash = claveHash,
            fechaRegistro = LocalDate.now().toString(),
        )
        usuarios += nuevo
        return nuevo
    }

    /** Reemplaza los datos de un usuario existente (editar perfil, editar usuario, aprobar vendedor). */
    fun actualizar(usuario: Usuario) {
        val i = usuarios.indexOfFirst { it.id == usuario.id }
        if (i >= 0) usuarios[i] = usuario
    }

    /** Baja de la cuenta (RF-ADU-05). En el servidor será un borrado lógico con anonimización. */
    fun eliminar(id: Int) {
        usuarios.removeAll { it.id == id }
    }

    private fun hashClave(clave: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(clave.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
