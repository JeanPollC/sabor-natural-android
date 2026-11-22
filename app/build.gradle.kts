import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/*
 * Firebase: el plugin google-services convierte app/google-services.json en recursos de la app.
 * Ese archivo es propio de cada proyecto de Firebase y NO se sube al repositorio (.gitignore).
 * Si no está, el proyecto igual compila; la app avisa al abrirse que falta configurar Firebase.
 */
val hayGoogleServices = file("google-services.json").exists()
if (hayGoogleServices) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.warn("Sabor Natural: falta app/google-services.json; la app compila pero no podrá conectarse a Firebase.")
}

/*
 * Llave PÚBLICA de Wompi: se lee de local.properties (que no se sube a git) y llega al código como
 * constante de BuildConfig. Nunca se escribe en el código fuente.
 *   WOMPI_PUBLIC_KEY=pub_test_...   (pub_test_ = sandbox; pub_prod_ = producción)
 * La llave privada y los secretos de integridad y eventos NO van en la app: viven solo como
 * secretos de las Cloud Functions (carpeta functions/).
 */
val propiedadesLocales = Properties().apply {
    val archivo = rootProject.file("local.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}
fun local(clave: String, porDefecto: String = ""): String =
    propiedadesLocales.getProperty(clave, porDefecto).trim().replace("\"", "")

android {
    namespace = "com.sabornatural.tienda"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sabornatural.tienda"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "2.0.0"

        buildConfigField("String", "WOMPI_PUBLIC_KEY", "\"${local("WOMPI_PUBLIC_KEY", local("wompi.publicKey"))}\"")
        buildConfigField("boolean", "FIREBASE_CONFIGURADO", hayGoogleServices.toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core.splashscreen)

    // Firebase (versiones alineadas por el BoM): autenticación, base de datos y archivos
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    // Cloud Functions: crearPago y verificarPago (firma y confirmación de Wompi en el servidor)
    implementation(libs.firebase.functions)
    // Corrutinas sobre las tareas de Firebase y Play Services (Task.await())
    implementation(libs.kotlinx.coroutines.play.services)

    // Ubicación del dispositivo (FusedLocationProviderClient), sin Google Maps ni clave de API
    implementation(libs.play.services.location)

    // Carga de las fotos de Firebase Storage en los ImageView
    implementation(libs.coil)

    testImplementation(libs.junit)
}
