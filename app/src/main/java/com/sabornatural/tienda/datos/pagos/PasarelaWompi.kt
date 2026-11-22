package com.sabornatural.tienda.datos.pagos

import android.net.Uri
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.sabornatural.tienda.BuildConfig
import com.sabornatural.tienda.datos.modelo.EstadoPago
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.io.IOException

/**
 * Integración con la pasarela Wompi (Web Checkout) en modo sandbox (RF-PAG-01 a RF-PAG-03).
 *
 * Reparto de responsabilidades, para que ningún secreto quede dentro del APK:
 *  - La app solo conoce la llave PÚBLICA (local.properties → BuildConfig.WOMPI_PUBLIC_KEY).
 *  - La Cloud Function `crearPago` recalcula el total con los precios de Firestore y calcula la
 *    firma de integridad con el secreto, que vive solo en los secretos de Functions.
 *  - El comprador paga en la página de Wompi dentro de un WebView (PagoWompiActivity): los datos
 *    de la tarjeta, PSE o Nequi nunca pasan por la app (RNF-09).
 *  - Wompi redirige a [URL_REDIRECCION] con `?id=<transacción>`; el WebView la intercepta.
 *  - La Cloud Function `verificarPago` consulta la transacción en Wompi, comprueba referencia y
 *    monto y es la única que escribe el resultado en el pedido (las reglas no dejan a la app).
 */
object PasarelaWompi {

    /** URL fija de regreso del checkout. No necesita existir: el WebView la intercepta antes de cargarla. */
    const val URL_REDIRECCION = "https://sabornatural.app/pago-resultado"

    private const val URL_CHECKOUT = "https://checkout.wompi.co/p/"

    /** Región donde están desplegadas las Cloud Functions. */
    private const val REGION = "us-central1"

    /** true si la llave pública de Wompi está configurada en local.properties. */
    val configurada: Boolean get() = BuildConfig.WOMPI_PUBLIC_KEY.isNotBlank()

    /** true si la llave es de sandbox (pub_test_): no se cobra dinero real. */
    val pruebas: Boolean get() = BuildConfig.WOMPI_PUBLIC_KEY.startsWith("pub_test_")

    private val funciones: FirebaseFunctions get() = FirebaseFunctions.getInstance(REGION)

    /** Datos que devuelve `crearPago` para abrir el checkout. */
    data class DatosPago(
        val referencia: String,
        val montoEnCentavos: Long,
        val moneda: String,
        val firmaIntegridad: String,
        val urlRedireccion: String,
    )

    /** Datos del comprador que se precargan en el checkout. */
    data class Pagador(
        val nombre: String,
        val documento: String,
        val correo: String,
        val telefono: String,
    )

    /** Resultado de verificar una transacción: estado en Wompi y estado de pago de la app. */
    data class Verificacion(val status: String, val estadoPago: EstadoPago)

    /**
     * Pide al servidor la referencia y la firma de integridad para pagar [pedidoId].
     * @throws FirebaseFunctionsException con un mensaje en español si el pedido no se puede pagar
     *         (por ejemplo, un producto se agotó).
     */
    suspend fun crearPago(pedidoId: String): DatosPago {
        val datos = funciones.getHttpsCallable("crearPago").call(mapOf("pedidoId" to pedidoId)).await().data as Map<*, *>
        return DatosPago(
            referencia = datos["referencia"] as String,
            montoEnCentavos = (datos["montoEnCentavos"] as Number).toLong(),
            moneda = datos["moneda"] as String,
            firmaIntegridad = datos["firmaIntegridad"] as String,
            urlRedireccion = datos["urlRedireccion"] as? String ?: URL_REDIRECCION,
        )
    }

    /**
     * URL del Web Checkout de Wompi con los parámetros que pide su documentación: llave pública,
     * moneda, monto en centavos, referencia, firma de integridad, URL de regreso y datos del
     * comprador precargados.
     */
    fun urlCheckout(datos: DatosPago, pagador: Pagador): String =
        Uri.parse(URL_CHECKOUT).buildUpon()
            .appendQueryParameter("public-key", BuildConfig.WOMPI_PUBLIC_KEY)
            .appendQueryParameter("currency", datos.moneda)
            .appendQueryParameter("amount-in-cents", datos.montoEnCentavos.toString())
            .appendQueryParameter("reference", datos.referencia)
            .appendQueryParameter("signature:integrity", datos.firmaIntegridad)
            .appendQueryParameter("redirect-url", datos.urlRedireccion)
            .appendQueryParameter("customer-data:email", pagador.correo)
            .appendQueryParameter("customer-data:full-name", pagador.nombre)
            .appendQueryParameter("customer-data:phone-number", pagador.telefono)
            .appendQueryParameter("customer-data:phone-number-prefix", "+57")
            .appendQueryParameter("customer-data:legal-id", pagador.documento)
            .appendQueryParameter("customer-data:legal-id-type", "CC")
            .build()
            .toString()

    /**
     * Si [url] es la URL de regreso, devuelve el id de la transacción (vacío si Wompi no lo envió);
     * si no, null.
     */
    fun transaccionDe(url: String): String? {
        if (!url.startsWith(URL_REDIRECCION)) return null
        return Uri.parse(url).getQueryParameter("id").orEmpty()
    }

    /** Pide al servidor que consulte la transacción en Wompi y guarde el resultado en el pedido. */
    suspend fun verificarPago(pedidoId: String, transaccionId: String): Verificacion {
        val datos = funciones.getHttpsCallable("verificarPago")
            .call(mapOf("pedidoId" to pedidoId, "transaccionId" to transaccionId)).await().data as Map<*, *>
        val estadoPago = (datos["estadoPago"] as? String)
            ?.let { nombre -> EstadoPago.entries.find { it.name == nombre } } ?: EstadoPago.PENDIENTE
        return Verificacion(datos["status"] as? String ?: "PENDING", estadoPago)
    }

    /**
     * Verifica la transacción y, mientras Wompi la reporte PENDING (o haya un corte de red
     * momentáneo), vuelve a preguntar cada [pausaMs] hasta [intentos] veces. Si al final sigue
     * pendiente, el pedido queda así y el comprador puede consultarlo desde "Mis pedidos".
     */
    suspend fun esperarResultado(pedidoId: String, transaccionId: String, intentos: Int = 20, pausaMs: Long = 2_500): Verificacion {
        var ultima = Verificacion("PENDING", EstadoPago.PENDIENTE)
        repeat(intentos) { vuelta ->
            try {
                ultima = verificarPago(pedidoId, transaccionId)
                if (ultima.estadoPago != EstadoPago.PENDIENTE) return ultima
            } catch (e: FirebaseFunctionsException) {
                if (e.code != FirebaseFunctionsException.Code.UNAVAILABLE || vuelta == intentos - 1) throw e
            } catch (e: IOException) {
                if (vuelta == intentos - 1) throw e
            }
            delay(pausaMs)
        }
        return ultima
    }
}
