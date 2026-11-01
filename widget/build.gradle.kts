plugins {
    alias(libs.plugins.bm.android.feature)
}

android {
    namespace = "com.eminallahverdi.balance.widget"
}

dependencies {
    implementation(project(":feature:transaction"))
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.work.runtime.ktx)
}
