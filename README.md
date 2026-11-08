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

## Estructura

```
app/src/main/
├── java/com/sabornatural/tienda/
│   └── ui/
│       ├── comun/      utilidades de interfaz compartidas
│       └── estilos/    guía de estilos (pantalla de referencia del sistema de diseño)
└── res/
    ├── color/          selectores de color por estado (enfocado, marcado, deshabilitado)
    ├── drawable/       íconos vectoriales, logotipo y fondos con forma o degradado
    ├── layout/         pantallas e ítems de lista
    ├── menu/           menús de navegación
    ├── mipmap-*/       ícono adaptable de la aplicación
    └── values/         colors, dimens, strings, styles y themes
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
