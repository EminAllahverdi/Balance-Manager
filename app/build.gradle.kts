plugins {
    alias(libs.plugins.bm.android.application)
    alias(libs.plugins.bm.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.eminallahverdi.balance"

    defaultConfig {
        applicationId = "com.eminallahverdi.balance"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Until a release keystore is configured, release builds are signed with the debug key so they install.
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":feature:home"))
    implementation(project(":feature:transaction"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.profileinstaller)
}
