# Sabor Natural · Tienda virtual Android

Aplicación Android nativa de **Sabor Natural**, una tienda virtual multivendedor de productos naturales y veganos.
Proyecto académico del curso *Desarrollo de Aplicaciones Móviles Nativas* (Fundación Universitaria Compensar).

Esta versión es el **proyecto final integrado**: las pantallas de los tres roles (administrador, vendedor y
comprador) funcionan con servicios reales: **Firebase** (Authentication, Cloud Firestore, Storage y Cloud
Functions), la **ubicación del dispositivo** y la pasarela **Wompi** en sandbox.

## Componentes del proyecto final

| # | Componente | Cómo se resolvió | Código principal |
|---|---|---|---|
| 1 | Autenticación (3 roles) | Firebase Authentication con correo y contraseña: registro, ingreso, cierre de sesión y recuperación por correo (`sendPasswordResetEmail`). El rol y el estado viven en `usuarios/{uid}`; el vendedor nuevo queda pendiente de aprobación. | `datos/RepositorioUsuarios`, `ui/auth/*` |
| 2 | Ingreso con huella | `BiometricPrompt` (biometría fuerte) + llave AES del **Android Keystore** que solo se usa tras una huella válida y se invalida si se registran huellas nuevas. La huella reabre la sesión de Firebase guardada; la contraseña nunca se guarda. | `ui/comun/Biometria`, `ui/auth/IngresoActivity` |
| 3 | CRUD de usuarios (administrador) | Listar, crear, editar, aprobar/rechazar vendedores y eliminar, en Firestore. Las cuentas nuevas se crean con una **instancia secundaria de FirebaseApp** para no cerrar la sesión del administrador, y el titular recibe un enlace para crear su contraseña. | `datos/RepositorioUsuarios`, `ui/admin/*` |
| 4 | CRUD de productos con imágenes | Productos en Firestore; foto desde la **cámara** o la **galería** subida a **Firebase Storage** (`productos/{id}.jpg`) y mostrada con Coil. Moderación del administrador. | `datos/RepositorioProductos`, `datos/remoto/Archivos`, `ui/vendedor/FormularioProductoFragment` |
| 5 | Carrito y órdenes | Carrito guardado en `carritos/{uid}`; pedidos en `pedidos/{id}` con historial, estados y descuento de existencias en una transacción. | `datos/Carrito`, `datos/RepositorioPedidos` |
| 6 | Geolocalización | Permiso en tiempo de ejecución, `FusedLocationProviderClient` + `Geocoder` (sin Google Maps ni clave de API) para llenar la dirección de entrega; latitud y longitud quedan en el pedido. Si se niega el permiso, la dirección se escribe a mano. | `datos/Ubicacion`, `ui/comprador/PasarelaFragment` |
| 7 | Pasarela de pagos | **Wompi** (sandbox): la Cloud Function `crearPago` recalcula el total y calcula la firma de integridad con un secreto que no está en el APK; el comprador paga en el **Web Checkout** dentro de un WebView; con el id de la transacción, la función `verificarPago` consulta Wompi, comprueba referencia y monto y guarda el resultado (aprobada, rechazada o pendiente) antes de mostrar la confirmación. | `functions/src/index.ts`, `datos/pagos/PasarelaWompi`, `ui/comprador/PagoWompiActivity` |

## Tecnologías

| Elemento | Versión |
|---|---|
| Lenguaje | Kotlin 2.2, corrutinas y Flow |
| Interfaz | Layouts XML con Material Design 3 (Material Components 1.12) |
| SDK | `minSdk` 26 · `targetSdk` 36 · `compileSdk` 36 |
| Compilación | Gradle 8.14.3 · Android Gradle Plugin 8.13 · JDK 21 |
| Firebase | BoM 34.19: Authentication, Cloud Firestore, Storage, Cloud Functions (plugin `com.google.gms.google-services` 4.4) |
| Cloud Functions | TypeScript, Node.js 22, `firebase-functions` 7 y `firebase-admin` 14, región `us-central1` |
| Otras bibliotecas | Play Services Location 21.3, kotlinx-coroutines-play-services 1.10, Coil 2.7, AppCompat, ConstraintLayout, RecyclerView, Navigation Component, Biometric, SplashScreen, Lifecycle |
| Pasarela | Wompi (Web Checkout, sandbox) |

## Configuración

El repositorio **no** incluye credenciales. El proyecto de Firebase es `sabor-natural-2a530` (ver `.firebaserc`).
Para compilar una app que se conecte hay que agregar (la guía paso a paso está en el documento de entrega):

1. **Firebase**: descargar `google-services.json` (app Android `com.sabornatural.tienda`) y copiarlo en
   `app/google-services.json`. Está en `.gitignore`. Sin ese archivo el proyecto **compila igual**, pero la app
   avisa al abrirse que falta configurar Firebase.
2. **Wompi**: agregar al final de `local.properties` (no se sube a git) la llave **pública** de sandbox, como
   muestra `local.properties.example`:

   ```
   WOMPI_PUBLIC_KEY=pub_test_...
   ```

   Gradle la pasa al código como `BuildConfig.WOMPI_PUBLIC_KEY`. La llave privada y los secretos **no** van en la app.
3. **Cloud Functions** (requiere el plan Blaze y Firebase CLI):

   ```
   npm install -g firebase-tools
   firebase login
   firebase functions:secrets:set WOMPI_INTEGRITY_SECRET
   firebase deploy --only functions,firestore:rules,storage
   ```

   El secreto de integridad lo pide la consola sin mostrarlo y queda en Secret Manager. `firebase.json` compila el
   TypeScript antes de desplegar (`npm --prefix functions run build`; la primera vez, `npm --prefix functions install`).

En la consola de Firebase: **Authentication > Correo/contraseña**, **Firestore** y **Storage** activos (Storage y
Functions exigen el plan Blaze). Las reglas [`firestore.rules`](firestore.rules) y [`storage.rules`](storage.rules)
se publican con el `deploy` anterior o pegándolas en la consola.

### Pagos con Wompi y Cloud Functions

| Función | Tipo | Qué hace |
|---|---|---|
| `crearPago` | callable | Verifica que el pedido sea de quien llama, vuelve a leer precio y existencias de cada producto en Firestore, recalcula el total con el domicilio, genera la referencia `SN-{pedido}-{marca de tiempo}` y la firma `SHA256(referencia + montoEnCentavos + COP + secreto)`. Devuelve los datos para el Web Checkout. |
| `verificarPago` | callable | Consulta `GET /v1/transactions/{id}` en Wompi, exige que referencia, monto y moneda coincidan con el pedido y guarda `estadoPago`, `transaccionId`, `metodoPago` y `fechaPago`. Si fue aprobada confirma el pedido y descuenta existencias en una transacción. Es idempotente; la app la repite cada 2,5 s mientras siga `PENDING`. |
| `webhookWompi` | HTTPS (opcional) | Recibe `transaction.updated`, valida el checksum con `WOMPI_EVENTS_SECRET` y aplica el mismo resultado. **No está configurado**: Wompi admite una sola URL de eventos por comercio y la del comercio de sandbox ya la usa otro proyecto, así que la confirmación se hace con `verificarPago`. |

La URL de regreso del checkout es `https://sabornatural.app/pago-resultado`: no necesita existir, el WebView la
intercepta y toma el `id` de la transacción.

### Primer administrador y datos de ejemplo

Las reglas no dejan registrarse como administrador. Para preparar un proyecto nuevo hay una **pantalla oculta**
(`ui/admin/ConfiguracionInicialActivity`, lógica en `datos/Semilla.kt`):

1. Crear una cuenta normal (comprador) en la app e ingresar.
2. En **Perfil**, mantener presionado el avatar → *Configuración inicial*.
3. *Convertirme en el primer administrador*: funciona una sola vez por proyecto (las reglas lo permiten solo mientras
   no exista `configuracion/administrador`). Alternativa: cambiar a mano el campo `rol` a `ADMINISTRADOR` en la consola.
4. *Sembrar datos de ejemplo*: crea en Firestore las categorías, las cuentas de prueba, 18 productos y 8 pedidos a
   partir de `app/src/main/assets/datos_demo/*.json`. Se puede repetir sin duplicar.

## Cuentas de prueba

Las crea la siembra. Todas usan **la contraseña que el administrador escribe al sembrar** (no está en el código ni
en el repositorio).

| Rol | Correo |
|---|---|
| Administrador | la cuenta que se convirtió en el primer administrador |
| Vendedor | `vendedor@sabornatural.co` (también `panaderia@` y `semillas@sabornatural.co`) |
| Comprador | `comprador@sabornatural.co` |
| Vendedor pendiente de aprobación | `pendiente@sabornatural.co` |
| Comprador suspendido | `daniela.vargas@correo.co` |

Los correos de prueba no existen: para probar la recuperación de contraseña use una cuenta con un correo real.

## Cómo abrirlo y ejecutarlo

1. Abrir la carpeta del proyecto en Android Studio.
2. Si Android Studio pide un JDK, elegir **JDK 21** (`Settings > Build Tools > Gradle > Gradle JDK`).
   El proyecto lo declara en `gradle/gradle-daemon-jvm.properties`, porque Gradle 8.14 no corre sobre JDK 25.
3. Ejecutar la configuración `app` en un emulador con Google Play o en un teléfono con Android 8.0 o superior.

Para generar el APK: `Build > Build App Bundle(s) / APK(s) > Build APK(s)`, o desde la terminal:

```
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

**Huella en el emulador:** registrar una huella en `Ajustes > Seguridad > Pixel Imprint` (pide un PIN) y usar los
controles extendidos del emulador (`... > Fingerprint > Touch sensor`). En un teléfono físico basta con tener una
huella registrada en los ajustes del sistema.

## Datos en Firestore

| Colección | Documento | Contenido |
|---|---|---|
| `usuarios` | `{uid}` | nombre, apellido, correo, teléfono, `rol`, `estado`, fecha de registro, `fotoUrl` |
| `productos` | id automático | datos del producto, `vendedorId`, `estado` de publicación, `imagenUrl` (Storage) |
| `pedidos` | id automático | comprador, vendedor, líneas, totales, `estado`, `estadoPago`, `referenciaPago` y `transaccionId` (Wompi), dirección, `latitud`, `longitud` |
| `carritos` | `{uid}` | líneas del carrito del comprador |
| `categorias` | nombre | las 6 categorías del catálogo |
| `configuracion` | `administrador` | uid del primer administrador |

Las pantallas leen los datos con *snapshot listeners* expuestos como `Flow` (`datos/remoto/ColeccionEnVivo.kt`),
así se actualizan solas cuando otro usuario cambia algo (por ejemplo, el vendedor despacha y el comprador lo ve).
Las escrituras son funciones `suspend` que se lanzan desde las pantallas con un diálogo de carga y manejo de errores
(`ui/comun/Carga.kt`); las listas muestran *Cargando…* o el error con *Reintentar* (`ui/comun/Estados.kt`).

### Limitaciones conocidas

- En el sandbox se usa el comercio de pruebas de Wompi de otro proyecto del autor, por eso el Web Checkout muestra
  "Pago a **CONSENTIDO VEGANO**". Con un comercio propio de Sabor Natural mostraría su nombre; el código no cambia
  (solo la llave pública y el secreto de integridad).

- La confirmación del pago la pide la app a `verificarPago` al volver del checkout. Si el comprador cierra la app
  antes de volver, el pedido queda en *Pago pendiente*; con el webhook configurado se confirmaría solo.
- El SDK cliente de Firebase no puede borrar la cuenta de Authentication de **otro** usuario: cuando el administrador
  elimina un usuario se borra su perfil (y ya no puede ingresar); el registro de Authentication se borra desde la consola.

## Pantallas

Cada rol tiene su propia Activity con **barra de navegación inferior** (BottomNavigationView) y **menú lateral**
(DrawerLayout + NavigationView); sus pantallas son Fragments conectados por un grafo de Navigation Component
(`res/navigation`). Los códigos de mockup remiten al documento técnico de planeación.

| Rol | Pantalla | Mockup | Clase |
|---|---|---|---|
| Todos | Splash animado | C-01 | `ui/inicio/SplashActivity` |
| Todos | Bienvenida (ingresar, crear cuenta, explorar sin cuenta) | C-01 | `ui/inicio/BienvenidaActivity` |
| Todos | Ingreso con validaciones y bloqueo tras 5 intentos | C-02 | `ui/auth/IngresoActivity` |
| Todos | Ingreso con huella (BiometricPrompt + Keystore) | C-03 | `ui/comun/Biometria` |
| Todos | Recuperar contraseña | C-02 | `ui/auth/RecuperarClaveActivity` |
| Todos | Crear cuenta de comprador o vendedor | C-04 | `ui/auth/RegistroActivity` |
| Todos | Perfil, editar perfil (cámara o galería), seguridad y privacidad | B-10 a B-12 | `ui/compartido/PerfilFragment`, `EditarPerfilFragment` |
| Comprador | Inicio | B-01 | `ui/comprador/InicioFragment` |
| Comprador | Catálogo con búsqueda y filtros | B-01 | `ui/comprador/CatalogoFragment` |
| Comprador | Detalle del producto | B-02 | `ui/comprador/DetalleProductoFragment` |
| Comprador | Carrito | B-03 | `ui/comprador/CarritoFragment` |
| Comprador | Pasarela (entrega con ubicación, medio de pago, facturación) | B-04, B-05 | `ui/comprador/PasarelaFragment` |
| Comprador | Web Checkout de Wompi | B-05 | `ui/comprador/PagoWompiActivity` |
| Comprador | Resultado del pago | B-06 | `ui/comprador/ConfirmacionPagoFragment` |
| Comprador | Mis pedidos y seguimiento | B-07, B-08 | `ui/compartido/ListaPedidosFragment`, `DetallePedidoFragment` |
| Vendedor | Panel con indicadores | V-01 | `ui/vendedor/PanelVendedorFragment` |
| Vendedor | Productos: crear, consultar, editar, pausar y eliminar | V-02 | `ui/compartido/GestionProductosFragment` |
| Vendedor | Formulario de producto con cámara y galería | V-03, V-04 | `ui/vendedor/FormularioProductoFragment` |
| Vendedor | Historial de pedidos recibidos y despacho | V-05, V-06 | `ui/compartido/ListaPedidosFragment`, `DetallePedidoFragment` |
| Administrador | Panel (dashboard) | A-01 | `ui/admin/PanelAdminFragment` |
| Administrador | Usuarios: listado, crear, editar, eliminar, aprobar vendedores | A-02 a A-04 | `ui/admin/UsuariosFragment`, `FormularioUsuarioFragment` |
| Administrador | Moderación de productos | A-05 | `ui/compartido/GestionProductosFragment` |
| Administrador | Reporte de ventas con gráfico | A-01, A-06 | `ui/admin/ReporteVentasFragment`, `GraficoBarras` |
| Oculta | Configuración inicial (primer administrador y datos de ejemplo) | — | `ui/admin/ConfiguracionInicialActivity` |

## Estructura

```
app/src/main/
├── assets/datos_demo/  datos de ejemplo (JSON) que siembra la configuración inicial
├── java/com/sabornatural/tienda/
│   ├── SaborNaturalApp.kt  inicializa Firebase
│   ├── datos/          modelos, repositorios (Firestore), carrito, sesión, ubicación, siembra
│   │   ├── remoto/     acceso a Firebase, mapeo de documentos, colecciones en vivo, Storage, errores
│   │   └── pagos/      integración con Wompi (llama a las Cloud Functions)
│   └── ui/
│       ├── inicio/     splash y bienvenida
│       ├── auth/       ingreso, recuperar contraseña y registro
│       ├── comprador/  Activity del comprador, pantallas y checkout de Wompi
│       ├── vendedor/   Activity del vendedor, panel y formulario de producto
│       ├── admin/      Activity del administrador, usuarios, reporte, gráfico y configuración inicial
│       ├── compartido/ pantallas que usan varios roles (perfil, pedidos, gestión de productos)
│       ├── comun/      base de pantallas, carga y errores, navegación, adaptadores, validaciones, biometría, cámara
│       └── estilos/    guía de estilos (mantener presionado el logo de la bienvenida)
└── res/                anim, color, drawable, layout, menu, mipmap, navigation, values
functions/              Cloud Functions en TypeScript (crearPago, verificarPago, webhookWompi)
firestore.rules         reglas de seguridad de Firestore por rol
storage.rules           reglas de seguridad de Storage
firebase.json, .firebaserc   configuración de Firebase CLI (proyecto sabor-natural-2a530)
```

## Sistema de diseño

Los colores, medidas, textos y estilos están centralizados en `res/values`; ningún layout usa colores ni textos fijos.

| Archivo | Contenido |
|---|---|
| `colors.xml` | Paleta de la marca (verde bosque `#1F4D2E`, crema `#F7F5EF`, dorado `#C9A24B`…) y colores semánticos |
| `dimens.xml` | Espaciados en `dp` y tamaños de texto en `sp` |
| `strings*.xml` | Todos los textos de la interfaz (por rol; `strings_integracion.xml` para Firebase, ubicación y pagos) |
| `styles.xml` | Formas, apariencias de texto y estilos de botones, campos, tarjetas, fichas y barras |
| `themes.xml` | Tema `Theme.SaborNatural` (Material 3) y tema de la pantalla de bienvenida |

## Seguridad del repositorio

El repositorio no contiene credenciales ni claves: `google-services.json`, `local.properties` (con la llave pública de
Wompi), los almacenes de claves (`*.jks`, `*.keystore`) y los archivos `.env` están excluidos en `.gitignore`. Los
secretos de Wompi (integridad y eventos) solo existen en Secret Manager, como secretos de las Cloud Functions.
