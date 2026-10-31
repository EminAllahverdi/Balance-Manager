plugins {
    alias(libs.plugins.bm.android.library)
    alias(libs.plugins.bm.hilt)
    alias(libs.plugins.bm.room)
}

android {
    namespace = "com.eminallahverdi.balance.core.database"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)
}
