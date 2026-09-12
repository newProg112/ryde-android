plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val requestedRydeMode = providers.gradleProperty("rydeAppMode")
    .orElse("LOCAL_DEMO")
    .map { it.uppercase() }

require(requestedRydeMode.get() in setOf("LOCAL_DEMO", "CONNECTED")) {
    "rydeAppMode must be LOCAL_DEMO or CONNECTED"
}

val hasGoogleServicesConfig = file("google-services.json").isFile
if (hasGoogleServicesConfig) {
    apply(plugin = "com.google.gms.google-services")
}
require(requestedRydeMode.get() != "CONNECTED" || hasGoogleServicesConfig) {
    "CONNECTED debug mode requires the ignored app/google-services.json file"
}

android {
    namespace = "uk.rydeapp.ryde"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "uk.rydeapp.ryde"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "RYDE_APP_MODE", "\"${requestedRydeMode.get()}\"")
        }
        release {
            buildConfigField("String", "RYDE_APP_MODE", "\"LOCAL_DEMO\"")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
