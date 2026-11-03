plugins {
    alias(libs.plugins.bm.android.feature)
}

android {
    namespace = "com.eminallahverdi.balance.feature.onboarding"
}

dependencies {
    implementation(project(":feature:settings"))
    implementation(project(":core:backup"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
