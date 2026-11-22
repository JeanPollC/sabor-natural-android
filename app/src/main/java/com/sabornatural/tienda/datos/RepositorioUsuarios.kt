package com.sabornatural.tienda.datos

import android.content.Context
import android.graphics.Bitmap
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.ListenerRegistration
import com.sabornatural.tienda.R
import com.sabornatural.tienda.SaborNaturalApp
import com.sabornatural.tienda.datos.modelo.EstadoCuenta
import com.sabornatural.tienda.datos.modelo.Rol
import com.sabornatural.tienda.datos.modelo.Usuario
import com.sabornatural.tienda.datos.remoto.Archivos
import com.sabornatural.tienda.datos.remoto.ColeccionEnVivo
import com.sabornatural.tienda.datos.remoto.Nube
import com.sabornatural.tienda.datos.remoto.aMapa
import com.sabornatural.tienda.datos.remoto.aUsuario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.time.LocalDate
import java.util.Base64

/**
 * Usuarios: autenticación con Firebase Authentication y perfiles en Firestore (`usuarios/{uid}`).
 *
 * El rol y el estado de cada cuenta viven en su documento de Firestore, no en Authentication;
 * las reglas de seguridad (firestore.rules) impiden que un usuario se cambie el rol o el estado.
 *
 * Las pantallas no saben que existe Firebase: llaman a estas funciones (las de escritura son
 * `suspend` y se ejecutan en corrutinas) y leen las listas, que se mantienen al día solas.
 */
object RepositorioUsuarios {

    /** Todos los usuarios (solo se llena para el administrador). */
    private val coleccion = ColeccionEnVivo { it.aUsuario() }

    private val _actual = MutableStateFlow<Usuario?>(null)

    /** Perfil del usuario con sesión, en vivo. */
    val actual: StateFlow<Usuario?> = _actual.asStateFlow()

    private val _sesionInvalida = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    /**
     * Avisa (con el texto a mostrar) cuando la cuenta abierta deja de ser válida mientras se usa:
     * el administrador la suspendió o la eliminó. La pantalla del rol cierra la sesión.
     */
    val sesionInvalida: SharedFlow<Int> = _sesionInvalida.asSharedFlow()

    private var registroPerfil: ListenerRegistration? = null

    /** Estado de carga de la lista de usuarios. */
    val estado: StateFlow<EstadoCarga> get() = coleccion.estado

    /** Emite cuando cambia la lista o su estado. */
    val cambios: Flow<Unit> get() = coleccion.cambios

    /** Lista de usuarios (vacía si quien consulta no es administrador). */
    fun todos(): List<Usuario> = coleccion.datos.value

    /** Usuario con ese id en la lista del administrador o el perfil propio, o null. */
    fun buscarPorId(id: String): Usuario? =
        coleccion.datos.value.find { it.id == id } ?: _actual.value?.takeIf { it.id == id }

    /** Vuelve a abrir la escucha de usuarios tras un error. */
    fun reintentar() = coleccion.reintentar()

    // ------------------------------------------------------------ Sincronización

    /** Deja disponible el perfil recién leído (al ingresar), antes de que llegue la escucha. */
    fun establecerActual(usuario: Usuario) {
        _actual.value = usuario
    }

    /**
     * Abre las escuchas de Firestore para [usuario]: su propio perfil (para ver cambios y detectar
     * una suspensión) y, si es administrador, la lista completa de usuarios.
     */
    fun escuchar(usuario: Usuario) {
        registroPerfil?.remove()
        registroPerfil = Nube.db.collection(Nube.USUARIOS).document(usuario.id).addSnapshotListener { doc, error ->
            if (error != null || doc == null) return@addSnapshotListener
            // Solo cuenta lo que confirmó el servidor (no la caché local) para decidir una expulsión
            if (doc.metadata.isFromCache) {
                doc.aUsuario()?.let { _actual.value = it }
                return@addSnapshotListener
            }
            val perfil = doc.aUsuario()
            when {
                perfil == null -> _sesionInvalida.tryEmit(R.string.sesion_cuenta_eliminada)
                perfil.estado == EstadoCuenta.SUSPENDIDO -> _sesionInvalida.tryEmit(R.string.ingreso_cuenta_suspendida)
                else -> {
                    _actual.value = perfil
                    Sesion(SaborNaturalApp.instancia).guardarCopia(perfil)
                }
            }
        }
        if (usuario.rol == Rol.ADMINISTRADOR) {
            coleccion.escuchar("todos", Nube.db.collection(Nube.USUARIOS))
        }
    }

    /** Cierra las escuchas (al cerrar sesión). */
    fun detener() {
        registroPerfil?.remove()
        registroPerfil = null
        coleccion.detener()
        _actual.value = null
    }

    // ------------------------------------------------------------ Autenticación

    /** Lee el perfil `usuarios/{uid}` directamente de Firestore. */
    suspend fun obtener(uid: String): Usuario? =
        Nube.db.collection(Nube.USUARIOS).document(uid).get().await().aUsuario()

    /**
     * Ingreso con correo y contraseña (RF-AUT-03).
     *
     * Firebase verifica la contraseña; después se lee el perfil para conocer rol y estado. Si la
     * cuenta no tiene perfil (fue eliminada), está pendiente o suspendida, la sesión de Firebase
     * se cierra enseguida y se lanza un [ErrorNegocio] con el motivo.
     */
    suspend fun autenticar(correo: String, clave: String): Usuario {
        val resultado = Nube.auth.signInWithEmailAndPassword(correo.trim(), clave).await()
        val uid = resultado.user?.uid ?: throw ErrorNegocio(R.string.ingreso_error_credenciales)
        val perfil = try {
            obtener(uid)
        } catch (e: Exception) {
            Nube.auth.signOut()
            throw e
        }
        val motivo = when (perfil?.estado) {
            null -> R.string.ingreso_error_credenciales
            EstadoCuenta.PENDIENTE -> R.string.ingreso_cuenta_pendiente
            EstadoCuenta.SUSPENDIDO -> R.string.ingreso_cuenta_suspendida
            EstadoCuenta.ACTIVO -> null
        }
        if (motivo != null || perfil == null) {
            Nube.auth.signOut()
            throw ErrorNegocio(motivo ?: R.string.ingreso_error_credenciales)
        }
        return perfil
    }

    /**
     * Registro desde la app (RF-AUT-01 y RF-AUT-02): crea la cuenta en Authentication y su perfil
     * en Firestore. El comprador queda activo y con la sesión abierta; el vendedor queda
     * PENDIENTE y se cierra su sesión hasta que el administrador lo apruebe.
     */
    suspend fun registrar(nombre: String, apellido: String, correo: String, telefono: String, clave: String, rol: Rol): Usuario {
        val cuenta = Nube.auth.createUserWithEmailAndPassword(correo.trim(), clave).await().user
            ?: throw ErrorNegocio(R.string.error_generico)
        val usuario = Usuario(
            id = cuenta.uid,
            nombre = nombre.trim(),
            apellido = apellido.trim(),
            correo = correo.trim().lowercase(),
            telefono = telefono.trim(),
            rol = rol,
            estado = if (rol == Rol.VENDEDOR) EstadoCuenta.PENDIENTE else EstadoCuenta.ACTIVO,
            fechaRegistro = LocalDate.now().toString(),
        )
        try {
            Nube.db.collection(Nube.USUARIOS).document(usuario.id).set(usuario.aMapa()).await()
        } catch (e: Exception) {
            // Sin perfil la cuenta no sirve: se borra para que el correo pueda registrarse de nuevo
            runCatching { cuenta.delete().await() }
            Nube.auth.signOut()
            throw e
        }
        if (rol == Rol.VENDEDOR) Nube.auth.signOut()
        return usuario
    }

    /**
     * Recuperación de la contraseña (RF-AUT-06): Firebase envía al correo un enlace para crear
     * una nueva. Si el correo no está registrado no se informa, para no revelar qué cuentas existen.
     */
    suspend fun enviarEnlaceRestablecer(correo: String) {
        try {
            Nube.auth.setLanguageCode("es")
            Nube.auth.sendPasswordResetEmail(correo.trim()).await()
        } catch (_: FirebaseAuthInvalidUserException) {
            // Misma respuesta exista o no la cuenta
        }
    }

    // ------------------------------------------------------------ Administración (RF-ADU)

    /**
     * Alta hecha por el administrador (RF-ADU-02).
     *
     * El administrador no asigna contraseñas. La cuenta se crea con una contraseña aleatoria que
     * nadie conoce, en la instancia secundaria de Firebase (ver [Nube.authSecundaria]) para no
     * cerrar la sesión del administrador. Luego se envía al titular el correo de
     * restablecimiento: con ese enlace crea su propia contraseña.
     */
    suspend fun crearDesdeAdministracion(
        contexto: Context, nombre: String, apellido: String, correo: String, telefono: String, rol: Rol, estado: EstadoCuenta,
    ): Usuario {
        val authSecundaria = Nube.authSecundaria(contexto)
        try {
            val cuenta = authSecundaria.createUserWithEmailAndPassword(correo.trim(), claveAleatoria()).await().user
                ?: throw ErrorNegocio(R.string.error_generico)
            val usuario = Usuario(
                id = cuenta.uid,
                nombre = nombre.trim(),
                apellido = apellido.trim(),
                correo = correo.trim().lowercase(),
                telefono = telefono.trim(),
                rol = rol,
                estado = estado,
                fechaRegistro = LocalDate.now().toString(),
            )
            // El perfil lo escribe la sesión del administrador (las reglas se lo permiten)
            Nube.db.collection(Nube.USUARIOS).document(usuario.id).set(usuario.aMapa()).await()
            authSecundaria.setLanguageCode("es")
            authSecundaria.sendPasswordResetEmail(usuario.correo).await()
            coleccion.reemplazarLocal(usuario) { it.id == usuario.id }
            return usuario
        } finally {
            authSecundaria.signOut()
        }
    }

    /**
     * Guarda los cambios hechos por el administrador a otra cuenta: datos, rol y estado
     * (editar usuario, aprobar o rechazar vendedor).
     */
    suspend fun actualizar(usuario: Usuario) {
        Nube.db.collection(Nube.USUARIOS).document(usuario.id).update(
            mapOf(
                "nombre" to usuario.nombre,
                "apellido" to usuario.apellido,
                "telefono" to usuario.telefono,
                "rol" to usuario.rol.name,
                "estado" to usuario.estado.name,
            ),
        ).await()
        coleccion.reemplazarLocal(usuario) { it.id == usuario.id }
    }

    /**
     * Baja hecha por el administrador (RF-ADU-05): borra el perfil y la foto. Sin perfil la cuenta
     * ya no puede ingresar (ver [autenticar]) y, si estaba usando la app, se le cierra la sesión.
     *
     * Limitación del SDK cliente: el registro de Authentication (correo y contraseña) solo se puede
     * borrar con el SDK de administrador o desde la consola de Firebase (pestaña Authentication).
     */
    suspend fun eliminar(id: String) {
        Nube.db.collection(Nube.USUARIOS).document(id).delete().await()
        runCatching { Archivos.borrar(Archivos.rutaPerfil(id)) }
    }

    // ------------------------------------------------------------ Perfil propio (RF-PER)

    /**
     * Cambios del propio usuario en "Editar perfil": nombre, apellido, teléfono y foto. El rol y el
     * estado no se envían (las reglas tampoco dejarían cambiarlos).
     *
     * @param fotoNueva foto tomada o elegida, que se sube a Storage; null si no cambió.
     * @param quitarFoto true para borrar la foto actual.
     */
    suspend fun actualizarPerfil(usuario: Usuario, nombre: String, apellido: String, telefono: String, fotoNueva: Bitmap?, quitarFoto: Boolean): Usuario {
        val ruta = Archivos.rutaPerfil(usuario.id)
        val fotoUrl = when {
            fotoNueva != null -> Archivos.subirFoto(ruta, fotoNueva)
            quitarFoto -> { Archivos.borrar(ruta); "" }
            else -> usuario.fotoUrl
        }
        val actualizado = usuario.copy(nombre = nombre.trim(), apellido = apellido.trim(), telefono = telefono.trim(), fotoUrl = fotoUrl)
        Nube.db.collection(Nube.USUARIOS).document(usuario.id).update(
            mapOf("nombre" to actualizado.nombre, "apellido" to actualizado.apellido, "telefono" to actualizado.telefono, "fotoUrl" to fotoUrl),
        ).await()
        _actual.value = actualizado
        Sesion(SaborNaturalApp.instancia).guardarCopia(actualizado)
        return actualizado
    }

    /**
     * Eliminación de la cuenta por su titular (RF-PER-04): borra el perfil, la foto y la cuenta de
     * Authentication. Firebase exige un ingreso reciente para borrar la cuenta; si el ingreso es
     * antiguo, el perfil ya quedó borrado (la cuenta no podrá entrar) y se pide volver a ingresar.
     */
    suspend fun eliminarCuentaPropia() {
        val cuenta = Nube.auth.currentUser ?: return
        // Se detiene la escucha del perfil para que el borrado no se tome como expulsión
        registroPerfil?.remove()
        registroPerfil = null
        Nube.db.collection(Nube.USUARIOS).document(cuenta.uid).delete().await()
        runCatching { Archivos.borrar(Archivos.rutaPerfil(cuenta.uid)) }
        runCatching { Nube.db.collection(Nube.CARRITOS).document(cuenta.uid).delete().await() }
        cuenta.delete().await()
    }

    /** Contraseña temporal de 24 caracteres que nadie conoce (se reemplaza con el enlace). */
    private fun claveAleatoria(): String {
        val bytes = ByteArray(18).also { SecureRandom().nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "A1"
    }
}
