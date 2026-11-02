plugins {
    alias(libs.plugins.bm.android.feature)
}

android {
    namespace = "com.eminallahverdi.balance.feature.accounts"
}

dependencies {
    implementation(project(":feature:history"))
}
