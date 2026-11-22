package com.sabornatural.tienda

import android.app.Application
import com.sabornatural.tienda.datos.remoto.Nube

/**
 * Clase Application: Android la crea antes que cualquier pantalla (se declara en el
 * AndroidManifest con android:name). Aquí se inicializa Firebase una sola vez.
 *
 * Los datos ya no se cargan de archivos locales: llegan de Firestore cuando cada pantalla de rol
 * arranca la sincronización (ver datos/Sincronizacion.kt).
 */
class SaborNaturalApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instancia = this
        Nube.iniciar(this)
    }

    companion object {
        /**
         * Contexto de la aplicación, para las clases de datos que necesitan leer preferencias
         * sin depender de una pantalla (nunca guarda una Activity, así que no hay fugas de memoria).
         */
        lateinit var instancia: SaborNaturalApp
            private set
    }
}
