plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.mdlb.core.ui"
    compileSdk = /* как в твоём :app */ 36

    defaultConfig { minSdk = /* как в :app */ 26 }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" } // как у тебя для Compose
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    api(libs.androidx.material3)
    api(libs.androidx.ui)
    api(libs.androidx.ui.tooling.preview)
    // если нужно activity-compose:
    implementation(libs.androidx.activity.compose)
}