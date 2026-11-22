package com.sabornatural.tienda.datos.remoto

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
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

/*
 * Conversión entre los modelos de la app y los documentos de Firestore (mapas clave → valor).
 *
 * Se hace a mano, campo por campo, en lugar de con toObject(), porque los modelos son data class
 * inmutables con enums que llevan recursos de Android. Los enums se guardan por su nombre
 * ("VENDEDOR", "PUBLICADO"...), que es lo que revisan las reglas de seguridad (firestore.rules).
 * Al leer se usan valores por defecto: un documento incompleto no tumba la app.
 */

/** Lee un enum por su nombre; si el valor no existe devuelve [porDefecto]. */
private inline fun <reified E : Enum<E>> DocumentSnapshot.enumerado(campo: String, porDefecto: E): E =
    getString(campo)?.let { nombre -> enumValues<E>().find { it.name == nombre } } ?: porDefecto

/** Lee un enum de un mapa anidado (líneas del pedido o del carrito). */
private inline fun <reified E : Enum<E>> Map<*, *>.enumerado(campo: String, porDefecto: E): E =
    (this[campo] as? String)?.let { nombre -> enumValues<E>().find { it.name == nombre } } ?: porDefecto

/** Lee un campo de texto; vacío si no existe. */
private fun DocumentSnapshot.texto(campo: String): String = getString(campo).orEmpty()

// ---------------------------------------------------------------- Usuarios

/** Campos del perfil que se guardan en `usuarios/{uid}` (el id es el nombre del documento). */
fun Usuario.aMapa(): Map<String, Any> = mapOf(
    "nombre" to nombre,
    "apellido" to apellido,
    "correo" to correo,
    "telefono" to telefono,
    "rol" to rol.name,
    "estado" to estado.name,
    "fechaRegistro" to fechaRegistro,
    "fotoUrl" to fotoUrl,
)

/** Documento de `usuarios` → [Usuario], o null si el documento no existe. */
fun DocumentSnapshot.aUsuario(): Usuario? {
    if (!exists()) return null
    return Usuario(
        id = id,
        nombre = texto("nombre"),
        apellido = texto("apellido"),
        correo = texto("correo"),
        telefono = texto("telefono"),
        rol = enumerado("rol", Rol.COMPRADOR),
        estado = enumerado("estado", EstadoCuenta.PENDIENTE),
        fechaRegistro = texto("fechaRegistro"),
        fotoUrl = texto("fotoUrl"),
    )
}

// ---------------------------------------------------------------- Productos

/** Campos del producto que se guardan en `productos/{id}`. */
fun Producto.aMapa(): Map<String, Any> = mapOf(
    "nombre" to nombre,
    "descripcion" to descripcion,
    "precio" to precio,
    "existencias" to existencias,
    "categoria" to categoria.name,
    "tipo" to tipo.name,
    "vendedorId" to vendedorId,
    "vendedorNombre" to vendedorNombre,
    "calificacion" to calificacion,
    "resenas" to resenas,
    "estado" to estado.name,
    "ingredientes" to ingredientes,
    "contenidoNeto" to contenidoNeto,
    "registroSanitario" to registroSanitario,
    "destacado" to destacado,
    "motivoRechazo" to motivoRechazo,
    "imagenUrl" to imagenUrl,
)

/** Documento de `productos` → [Producto]. */
fun DocumentSnapshot.aProducto(): Producto? {
    if (!exists()) return null
    return Producto(
        id = id,
        nombre = texto("nombre"),
        descripcion = texto("descripcion"),
        precio = getLong("precio") ?: 0L,
        existencias = (getLong("existencias") ?: 0L).toInt(),
        categoria = enumerado("categoria", Categoria.PREPARADOS),
        tipo = enumerado("tipo", TipoProducto.EMPACADO),
        vendedorId = texto("vendedorId"),
        vendedorNombre = texto("vendedorNombre"),
        calificacion = getDouble("calificacion") ?: 0.0,
        resenas = (getLong("resenas") ?: 0L).toInt(),
        estado = enumerado("estado", EstadoPublicacion.EN_REVISION),
        ingredientes = texto("ingredientes"),
        contenidoNeto = texto("contenidoNeto"),
        registroSanitario = texto("registroSanitario"),
        destacado = getBoolean("destacado") ?: false,
        motivoRechazo = texto("motivoRechazo"),
        imagenUrl = texto("imagenUrl"),
    )
}

/** Mapa de un producto con su id, para guardarlo dentro del carrito. */
fun Producto.aMapaConId(): Map<String, Any> = aMapa() + ("id" to id)

/** Mapa anidado (guardado en el carrito) → [Producto]. */
fun Map<*, *>.aProducto(): Producto? {
    val id = this["id"] as? String ?: return null
    return Producto(
        id = id,
        nombre = this["nombre"] as? String ?: "",
        descripcion = this["descripcion"] as? String ?: "",
        precio = (this["precio"] as? Number)?.toLong() ?: 0L,
        existencias = (this["existencias"] as? Number)?.toInt() ?: 0,
        categoria = enumerado("categoria", Categoria.PREPARADOS),
        tipo = enumerado("tipo", TipoProducto.EMPACADO),
        vendedorId = this["vendedorId"] as? String ?: "",
        vendedorNombre = this["vendedorNombre"] as? String ?: "",
        calificacion = (this["calificacion"] as? Number)?.toDouble() ?: 0.0,
        resenas = (this["resenas"] as? Number)?.toInt() ?: 0,
        estado = enumerado("estado", EstadoPublicacion.PUBLICADO),
        ingredientes = this["ingredientes"] as? String ?: "",
        contenidoNeto = this["contenidoNeto"] as? String ?: "",
        registroSanitario = this["registroSanitario"] as? String ?: "",
        destacado = this["destacado"] as? Boolean ?: false,
        motivoRechazo = this["motivoRechazo"] as? String ?: "",
        imagenUrl = this["imagenUrl"] as? String ?: "",
    )
}

// ---------------------------------------------------------------- Pedidos

/** Campos del pedido que se guardan en `pedidos/{id}`; las líneas van como lista de mapas. */
fun Pedido.aMapa(): Map<String, Any?> = mapOf(
    "codigo" to codigo,
    "compradorId" to compradorId,
    "compradorNombre" to compradorNombre,
    "compradorTelefono" to compradorTelefono,
    "vendedorId" to vendedorId,
    "vendedorNombre" to vendedorNombre,
    "lineas" to lineas.map {
        mapOf(
            "productoId" to it.productoId,
            "nombre" to it.nombre,
            "categoria" to it.categoria.name,
            "cantidad" to it.cantidad,
            "precioUnitario" to it.precioUnitario,
        )
    },
    "domicilio" to domicilio,
    "total" to total,
    "estado" to estado.name,
    "estadoPago" to estadoPago.name,
    "detallePago" to detallePago,
    "medioPago" to medioPago.name,
    "tipoEntrega" to tipoEntrega.name,
    "direccion" to direccion,
    "latitud" to latitud,
    "longitud" to longitud,
    "referenciaPago" to referenciaPago,
    "transaccionId" to transaccionId,
    "metodoPago" to metodoPago,
    "fecha" to fecha,
    "actualizado" to FieldValue.serverTimestamp(),
)

/** Documento de `pedidos` → [Pedido]. */
fun DocumentSnapshot.aPedido(): Pedido? {
    if (!exists()) return null
    val lineas = (get("lineas") as? List<*>).orEmpty().mapNotNull { elemento ->
        val l = elemento as? Map<*, *> ?: return@mapNotNull null
        LineaPedido(
            productoId = l["productoId"] as? String ?: "",
            nombre = l["nombre"] as? String ?: "",
            categoria = l.enumerado("categoria", Categoria.PREPARADOS),
            cantidad = (l["cantidad"] as? Number)?.toInt() ?: 0,
            precioUnitario = (l["precioUnitario"] as? Number)?.toLong() ?: 0L,
        )
    }
    return Pedido(
        id = id,
        codigo = texto("codigo"),
        compradorId = texto("compradorId"),
        compradorNombre = texto("compradorNombre"),
        compradorTelefono = texto("compradorTelefono"),
        vendedorId = texto("vendedorId"),
        vendedorNombre = texto("vendedorNombre"),
        lineas = lineas,
        domicilio = getLong("domicilio") ?: 0L,
        estado = enumerado("estado", EstadoPedido.PAGO_PENDIENTE),
        medioPago = enumerado("medioPago", MedioPago.TARJETA),
        tipoEntrega = enumerado("tipoEntrega", TipoEntrega.DOMICILIO),
        direccion = texto("direccion"),
        referenciaPago = texto("referenciaPago"),
        fecha = texto("fecha"),
        estadoPago = enumerado("estadoPago", EstadoPago.PENDIENTE),
        detallePago = texto("detallePago"),
        latitud = getDouble("latitud"),
        longitud = getDouble("longitud"),
        transaccionId = texto("transaccionId"),
        metodoPago = texto("metodoPago"),
    )
}
