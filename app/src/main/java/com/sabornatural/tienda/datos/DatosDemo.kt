package com.sabornatural.tienda.datos

import android.content.Context
import com.sabornatural.tienda.datos.modelo.Categoria
import com.sabornatural.tienda.datos.modelo.EstadoCuenta
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
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lee los datos de prueba de assets/datos_demo (usuarios, productos y pedidos ficticios).
 *
 * Esta entrega es solo el frontend: no hay conexión con la API. Cuando se integre el backend,
 * los repositorios pedirán estos datos al servidor y esta clase dejará de usarse.
 */
object DatosDemo {

    private var cargado = false

    /** Se llama una vez al iniciar la app (SaborNaturalApp). */
    fun cargar(contexto: Context) {
        if (cargado) return
        RepositorioUsuarios.reemplazarTodos(leer(contexto, "usuarios").map(::aUsuario))
        RepositorioProductos.reemplazarTodos(leer(contexto, "productos").map(::aProducto))
        RepositorioPedidos.reemplazarTodos(leer(contexto, "pedidos").map(::aPedido))
        cargado = true
    }

    private fun leer(contexto: Context, nombre: String): List<JSONObject> {
        val texto = contexto.assets.open("datos_demo/$nombre.json").bufferedReader().use { it.readText() }
        return JSONObject(texto).getJSONArray(nombre).objetos()
    }

    private fun JSONArray.objetos(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun aUsuario(j: JSONObject) = Usuario(
        id = j.getInt("id"),
        nombre = j.getString("nombre"),
        apellido = j.getString("apellido"),
        correo = j.getString("correo"),
        telefono = j.getString("telefono"),
        rol = Rol.valueOf(j.getString("rol")),
        estado = EstadoCuenta.valueOf(j.getString("estado")),
        claveHash = j.getString("claveHash"),
        fechaRegistro = j.getString("fechaRegistro"),
    )

    private fun aProducto(j: JSONObject) = Producto(
        id = j.getInt("id"),
        nombre = j.getString("nombre"),
        descripcion = j.getString("descripcion"),
        precio = j.getLong("precio"),
        existencias = j.getInt("existencias"),
        categoria = Categoria.valueOf(j.getString("categoria")),
        tipo = TipoProducto.valueOf(j.getString("tipo")),
        vendedorId = j.getInt("vendedorId"),
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

    private fun aPedido(j: JSONObject) = Pedido(
        id = j.getInt("id"),
        codigo = j.getString("codigo"),
        compradorId = j.getInt("compradorId"),
        compradorNombre = j.getString("compradorNombre"),
        compradorTelefono = j.getString("compradorTelefono"),
        vendedorId = j.getInt("vendedorId"),
        vendedorNombre = j.getString("vendedorNombre"),
        lineas = j.getJSONArray("lineas").objetos().map {
            LineaPedido(
                productoId = it.getInt("productoId"),
                nombre = it.getString("nombre"),
                categoria = Categoria.valueOf(it.getString("categoria")),
                cantidad = it.getInt("cantidad"),
                precioUnitario = it.getLong("precioUnitario"),
            )
        },
        domicilio = j.getLong("domicilio"),
        estado = EstadoPedido.valueOf(j.getString("estado")),
        medioPago = MedioPago.valueOf(j.getString("medioPago")),
        tipoEntrega = TipoEntrega.valueOf(j.getString("tipoEntrega")),
        direccion = j.getString("direccion"),
        referenciaPago = j.getString("referenciaPago"),
        fecha = j.getString("fecha"),
    )
}
