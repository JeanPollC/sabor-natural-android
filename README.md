# Sabor Natural · Tienda virtual Android

Aplicación Android nativa de **Sabor Natural**, una tienda virtual multivendedor de productos naturales y veganos.
Proyecto académico del curso *Desarrollo de Aplicaciones Móviles Nativas* (Fundación Universitaria Compensar).

Esta entrega corresponde al **frontend**: las interfaces gráficas de los tres roles (administrador, vendedor y
comprador) y las pantallas comunes, construidas a partir de los mockups del documento técnico de planeación.

## Tecnologías

| Elemento | Versión |
|---|---|
| Lenguaje | Kotlin 2.2 |
| Interfaz | Layouts XML con Material Design 3 (Material Components 1.12) |
| SDK | `minSdk` 26 · `targetSdk` 36 · `compileSdk` 36 |
| Compilación | Gradle 8.14.3 · Android Gradle Plugin 8.13 · JDK 21 |
| Bibliotecas | AppCompat, ConstraintLayout, RecyclerView, Navigation Component, Biometric, SplashScreen, Lifecycle |

## Cómo abrirlo y ejecutarlo

1. Abrir la carpeta del proyecto en Android Studio.
2. Si Android Studio pide un JDK, elegir **JDK 21** (`Settings > Build Tools > Gradle > Gradle JDK`).
   El proyecto lo declara en `gradle/gradle-daemon-jvm.properties`, porque Gradle 8.14 no corre sobre JDK 25.
3. Ejecutar la configuración `app` en un emulador o en un teléfono con Android 8.0 o superior.

Para generar el APK: `Build > Build App Bundle(s) / APK(s) > Build APK(s)`, o desde la terminal:

```
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Cuentas de prueba

En esta entrega la app no se conecta a la API: los usuarios salen de `app/src/main/assets/datos_demo/usuarios.json`,
donde la contraseña está guardada como hash SHA-256 (no en texto plano). Estas son las credenciales de prueba:

| Rol | Correo | Contraseña |
|---|---|---|
| Administrador | `admin@sabornatural.co` | `Admin2026*` |
| Vendedor | `vendedor@sabornatural.co` | `Vende2026*` |
| Comprador | `comprador@sabornatural.co` | `Compra2026*` |
| Vendedor pendiente de aprobación | `pendiente@sabornatural.co` | `Vende2026*` |

Todo lo que se crea o modifica en la app (cuentas, productos, pedidos, fotos) se guarda en memoria mientras la app
está abierta; al cerrarla vuelven los datos de prueba. La conexión con la API se hará en la siguiente fase del curso.

**Ingreso con huella en el emulador:** registrar una huella en `Ajustes > Seguridad > Pixel Imprint` (pide un PIN)
y usar los controles extendidos del emulador (`... > Fingerprint > Touch sensor`) para simular el toque.

## Pantallas

Cada rol tiene su propia Activity con **barra de navegación inferior** (BottomNavigationView) y **menú lateral**
(DrawerLayout + NavigationView); sus pantallas son Fragments conectados por un grafo de Navigation Component
(`res/navigation`). Los códigos de mockup remiten al documento técnico de planeación.

| Rol | Pantalla | Mockup | Clase |
|---|---|---|---|
| Todos | Splash animado | C-01 | `ui/inicio/SplashActivity` |
| Todos | Bienvenida (ingresar, crear cuenta, explorar sin cuenta) | C-01 | `ui/inicio/BienvenidaActivity` |
| Todos | Ingreso con validaciones y bloqueo tras 5 intentos | C-02 | `ui/auth/IngresoActivity` |
| Todos | Ingreso con huella (BiometricPrompt) | C-03 | `ui/comun/Biometria` |
| Todos | Recuperar contraseña | C-02 | `ui/auth/RecuperarClaveActivity` |
| Todos | Crear cuenta de comprador o vendedor | C-04 | `ui/auth/RegistroActivity` |
| Todos | Perfil, editar perfil (cámara o galería), seguridad y privacidad | B-10 a B-12 | `ui/compartido/PerfilFragment`, `EditarPerfilFragment` |
| Comprador | Inicio | B-01 | `ui/comprador/InicioFragment` |
| Comprador | Catálogo con búsqueda y filtros | B-01 | `ui/comprador/CatalogoFragment` |
| Comprador | Detalle del producto | B-02 | `ui/comprador/DetalleProductoFragment` |
| Comprador | Carrito | B-03 | `ui/comprador/CarritoFragment` |
| Comprador | Pasarela de pagos (entrega, medio de pago, facturación) | B-04, B-05 | `ui/comprador/PasarelaFragment` |
| Comprador | Pago aprobado | B-06 | `ui/comprador/ConfirmacionPagoFragment` |
| Comprador | Mis pedidos y seguimiento | B-07, B-08 | `ui/compartido/ListaPedidosFragment`, `DetallePedidoFragment` |
| Vendedor | Panel con indicadores | V-01 | `ui/vendedor/PanelVendedorFragment` |
| Vendedor | Productos: crear, consultar, editar, pausar y eliminar | V-02 | `ui/compartido/GestionProductosFragment` |
| Vendedor | Formulario de producto con cámara y galería | V-03, V-04 | `ui/vendedor/FormularioProductoFragment` |
| Vendedor | Historial de pedidos recibidos y despacho | V-05, V-06 | `ui/compartido/ListaPedidosFragment`, `DetallePedidoFragment` |
| Administrador | Panel (dashboard) | A-01 | `ui/admin/PanelAdminFragment` |
| Administrador | Usuarios: listado, crear, editar, eliminar, aprobar vendedores | A-02 a A-04 | `ui/admin/UsuariosFragment`, `FormularioUsuarioFragment` |
| Administrador | Moderación de productos | A-05 | `ui/compartido/GestionProductosFragment` |
| Administrador | Reporte de ventas con gráfico | A-01, A-06 | `ui/admin/ReporteVentasFragment`, `GraficoBarras` |

## Estructura

```
app/src/main/
├── assets/datos_demo/  usuarios, productos y pedidos de prueba (JSON)
├── java/com/sabornatural/tienda/
│   ├── SaborNaturalApp.kt  carga los datos de prueba al iniciar la app
│   ├── datos/          modelos, repositorios en memoria, carrito, sesión y preferencias
│   └── ui/
│       ├── inicio/     splash y bienvenida
│       ├── auth/       ingreso, recuperar contraseña y registro
│       ├── comprador/  Activity del comprador y sus pantallas
│       ├── vendedor/   Activity del vendedor, panel y formulario de producto
│       ├── admin/      Activity del administrador, usuarios, reporte y gráfico de barras
│       ├── compartido/ pantallas que usan varios roles (perfil, pedidos, gestión de productos)
│       ├── comun/      base de pantallas, navegación, adaptadores, validaciones, biometría, cámara
│       └── estilos/    guía de estilos (mantener presionado el logo de la bienvenida)
└── res/
    ├── anim/           animaciones del splash y de las transiciones entre pantallas
    ├── color/          selectores de color por estado (enfocado, marcado, deshabilitado)
    ├── drawable/       íconos vectoriales, logotipo y fondos con forma o degradado
    ├── layout/         activities, fragments, includes reutilizables, ítems de lista y hojas inferiores
    ├── menu/           barra inferior y menú lateral de cada rol
    ├── mipmap-*/       ícono adaptable de la aplicación
    ├── navigation/     grafos de navegación de cada rol
    └── values/         colors, dimens, strings (por rol, con plurals), styles y themes
```

## Sistema de diseño

Los colores, medidas, textos y estilos están centralizados en `res/values`; ningún layout usa colores ni textos fijos.

| Archivo | Contenido |
|---|---|
| `colors.xml` | Paleta de la marca (verde bosque `#1F4D2E`, crema `#F7F5EF`, dorado `#C9A24B`…) y colores semánticos |
| `dimens.xml` | Espaciados en `dp` y tamaños de texto en `sp` |
| `strings.xml` | Todos los textos de la interfaz |
| `styles.xml` | Formas, apariencias de texto y estilos de botones, campos, tarjetas, fichas y barras |
| `themes.xml` | Tema `Theme.SaborNatural` (Material 3) y tema de la pantalla de bienvenida |

## Seguridad del repositorio

El repositorio no contiene credenciales ni claves: `local.properties`, los almacenes de claves (`*.jks`, `*.keystore`)
y los archivos de configuración de servicios externos están excluidos en `.gitignore`.
