// Archivo de compilación raíz: solo declara los plugins; cada módulo los aplica.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    // Firebase: lee app/google-services.json. El módulo app lo aplica solo si el archivo existe.
    alias(libs.plugins.google.services) apply false
}
