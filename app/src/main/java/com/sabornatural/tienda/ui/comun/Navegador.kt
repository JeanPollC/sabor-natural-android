package com.sabornatural.tienda.ui.comun

import android.app.Activity
import android.content.Intent
import com.sabornatural.tienda.datos.modelo.Usuario
import com.sabornatural.tienda.ui.inicio.BienvenidaActivity
import com.sabornatural.tienda.ui.panel.PanelProvisionalActivity

/**
 * Punto único de navegación entre los grandes bloques de la app. Cuando la sesión es válida,
 * el rol del usuario decide qué conjunto de pantallas se abre (documento técnico, sección 5.2).
 */
object Navegador {

    /** Abre el panel que corresponde al rol y borra la pila: "atrás" no regresa al ingreso. */
    fun abrirPanel(origen: Activity, usuario: Usuario) {
        // Fases 3 a 5: cada rol tendrá su propia Activity con barra inferior.
        val intent = Intent(origen, PanelProvisionalActivity::class.java)
            .putExtra(PanelProvisionalActivity.EXTRA_USUARIO_ID, usuario.id)
        iniciarLimpio(origen, intent)
    }

    /** Vuelve a la bienvenida después de cerrar sesión. */
    fun abrirBienvenida(origen: Activity) = iniciarLimpio(origen, Intent(origen, BienvenidaActivity::class.java))

    private fun iniciarLimpio(origen: Activity, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        origen.startActivity(intent)
        origen.finish()
    }
}
