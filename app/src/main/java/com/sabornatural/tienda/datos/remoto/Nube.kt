package com.sabornatural.tienda.datos.remoto

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.sabornatural.tienda.BuildConfig

/**
 * Punto único de acceso a Firebase (Authentication, Firestore y Storage).
 *
 * Firebase se configura con app/google-services.json. Si el archivo no estaba al compilar, la
 * app no se cae: [configurado] queda en false y el splash explica qué falta.
 */
object Nube {

    /** Nombres de las colecciones de Firestore. */
    const val USUARIOS = "usuarios"
    const val PRODUCTOS = "productos"
    const val PEDIDOS = "pedidos"
    const val CARRITOS = "carritos"
    const val CATEGORIAS = "categorias"
    const val CONFIGURACION = "configuracion"

    /** Documento que marca quién fue el primer administrador (ver firestore.rules). */
    const val DOC_ADMINISTRADOR = "administrador"

    /** Nombre de la instancia secundaria de Firebase que usa el administrador para crear cuentas. */
    private const val APP_SECUNDARIA = "administracion"

    /** true si Firebase quedó inicializado con un google-services.json válido. */
    var configurado: Boolean = false
        private set

    /**
     * Inicializa Firebase. Se llama una vez desde SaborNaturalApp.
     * Normalmente Firebase ya se inicializó solo (FirebaseInitProvider); si no hay configuración,
     * initializeApp devuelve null y la app sigue en modo "sin configurar".
     */
    fun iniciar(contexto: Context) {
        configurado = BuildConfig.FIREBASE_CONFIGURADO && runCatching {
            FirebaseApp.getApps(contexto).isNotEmpty() || FirebaseApp.initializeApp(contexto) != null
        }.onFailure { Log.e("Nube", "No se pudo inicializar Firebase", it) }.getOrDefault(false)
    }

    /** Firebase Authentication de la instancia principal (la sesión del usuario de este teléfono). */
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    /** Base de datos Cloud Firestore. */
    val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    /** Almacenamiento de archivos (fotos de productos y de perfil). */
    val storage: FirebaseStorage get() = FirebaseStorage.getInstance()

    /** uid del usuario con sesión de Firebase abierta en este teléfono, o null. */
    val uidActual: String? get() = if (configurado) auth.currentUser?.uid else null

    /**
     * Instancia secundaria de Firebase Authentication, con la misma configuración que la principal.
     *
     * Por qué existe: con el SDK cliente, `createUserWithEmailAndPassword` deja abierta la sesión
     * de la cuenta recién creada. Si el administrador creara usuarios con la instancia principal,
     * su propia sesión se cerraría. Con una instancia aparte, la cuenta nueva se crea (y se cierra)
     * allí y la sesión del administrador no se toca.
     */
    fun authSecundaria(contexto: Context): FirebaseAuth {
        val app = FirebaseApp.getApps(contexto).find { it.name == APP_SECUNDARIA }
            ?: FirebaseApp.initializeApp(contexto, FirebaseApp.getInstance().options, APP_SECUNDARIA)
        return FirebaseAuth.getInstance(app)
    }
}
