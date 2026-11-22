package com.sabornatural.tienda.datos

import android.content.Context
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FieldValue
import com.sabornatural.tienda.R
import com.sabornatural.tienda.datos.modelo.Categoria
import com.sabornatural.tienda.datos.modelo.EstadoCuenta
import com.sabornatural.tienda.datos.modelo.EstadoPago
import com.sabornatural.tienda.datos.modelo.EstadoPedido
import com.sabornatural.tienda.datos.modelo.EstadoPublicacion
import com.sabornatural.tienda.datos.modelo.LineaPedido
import com.sabornatural.tienda.datos.modelo.MedioPago
import com.sabornatural.tienda.datos.modelo.Pedido
import com.sabornatural.tienda.datos.modelo.Producto
import com.sabornatural.tienda.datos.modelo.Rol
import com.sabornatural.tienda.datos.modelo.TipoEntrega
import com.sabornatural.tienda.datos.modelo.TipoProducto
import com.sabornatural.tienda.datos.modelo.Usuario
import com.sabornatural.tienda.datos.remoto.Nube
import com.sabornatural.tienda.datos.remoto.aMapa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Configuración inicial de un proyecto de Firebase vacío (la usa la pantalla oculta
 * ui/admin/ConfiguracionInicialActivity):
 *
 *  1. [reclamarAdministrador]: convierte la cuenta con sesión en el PRIMER administrador. Solo
 *     funciona una vez: las reglas lo permiten mientras no exista `configuracion/administrador`.
 *  2. [sembrar]: con la sesión del administrador, crea las categorías, las cuentas de prueba
 *     (vendedores y compradores), los productos y unos pedidos de ejemplo, a partir de
 *     los archivos JSON de assets/datos_demo. Se puede repetir: usa ids fijos ("demo-1"...) y no duplica.
 */
object Semilla {

    /**
     * Hace administrador al usuario con sesión, en una sola escritura atómica (batch):
     * crea `configuracion/administrador` con su uid y cambia su rol. firestore.rules solo acepta
     * el cambio de rol si en ese mismo batch se crea el documento y este aún no existía.
     */
    suspend fun reclamarAdministrador(usuario: Usuario): Usuario {
        val db = Nube.db
        val lote = db.batch()
        lote.set(
            db.collection(Nube.CONFIGURACION).document(Nube.DOC_ADMINISTRADOR),
            mapOf("uid" to usuario.id, "correo" to usuario.correo, "fecha" to FieldValue.serverTimestamp()),
        )
        lote.update(
            db.collection(Nube.USUARIOS).document(usuario.id),
            mapOf("rol" to Rol.ADMINISTRADOR.name, "estado" to EstadoCuenta.ACTIVO.name),
        )
        lote.commit().await()
        return usuario.copy(rol = Rol.ADMINISTRADOR, estado = EstadoCuenta.ACTIVO)
    }

    /** true si ya hay un primer administrador registrado. */
    suspend fun hayAdministrador(): Boolean =
        Nube.db.collection(Nube.CONFIGURACION).document(Nube.DOC_ADMINISTRADOR).get().await().exists()

    /**
     * Siembra los datos de ejemplo. Debe ejecutarla un administrador.
     *
     * @param claveDemo contraseña que tendrán las cuentas de prueba (la escribe el administrador
     *                  en la pantalla; no está en el código ni en el repositorio).
     * @param avance recibe mensajes de progreso para mostrarlos.
     */
    suspend fun sembrar(contexto: Context, claveDemo: String, avance: (String) -> Unit) {
        val db = Nube.db
        val usuariosJson = leer(contexto, "usuarios")
        val productosJson = leer(contexto, "productos")
        val pedidosJson = leer(contexto, "pedidos")

        // 1. Categorías (en la app son un enum; aquí quedan también como colección consultable)
        Categoria.entries.forEachIndexed { orden, categoria ->
            db.collection(Nube.CATEGORIAS).document(categoria.name)
                .set(mapOf("nombre" to contexto.getString(categoria.nombre), "orden" to orden)).await()
        }
        avance(contexto.getString(R.string.semilla_categorias, Categoria.entries.size))

        // 2. Cuentas de prueba: se crean en la instancia secundaria para no cerrar la sesión del administrador
        val uids = mutableMapOf<Int, String>()
        val authSecundaria = Nube.authSecundaria(contexto)
        for (j in usuariosJson) {
            val rol = Rol.valueOf(j.getString("rol"))
            if (rol == Rol.ADMINISTRADOR) continue // el administrador es la cuenta que ejecuta la siembra
            val correo = j.getString("correo")
            val uid = try {
                authSecundaria.createUserWithEmailAndPassword(correo, claveDemo).await().user?.uid
            } catch (_: FirebaseAuthUserCollisionException) {
                // Ya existía (siembra repetida): se recupera su uid ingresando con la misma contraseña
                runCatching { authSecundaria.signInWithEmailAndPassword(correo, claveDemo).await().user?.uid }.getOrNull()
            } finally {
                authSecundaria.signOut()
            }
            if (uid == null) {
                avance(contexto.getString(R.string.semilla_cuenta_omitida, correo))
                continue
            }
            uids[j.getInt("id")] = uid
            val usuario = Usuario(
                id = uid,
                nombre = j.getString("nombre"),
                apellido = j.getString("apellido"),
                correo = correo,
                telefono = j.getString("telefono"),
                rol = rol,
                estado = EstadoCuenta.valueOf(j.getString("estado")),
                fechaRegistro = j.getString("fechaRegistro"),
            )
            db.collection(Nube.USUARIOS).document(uid).set(usuario.aMapa()).await()
        }
        avance(contexto.getString(R.string.semilla_cuentas, uids.size))

        // 3. Productos de los vendedores de prueba
        var productos = 0
        for (j in productosJson) {
            val vendedorId = uids[j.getInt("vendedorId")] ?: continue
            val producto = Producto(
                id = "demo-" + j.getInt("id"),
                nombre = j.getString("nombre"),
                descripcion = j.getString("descripcion"),
                precio = j.getLong("precio"),
                existencias = j.getInt("existencias"),
                categoria = Categoria.valueOf(j.getString("categoria")),
                tipo = TipoProducto.valueOf(j.getString("tipo")),
                vendedorId = vendedorId,
                vendedorNombre = j.getString("vendedorNombre"),
                calificacion = j.getDouble("calificacion"),
                resenas = j.getInt("resenas"),
                estado = EstadoPublicacion.valueOf(j.getString("estado")),
                ingredientes = j.getString("ingredientes"),
                contenidoNeto = j.getString("contenidoNeto"),
                registroSanitario = j.getString("registroSanitario"),
                destacado = j.getBoolean("destacado"),
                motivoRechazo = j.optString("motivoRechazo"),
            )
            db.collection(Nube.PRODUCTOS).document(producto.id).set(producto.aMapa()).await()
            productos++
        }
        avance(contexto.getString(R.string.semilla_productos, productos))

        // 4. Pedidos de ejemplo (historial y datos para el reporte de ventas)
        var pedidos = 0
        for (j in pedidosJson) {
            val compradorId = uids[j.getInt("compradorId")] ?: continue
            val vendedorId = uids[j.getInt("vendedorId")] ?: continue
            val estado = EstadoPedido.valueOf(j.getString("estado"))
            val pedido = Pedido(
                id = "demo-" + j.getInt("id"),
                codigo = j.getString("codigo"),
                compradorId = compradorId,
                compradorNombre = j.getString("compradorNombre"),
                compradorTelefono = j.getString("compradorTelefono"),
                vendedorId = vendedorId,
                vendedorNombre = j.getString("vendedorNombre"),
                lineas = j.getJSONArray("lineas").objetos().map {
                    LineaPedido(
                        productoId = "demo-" + it.getInt("productoId"),
                        nombre = it.getString("nombre"),
                        categoria = Categoria.valueOf(it.getString("categoria")),
                        cantidad = it.getInt("cantidad"),
                        precioUnitario = it.getLong("precioUnitario"),
                    )
                },
                domicilio = j.getLong("domicilio"),
                estado = estado,
                medioPago = MedioPago.valueOf(j.getString("medioPago")),
                tipoEntrega = TipoEntrega.valueOf(j.getString("tipoEntrega")),
                direccion = j.getString("direccion"),
                referenciaPago = j.getString("referenciaPago"),
                fecha = j.getString("fecha"),
                estadoPago = EstadoPago.APROBADO,
                detallePago = "Pedido de ejemplo",
            )
            db.collection(Nube.PEDIDOS).document(pedido.id).set(pedido.aMapa()).await()
            pedidos++
        }
        avance(contexto.getString(R.string.semilla_pedidos, pedidos))
    }

    /** Lee assets/datos_demo/[nombre].json y devuelve su arreglo principal. */
    private suspend fun leer(contexto: Context, nombre: String): List<JSONObject> = withContext(Dispatchers.IO) {
        val texto = contexto.assets.open("datos_demo/$nombre.json").bufferedReader().use { it.readText() }
        JSONObject(texto).getJSONArray(nombre).objetos()
    }

    /** Convierte un arreglo JSON en una lista de objetos. */
    private fun JSONArray.objetos(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
