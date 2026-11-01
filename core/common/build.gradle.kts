plugins {
    alias(libs.plugins.bm.android.library)
    alias(libs.plugins.bm.hilt)
}

android {
    namespace = "com.eminallahverdi.balance.core.common"
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.android)
    api(libs.androidx.biometric)
    api(libs.androidx.work.runtime.ktx)
}
