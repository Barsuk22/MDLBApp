plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.app.mdlbapp.core.ui"
    compileSdk = 36            // как в app

    defaultConfig {
        minSdk = 26            // как в app
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" } // как в app
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    api(libs.androidx.material3)
    api(libs.androidx.ui)
    api(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
}