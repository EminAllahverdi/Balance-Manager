plugins {
    alias(libs.plugins.bm.android.feature)
}

android {
    namespace = "com.eminallahverdi.balance.feature.settings"
}

dependencies {
    implementation(project(":importer:moneymanager"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
