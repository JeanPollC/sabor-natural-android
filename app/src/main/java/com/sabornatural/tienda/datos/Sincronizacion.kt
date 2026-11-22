package com.sabornatural.tienda.datos

import com.sabornatural.tienda.datos.modelo.Rol
import com.sabornatural.tienda.datos.modelo.Usuario
import com.sabornatural.tienda.datos.remoto.Nube

/**
 * Abre y cierra las escuchas de Firestore según quién usa la app.
 *
 * La llama cada pantalla de rol al crearse (PanelRolActivity). Es idempotente: si el usuario y el
 * rol no cambiaron, no vuelve a abrir nada. Al cerrar sesión, [detener] cierra todo para que el
 * siguiente usuario no vea datos del anterior.
 */
object Sincronizacion {

    private var claveActual: String? = null

    /** Empieza a sincronizar los datos que [usuario] puede ver (null = invitado: solo el catálogo). */
    fun iniciar(usuario: Usuario?) {
        if (!Nube.configurado) return
        val clave = "${usuario?.id}:${usuario?.rol}"
        if (clave == claveActual) return
        detener()
        claveActual = clave

        RepositorioProductos.escuchar(usuario)
        if (usuario == null) return
        RepositorioUsuarios.escuchar(usuario)
        RepositorioPedidos.escuchar(usuario)
        if (usuario.rol == Rol.COMPRADOR) Carrito.sincronizar(usuario.id)
    }

    /** Cierra todas las escuchas y vacía las copias locales. */
    fun detener() {
        claveActual = null
        if (!Nube.configurado) return
        RepositorioProductos.detener()
        RepositorioUsuarios.detener()
        RepositorioPedidos.detener()
    }
}
