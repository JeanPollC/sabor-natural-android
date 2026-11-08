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
| Bibliotecas | AppCompat, ConstraintLayout, RecyclerView, Navigation, Biometric, SplashScreen |

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

Las cuentas creadas desde la pantalla de registro se guardan solo en memoria mientras la app está abierta.

**Ingreso con huella en el emulador:** registrar una huella en `Ajustes > Seguridad > Pixel Imprint` (pide un PIN)
y usar los controles extendidos del emulador (`... > Fingerprint > Touch sensor`) para simular el toque.

## Pantallas

| Fase | Pantalla | Mockup | Requerimientos |
|---|---|---|---|
| 2 | Splash animado | C-01 | RF-INI-01 |
| 2 | Bienvenida | C-01 | RF-INI-01, RF-INI-02 |
| 2 | Ingreso con correo y contraseña, bloqueo tras 5 intentos | C-02 | RF-AUT-03 |
| 2 | Ingreso con huella (BiometricPrompt) | C-03 | RF-AUT-05 |
| 2 | Recuperar contraseña | C-02 | RF-AUT-06 |
| 2 | Crear cuenta de comprador o vendedor | C-04 | RF-AUT-01, RF-AUT-02 |

## Estructura

```
app/src/main/
├── assets/datos_demo/  usuarios de prueba (JSON)
├── java/com/sabornatural/tienda/
│   ├── datos/          modelos, repositorio de usuarios y sesión
│   └── ui/
│       ├── auth/       ingreso, huella, recuperar contraseña y registro
│       ├── comun/      pantalla base, navegación por rol, validaciones, biometría e insets
│       ├── estilos/    guía de estilos (mantener presionado el logo de la bienvenida)
│       ├── inicio/     splash y bienvenida
│       └── panel/      panel provisional por rol (se reemplaza en las fases 3 a 5)
└── res/
    ├── anim/           animaciones de entrada del splash
    ├── color/          selectores de color por estado (enfocado, marcado, deshabilitado)
    ├── drawable/       íconos vectoriales, logotipo y fondos con forma o degradado
    ├── layout/         pantallas, encabezados reutilizables (include) e ítems de lista
    ├── menu/           menús de navegación
    ├── mipmap-*/       ícono adaptable de la aplicación
    └── values/         colors, dimens, strings (con plurals), styles y themes
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
