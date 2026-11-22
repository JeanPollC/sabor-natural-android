/**
 * Cloud Functions de Sabor Natural: pagos con Wompi (Web Checkout) en modo sandbox.
 *
 * Por qué hay funciones: Wompi exige una "firma de integridad" calculada con un secreto que no
 * puede ir dentro del APK, y el resultado del pago no debe escribirlo el teléfono del comprador
 * (podría falsificarlo). Estas funciones corren en los servidores de Google con permisos de
 * administrador y son lo único que escribe el estado del pago en Firestore (ver firestore.rules).
 *
 * Funciones:
 *  - crearPago (callable): recalcula el total del pedido con los precios de Firestore, genera la
 *    referencia y la firma, y devuelve los datos para abrir el checkout.
 *  - verificarPago (callable): consulta la transacción en la API de Wompi, comprueba que la
 *    referencia y el monto coinciden con el pedido y guarda el resultado (descuenta existencias
 *    si fue aprobada). La app la llama al volver del checkout y la repite mientras siga PENDING.
 *  - webhookWompi (HTTPS, OPCIONAL y desactivado por defecto): recibe el evento
 *    transaction.updated de Wompi. Wompi solo admite una URL de eventos por comercio y ambiente;
 *    si ya la usa otro proyecto, no se activa y la confirmación se hace con verificarPago.
 *
 * Secretos (firebase functions:secrets:set NOMBRE):
 *  - WOMPI_INTEGRITY_SECRET: imprescindible (firma de integridad).
 *  - WOMPI_EVENTS_SECRET: solo si se activa el webhook (checksum de eventos).
 */

import { initializeApp } from "firebase-admin/app";
import { DocumentReference, FieldValue, getFirestore, Transaction } from "firebase-admin/firestore";
import { defineSecret } from "firebase-functions/params";
import { setGlobalOptions } from "firebase-functions/v2";
import { HttpsError, onCall, onRequest } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import { checksumValido, firmaIntegridad } from "./wompi";

initializeApp();
const db = getFirestore();

setGlobalOptions({ region: "us-central1", maxInstances: 5 });

const WOMPI_INTEGRITY_SECRET = defineSecret("WOMPI_INTEGRITY_SECRET");
const WOMPI_EVENTS_SECRET = defineSecret("WOMPI_EVENTS_SECRET");

/** API de Wompi: sandbox por defecto. Se cambia con WOMPI_API_URL en functions/.env. */
const WOMPI_API = process.env.WOMPI_API_URL ?? "https://sandbox.wompi.co/v1";

/** URL a la que Wompi redirige al terminar. No necesita existir: el WebView de la app la intercepta. */
const URL_REDIRECCION = "https://sabornatural.app/pago-resultado";

/** Valor del domicilio (igual a RepositorioPedidos.VALOR_DOMICILIO en la app). */
const VALOR_DOMICILIO = 6000;

const MONEDA = "COP";

// ------------------------------------------------------------------ Tipos

/** Línea de un pedido tal como se guarda en Firestore. */
interface LineaPedido {
  productoId: string;
  nombre: string;
  categoria: string;
  cantidad: number;
  precioUnitario: number;
}

/** Campos del pedido que usan estas funciones. */
interface Pedido {
  compradorId: string;
  vendedorId: string;
  lineas: LineaPedido[];
  tipoEntrega: string;
  estado: string;
  estadoPago: string;
  referenciaPago?: string;
  montoEnCentavos?: number;
}

/** Datos de una transacción de Wompi (GET /transactions/{id} o evento transaction.updated). */
interface TransaccionWompi {
  id: string;
  status: string; // APPROVED | DECLINED | VOIDED | ERROR | PENDING
  reference: string;
  amount_in_cents: number;
  currency: string;
  payment_method_type?: string;
  status_message?: string;
  finalized_at?: string | null;
}

// ------------------------------------------------------------------ Utilidades

/** Exige sesión y devuelve el uid de quien llama. */
function exigirSesion(auth: { uid: string } | undefined): string {
  if (!auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  return auth.uid;
}

/** Lee el pedido y comprueba que pertenece al comprador que llama. */
async function pedidoDelComprador(pedidoId: unknown, uid: string): Promise<{ ref: DocumentReference; pedido: Pedido }> {
  if (typeof pedidoId !== "string" || pedidoId.length === 0) {
    throw new HttpsError("invalid-argument", "Falta el id del pedido.");
  }
  const ref = db.collection("pedidos").doc(pedidoId);
  const doc = await ref.get();
  if (!doc.exists) throw new HttpsError("not-found", "El pedido no existe.");
  const pedido = doc.data() as Pedido;
  if (pedido.compradorId !== uid) throw new HttpsError("permission-denied", "El pedido no es tuyo.");
  return { ref, pedido };
}

/** Traduce el estado de Wompi al estado de pago que usa la app. */
function estadoPagoSegun(status: string): "APROBADO" | "RECHAZADO" | "PENDIENTE" {
  if (status === "APPROVED") return "APROBADO";
  if (status === "PENDING") return "PENDIENTE";
  return "RECHAZADO"; // DECLINED, VOIDED, ERROR
}

/**
 * Guarda en el pedido el resultado de una transacción, dentro de una transacción de Firestore.
 *
 * Es idempotente: si el pedido ya tiene un resultado final (aprobado o rechazado), no cambia
 * nada. Si la transacción fue aprobada y el pedido seguía en PAGO_PENDIENTE, lo confirma y
 * descuenta las existencias de cada producto en la misma operación.
 *
 * @returns el estado de pago y el estado del pedido después de aplicar el resultado.
 */
async function aplicarResultado(ref: DocumentReference, t: TransaccionWompi): Promise<{ estadoPago: string; estado: string }> {
  return db.runTransaction(async (tx: Transaction) => {
    const doc = await tx.get(ref);
    const pedido = doc.data() as Pedido;
    if (pedido.estadoPago === "APROBADO" || pedido.estadoPago === "RECHAZADO") {
      return { estadoPago: pedido.estadoPago, estado: pedido.estado };
    }
    const estadoPago = estadoPagoSegun(t.status);
    let estado = pedido.estado;
    if (pedido.estado === "PAGO_PENDIENTE") {
      if (estadoPago === "APROBADO") estado = "CONFIRMADO";
      if (estadoPago === "RECHAZADO") estado = "CANCELADO";
    }

    // En una transacción todas las lecturas van antes de las escrituras
    const descontar = pedido.estado === "PAGO_PENDIENTE" && estado === "CONFIRMADO";
    const productos = descontar
      ? await Promise.all(pedido.lineas.map(async (l) => {
          const refProducto = db.collection("productos").doc(l.productoId);
          return { refProducto, doc: await tx.get(refProducto), cantidad: l.cantidad };
        }))
      : [];
    for (const p of productos) {
      if (!p.doc.exists) continue;
      const existencias = Number(p.doc.get("existencias") ?? 0);
      tx.update(p.refProducto, { existencias: Math.max(0, existencias - p.cantidad) });
    }

    const detalle = [t.status, t.payment_method_type, t.status_message].filter(Boolean).join(" · ");
    tx.update(ref, {
      estado,
      estadoPago,
      transaccionId: t.id,
      metodoPago: t.payment_method_type ?? "",
      detallePago: estado === "CANCELADO" && pedido.estado !== "PAGO_PENDIENTE"
        ? `${detalle} (el pedido ya estaba cancelado: revisar reembolso)`
        : detalle,
      fechaPago: t.finalized_at ?? null,
      actualizado: FieldValue.serverTimestamp(),
    });
    return { estadoPago, estado };
  });
}

// ------------------------------------------------------------------ crearPago

/**
 * Prepara el pago de un pedido para el Web Checkout de Wompi.
 *
 * No confía en el total que calculó el teléfono: vuelve a leer el precio de cada producto en
 * Firestore, comprueba que esté publicado y con existencias, y recalcula el total con el
 * domicilio. Luego genera una referencia única (SN-{pedido}-{marca de tiempo}) y la firma de
 * integridad, y deja la referencia y el monto guardados en el pedido para verificarlos después.
 *
 * Entrada: { pedidoId }. Salida: { referencia, montoEnCentavos, moneda, firmaIntegridad, urlRedireccion }.
 */
export const crearPago = onCall({ secrets: [WOMPI_INTEGRITY_SECRET] }, async (request) => {
  const uid = exigirSesion(request.auth);
  const { ref, pedido } = await pedidoDelComprador(request.data?.pedidoId, uid);
  if (pedido.estado !== "PAGO_PENDIENTE" || pedido.estadoPago !== "PENDIENTE") {
    throw new HttpsError("failed-precondition", "Este pedido ya no está pendiente de pago.");
  }
  if (!Array.isArray(pedido.lineas) || pedido.lineas.length === 0) {
    throw new HttpsError("failed-precondition", "El pedido no tiene productos.");
  }

  // Precios y existencias reales, leídos en el servidor
  const lineas: LineaPedido[] = [];
  for (const l of pedido.lineas) {
    const cantidad = Math.floor(Number(l.cantidad));
    const producto = await db.collection("productos").doc(l.productoId).get();
    if (!producto.exists || producto.get("estado") !== "PUBLICADO" || producto.get("vendedorId") !== pedido.vendedorId) {
      throw new HttpsError("failed-precondition", `El producto "${l.nombre}" ya no está disponible.`);
    }
    if (!(cantidad >= 1) || Number(producto.get("existencias") ?? 0) < cantidad) {
      throw new HttpsError("failed-precondition", `No hay suficientes unidades de "${l.nombre}".`);
    }
    lineas.push({ ...l, cantidad, precioUnitario: Math.round(Number(producto.get("precio"))) });
  }
  const domicilio = pedido.tipoEntrega === "DOMICILIO" ? VALOR_DOMICILIO : 0;
  const total = lineas.reduce((suma, l) => suma + l.precioUnitario * l.cantidad, 0) + domicilio;
  const montoEnCentavos = total * 100;
  const referencia = `SN-${ref.id}-${Date.now()}`;
  const firma = firmaIntegridad(referencia, montoEnCentavos, MONEDA, WOMPI_INTEGRITY_SECRET.value());

  await ref.update({
    lineas,
    domicilio,
    total,
    montoEnCentavos,
    referenciaPago: referencia,
    actualizado: FieldValue.serverTimestamp(),
  });

  return { referencia, montoEnCentavos, moneda: MONEDA, firmaIntegridad: firma, urlRedireccion: URL_REDIRECCION };
});

// ------------------------------------------------------------------ verificarPago

/**
 * Consulta una transacción en la API pública de Wompi (GET /transactions/{id}, sin llave) y
 * guarda el resultado en el pedido.
 *
 * Antes de guardar comprueba que la transacción sea de ESTE pedido: misma referencia, mismo
 * monto en centavos y misma moneda. Así nadie puede "pagar" un pedido con el id de otra compra.
 * Si Wompi responde PENDING, el pedido queda pendiente y la app vuelve a llamar en unos segundos.
 *
 * Entrada: { pedidoId, transaccionId }. Salida: { status, estadoPago, estado }.
 */
export const verificarPago = onCall(async (request) => {
  const uid = exigirSesion(request.auth);
  const { ref, pedido } = await pedidoDelComprador(request.data?.pedidoId, uid);
  const transaccionId = request.data?.transaccionId;
  if (typeof transaccionId !== "string" || !/^[\w-]+$/.test(transaccionId)) {
    throw new HttpsError("invalid-argument", "Falta el id de la transacción.");
  }
  if (!pedido.referenciaPago) {
    throw new HttpsError("failed-precondition", "El pedido no tiene un pago iniciado.");
  }

  const respuesta = await fetch(`${WOMPI_API}/transactions/${encodeURIComponent(transaccionId)}`, {
    headers: { Accept: "application/json" },
  });
  if (!respuesta.ok) {
    logger.warn("Wompi respondió con error", { status: respuesta.status, transaccionId });
    throw new HttpsError("unavailable", "Wompi no respondió. Intenta de nuevo.");
  }
  const t = ((await respuesta.json()) as { data: TransaccionWompi }).data;

  const corresponde = t.reference === pedido.referenciaPago &&
    Number(t.amount_in_cents) === Number(pedido.montoEnCentavos) &&
    t.currency === MONEDA;
  if (!corresponde) {
    logger.warn("Transacción que no corresponde al pedido", { pedido: ref.id, transaccionId });
    throw new HttpsError("permission-denied", "La transacción no corresponde a este pedido.");
  }

  // Si sigue pendiente solo se anota el id; el resultado final llega en una consulta posterior
  if (t.status === "PENDING") {
    await ref.update({ transaccionId: t.id, actualizado: FieldValue.serverTimestamp() });
    return { status: t.status, estadoPago: "PENDIENTE", estado: pedido.estado };
  }
  const resultado = await aplicarResultado(ref, t);
  return { status: t.status, ...resultado };
});

// ------------------------------------------------------------------ webhookWompi (opcional)

/**
 * Receptor OPCIONAL de eventos de Wompi (transaction.updated).
 *
 * Wompi permite una sola URL de eventos por comercio y ambiente. Si el comercio ya la usa para
 * otro proyecto, NO se configura: la app confirma con verificarPago. Si se configura, valida el
 * checksum, busca el pedido por la referencia y aplica el resultado de forma idempotente.
 * Siempre responde 200 para que Wompi no reintente indefinidamente.
 *
 * Está DESACTIVADO por defecto (no se despliega ni exige WOMPI_EVENTS_SECRET). Para activarlo:
 * HABILITAR_WEBHOOK_WOMPI=true en functions/.env, firebase functions:secrets:set WOMPI_EVENTS_SECRET
 * y volver a desplegar.
 */
const webhookWompi = onRequest({ secrets: [WOMPI_EVENTS_SECRET] }, async (req, res) => {
  try {
    const evento = req.body as Record<string, unknown>;
    if (evento?.event !== "transaction.updated" || !checksumValido(evento, WOMPI_EVENTS_SECRET.value())) {
      logger.warn("Evento de Wompi ignorado (tipo o checksum inválido)");
      res.status(200).send("ignorado");
      return;
    }
    const t = (evento.data as { transaction: TransaccionWompi }).transaction;
    if (!t?.reference?.startsWith("SN-")) {
      res.status(200).send("otra referencia");
      return;
    }
    const consulta = await db.collection("pedidos").where("referenciaPago", "==", t.reference).limit(1).get();
    const doc = consulta.docs[0];
    if (!doc) {
      res.status(200).send("pedido no encontrado");
      return;
    }
    const pedido = doc.data() as Pedido;
    if (Number(t.amount_in_cents) !== Number(pedido.montoEnCentavos) || t.currency !== MONEDA) {
      logger.warn("Evento con monto distinto al del pedido", { pedido: doc.id });
      res.status(200).send("monto distinto");
      return;
    }
    if (t.status !== "PENDING") await aplicarResultado(doc.ref, t);
    res.status(200).send("ok");
  } catch (e) {
    logger.error("Error procesando evento de Wompi", e);
    res.status(200).send("error registrado");
  }
});

// El webhook solo se exporta (y por lo tanto solo se despliega) si se activa explícitamente
if (process.env.HABILITAR_WEBHOOK_WOMPI === "true") {
  exports.webhookWompi = webhookWompi;
}
