plugins {
    alias(libs.plugins.bm.android.library)
    alias(libs.plugins.bm.hilt)
}

android {
    namespace = "com.eminallahverdi.balance.core.datastore"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}
