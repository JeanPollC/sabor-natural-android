package com.sabornatural.tienda.datos

import android.content.Context
import androidx.core.content.edit

/**
 * Preferencias de notificaciones del usuario (RF-PER-05, Ley 2300 de 2023):
 * los avisos del pedido vienen activados; las promociones, desactivadas hasta que el usuario las active.
 */
class Preferencias(contexto: Context) {

    private val preferencias = contexto.getSharedPreferences("preferencias", Context.MODE_PRIVATE)

    var avisosPedidos: Boolean
        get() = preferencias.getBoolean("avisos_pedidos", true)
        set(valor) = preferencias.edit { putBoolean("avisos_pedidos", valor) }

    var promociones: Boolean
        get() = preferencias.getBoolean("promociones", false)
        set(valor) = preferencias.edit { putBoolean("promociones", valor) }
}
