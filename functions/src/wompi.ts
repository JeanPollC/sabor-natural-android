/**
 * Utilidades criptográficas de Wompi, separadas de index.ts para poder probarlas solas y para
 * que Firebase solo vea como funciones desplegables las que exporta index.ts.
 */

import { createHash } from "node:crypto";

/** SHA-256 en hexadecimal (minúsculas). */
export function sha256(texto: string): string {
  return createHash("sha256").update(texto, "utf8").digest("hex");
}

/**
 * Firma de integridad de Wompi: SHA-256 de referencia + monto en centavos + moneda + secreto,
 * concatenados sin separadores.
 */
export function firmaIntegridad(referencia: string, montoEnCentavos: number, moneda: string, secreto: string): string {
  return sha256(`${referencia}${montoEnCentavos}${moneda}${secreto}`);
}

/** Lee un valor anidado con una ruta tipo "transaction.status" dentro de [objeto]. */
function valorEnRuta(objeto: unknown, ruta: string): unknown {
  return ruta.split(".").reduce<unknown>(
    (actual, clave) => (actual && typeof actual === "object" ? (actual as Record<string, unknown>)[clave] : undefined),
    objeto,
  );
}

/**
 * Valida el checksum de un evento de Wompi: concatena los valores de signature.properties
 * (rutas relativas a data), luego timestamp y luego el secreto de eventos; el SHA-256 en
 * hexadecimal debe coincidir (sin distinguir mayúsculas) con signature.checksum.
 */
export function checksumValido(evento: Record<string, unknown>, secretoEventos: string): boolean {
  const firma = evento.signature as { properties?: string[]; checksum?: string } | undefined;
  if (!firma?.properties || !firma.checksum) return false;
  const valores = firma.properties.map((ruta) => String(valorEnRuta(evento.data, ruta) ?? "")).join("");
  const calculado = sha256(`${valores}${evento.timestamp}${secretoEventos}`);
  return calculado.toLowerCase() === firma.checksum.toLowerCase();
}
