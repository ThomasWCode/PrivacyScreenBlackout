plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Optional fixed debug keystore, so builds from different machines can be installed over each other.
val ciKeystorePath: String? = System.getenv("DEBUG_KEYSTORE_PATH")?.takeIf { it.isNotBlank() && file(it).exists() }

android {
    namespace = "io.github.thomaswcode.privacyscreenblackout"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.thomaswcode.privacyscreenblackout"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "0.1.${System.getenv("GITHUB_RUN_NUMBER") ?: "local"}"
    }

    signingConfigs {
        if (ciKeystorePath != null) {
            create("ciDebug") {
                storeFile = file(ciKeystorePath)
                storePassword = System.getenv("DEBUG_KEYSTORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("DEBUG_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("DEBUG_KEY_PASSWORD") ?: "android"
            }
        }
    }

    buildTypes {
        debug {
            if (ciKeystorePath != null) {
                signingConfig = signingConfigs.getByName("ciDebug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
