plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Número do build do GitHub Actions vira o versionCode, para que cada APK novo
// instale por cima do anterior sem perder as permissões.
val ciBuildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.hyper.island"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hyper.island"
        minSdk = 30
        targetSdk = 35
        versionCode = ciBuildNumber
        versionName = "0.1.$ciBuildNumber"
    }

    // Chave de desenvolvimento fixa: todo APK gerado pelo CI tem a mesma assinatura,
    // então as atualizações instalam por cima. Trocar por uma chave secreta na Etapa 5.
    signingConfigs {
        create("dev") {
            storeFile = rootProject.file("keystore/hyper-dev.jks")
            storePassword = "hyperdev"
            keyAlias = "hyper"
            keyPassword = "hyperdev"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("dev")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("dev")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
